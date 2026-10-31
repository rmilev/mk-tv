package mk.tv.data

import java.io.IOException

/**
 * Turns a channel into a playable URL. "page" channels (e.g. MRT Play) embed a link that is
 * signed for a limited time, so it is scraped fresh from the channel page on each play.
 */
object StreamResolver {
    private const val DEFAULT_PATTERN = """https?://[^"'\s<>]+\.m3u8[^"'\s<>]*"""
    private const val CACHE_MS = 10 * 60 * 1000L

    private val cache = mutableMapOf<String, Pair<Long, String>>()

    suspend fun resolve(channel: Channel, fresh: Boolean = false): ResolvedStream {
        val headers = buildMap {
            channel.page?.let { put("Referer", it) }
            putAll(channel.headers)
        }
        val url = when (channel.type) {
            "page" -> scrape(channel, fresh)
            else -> channel.url ?: throw IOException("No stream URL for ${channel.id}")
        }
        return ResolvedStream(url, headers)
    }

    private suspend fun scrape(channel: Channel, fresh: Boolean): String {
        val page = channel.page ?: throw IOException("No page for ${channel.id}")
        val now = System.currentTimeMillis()
        if (!fresh) cache[channel.id]?.let { (expires, url) -> if (expires > now) return url }
        val html = Http.get(page, channel.headers)
        val match = Regex(channel.pattern ?: DEFAULT_PATTERN).find(html)
            ?: throw IOException("Stream link not found on $page")
        val url = match.value.replace("\\/", "/").replace("&amp;", "&")
        cache[channel.id] = now + CACHE_MS to url
        return url
    }
}
