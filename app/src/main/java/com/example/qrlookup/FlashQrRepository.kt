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
    val corrigerEmplacement: Boolean,
    val nouvelEtgId: Int?,             // null si pas de nouveau rangement
    val corrigerTech: Boolean,
    val corrigerLivraison: Boolean,
    val techAlias: String? = null      // si tu veux recaler QfaPECPar aussi
)
class FlashQrRepository {
    // À adapter avec ton URL / user / pwd ou ton helper existant
    private fun getSqlConnection(): Connection {
        // return MySqlHelper.getConnection()
        return DriverManager.getConnection("jdbc:sqlserver://10.135.214.34:1433;databaseName=SIA;user=russe;password=cia")
    }
    suspend fun diagnostiquerParFqrId(fqrId: Int?): FlashQrDiagnostic? =
        withContext(Dispatchers.IO) {
            //val conn = getSqlConnection()
            Class.forName("net.sourceforge.jtds.jdbc.Driver")
            val url = "jdbc:jtds:sqlserver://10.135.214.34:1433/SIA"

            DriverManager.getConnection(url, "russe", "cia").use { conn ->
                conn.use { c ->
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

                    c.prepareStatement(sql).use { stmt ->
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
        }

    suspend fun appliquerCorrections(options: CorrectionOptions, diag: FlashQrDiagnostic) =
        withContext(Dispatchers.IO) {
            val conn = getSqlConnection()
            conn.use { c ->
                c.autoCommit = false
                try {
                    // 1) Correction emplacement
                    if (options.corrigerEmplacement && options.nouvelEtgId != null) {
                        val sql = "UPDATE tFlashQR SET EtgId = ? WHERE FqrId = ?"
                        c.prepareStatement(sql).use { stmt ->
                            stmt.setInt(1, options.nouvelEtgId)
                            stmt.setInt(2, options.fqrId)
                            stmt.executeUpdate()
                        }
                    }

                    // 2) Correction TECH (intervention terminée)
                    if (options.corrigerTech && diag.affDateFin != null) {
                        val sql = """
                            UPDATE tFlashQR
                            SET QfaPECTech = ?, 
                                QfaPECPar  = ISNULL(QfaPECPar, ?) 
                            WHERE FqrId = ?
                        """.trimIndent()
                        c.prepareStatement(sql).use { stmt ->
                            stmt.setTimestamp(1, Timestamp(diag.affDateFin.time))
                            stmt.setString(2, options.techAlias ?: "")
                            stmt.setInt(3, options.fqrId)
                            stmt.executeUpdate()
                        }
                    }

                    // 3) Correction LIVRAISON
                    if (options.corrigerLivraison) {
                        if (diag.expdDte != null) {
                            val sql = """
                                UPDATE tFlashQR
                                SET QfaPECLivr = ?, 
                                    QfaPECPar = '',
                                    EtgId = 0
                                WHERE FqrId = ?
                            """.trimIndent()
                            c.prepareStatement(sql).use { stmt ->
                                stmt.setTimestamp(1, Timestamp(diag.expdDte.time))
                                stmt.setInt(2, options.fqrId)
                                stmt.executeUpdate()
                            }
                        } else {
                            val sql = """
                                UPDATE tFlashQR
                                SET QfaPECLivr = GETDATE(),
                                    QfaPECPar = '',
                                    EtgId = 0
                                WHERE FqrId = ?
                            """.trimIndent()
                            c.prepareStatement(sql).use { stmt ->
                                stmt.setInt(1, options.fqrId)
                                stmt.executeUpdate()
                            }
                        }
                    }

                    c.commit()
                } catch (ex: Exception) {
                    c.rollback()
                    throw ex
                } finally {
                    c.autoCommit = true
                }
            }
        }


}