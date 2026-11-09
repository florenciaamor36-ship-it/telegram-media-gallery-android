package com.example.catalog

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Public-catalog adapters. Each result links back to its source and carries per-item rights metadata. */
object CatalogRepository {
    suspend fun searchInternetArchive(query: String): List<CatalogItem> = withContext(Dispatchers.IO) {
        val q = if (query.isBlank()) "mediatype:movies" else "mediatype:movies AND (${query.trim()})"
        val url = "https://archive.org/advancedsearch.php?q=${enc(q)}&fl[]=identifier&fl[]=title&fl[]=creator&fl[]=year&fl[]=licenseurl&fl[]=description&rows=40&page=1&output=json"
        val root = JSONObject(get(url))
        val docs = root.optJSONObject("response")?.optJSONArray("docs") ?: return@withContext emptyList()
        buildList {
            for (i in 0 until docs.length()) {
                val doc = docs.optJSONObject(i) ?: continue
                val id = doc.optString("identifier").takeIf(String::isNotBlank) ?: continue
                val title = doc.optString("title", id)
                val metadata = runCatching { JSONObject(get("https://archive.org/metadata/${enc(id)}")) }.getOrNull() ?: continue
                val files = metadata.optJSONArray("files") ?: continue
                var mediaUrl: String? = null
                for (j in 0 until files.length()) {
                    val f = files.optJSONObject(j) ?: continue
                    val name = f.optString("name")
                    val format = f.optString("format").lowercase()
                    val isVideo = format.contains("mpeg4") || format.contains("h.264") || format.contains("webm") || format.contains("ogg video") || name.substringAfterLast('.', "").lowercase() in setOf("mp4", "m4v", "webm", "ogv")
                    if (isVideo && f.optString("source") != "metadata" && f.optString("private") != "true") {
                        mediaUrl = "https://archive.org/download/${enc(id)}/${encPath(name)}"
                        break
                    }
                }
                val meta = metadata.optJSONObject("metadata") ?: JSONObject()
                val rights = listOf("licenseurl", "rights", "license")
                    .mapNotNull { meta.optString(it).takeIf(String::isNotBlank) }.distinct().joinToString(" · ")
                if (mediaUrl != null) add(CatalogItem(
                    id = "ia:$id", title = title, creator = doc.optString("creator"), year = doc.optString("year"),
                    description = doc.optString("description").take(500), mediaUrl = mediaUrl,
                    sourceName = "Internet Archive", sourceUrl = "https://archive.org/details/${enc(id)}",
                    rights = rights.ifBlank { "Licencia no informada: revisar ficha del título" }
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
                val lower = media.lowercase()
                if (media.isBlank() || listOf(".mp4", ".webm", ".ogv", ".ogv").none(lower::contains)) continue
                val ext = info.optJSONObject("extmetadata") ?: JSONObject()
                fun meta(key: String): String = ext.optJSONObject(key)?.optString("value")?.replace(Regex("<[^>]*>"), "")?.trim().orEmpty()
                val title = page.optString("title").removePrefix("File:")
                val pageUrl = "https://commons.wikimedia.org/wiki/${enc(page.optString("title").replace(' ', '_'))}"
                val rights = listOf(meta("LicenseShortName"), meta("UsageTerms")).filter(String::isNotBlank).distinct().joinToString(" · ")
                add(CatalogItem(
                    id = "commons:${page.optString("pageid")}", title = title, creator = meta("Artist"), year = "",
                    description = "Video de Wikimedia Commons. Verificá atribución y condiciones en la ficha.", mediaUrl = media,
                    sourceName = "Wikimedia Commons", sourceUrl = pageUrl,
                    rights = rights.ifBlank { "Licencia no identificada: revisar ficha antes de reutilizar" }
                ))
            }
        }
    }

    private fun get(address: String): String {
        val connection = URL(address).openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 20000
        connection.setRequestProperty("User-Agent", "LaClavePlayAndroid/0.1")
        return connection.inputStream.bufferedReader().use { it.readText() }.also { connection.disconnect() }
    }
    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")
    private fun encPath(value: String) = value.split('/').joinToString("/") { enc(it).replace("+", "%20") }
}

data class CatalogItem(
    val id: String, val title: String, val creator: String, val year: String,
    val description: String, val mediaUrl: String, val sourceName: String,
    val sourceUrl: String, val rights: String
)
