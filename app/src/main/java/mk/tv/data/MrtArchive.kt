package mk.tv.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException

/**
 * MRT Play on-demand archive (play.mrt.com.mk), browsed live:
 *   /c/<id>                          section page; links to /c/<show> are its shows
 *   /p/<cat>/<page>/1/<order>/<n>/4/  JSON {page_count, page_html} with n episodes per page
 *   /play/<id>                       episode page with the HLS link (no token; plays worldwide)
 */
object MrtArchive {
    private const val SITE = "https://play.mrt.com.mk"
    const val PAGE_SIZE = 24

    val sections = listOf(
        ArchiveSection(2, "Вести"),
        ArchiveSection(3, "Спорт"),
        ArchiveSection(4, "МРТ продукција"),
        ArchiveSection(5, "Документарна"),
        ArchiveSection(7, "50 години МРТ"),
        ArchiveSection(8, "МРТ промо"),
    )

    /** Category ids that appear in the site-wide menu, i.e. not shows of the current section. */
    private val navIds = setOf(1, 2, 3, 4, 5, 6, 7, 8, 9)
    private val showLink = Regex("""<a[^>]*href=['"]https://play\.mrt\.com\.mk/c/(\d+)['"][^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
    private val tag = Regex("<[^>]+>")
    private val episodeItem = Regex(
        """href="https://play\.mrt\.com\.mk/play/(\d+)".*?<img src="([^"]+)".*?overlay-title[^>]*>([^<]+)<""",
        RegexOption.DOT_MATCHES_ALL,
    )
    private val streamLink = Regex("""https://[^"'\s<>]+\.m3u8[^"'\s<>]*""")
    private val json = Json { ignoreUnknownKeys = true }

    private val showsCache = mutableMapOf<Int, List<ArchiveShow>>()

    suspend fun shows(sectionId: Int): List<ArchiveShow> = showsCache[sectionId] ?: run {
        val html = Http.get("$SITE/c/$sectionId")
        val shows = showLink.findAll(html)
            .map { it.groupValues[1].toInt() to it.groupValues[2].replace(tag, " ").htmlDecode() }
            .filter { (id, title) -> id !in navIds && id != sectionId && title.isNotBlank() }
            .distinctBy { it.first }
            .map { (id, title) -> ArchiveShow(id, title.removeSuffix("..").trim().prettyTitle()) }
            .toList()
        showsCache[sectionId] = shows
        shows
    }

    suspend fun episodes(categoryId: Int, page: Int): EpisodePage {
        val body = Http.get("$SITE/p/$categoryId/$page/1/0/$PAGE_SIZE/4/")
        val obj = json.parseToJsonElement(body).jsonObject
        val pageCount = obj["page_count"]?.jsonPrimitive?.int ?: 1
        val html = obj["page_html"]?.jsonPrimitive?.content ?: ""
        val episodes = episodeItem.findAll(html).map {
            ArchiveEpisode(it.groupValues[1].toLong(), it.groupValues[3].htmlDecode(), it.groupValues[2])
        }.distinctBy { it.id }.toList()
        return EpisodePage(episodes, page, pageCount)
    }

    suspend fun episodeStream(episodeId: Long): ResolvedStream {
        val page = "$SITE/play/$episodeId"
        val html = Http.get(page)
        val url = streamLink.find(html)?.value ?: throw IOException("No video found for episode $episodeId")
        return ResolvedStream(url.replace("&amp;", "&"), mapOf("Referer" to page))
    }

    /** "ДОБРО УТРО МАКЕДОНИЈО" -> "Добро утро Македонијо" for nicer TV typography. */
    private fun String.prettyTitle(): String {
        val letters = filter { it.isLetter() }
        if (letters.isEmpty() || letters.any { it.isLowerCase() }) return this
        val lower = lowercase()
        return lower.replaceFirstChar { it.uppercase() }
    }
}
