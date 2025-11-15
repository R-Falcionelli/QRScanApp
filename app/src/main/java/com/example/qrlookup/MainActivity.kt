package com.example.qrlookup
import android.app.Activity
import android.content.Context
import android.database.SQLException
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.ColorRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2CreateOptions
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import java.io.File
import java.io.FileOutputStream
import java.sql.DriverManager
import java.sql.ResultSet
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.ProgressBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.hierynomus.msfscc.FileAttributes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.EnumSet

class MainActivity : AppCompatActivity() {
    data class AffaireInfo(
        var etgCode: String = "",
        var numfi: String = "",
        var numAff: String = "",
        var client: String = "",
        var appareil: String = "",
        var marque: String = "",
        var type: String = "",
        var serie: String = "",
        var marquage: String = "",
        var numbl: String = "",
        var datebl: String = "",
        var datecrea: String = "",
        var par: String = "",
        var numfact: String = "",
        var datefact: String = "",
        var positaff: String = "",
        var datefin: String = "",
        var datecf: String = "",
        var finterv: Boolean = false,
        var opreal: String = "",
        var docs: String = "",
        var conclusion: String = "",
        var dateentre: String = "",
        var dateenreg: String = ""
    )

    private var current_info: AffaireInfo? = null
    private lateinit var etSearch: EditText
    private lateinit var btnSearch: Button
    private lateinit var btnScanFi: Button

    // ✅ Nouveau launcher moderne pour récupérer le résultat du scan
    private val qrScanLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scannedCode = result.data?.getStringExtra("SCANNED_CODE")
                ?.trim()
                .orEmpty()

