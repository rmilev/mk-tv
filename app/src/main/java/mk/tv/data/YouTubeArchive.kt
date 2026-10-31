package mk.tv.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.IOException

data class YtVideo(
    val id: String,
    val title: String,
    /** e.g. "пред 7 часа" as YouTube phrases it in Macedonian. */
    val published: String,
    /** e.g. "14:06". */
    val duration: String,
) {
    val thumbnail: String get() = "https://i.ytimg.com/vi/$id/mqdefault.jpg"
}

data class YtPlaylist(val id: String, val title: String)

data class YtPage(val videos: List<YtVideo>, val continuation: String?)

/**
 * Stations' official YouTube channels, read from the public channel pages (the same data a browser
 * renders: ytInitialData), so no API key is needed. Older items load through the web client's
 * "browse" continuation, exactly like scrolling on youtube.com. Videos play in the official
 * embedded player (see YouTubePlayerScreen).
 */
object YouTubeArchive {
    private const val SITE = "https://www.youtube.com"
    private val headers = mapOf(
        "Accept-Language" to "mk,en;q=0.8",
        // Essential-cookies-only consent, so YouTube serves the page instead of a consent wall
        "Cookie" to "SOCS=CAI; CONSENT=YES+",
    )
    private val json = Json { ignoreUnknownKeys = true }
    private val initialData = Regex("""var ytInitialData = (\{.*?\});</script>""", RegexOption.DOT_MATCHES_ALL)
    private val clientVersionRe = Regex(""""INNERTUBE_CLIENT_VERSION":"([^"]+)"""")
    private const val DESKTOP_UA =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36"

    @Volatile private var clientVersion = "2.20261029.11.00"
    private val playlistCache = mutableMapOf<String, List<YtPlaylist>>()

    suspend fun latest(channelId: String): YtPage = parsePage(page("$SITE/channel/$channelId/videos"))

    suspend fun playlist(listId: String): YtPage = parsePage(page("$SITE/playlist?list=$listId"))

    suspend fun playlists(channelId: String): List<YtPlaylist> = playlistCache[channelId] ?: run {
        val data = page("$SITE/channel/$channelId/playlists")
        val lists = lockups(data)
            .filter { it.type.contains("PLAYLIST") || it.type.contains("PODCAST") }
            .map { YtPlaylist(it.id, it.title) }
            .distinctBy { it.id }
        playlistCache[channelId] = lists
        lists
    }

    /** Next batch of videos (≈30) for a page or playlist. */
    suspend fun more(token: String): YtPage {
        val body = buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", "WEB"); put("clientVersion", clientVersion); put("hl", "mk")
                }
            }
            put("continuation", token)
        }
        val text = Http.postJson("$SITE/youtubei/v1/browse?prettyPrint=false", body.toString(), headers + ("User-Agent" to DESKTOP_UA))
        return parsePage(json.parseToJsonElement(text))
    }

    private suspend fun page(url: String): JsonElement {
        val html = Http.get(url, headers + ("User-Agent" to DESKTOP_UA))
        clientVersionRe.find(html)?.let { clientVersion = it.groupValues[1] }
        val raw = initialData.find(html)?.groupValues?.get(1) ?: throw IOException("No video data on $url")
        return json.parseToJsonElement(raw)
    }

    private fun parsePage(root: JsonElement): YtPage {
        val videos = lockups(root)
            .filter { it.type.contains("VIDEO") }
            .map { YtVideo(it.id, it.title, it.meta.lastOrNull().orEmpty(), it.badge) }
            .distinctBy { it.id }
        return YtPage(videos, continuationToken(root))
    }

    private data class Lockup(val type: String, val id: String, val title: String, val meta: List<String>, val badge: String)

    /** YouTube's current list item ("lockupViewModel") for both videos and playlists. */
    private fun lockups(root: JsonElement): List<Lockup> = findAll(root, "lockupViewModel").mapNotNull { el ->
        val lv = el as? JsonObject ?: return@mapNotNull null
        val id = lv.str("contentId") ?: return@mapNotNull null
        val meta = lv.obj("metadata")?.obj("lockupMetadataViewModel")
        val title = meta?.obj("title")?.str("content").orEmpty()
        val parts = meta?.let { m -> findAll(m, "metadataParts").flatMap { (it as? JsonArray).orEmpty() } }
            .orEmpty().mapNotNull { (it as? JsonObject)?.obj("text")?.str("content") }
            .filter { it.isNotBlank() }
        val badge = lv.obj("contentImage")?.let { findAll(it, "thumbnailBadgeViewModel").firstOrNull() as? JsonObject }
            ?.str("text").orEmpty()
        Lockup(lv.str("contentType").orEmpty(), id, title, parts, badge)
    }

    private fun continuationToken(root: JsonElement): String? =
        findAll(root, "continuationItemRenderer").firstNotNullOfOrNull { item ->
            findAll(item, "continuationCommand").firstNotNullOfOrNull { (it as? JsonObject)?.str("token") }
        }

    private fun findAll(el: JsonElement, key: String): List<JsonElement> {
        val out = mutableListOf<JsonElement>()
        fun walk(e: JsonElement) {
            when (e) {
                is JsonObject -> e.forEach { (k, v) -> if (k == key) out += v; walk(v) }
                is JsonArray -> e.forEach(::walk)
                else -> Unit
            }
        }
        walk(el)
        return out
    }

    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
}
