package fr.projet.infosapp

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder

class ScanApi(
    private val baseUrl: String,
    private val deviceId: String
) {
    private val client = OkHttpClient()
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    suspend fun getInfosApp(rawValue: String): DeviceInfos? =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$baseUrl/api/AppInfos/$rawValue")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null

                val body = response.body?.string() ?: return@withContext null
                val json = JSONObject(body)

                return@withContext DeviceInfos(
                    fqrId = json.optInt("fqrId"),
                    qrCode = json.optString("qrCode"),
                    etgCode = json.optString("etgcode"),
                    affaireId = json.optString("affaireId"),
                    numFI = json.optString("numFI"),
                    clientId = json.optInt("clientId"),
                    nomClient = json.optString("nomclient"),
                    designation = json.optString("designation"),
                    marque = json.optString("marque"),
                    type = json.optString("type"),
                    numSerie = json.optString("numSerie"),
                    marquage = json.optString("marquage"),
                    blId = json.optString("blId"),
                    dateBL = json.optString("dateBL").takeIf { it.isNotBlank() },
                    dateCreationBL = json.optString("dateCreation").takeIf { it.isNotBlank() },
                    parBL = json.optString("parBL"),
                    factId = json.optString("factId"),
                    dateFact = json.optString("dateFact").takeIf { it.isNotBlank() },
                    dateEntree = json.optString("dateEntree").takeIf { it.isNotBlank() },
                    dateEnreg = json.optString("dateEnreg").takeIf { it.isNotBlank() },
                    finInterv = json.optBoolean("finInterv"),
                    datefinInterv = json.optString("datefinInterv").takeIf { it.isNotBlank() },
                    dateCtrlFinal = json.optString("dateCtrlFinal").takeIf { it.isNotBlank() },
                    opreal = json.optString("opreal"),
                    docs = json.optString("docs"),
                    conclusion = json.optString("conclusion"),
                    positAff = json.optString("posiAff"),
                    numST = json.optString("numST"),
                    distribTech = json.optString("distribTech"),
                    domaine = json.optString("domaine"),
                    cdeST = json.optString("cdeST")
                )
            }
        }
}