            if (scannedCode.isNotBlank()) {
                // 👉 Ici on considère que le QR contient un N°Affaire OR25090256 etc.
                loadByAffaireCode(scannedCode)
            } else {
                Toast.makeText(this, "QR vide ou illisible", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etSearch   = findViewById(R.id.etSearch)
        btnSearch  = findViewById(R.id.btnSearch)
        btnScanFi  = findViewById(R.id.btnScanFi)

        val btnInit = findViewById<Button>(R.id.btnInit)
        val tvResult: TextView = findViewById(R.id.tvResult)
        val tvNumAff: TextView = findViewById(R.id.tvNumAff)
        val tvNumFI: TextView = findViewById(R.id.tvNumFI)
        val tvClient: TextView = findViewById(R.id.tvClient)
        val tvAppareil: TextView = findViewById(R.id.tvAppareil)
        val tvMarque: TextView = findViewById(R.id.tvMarque)
        val tvType: TextView = findViewById(R.id.tvType)
        val tvSerie: TextView = findViewById(R.id.tvSerie)
        val tvMarquage: TextView = findViewById(R.id.tvMarquage)
        //val tvNumBL:TextView = findViewById(R.id.tvNumBL)
        val tvDateBL:TextView = findViewById(R.id.tvDateBL)
        val tvDateCrea: TextView = findViewById(R.id.tvDateCrea)
        val tvPar: TextView = findViewById(R.id.tvPar)
        val tvNumFact:TextView = findViewById(R.id.tvNumFact)
        val tvDateFact:TextView = findViewById(R.id.tvDateFact)
        val tvDateEnreg:TextView = findViewById(R.id.tvDateEnreg)
        val tvDateEntre:TextView = findViewById(R.id.tvDateEntre)
        val tvPosit: TextView = findViewById(R.id.tvPosit)
        val tvOpReal: TextView = findViewById(R.id.tvOpReal)
        //val tvDocs: TextView = findViewById( R.id.tvDocs)
        val tvDateFin: TextView = findViewById( R.id.tvDateFin)
        val tvConclusion: TextView = findViewById( R.id.tvConclusion)
        val tvDateCF: TextView = findViewById( R.id.tvDateCF)
        val chkFinterv: CheckBox = findViewById( R.id.chkFinterv)
        val chkCF: CheckBox = findViewById( R.id.chkCF)
        val tvLblEmp: TextView = findViewById( R.id.tvLblEmp)
        var found = true

        // Insertion automatique du '/' après les 2 premiers caractères
        etSearch.doAfterTextChanged { s ->
            s?.let {
                val text = it.toString()
                if (text.length == 2 && !text.contains("/")) {
                    etSearch.setText("$text/")
                    etSearch.setSelection(etSearch.text.length)
                }
            }
        }

        // 🚀 Lancement du scanner
        btnScanFi.setOnClickListener {
            val intent = Intent(this, QrScanActivity::class.java)
            qrScanLauncher.launch(intent)
        }

        btnSearch.setOnClickListener {
            val inputCode = etSearch.text.toString().trim()
            val info = AffaireInfo()

            hideKeyboard()

            btnInit.performClick()

            if (inputCode.length != 8) {
                Toast.makeText(this, "Veuillez saisir un code de 8 caractères", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Lancer la requête SQL dans un thread pour ne pas bloquer l'UI
            Thread {
                try {
                    // JDBC minimal
                    Class.forName("net.sourceforge.jtds.jdbc.Driver")
                    val url = "jdbc:jtds:sqlserver://10.135.214.34:1433/SIA"
                    val conn = DriverManager.getConnection(url, "russe", "cia")
                    val stmt = conn.prepareStatement("""
                        SELECT  trEtageres.EtgCode, tAffaire.AffID, tAffaire.AffNoFI,
                                isnull(tClient.CltCodMichelin, '') + ' ' + isnull(tClient.CltNom, '') as Client,
                                tAffaire.AffDesignation, 
                                tAffaire.AffMarque, tAffaire.AffType, tAffaire.AffSerie, tAffaire.AffNoClientInterne,
                                isnull(tAffaire.ExpdID, '') as NumBL, 
                                isnull(tAffaire.CltFactID, '') as NumFact,
                                case when tAffaire.ExpdDte is null then '' else convert(varchar(10), tAffaire.ExpdDte, 103) end as DateBL,
                                case when tBlE2MEntete.BlmDateCreation is null then '' else convert(varchar(10), tBlE2MEntete.BlmDateCreation, 103) end as DateCrea,
                                isnull(tBlE2MEntete.EmpID, '') as Par,
                                case when tAffaire.CltFactDte is null then '' else convert(varchar(10), tAffaire.CltFactDte, 103) end as DateFact,
                                case when tAffaire.AffDebutDte is null then '' else convert(varchar(10), tAffaire.AffDebutDte, 103) end as DateEntre,
                                case when tAffaire.AffDteEnreg is null then '' else convert(varchar(10), tAffaire.AffDteEnreg, 103) end as DateEnreg,
                                tAffaire.AffIntervTech [Term],
                                case when tAffaire.AffDateFin is null then '' else convert(varchar(10), tAffaire.AffDateFin, 103) end as DateFin,
                                case when tAffaire.AffDateCtrlFinal is null then '' else convert(varchar(10), tAffaire.AffDateCtrlFinal, 103) end as DateCF,
                                dbo.GetOpReal(tAffaire.AffID) as OpReal, dbo.GetDocs(tAffaire.AffID) [Docs], 
                                dbo.GetConclusion(tAffaire.AffID) [Conclusion],
                                cast(floor(tAffaire.PositAff) as varchar(4)) as PositAff
                        FROM tFlashQR
                        JOIN trEtageres ON tFlashQR.EtgId = trEtageres.EtgId
                        JOIN tAffaire ON tAffaire.AffID = tFlashQR.AffID
                        LEFT JOIN tBlE2MEntete on tBlE2MEntete.BlmRefE2M = tAffaire.ExpdID
                        JOIN tClient ON tClient.CltId = tAffaire.CltId
                        WHERE tAffaire.AffNoFI = ? and tAffaire.OffreID not in('RP', 'SRP', 'SFD', 'ADD')
                    """.trimIndent())

                    val stmt2 = conn.prepareStatement("""
                            SELECT tAffaire.AffID, tAffaire.AffNoFI,  tAffaire.AffDesignation, tAffaire.AffMarque, 
                                   tAffaire.AffType, tAffaire.AffSerie, tAffaire.AffNoClientInterne,
                                   isnull(tClient.CltCodMichelin, '') + ' ' + isnull(tClient.CltNom, '') as Client,
                                   isnull(tAffaire.ExpdID, '') as NumBL, 
                                   isnull(tAffaire.CltFactID, '') as NumFact,
                                   case when tAffaire.ExpdDte is null then '' else convert(varchar(10), tAffaire.ExpdDte, 103) end as DateBL,
                                   case when tBlE2MEntete.BlmDateCreation is null then '' else convert(varchar(10), tBlE2MEntete.BlmDateCreation, 103) end as DateCrea,
                                   isnull(tBlE2MEntete.EmpID, '') as Par,
                                   case when tAffaire.CltFactDte is null then '' else convert(varchar(10), tAffaire.CltFactDte, 103) end as DateFact,
                                   case when tAffaire.AffDebutDte is null then '' else convert(varchar(10), tAffaire.AffDebutDte, 103) end as DateEntre,
                                   case when tAffaire.AffDteEnreg is null then '' else convert(varchar(10), tAffaire.AffDteEnreg, 103) end as DateEnreg,
                                   tAffaire.AffIntervTech [Term], 
                                   case when tAffaire.AffDateFin is null then '' else convert(varchar(10), tAffaire.AffDateFin, 103) end as DateFin,
                                   case when tAffaire.AffDateCtrlFinal is null then '' else convert(varchar(10), tAffaire.AffDateCtrlFinal, 103) end as DateCF,
                                   dbo.GetOpReal(tAffaire.AffID) as OpReal, dbo.GetDocs(tAffaire.AffID) [Docs], 
                                   dbo.GetConclusion(tAffaire.AffID) [Conclusion],
                                   cast(floor(tAffaire.PositAff) as varchar(4)) as PositAff                                   
                            FROM tAffaire         
                            LEFT JOIN tBlE2MEntete on tBlE2MEntete.BlmRefE2M = tAffaire.ExpdID                                         
                            JOIN tClient ON tClient.CltId = tAffaire.CltId
                            WHERE tAffaire.AffNoFI = ? and tAffaire.OffreID not in('RP', 'SRP', 'SFD', 'ADD')
                        """.trimIndent())

                    stmt.setString(1, inputCode)
                    val rs = stmt.executeQuery()
                    if (rs.next()) {
                        found = true
                        info.etgCode = safeGetString(rs, "EtgCode")
                        fillFromResult(rs, info)
                        current_info = info
                        runOnUiThread {
                            createDocButtons(current_info?.docs.orEmpty())
                            createBLButton(current_info?.numbl.orEmpty())
                        }
                    } else {
                        stmt2.setString(1, inputCode)
                        val rs2 = stmt2.executeQuery()
                        if (rs2.next()) {
                            found = true
                            info.etgCode = "" // pas dispo ici
                            fillFromResult(rs2, info)
                            current_info = info
                            runOnUiThread {
                                createDocButtons(current_info?.docs.orEmpty())
                                createBLButton(current_info?.numbl.orEmpty())
                            }
                            rs2.close()
                            stmt2.close()
                        } else {
                            found = false
                        }
                    }
                    rs.close()
                    stmt.close()
                    conn.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                    info.etgCode = "Erreur : ${e.message}"
                }

                runOnUiThread {
                    // Affichage dans le TextView
                    tvResult.text = info.etgCode

                    // Changement dynamique de la couleur selon le résultat
                    if (found){
                        if (info.etgCode == "A00") {
                            tvResult.setBackgroundColor(Color.RED)
                            tvResult.setTextColor(Color.WHITE)
                            tvLblEmp.isVisible = true
                            tvResult.isVisible = true
                            Toast.makeText(this, "Appareil non rangé sur étagère ou expédié", Toast.LENGTH_LONG).show()
                        } else {
                            if (info.etgCode=="")
                            {
                                tvLblEmp.isVisible = false
                                tvResult.isVisible = false
                            } else {
                                tvResult.setBackgroundColor(Color.parseColor("#006400")) // vert foncé
                                tvResult.setTextColor(Color.WHITE)
                                tvLblEmp.isVisible = true
                                tvResult.isVisible = true
                            }
                        }

                        setLabelValueStyle(tvNumAff, "N°Affaire : ", info.numAff, labelBold = true, valueBold = true, R.color.labelfi,R.color.valuefi)
                        setLabelValueStyle(tvNumFI, "N°FI : ", info.numfi, labelBold = true, valueBold = true, R.color.labelfi,R.color.valuefi)
                        setLabelValueStyle(tvAppareil, "Appareil : ", info.appareil , true, false)
                        setLabelValueStyle(tvClient, "", info.client , false, true, R.color.black, R.color.client)
                        setLabelValueStyle(tvMarque, "Marque : ", info.marque , true, false, R.color.black, R.color.black)
                        setLabelValueStyle(tvType, "Type : ", info.type , true, false, R.color.black, R.color.black)
                        setLabelValueStyle(tvSerie, "N°Série : ", info.serie , true, false, R.color.black, R.color.black)
                        setLabelValueStyle(tvMarquage, "Marquage : ", info.marquage , true, false, R.color.black, R.color.black)
                        //setLabelValueStyle(tvNumBL, "N°BL : ", info.numbl , true, false, R.color.black, R.color.black)
                        setLabelValueStyle(tvDateEntre, "Entré le : ", info.dateentre , true, false,R.color.black, R.color.black)
                        setLabelValueStyle(tvDateEnreg, "Enregistré le : ", info.dateenreg , true, false,R.color.black, R.color.black)
                        setLabelValueStyle(tvDateBL, "", info.datebl , true, false,R.color.black, R.color.black)
                        setLabelValueStyle(tvDateCrea, "Créé le : ", info.datecrea , true, false,R.color.black, R.color.black)
                        setLabelValueStyle(tvPar, "Par : ", info.par , true, true, R.color.black, R.color.par)
                        setLabelValueStyle(tvNumFact, "N°Fact. : ", info.numfact , true, false, R.color.black, R.color.black)
                        setLabelValueStyle(tvDateFact, "", info.datefact , true, false, R.color.black, R.color.black)
                        setLabelValueStyle(tvPosit, "Position ", info.positaff , true, true, R.color.black, R.color.posit)
                        tvDateFin.text = info.datefin
                        tvDateCF.text = info.datecf
                        chkCF.isChecked = (info.datecf != "")
                        chkFinterv.isChecked = info.finterv
                        setLabelValueStyle(tvOpReal, "Opérations : ", info.opreal , true, false)
                        //setLabelValueStyle(tvDocs, "", info.docs , true, true, R.color.black, R.color.black)
                        runOnUiThread {
                            updateConclusion(info.conclusion.toString().trim().uppercase())
                        }
                    } else {
                        Toast.makeText(this, "N°FI inconnu", Toast.LENGTH_LONG).show()
                    }
                }

            }.start()
        }

        btnInit.setOnClickListener {
            tvNumAff.text = "N°Affaire"
            tvNumFI.text = "N°FI"
            tvClient.text = "Client"
            tvAppareil.text = "Appareil"
            tvMarque.text = "Marque"
            tvType.text = "Type"
            tvSerie.text = "N°Série"
            tvMarquage.text = "Marquage"
            //tvNumBL.text = "N°BL"
            tvDateBL.text = "Date BL"
            tvDateCrea.text = "BL créé le"
            tvPar.text = "Par"
            tvNumFact.text = "N°Fact."
            tvDateFact.text = "Date Facture"
            tvDateEntre.text = "Entré le"
            tvDateEnreg.text = "Enregistré le"
            tvPosit.text = "Position"
            tvDateFin.text = "Date Fin Interv."
            tvDateCF.text = "Date Ctrl.Final"
            chkCF.isChecked = false
            chkFinterv.isChecked = false
            tvOpReal.text = "Opérations"
            //tvDocs.text = "Docs"
            tvConclusion.text = "Conclusion"
            tvResult.text = ""
            etSearch.setText("")
        }
    }

    // Bouton N°BL
    private fun createBLButton(numbl: String) {
        val container = findViewById<LinearLayout>(R.id.BLContainer)
        container.removeAllViews()
        if (numbl.isBlank()) return

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, 0, 16, 14)   // ← espace entre boutons
        }

        val btn = Button(this).apply{
            text = numbl
            textSize = 12f
            setTextColor(Color.WHITE)
            minHeight = 0
            minimumHeight = 0
            setPadding(20, 8, 20, 8)
            background = ContextCompat.getDrawable(context, R.drawable.doc_button)
            layoutParams = params

            // 🔥 Ajout de l’icône à gauche
            setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_file_24dp, 0, 0, 0)
            compoundDrawablePadding = 10  // Espace entre icône et texte
        }

        btn.setOnClickListener { onBLlicked(numbl) }
        container.addView(btn)
    }

