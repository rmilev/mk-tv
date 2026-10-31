package mk.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.SubcomposeAsyncImage
import kotlinx.coroutines.delay
import mk.tv.data.ArchiveEpisode
import mk.tv.data.ArchiveShow
import mk.tv.data.Channel
import mk.tv.data.MrtArchive
import mk.tv.data.YouTubeArchive
import mk.tv.data.YtPlaylist
import mk.tv.data.YtVideo

/**
 * Catch-up: MRT's own archive (sections -> shows -> episodes) and the private stations' official
 * YouTube channels (latest + playlists). Episodes are shown as a readable list. Where the viewer
 * was (section, show, scroll, focused item) lives in [BrowseState], so it survives playing a video.
 */
@Composable
fun ArchiveTab(
    channels: List<Channel>,
    browse: BrowseState,
    onPlayEpisode: (ArchiveEpisode, String) -> Unit,
    onPlayYoutube: (List<YtVideo>, Int, String) -> Unit,
    youtubeApp: Boolean,
    onYoutubeApp: (Boolean) -> Unit,
) {
    // Macedonian-language stations first, Albanian-language ones at the end
    val youtubeChannels = remember(channels) {
        channels.filter { it.catchup?.type == "youtube" && it.catchup.youtubeChannelId != null }.sortedBy { it.albanian }
    }
    val source = browse.archiveSource

    Row(Modifier.fillMaxSize().padding(start = Insets.Horizontal, top = 8.dp)) {
        // Left menu: switches content on focus, so browsing needs no extra clicks
        // Menu rows: header, MRT sections, header, stations, ... (open scrolled to the selected one)
        val selectedRow = MrtArchive.sections.indexOfFirst { source == "mrt:${it.id}" }.takeIf { it >= 0 }?.plus(1)
            ?: youtubeChannels.indexOfFirst { source == "yt:${it.catchup?.youtubeChannelId}" }.takeIf { it >= 0 }
                ?.plus(MrtArchive.sections.size + 2) ?: 0
        val menuState = browse.listState("archive-menu")
        LaunchedEffect(selectedRow) {
            val info = menuState.layoutInfo
            val fullyVisible = info.visibleItemsInfo.any {
                it.index == selectedRow && it.offset >= info.viewportStartOffset && it.offset + it.size <= info.viewportEndOffset
            }
            if (selectedRow > 0 && !fullyVisible) menuState.scrollToItem((selectedRow - 3).coerceAtLeast(0))
        }
        LazyColumn(
            Modifier.width(250.dp).fillMaxHeight(),
            state = menuState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            item { MenuHeader("МРТ архива") }
            items(MrtArchive.sections, key = { it.id }) { s ->
                MenuItem(s.title, selected = source == "mrt:${s.id}", onSelect = { browse.selectSource("mrt:${s.id}") })
            }
            if (youtubeChannels.isNotEmpty()) {
                item { Spacer(Modifier.height(12.dp)); MenuHeader("Други станици") }
                items(youtubeChannels, key = { "yt-${it.id}" }) { ch ->
                    val key = "yt:${ch.catchup!!.youtubeChannelId}"
                    MenuItem(ch.name, selected = source == key, onSelect = { browse.selectSource(key) })
                }
                item { Spacer(Modifier.height(12.dp)); MenuHeader("YouTube Premium") }
                item { PremiumToggle(youtubeApp, onYoutubeApp) }
            }
        }
        Spacer(Modifier.width(28.dp))
        Box(Modifier.weight(1f).fillMaxHeight()) {
            when {
                source.startsWith("yt:") -> youtubeChannels.firstOrNull { "yt:${it.catchup?.youtubeChannelId}" == source }
                    ?.let { YouTubeStation(it, browse, onPlayYoutube) }
                else -> MrtSection(source.removePrefix("mrt:").toIntOrNull() ?: MrtArchive.sections.first().id, browse, onPlayEpisode)
            }
        }
    }
}

