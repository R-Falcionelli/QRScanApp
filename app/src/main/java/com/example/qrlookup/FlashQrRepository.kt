package com.example.qrlookup
import android.content.Context
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Timestamp
import java.sql.Date

data class FlashQrDiagnostic(
    val fqrId: Int,
    val qrId: String,
    val etgId: Int?,
    val affId: String?,
    val qfaPECTech: java.sql.Date?,
    val qfaPECLivr: java.sql.Date?,
    val affDateFin: java.sql.Date?,
    val expdDte: java.sql.Date?,
    val anomalieTech: Boolean,
    val anomalieLivraison: Boolean
)

data class CorrectionOptions(
    val fqrId: Int,

    val updateTechAlias: Boolean,
    val deleteTechAlias: Boolean,
    val techAlias: String?,
    val updateTechDate: Boolean,
    val techDate: java.util.Date? = null,
    val updateTechDateFromAffDateFin: Boolean,
    val updateLivrDateFromExpdDte: Boolean,
    val livrDate: java.util.Date? = null,
    val deleteTechDate: Boolean,
    val deleteLivrDate: Boolean,

    val updateEmplacement: Boolean,
    val etgCode: String?,
    val etgId: Int?
)
class FlashQrRepository {
    // À adapter avec URL / user / pwd ou ton helper existant
    suspend fun checkTechExists(alias: String): Boolean =
        withContext(Dispatchers.IO) {
            Class.forName("net.sourceforge.jtds.jdbc.Driver")
            val url = "jdbc:jtds:sqlserver://10.135.214.34:1433/SIA"

            DriverManager.getConnection(url, "russe", "cia").use { conn ->
                val sql = """SELECT 1 from tEmployee where EmpInit = ?""".trimIndent()

                conn.prepareStatement(sql).use { stmt ->

                    // 🔹 ICI : remplacement du ? par la valeur du paramètre
                    stmt.setString(1, alias)

                    val rs = stmt.executeQuery()
                    rs.next()

                    rs.getInt(1) > 0
                }
            }
        }

    suspend fun checkEtageresExists(etgCode: String): Int? =
        withContext(Dispatchers.IO) {
            Class.forName("net.sourceforge.jtds.jdbc.Driver")
            val url = "jdbc:jtds:sqlserver://10.135.214.34:1433/SIA"

            DriverManager.getConnection(url, "russe", "cia").use { conn ->
                val sql = """SELECT EtgId from trEtageres where EtgCode = ?""".trimIndent()

                conn.prepareStatement(sql).use { stmt ->

                    // 🔹 ICI : remplacement du ? par la valeur du paramètre
                    stmt.setString(1, etgCode)

                    val rs = stmt.executeQuery()
                    if (rs.next()) {rs.getInt(1)} else { null }
                }
            }
        }

    suspend fun diagnostiquerParFqrId(fqrId: Int?): FlashQrDiagnostic? =
        withContext(Dispatchers.IO) {
            //val conn = getSqlConnection()
            Class.forName("net.sourceforge.jtds.jdbc.Driver")
            val url = "jdbc:jtds:sqlserver://10.135.214.34:1433/SIA"

            DriverManager.getConnection(url, "russe", "cia").use { conn ->
                    val sql = """
                    SELECT 
                        f.FqrId, f.QrId, f.EtgId,
                        f.QfaPECTech, f.QfaPECLivr,
                        f.AffId,
                        a.AffDateFin, a.ExpdDte
                    FROM tFlashQR f
                    LEFT OUTER JOIN tAffaire a ON a.AffId = f.AffId
                    WHERE f.FqrId = ?
                    ORDER BY f.FqrId DESC
                """.trimIndent()

                    val fqr = fqrId
                    if (fqr == null || fqr <= 0) return@withContext null

                    conn.prepareStatement(sql).use { stmt ->
                        stmt.setInt(1, fqrId)
                        val rs = stmt.executeQuery()
                        if (!rs.next()) return@withContext null

                        fun getDate(col: String): Date? =
                            rs.getTimestamp(col)?.time?.let { Date(it) }

                        val qfaPECTech = getDate("QfaPECTech")
                        val qfaPECLivr = getDate("QfaPECLivr")
                        val affDateFin = getDate("AffDateFin")
                        val expdDte = getDate("ExpdDte")

                        val anomalieTech = (affDateFin != null && qfaPECTech == null)
                        val anomalieLivraison = (expdDte != null && qfaPECLivr == null)

                        FlashQrDiagnostic(
                            fqrId = rs.getInt("FqrId"),
                            qrId = rs.getString("QrId"),
                            etgId = rs.getInt("EtgId").let { if (rs.wasNull()) null else it },
                            affId = rs.getString("AffId"),
                            qfaPECTech = qfaPECTech,
                            qfaPECLivr = qfaPECLivr,
                            affDateFin = affDateFin,
                            expdDte = expdDte,
                            anomalieTech = anomalieTech,
                            anomalieLivraison = anomalieLivraison
                        )
                    }
            }
        }

