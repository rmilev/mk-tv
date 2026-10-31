package mk.tv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

object Http {
    /** Browser-like UA: several broadcaster sites serve different markup to unknown clients. */
    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 12; Android TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36"

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): String =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).apply {
                headers.forEach { (k, v) -> header(k, v) }
            }.build()
            client.newCall(request).execute().use { resp ->
                if (resp.code == 403 || resp.code == 451) throw GeoBlockedException("HTTP ${resp.code} for $url")
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} for $url")
                resp.body?.string() ?: ""
            }
        }
}

/** Minimal HTML entity decoding for titles scraped from pages. */
fun String.htmlDecode(): String = this
    .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
    .replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ")
    .replace(Regex("&#(\\d+);")) { it.groupValues[1].toInt().toChar().toString() }
    .replace(Regex("\\s+"), " ").trim()
