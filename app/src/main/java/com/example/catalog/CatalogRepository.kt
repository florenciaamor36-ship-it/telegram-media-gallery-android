package com.example.catalog

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/** Public-catalog adapters. Only items with explicit, supported reuse terms enter the playable list. */
object CatalogRepository {
    suspend fun searchInternetArchive(query: String): List<CatalogItem> = withContext(Dispatchers.IO) {
        val q = if (query.isBlank()) "mediatype:movies" else "mediatype:movies AND (${query.trim()})"
        val url = "https://archive.org/advancedsearch.php?q=${enc(q)}&fl[]=identifier&fl[]=title&fl[]=creator&fl[]=year&rows=12&page=1&output=json"
        val root = JSONObject(get(url))
        val docs = root.optJSONObject("response")?.optJSONArray("docs") ?: return@withContext emptyList()
        buildList {
            for (i in 0 until docs.length()) {
                val doc = docs.optJSONObject(i) ?: continue
                val id = doc.optString("identifier").takeIf(String::isNotBlank) ?: continue
                val metadata = runCatching { JSONObject(get("https://archive.org/metadata/${enc(id)}")) }.getOrNull() ?: continue
                val meta = metadata.optJSONObject("metadata") ?: JSONObject()
                val licenseUrl = meta.optString("licenseurl").takeIf(String::isNotBlank).orEmpty()
                val rightsText = listOf("rights", "license").mapNotNull { meta.optString(it).takeIf(String::isNotBlank) }.joinToString(" · ")
                val license = supportedLicense(licenseUrl, rightsText) ?: continue
                val files = metadata.optJSONArray("files") ?: continue
                var mediaUrl: String? = null
                for (j in 0 until files.length()) {
                    val f = files.optJSONObject(j) ?: continue
                    val name = f.optString("name")
                    val format = f.optString("format").lowercase(Locale.ROOT)
                    val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
                    val isVideo = format.contains("mpeg4") || format.contains("h.264") || format.contains("webm") || format.contains("ogg video") || ext in setOf("mp4", "m4v", "webm", "ogv")
                    if (isVideo && f.optString("source") != "metadata" && f.optString("private") != "true") {
                        mediaUrl = "https://archive.org/download/${enc(id)}/${encPath(name)}"
                        break
                    }
                }
                val playable = mediaUrl ?: continue
                add(CatalogItem(
                    id = "ia:$id", title = doc.optString("title", id), creator = doc.optString("creator"), year = doc.optString("year"),
                    description = doc.optString("description").take(500), mediaUrl = playable,
                    sourceName = "Internet Archive", sourceUrl = "https://archive.org/details/${enc(id)}", rights = license
                ))
            }
        }
    }

    suspend fun searchWikimedia(query: String): List<CatalogItem> = withContext(Dispatchers.IO) {
        val search = (query.trim().ifBlank { "public domain film" }) + " filetype:video"
        val url = "https://commons.wikimedia.org/w/api.php?action=query&generator=search&gsrsearch=${enc(search)}&gsrnamespace=6&gsrlimit=30&prop=imageinfo&iiprop=url%7Cextmetadata&format=json&origin=*"
        val root = JSONObject(get(url))
        val pages = root.optJSONObject("query")?.optJSONObject("pages") ?: return@withContext emptyList()
        buildList {
            val keys = pages.keys()
            while (keys.hasNext()) {
                val page = pages.optJSONObject(keys.next()) ?: continue
                val info = page.optJSONArray("imageinfo")?.optJSONObject(0) ?: continue
                val media = info.optString("url")
                val lower = media.lowercase(Locale.ROOT)
                if (media.isBlank() || listOf(".mp4", ".webm", ".ogv").none(lower::contains)) continue
                val ext = info.optJSONObject("extmetadata") ?: JSONObject()
                fun meta(key: String): String = ext.optJSONObject(key)?.optString("value")?.replace(Regex("<[^>]*>"), "")?.trim().orEmpty()
                val shortLicense = meta("LicenseShortName")
                val licenseUrl = meta("LicenseUrl")
                val usageTerms = meta("UsageTerms")
                val license = supportedLicense(licenseUrl, "$shortLicense $usageTerms") ?: continue
                val title = page.optString("title").removePrefix("File:")
                val pageTitle = page.optString("title").replace(' ', '_')
                val pageUrl = "https://commons.wikimedia.org/wiki/${enc(pageTitle)}"
                add(CatalogItem(
                    id = "commons:${page.optString("pageid")}", title = title, creator = meta("Artist"), year = "",
                    description = "Video de Wikimedia Commons. Revisá la atribución y condiciones en la ficha.", mediaUrl = media,
                    sourceName = "Wikimedia Commons", sourceUrl = pageUrl, rights = license
                ))
            }
        }
    }

    /** Allow explicit public-domain/CC0/attribution licenses only; do not infer a license from a missing field. */
    private fun supportedLicense(url: String, label: String): String? {
        val u = url.lowercase(Locale.ROOT)
        val text = label.lowercase(Locale.ROOT)
        val combined = "$u $text"
        if (combined.contains("noncommercial") || combined.contains("non-commercial") || combined.contains("cc by-nc") || combined.contains("cc-by-nc") || combined.contains("no derivatives") || combined.contains("no-derivatives") || combined.contains("cc by-nd") || combined.contains("cc-by-nd")) return null
        if (combined.contains("public domain") || combined.contains("publicdomain") || combined.contains("creativecommons.org/publicdomain/mark") || combined.contains("creativecommons.org/publicdomain/zero") || Regex("\\bcc0\\b").containsMatchIn(combined)) return "Dominio público / CC0 · revisá la ficha original"
        val isBy = combined.contains("creativecommons.org/licenses/by/") || Regex("\\bcc by(?: |-|$)").containsMatchIn(combined) || Regex("\\bcc-by(?:-| )").containsMatchIn(combined)
        val isBySa = combined.contains("creativecommons.org/licenses/by-sa/") || Regex("\\bcc by-sa(?: |-|$)").containsMatchIn(combined) || Regex("\\bcc-by-sa(?:-| )").containsMatchIn(combined)
        if (isBySa) return "CC BY-SA · requiere atribución y compartir bajo igual licencia"
        if (isBy) return "CC BY · requiere atribución"
        return null
    }

    private fun get(address: String): String {
        val connection = URL(address).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15000
            connection.readTimeout = 20000
            connection.setRequestProperty("User-Agent", "LaClavePlayAndroid/0.2 (catalog viewer)")
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")
    private fun encPath(value: String) = value.split('/').joinToString("/") { enc(it).replace("+", "%20") }
}

data class CatalogItem(
    val id: String, val title: String, val creator: String, val year: String,
    val description: String, val mediaUrl: String, val sourceName: String,
    val sourceUrl: String, val rights: String
)