/** With Premium, videos open in the signed-in YouTube app (no ads); Back returns here. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PremiumToggle(on: Boolean, onChange: (Boolean) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val fg = if (focused) androidx.compose.ui.graphics.Color.Black else Palette.Text
    Surface(
        onClick = { onChange(!on) },
        modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            contentColor = Palette.TextDim,
            focusedContainerColor = Palette.Gold,
            focusedContentColor = androidx.compose.ui.graphics.Color.Black,
        ),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Без реклами", style = MaterialTheme.typography.titleMedium, color = fg, modifier = Modifier.weight(1f))
                Text(
                    if (on) "ВКЛ" else "ИСКЛ",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (on) androidx.compose.ui.graphics.Color.White else Palette.TextDim,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) Palette.Red else Palette.SurfaceHigh).padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            Text(
                if (on) "Видеата се отвораат во YouTube апликацијата (без реклами со Premium)." else "Видеата се пуштаат во МК ТВ.",
                style = MaterialTheme.typography.bodyMedium,
                color = if (focused) androidx.compose.ui.graphics.Color(0xFF3A3000) else Palette.TextDim,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun MenuHeader(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = Palette.Gold, modifier = Modifier.padding(start = 14.dp, bottom = 4.dp, top = 4.dp))
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MenuItem(title: String, selected: Boolean, onSelect: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused; if (it.isFocused) onSelect() },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Palette.SurfaceHigh else androidx.compose.ui.graphics.Color.Transparent,
            focusedContainerColor = Palette.Gold,
        ),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = when {
                focused -> androidx.compose.ui.graphics.Color.Black
                selected -> Palette.Text
                else -> Palette.TextDim
            },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

/** Shows of one MRT section as chips, then a paged list of episodes. */
@Composable
private fun MrtSection(sectionId: Int, browse: BrowseState, onPlayEpisode: (ArchiveEpisode, String) -> Unit) {
    val sourceKey = "mrt:$sectionId"
    val sectionTitle = MrtArchive.sections.firstOrNull { it.id == sectionId }?.title.orEmpty()
    var shows by remember(sectionId) { mutableStateOf<List<ArchiveShow>>(emptyList()) }
    LaunchedEffect(sectionId) { shows = runCatching { MrtArchive.shows(sectionId) }.getOrDefault(emptyList()) }
    val category = browse.selection(sourceKey)?.toIntOrNull() ?: sectionId

    Column(Modifier.fillMaxSize()) {
        if (shows.isNotEmpty()) {
            LazyRow(
                state = browse.listState("chips:$sourceKey"),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 14.dp, end = Insets.Horizontal),
            ) {
                item(key = "all") { Chip("Најново", category == sectionId) { browse.select(sourceKey, sectionId.toString()) } }
                items(shows, key = { it.id }) { show -> Chip(show.title, category == show.id) { browse.select(sourceKey, show.id.toString()) } }
            }
        }
        val showTitle = shows.firstOrNull { it.id == category }?.title ?: sectionTitle
        EpisodeList(category, browse, onPlay = { onPlayEpisode(it, showTitle) })
    }
}

@Composable
private fun EpisodeList(categoryId: Int, browse: BrowseState, onPlay: (ArchiveEpisode) -> Unit) {
    val listKey = "mrt-list:$categoryId"
    val data = browse.episodes(categoryId)

    suspend fun loadNext() {
        if (data.loading || data.page >= data.pageCount) return
        data.loading = true
        runCatching { MrtArchive.episodes(categoryId, data.page + 1) }
            .onSuccess { result ->
                val known = data.items.map { it.id }.toHashSet()
                data.items.addAll(result.episodes.filter { it.id !in known })
                data.page = result.page
                data.pageCount = result.pageCount
                data.failed = false
            }
            .onFailure { data.failed = data.items.isEmpty() }
        data.loading = false
    }

    PagedList(
        listKey = listKey,
        browse = browse,
        keys = data.items.map { "ep:${it.id}" },
        loading = data.loading,
        failed = data.failed,
        loadNext = ::loadNext,
        hasMore = data.page < data.pageCount,
    ) { i, focusModifier ->
        val ep = data.items[i]
        ArchiveRow(
            thumbnail = ep.thumbnail,
            title = ep.title,
            subtitle = null,
            badge = null,
            modifier = focusModifier,
            onFocus = { browse.focus(listKey, "ep:${ep.id}", i) },
            onClick = { browse.requestFocusRestore(); onPlay(ep) },
        )
    }
}

/** One station's YouTube catch-up: "Најново" plus its playlists (shows) as chips, then a paged list. */
@Composable
private fun YouTubeStation(channel: Channel, browse: BrowseState, onPlay: (List<YtVideo>, Int, String) -> Unit) {
    val channelId = channel.catchup?.youtubeChannelId ?: return
    val sourceKey = "yt:$channelId"
    var playlists by remember(channelId) { mutableStateOf<List<YtPlaylist>>(emptyList()) }
    LaunchedEffect(channelId) { playlists = runCatching { YouTubeArchive.playlists(channelId) }.getOrDefault(emptyList()) }
    val listId = browse.selection(sourceKey)?.ifEmpty { null }

    Column(Modifier.fillMaxSize()) {
        LazyRow(
            state = browse.listState("chips:$sourceKey"),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 14.dp, end = Insets.Horizontal),
        ) {
            item(key = "latest") { Chip("Најново", listId == null) { browse.select(sourceKey, "") } }
            items(playlists, key = { it.id }) { pl -> Chip(pl.title, listId == pl.id) { browse.select(sourceKey, pl.id) } }
        }
        VideoList(channelId, listId, browse, onPlay = { videos, i -> onPlay(videos, i, channel.name) })
    }
}