    suspend fun appliquerCorrections(options: CorrectionOptions, diag: FlashQrDiagnostic) =
        withContext(Dispatchers.IO) {
            Class.forName("net.sourceforge.jtds.jdbc.Driver")
            val url = "jdbc:jtds:sqlserver://10.135.214.34:1433/SIA"

            DriverManager.getConnection(url, "russe", "cia").use { conn ->
                conn.autoCommit = false
                try {
                    // 1) Correction emplacement
                    if (options.updateEmplacement && options.etgId != null) {
                        val sql = "UPDATE tFlashQR SET EtgId = ? WHERE FqrId = ?"
                        conn.prepareStatement(sql).use { stmt ->
                            stmt.setInt(1, options.etgId)
                            stmt.setInt(2, options.fqrId)
                            stmt.executeUpdate()
                        }
                    }

                    // 2) Correction TECH (intervention terminée)
                    val dt = options.techDate
                    if (options.updateTechDate && dt != null) {
                        val sql = """
                            UPDATE tFlashQR
                            SET QfaPECTech = ?, 
                                QfaPECPar = ? 
                            WHERE FqrId = ?
                        """.trimIndent()
                        conn.prepareStatement(sql).use { stmt ->
                            stmt.setTimestamp(1, Timestamp(dt.time))
                            stmt.setString(2, options.techAlias ?: "")
                            stmt.setInt(3, options.fqrId)
                            stmt.executeUpdate()
                        }
                    }

                    // 3) Correction LIVRAISON
                    val dl = options.livrDate
                    if (options.updateLivrDateFromExpdDte && dl != null) {
                        val sql = """
                            UPDATE tFlashQR
                            SET QfaPECLivr = ?, 
                                QfaPECPar = '',
                                EtgId = 0
                            WHERE FqrId = ?
                        """.trimIndent()
                        conn.prepareStatement(sql).use { stmt ->
                            stmt.setTimestamp(1, Timestamp(dl.time))
                            stmt.setInt(2, options.fqrId)
                            stmt.executeUpdate()
                        }
                    }

                    // 4) Correction TECH (suppression du tech)
                    if (options.deleteTechAlias) {
                        val sql = """
                            UPDATE tFlashQR
                            SET QfaPECPar = '' 
                            WHERE FqrId = ?
                        """.trimIndent()
                        conn.prepareStatement(sql).use { stmt ->
                            stmt.setInt(1, options.fqrId)
                            stmt.executeUpdate()
                        }
                    }

                    // 5) Suppression date de prise en charge technicien
                    if (options.deleteTechDate) {
                        val sql = """
                            UPDATE tFlashQR
                            SET QfaPECPar = '', QfaPECtech = null 
                            WHERE FqrId = ?
                        """.trimIndent()
                        conn.prepareStatement(sql).use { stmt ->
                            stmt.setInt(1, options.fqrId)
                            stmt.executeUpdate()
                        }
                    }

                    // 6) Suppression date de prise en livraison
                    if (options.deleteLivrDate) {
                        val sql = """
                            UPDATE tFlashQR
                            SET QfaPECLivr = null 
                            WHERE FqrId = ?
                        """.trimIndent()
                        conn.prepareStatement(sql).use { stmt ->
                            stmt.setInt(1, options.fqrId)
                            stmt.executeUpdate()
                        }
                    }

                    conn.commit()
                } catch (ex: Exception) {
                    conn.rollback()
                    throw ex
                } finally {
                    conn.autoCommit = true
                }
            }
        }


}