package com.chy.muscletome.data.remote

import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class WgerApiClient @Inject constructor() {

    suspend fun fetchPage(offset: Int, limit: Int = 50): WgerPage = withContext(Dispatchers.IO) {
        val url = URL("https://wger.de/api/v2/exerciseinfo/?limit=$limit&offset=$offset")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                error("wger HTTP $code")
            }
            val body = connection.inputStream.bufferedReader().use(BufferedReader::readText)
            parsePage(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun parsePage(body: String): WgerPage {
        val root = JSONObject(body)
        val resultsJson = root.getJSONArray("results")
        val results = buildList {
            for (i in 0 until resultsJson.length()) {
                add(parseExercise(resultsJson.getJSONObject(i)))
            }
        }
        return WgerPage(
            count = root.optInt("count"),
            next = if (root.isNull("next")) null else root.optString("next"),
            results = results,
        )
    }

    private fun parseExercise(obj: JSONObject): WgerExerciseInfo {
        val muscles = parseMuscles(obj.optJSONArray("muscles"))
        val secondary = parseMuscles(obj.optJSONArray("muscles_secondary"))
        val equipment = buildList {
            val arr = obj.optJSONArray("equipment") ?: return@buildList
            for (i in 0 until arr.length()) {
                val e = arr.getJSONObject(i)
                add(WgerEquipment(e.getInt("id"), e.optString("name")))
            }
        }
        var englishName: String? = null
        var englishDescription: String? = null
        val translations = obj.optJSONArray("translations")
        if (translations != null) {
            for (i in 0 until translations.length()) {
                val t = translations.getJSONObject(i)
                if (t.optInt("language") == 2) {
                    englishName = t.optString("name").ifBlank { null }
                    englishDescription = stripHtml(t.optString("description"))
                    break
                }
            }
            if (englishName == null && translations.length() > 0) {
                val t = translations.getJSONObject(0)
                englishName = t.optString("name").ifBlank { null }
                englishDescription = stripHtml(t.optString("description"))
            }
        }
        val license = obj.optJSONObject("license")
        return WgerExerciseInfo(
            id = obj.getInt("id"),
            categoryName = obj.optJSONObject("category")?.optString("name"),
            muscles = muscles,
            musclesSecondary = secondary,
            equipment = equipment,
            licenseAuthor = obj.optString("license_author").ifBlank { null },
            licenseName = license?.optString("short_name"),
            englishName = englishName,
            englishDescription = englishDescription,
        )
    }

    private fun parseMuscles(arr: JSONArray?): List<WgerMuscle> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val m = arr.getJSONObject(i)
                val nameEn = m.optString("name_en").ifBlank { m.optString("name") }
                if (nameEn.isNotBlank()) {
                    add(WgerMuscle(m.getInt("id"), nameEn))
                }
            }
        }
    }

    private fun stripHtml(html: String): String =
        html.replace(Regex("<[^>]*>"), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace(Regex("\\s+"), " ")
            .trim()
}