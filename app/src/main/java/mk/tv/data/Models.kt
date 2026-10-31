package mk.tv.data

import kotlinx.serialization.Serializable

/** Root of channels.json (bundled in assets, optionally refreshed from GitHub). */
@Serializable
data class ChannelList(
    val version: Int = 1,
    val channels: List<Channel> = emptyList(),
)

@Serializable
data class Channel(
    val id: String,
    val name: String,
    val nameLatin: String? = null,
    /** Row on the home screen, e.g. "МРТ", "Национални", "Регионални", "Музика". */
    val group: String = "Останати",
    val logo: String? = null,
    /** Badge text when there is no logo image (e.g. "МРТ 1"). */
    val badge: String? = null,
    /** "page": stream link is scraped from [page] with [pattern]; "url": fixed stream [url]. */
    val type: String = "url",
    val url: String? = null,
    val page: String? = null,
    val pattern: String? = null,
    val headers: Map<String, String> = emptyMap(),
    /** True when the broadcaster only allows viewers in North Macedonia (needs an MK VPN abroad). */
    val mkOnly: Boolean = false,
    val radio: Boolean = false,
    /** False for stations without a public live stream (listed only for their catch-up). */
    val live: Boolean = true,
    val catchup: Catchup? = null,
)

@Serializable
data class Catchup(
    /** "mrt" = MRT Play archive, "youtube" = official YouTube channel. */
    val type: String,
    val youtubeChannelId: String? = null,
    val mrtSection: Int? = null,
)

/** A playable stream after resolving a channel or an archive episode. */
data class ResolvedStream(
    val url: String,
    val headers: Map<String, String>,
)

data class ArchiveSection(val id: Int, val title: String)

data class ArchiveShow(val id: Int, val title: String)

data class ArchiveEpisode(
    val id: Long,
    val title: String,
    val thumbnail: String,
)

data class EpisodePage(
    val episodes: List<ArchiveEpisode>,
    val page: Int,
    val pageCount: Int,
)

/** Thrown when the broadcaster refuses the stream from this location (HTTP 403/451). */
class GeoBlockedException(message: String) : Exception(message)