    private fun onBLlicked(numbl:String){
        val bl = current_info?.numbl?.trim().orEmpty()
    }

    // Sépare sur +, espaces, virgules, point-virgule (ex: "CV + RM, CEC")
    //val docs = Regex("[+ ,;]+").split(docsString).map { it.trim() }.filter { it.isNotEmpty() }
    private fun createDocButtons(docs: String) {
        val container = findViewById<LinearLayout>(R.id.docsContainer)
        container.removeAllViews()

        if (docs.isBlank()) return

        val docList = docs.split("+").map { it.trim() }

        for (doc in docList) {

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 16, 14)   // ← espace entre boutons
            }

            val btn = Button(this).apply {
                text = doc
                textSize = 12f
                setTextColor(Color.WHITE)
                minHeight = 0
                minimumHeight = 0
                setPadding(20, 8, 20, 8)
                background = ContextCompat.getDrawable(context, R.drawable.doc_button)
                layoutParams = params

                // 🔥 Ajout de l’icône à gauche
                setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_file_24dp, 0, 0, 0)
                compoundDrawablePadding = 10  // Espace entre icône et texte
            }

            btn.setOnClickListener { onDocClicked(doc) }
            container.addView(btn)
        }
    }

    private fun onDocClicked(docType: String) {
        val fi = current_info?.numfi?.trim().orEmpty()
        if (fi.isBlank()) {
            AlertDialog.Builder(this)
                .setTitle("Document $docType")
                .setMessage("Aucun N°FI en mémoire.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        showLoading("Recherche en cours…")
        Thread {
            val (zone, fileName) = findDocLocationForType(
                serverIp = "10.135.214.5",
                shareName = "DOCUMENTS",
                usernameWithDomain = "E2M\\RF",
                password = "BTf1vDt0,e",
                fiValue = fi,
                docType = docType
            )

            runOnUiThread {
                hideLoading()
                if (zone.isBlank()) {
                    AlertDialog.Builder(this)
                        .setTitle("Document $docType")
                        .setMessage("Non localisé sur le NAS.")
                        .setPositiveButton("OK", null)
                        .show()
                    return@runOnUiThread
                }

                val base = "Documents associes/Documents finaux"
                val yr = yearFromFi(fi)
                val dir = if (zone == "Validation" || zone == "Envoi") "$base/$zone/$docType" else "$base/$zone/$docType/$yr"

                AlertDialog.Builder(this)
                    .setTitle("Document $docType")
                    .setMessage("Zone : $zone\n\nDossier :\n$dir\n\nFichier :\n$fileName")
                    .setNegativeButton("Annuler", null)
                    .setNeutralButton("Partager/Télécharger") { _, _ ->
                        showLoading("Téléchargement en cours…")
                        // Télécharge en cache puis ouvre la feuille de partage
                        Thread {
                            val local = downloadDocFromServer(
                                serverIp = "10.135.214.5",
                                shareName = "Documents",
                                usernameWithDomain = "E2M\\RF",
                                password = "BTf1vDt0,e",
                                dirPath = dir,
                                fileName = fileName
                            )
                            runOnUiThread {
                                hideLoading()
                                if (local != null) sharePdf(local) else toast("Téléchargement échoué.")
                            }
                        }.start()
                    }
                    .setPositiveButton("Voir") { _, _ ->
                        showLoading("Chargement en cours…")
                        // Télécharge en cache puis ouvre direct le lecteur PDF
                        Thread {
                            val local = downloadDocFromServer(
                                serverIp = "10.135.214.5",
                                shareName = "Documents",
                                usernameWithDomain = "E2M\\RF",
                                password = "BTf1vDt0,e",
                                dirPath = dir,
                                fileName = fileName
                            )
                            runOnUiThread {
                                hideLoading()
                                if (local != null) openPdf(local) else toast("Téléchargement échoué.")
                            }
                        }.start()
                    }
                    .show()
            }
        }.start()
    }

    private fun yearFromFi(fi: String): String {
        val s = fi.trim().uppercase()
        if (s.length < 2) return ""
        val yPrefix = when (s[0]) {
            'F' -> "202"
            'G' -> "203"
            'H' -> "204"
            else -> return ""
        }
        return yPrefix + s[1]  // F5 -> 2025, G0 -> 2030, H0 -> 2040
    }

    private fun smbListDir(
        disk: com.hierynomus.smbj.share.DiskShare,
        dir: String
    ): List<String> {
        return try {
            val entries = disk.list(dir)
            Log.d("SMB", "OK list '$dir' (${entries.size} items)")
            entries.map { it.fileName }
        } catch (e: Exception) {
            Log.e("SMB", "FAIL list '$dir' : ${e.message}")
            emptyList()
        }
    }

    private fun buildDocDirs(zone: String, docType: String, year: String): List<String> {
        // on essaie avec et sans accent, pour être sûr
        val assocAcc = "Documents associés"
        val assocNo  = "Documents associes"
        val baseAcc  = "$assocAcc/Documents finaux"
        val baseNo   = "$assocNo/Documents finaux"

        val bases = listOf(baseAcc, baseNo)

        return if (zone == "Validation" || zone == "Envoi")  {
            bases.map { "$it/$zone/$docType" }
        } else {
            bases.map { "$it/$zone/$docType/$year" }
        }
    }

    private fun normalizeFi(fi: String) = fi.replace("/", "").trim()

    private fun showDocLocationPopup(doc: String, numFi: String) {
        AlertDialog.Builder(this)
            .setTitle("Emplacement du document $doc")
            .setMessage("Recherche en cours... (on code la détection NAS juste après)")
            .setPositiveButton("OK", null)
            .show()
    }
    fun safeGetString(rs: ResultSet, column: String): String {
        return try {
            val value = rs.getString(column)
            value ?: "" // si NULL => ""
        } catch (e: SQLException) {
            "" // si colonne inexistante => ""
        }
    }

    private fun extractIndex(fileName: String): Int {
        val m = Regex("-(\\d+)\\.pdf$", RegexOption.IGNORE_CASE).find(fileName)
        return m?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
    }

    fun safeGetBool(rs: ResultSet, column: String): Boolean {
        return try {
            val value = rs.getBoolean(column)
            value ?: false // si NULL => ""
        } catch (e: SQLException) {
            false // si colonne inexistante => ""
        }
    }

    fun fillFromResult(rs: ResultSet, info: AffaireInfo) {
        info.numAff = safeGetString(rs, "AffID")
        info.numfi = safeGetString(rs, "AffNoFI")
        info.client = safeGetString(rs, "Client")
        info.appareil = safeGetString(rs, "AffDesignation")
        info.marque = safeGetString(rs, "AffMarque")
        info.type = safeGetString(rs, "AffType")
        info.serie = safeGetString(rs, "AffSerie")
        info.marquage = safeGetString(rs, "AffNoClientInterne")
        info.dateentre = safeGetString(rs, "DateEntre")
        info.dateenreg = safeGetString(rs, "DateEnreg")
        info.numbl  = safeGetString(rs, "NumBL")
        info.datebl = safeGetString(rs, "DateBL")
        info.datecrea = safeGetString(rs, "DateCrea")
        info.par = safeGetString(rs, "Par")
        info.numfact = safeGetString(rs, "NumFact")
        info.datefact = safeGetString(rs, "DateFact")
        info.positaff = safeGetString(rs, "PositAff")
        info.datefin = safeGetString(rs, "DateFin")
        info.finterv = safeGetBool(rs, "Term")
        info.datecf = safeGetString(rs, "DateCF")
        info.docs = safeGetString(rs, "Docs")
        info.opreal = safeGetString(rs, "OpReal")
        info.conclusion = safeGetString(rs, "Conclusion")
    }

    fun updateConclusion(text: String) {
        val tvConclusion = findViewById<TextView>(R.id.tvConclusion)

        // Mettre le texte
        if (text != ""){
            tvConclusion.text = text
        } else {
            tvConclusion.text = "N/A"
        }

        // Changer la couleur de fond et du texte selon la valeur
        when (text) {
            "CONFORME" -> {
                tvConclusion.setBackgroundColor(ContextCompat.getColor(this, R.color.conclusion_conf))
                tvConclusion.setTextColor(Color.WHITE)
            }
            "CONFORME AVEC RESTRICTION" -> {
                tvConclusion.setBackgroundColor(ContextCompat.getColor(this, R.color.conclusion_confrest))
                tvConclusion.setTextColor(Color.WHITE)
            }
            "NON CONFORME" -> {
                tvConclusion.setBackgroundColor(ContextCompat.getColor(this, R.color.conclusion_nonconf))
                tvConclusion.setTextColor(Color.WHITE)
            }
            else -> {
                tvConclusion.setBackgroundColor(Color.TRANSPARENT)
                tvConclusion.setTextColor(Color.BLACK)
            }
        }

        // Ajuster dynamiquement la largeur selon le texte + padding
        tvConclusion.post {
            val scale = tvConclusion.context.resources.displayMetrics.density
            val extraPadding = (16 * scale).toInt() // 16dp de marge
            val textWidth = tvConclusion.paint.measureText(tvConclusion.text.toString())
            val params = tvConclusion.layoutParams
            params.width = (textWidth + 2 * extraPadding).toInt()
            tvConclusion.layoutParams = params
        }
    }

    fun hideKeyboard() {
        val view = this.currentFocus
        if (view != null) {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }

    fun setLabelValueStyle(
        textView: TextView,
        label: String,
        value: String,
        labelBold: Boolean = false,
        valueBold: Boolean = false,
        @ColorRes labelColor: Int? = null,
        @ColorRes valueColor: Int? = null
    ) {
        val fullText = "$label$value"
        val spannable = SpannableString(fullText)

        // --- Label ---
        val labelStyle = if (labelBold) Typeface.BOLD else Typeface.NORMAL
        spannable.setSpan(
            StyleSpan(labelStyle),
            0,
            label.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        labelColor?.let {
            val colorInt = ContextCompat.getColor(textView.context, it)
            spannable.setSpan(
                ForegroundColorSpan(colorInt),
                0,
                label.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        // --- Valeur ---
        val valueStyle = if (valueBold) Typeface.BOLD else Typeface.NORMAL
        spannable.setSpan(
            StyleSpan(valueStyle),
            label.length,
            fullText.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        valueColor?.let {
            val colorInt = ContextCompat.getColor(textView.context, it)
            spannable.setSpan(
                ForegroundColorSpan(colorInt),
                label.length,
                fullText.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        textView.text = spannable
    }

    private fun downloadDocFromServer(
        serverIp: String,           // "10.135.214.5" (évite le nom NetBIOS si DNS incertain)
        shareName: String,          // "Documents"
        usernameWithDomain: String, // "E2M\\RF"
        password: String,
        dirPath: String,            // "Documents finaux/Envoi/CV"
        fileName: String            // "F516903-CV-02.pdf"
    ): File? {
        return try {
            val client = SMBClient()
            client.connect(serverIp).use { conn ->
                val domain = usernameWithDomain.substringBefore('\\', "")
                val user   = usernameWithDomain.substringAfter('\\', usernameWithDomain)
                val auth = AuthenticationContext(user, password.toCharArray(), domain)
                val session = conn.authenticate(auth)

                session.connectShare(shareName).use { share ->
                    val disk = share as DiskShare
                    val remotePath = "$dirPath/$fileName" // IMPORTANT: séparateurs "/"

                    // --- Variante SMB2 (évite les "OpenDisposition"/"CreateOptions") ---
                    disk.openFile(
                        remotePath,
                        EnumSet.of(AccessMask.GENERIC_READ),
                        EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
                        SMB2ShareAccess.ALL, // ou EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ)
                        SMB2CreateDisposition.FILE_OPEN,
                        EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE)
                    ).use { fh ->
                        val outDir = File(cacheDir, "docs").apply { mkdirs() }
                        val outFile = File(outDir, fileName)

                        FileOutputStream(outFile).use { os ->
                            val buf = ByteArray(64 * 1024)
                            var offset = 0L
                            while (true) {
                                // read(buffer, fileOffset, bufferOffset, length)
                                val read = fh.read(buf, offset, 0, buf.size)
                                if (read <= 0) break
                                os.write(buf, 0, read)
                                offset += read
                            }
                            os.flush()
                        }
                        outFile
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun findDocLocationForType(
        serverIp: String,
        shareName: String,
        usernameWithDomain: String, // "E2M\\RF"
        password: String,
        fiValue: String,            // "F5/16903"
        docType: String             // "CV" / "RM" / ...
    ): Pair<String, String> {
        val fiNoSlash = normalizeFi(fiValue)
        val yr = yearFromFi(fiValue)
        val zones = listOf("Validation", "Envoi", "Solde", "Archive")
        val pattern = Regex("^${Regex.escape(fiNoSlash)}-${Regex.escape(docType)}(-\\d+)?\\.pdf$", RegexOption.IGNORE_CASE)

        Log.d("SMB", "---- findDocLocationForType ----")
        Log.d("SMB", "server=$serverIp share=$shareName user=$usernameWithDomain fi=$fiValue($fiNoSlash) doc=$docType year=$yr")

        try {
            val client = SMBClient()
            client.connect(serverIp).use { conn ->
                val domain = usernameWithDomain.substringBefore('\\', "")
                val user   = usernameWithDomain.substringAfter('\\', usernameWithDomain)
                val auth = AuthenticationContext(user, password.toCharArray(), domain)
                val session = conn.authenticate(auth)

                session.connectShare(shareName).use { share ->
                    val disk = share as DiskShare

                    // Log: racine du share
                    smbListDir(disk, ".") // liste la racine du share
                    smbListDir(disk, "Documents associés") // test presence du dossier accentué
                    smbListDir(disk, "Documents associes") // et non accentué

                    for (zone in zones) {
                        val candidatesDirs = buildDocDirs(zone, docType, yr)
                        for (dirPath in candidatesDirs) {
                            val names = smbListDir(disk, dirPath)
                            if (names.isEmpty()) continue

                            val files = names.filter { pattern.matches(it) }
                            Log.d("SMB", "zone=$zone dir='$dirPath' matches=${files.size}")

                            if (files.isNotEmpty()) {
                                val latest = files.maxByOrNull { extractIndex(it) }!!
                                return zone to latest
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SMB", "Exception: ${e.message}", e)
        }
        return "" to ""
    }
    private fun openPdf(localFile: File) {
        val uri: Uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", localFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
        }
        try {
            startActivity(Intent.createChooser(intent, "Ouvrir avec…"))
        } catch (e: Exception) {
            Toast.makeText(this, "Aucune application PDF installée.", Toast.LENGTH_LONG).show()
        }
    }

    private fun sharePdf(localFile: File) {
        val uri: Uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", localFile)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(Intent.createChooser(intent, "Partager le PDF"))
        } catch (_: Exception) {
            Toast.makeText(this, "Impossible de partager ce PDF.", Toast.LENGTH_LONG).show()
        }
    }

    private fun android.content.Context.toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    private var loadingDialog: AlertDialog? = null

    private fun showLoading(message: String) {
        if (loadingDialog?.isShowing == true) return
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(48, 32, 48, 32)
            gravity = Gravity.CENTER_VERTICAL
            addView(ProgressBar(this@MainActivity))
            addView(TextView(this@MainActivity).apply {
                text = "  $message"
                textSize = 16f
            })
        }
        loadingDialog = AlertDialog.Builder(this)
            .setView(layout)
            .setCancelable(false)
            .create()
        loadingDialog?.show()
    }

    private fun hideLoading() {
        loadingDialog?.dismiss()
        loadingDialog = null
    }

    private val scanLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            if (res.resultCode == RESULT_OK) {
                val scanned = res.data?.getStringExtra("SCANNED_CODE")?.trim().orEmpty()
                if (scanned.isNotEmpty()) {
                    // Ici tu décides : c’est un N°FI ou un N°Affaire ?
                    // Exemples de heuristiques
                    val isAffaire = scanned.matches(Regex("^(MI|OR|SU|BE)[A-Z0-9]{6,}$"))
                    val isFi = scanned.length == 8 && scanned.contains("/")

                    if (isAffaire) {
                        // Ta fonction “par Affaire”
                        loadByAffaireCode(scanned)
                    } else if (isFi) {
                        // Remplir la zone + déclencher la recherche existante
                        val etSearch = findViewById<EditText>(R.id.etSearch)
                        val btnSearch = findViewById<Button>(R.id.btnSearch)
                        etSearch.setText(scanned)
                        btnSearch.performClick()

                    } else {
                        Toast.makeText(this, "Code non reconnu : $scanned", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

    private fun normalizeAffaireCode(src: String): String {
        // "OR-20250100252" / "OR20250100252" / " MI25101400 " → "OR20250100252"
        return src.trim().uppercase().replace(" ", "").replace("-", "").replace("/", "")
    }

    private fun loadByAffaireCode(raw: String) {
        val code = raw.trim().uppercase()

        // lifecycleScope :
        // - fourni par AndroidX (lifecycle-runtime-ktx)
        // - scope lié au cycle de vie de l'Activity
        // - si l'Activity est détruite, toutes les coroutines du scope sont annulées automatiquement
        lifecycleScope.launch(Dispatchers.IO) {
            // launch :
            // - démarre une nouvelle coroutine de façon asynchrone
            // - Dispatchers.IO indique qu'on veut exécuter ce bloc sur un pool de threads dédié aux opérations I/O (réseau, SQL, fichiers...)

            var found = false
            var fi: String? = null
            var hadError = false

            try {
                // On reste dans le contexte IO ici (thread de fond)

                Class.forName("net.sourceforge.jtds.jdbc.Driver")
                val url = "jdbc:jtds:sqlserver://10.135.214.34:1433/SIA"
                Log.d("AFF_SCAN", "Driver chargé (coroutine / IO)")

                DriverManager.getConnection(url, "russe", "cia").use { conn ->
                    Log.d("AFF_SCAN", "Connexion SQL OK (coroutine / IO)")

                    conn.prepareStatement(
                        """
                    SELECT TOP 1 AffNoFI
                    FROM tAffaire
                    WHERE AffID = ?
                    """.trimIndent()
                    ).use { ps ->
                        ps.setString(1, code)
                        ps.executeQuery().use { rs ->
                            if (rs.next()) {
                                fi = rs.getString("AffNoFI")
                                found = !fi.isNullOrBlank()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                hadError = true
                Log.e("AFF_SCAN", "ERREUR connexion SQL : ${e.message}")
                Log.e("AFF_SCAN", Log.getStackTraceString(e))
            }

            // withContext :
            // - permet de changer de Dispatcher à l'intérieur de la même coroutine
            // - ici on revient sur le thread principal (UI) pour pouvoir toucher aux vues (Toast, EditText, Button...)
            withContext(Dispatchers.Main) {
                when {
                    hadError -> {
                        // this@MainActivity :
                        // - on précise le contexte Activity pour éviter l'ambiguïté "this" (qui peut désigner la coroutine)
                        Toast.makeText(this@MainActivity, "Erreur de connexion au serveur", Toast.LENGTH_LONG).show()
                    }
                    !found -> {
                        Toast.makeText(this@MainActivity, "Affaire introuvable : $code", Toast.LENGTH_LONG).show()
                    }
                    else -> {
                        // Ici on est sur le thread UI donc on peut manipuler les vues directement
                        etSearch.setText(fi)
                        btnSearch.performClick()
                    }
                }
            }
        }  // fin du launch : la coroutine se termine ici
    }
}