@Composable
private fun VideoList(channelId: String, listId: String?, browse: BrowseState, onPlay: (List<YtVideo>, Int) -> Unit) {
    val dataKey = listId ?: channelId
    val listKey = "yt-list:$dataKey"
    val data = browse.videos(dataKey)

    suspend fun loadNext() {
        if (data.loading || (data.started && data.next == null)) return
        data.loading = true
        runCatching {
            when {
                !data.started && listId == null -> YouTubeArchive.latest(channelId)
                !data.started -> YouTubeArchive.playlist(listId!!)
                else -> YouTubeArchive.more(data.next!!)
            }
        }.onSuccess { page ->
            val known = data.items.map { it.id }.toHashSet()
            data.items.addAll(page.videos.filter { it.id !in known })
            data.next = page.continuation
            data.started = true
            data.failed = false
        }.onFailure { data.failed = data.items.isEmpty() }
        data.loading = false
    }

    PagedList(
        listKey = listKey,
        browse = browse,
        keys = data.items.map { "yt:${it.id}" },
        loading = data.loading || !data.started,
        failed = data.failed,
        loadNext = ::loadNext,
        hasMore = !data.started || data.next != null,
    ) { i, focusModifier ->
        val v = data.items[i]
        ArchiveRow(
            thumbnail = v.thumbnail,
            title = v.title,
            subtitle = v.published,
            badge = v.duration,
            modifier = focusModifier,
            onFocus = { browse.focus(listKey, "yt:${v.id}", i) },
            onClick = { browse.requestFocusRestore(); onPlay(data.items.toList(), i) },
        )
    }
}

/**
 * Vertical list with infinite paging that keeps its scroll state in [BrowseState] and, when the viewer
 * returns from a video (or the app restarts), scrolls to and focuses the item they were on.
 */
@Composable
private fun PagedList(
    listKey: String,
    browse: BrowseState,
    keys: List<String>,
    loading: Boolean,
    failed: Boolean,
    hasMore: Boolean,
    loadNext: suspend () -> Unit,
    row: @Composable (index: Int, focusModifier: Modifier) -> Unit,
) {
    val state = browse.listState(listKey, browse.restoreIndex(keys))
    val restoreRequester = remember(listKey) { FocusRequester() }
    val restoreKey = browse.pendingRestoreKey()
    val restoreIndex = restoreKey?.let { keys.indexOf(it) } ?: -1

    LaunchedEffect(listKey) { if (keys.isEmpty()) loadNext() }
    // After an app restart the remembered item may sit beyond the first page: load until it's there
    LaunchedEffect(listKey, keys.size, restoreKey) {
        if (restoreKey != null && restoreIndex < 0 && hasMore && keys.size < browse.pendingIndex() + 1 && keys.size < 300) loadNext()
    }
    LaunchedEffect(listKey, restoreIndex) {
        if (restoreIndex >= 0) {
            state.scrollToItem((restoreIndex - 1).coerceAtLeast(0))
            delay(60)
            if (runCatching { restoreRequester.requestFocus() }.isSuccess) browse.focusRestored()
        }
    }
    val nearEnd by remember(listKey) {
        derivedStateOf {
            val last = state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= state.layoutInfo.totalItemsCount - 5
        }
    }
    LaunchedEffect(nearEnd, listKey, keys.size) { if (nearEnd && keys.isNotEmpty() && hasMore) loadNext() }

    when {
        failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Message("Архивата не е достапна", "Проверете ја интернет врската и обидете се повторно.")
        }
        keys.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Message(if (loading) "Се вчитува…" else "Нема емисии")
        }
        else -> LazyColumn(
            state = state,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 6.dp, bottom = 48.dp, end = Insets.Horizontal, start = 4.dp),
        ) {
            items(keys.size, key = { keys[it] }) { i ->
                row(i, if (i == restoreIndex) Modifier.focusRequester(restoreRequester) else Modifier)
            }
        }
    }
}

/** One episode / video: thumbnail on the left, full title (two lines, large) and details on the right. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ArchiveRow(
    thumbnail: String,
    title: String,
    subtitle: String?,
    badge: String?,
    modifier: Modifier,
    onFocus: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus() },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        colors = ClickableSurfaceDefaults.colors(containerColor = Palette.Surface, focusedContainerColor = Palette.SurfaceHigh),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = androidx.tv.material3.Border(androidx.compose.foundation.BorderStroke(3.dp, Palette.Gold), shape = RoundedCornerShape(14.dp)),
        ),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(192.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(Palette.SurfaceHigh)) {
                SubcomposeAsyncImage(
                    model = thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (!badge.isNullOrBlank()) {
                    Box(Modifier.align(Alignment.BottomEnd).padding(6.dp)) {
                        Pill { Text(badge, style = MaterialTheme.typography.labelMedium, color = androidx.compose.ui.graphics.Color.White) }
                    }
                }
            }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (focused) Palette.Gold else Palette.Text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}

/** Show / playlist chip: selects as soon as it gets focus, so the list loads without pressing OK. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Chip(text: String, selected: Boolean, onSelect: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onSelect,
        modifier = Modifier.onFocusChanged { focused = it.isFocused; if (it.isFocused) onSelect() },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Palette.Red else Palette.Surface,
            focusedContainerColor = Palette.Gold,
        ),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            color = if (focused) androidx.compose.ui.graphics.Color.Black else Palette.Text,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
        )
    }
}
