package mk.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.focusProperties
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.launch
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
 * Catch-up, navigated left to right: sources (MRT sections, then stations) | that source's shows
 * ("Најново" + shows/playlists) | the episode list. Both menus switch on focus. When the list has
 * focus the two menus tuck away so titles get the full width; ◀ from the list brings them back.
 * Where the viewer was lives in [BrowseState], so it survives playing a video or an app restart.
 */
@Composable
fun ArchiveTab(
    channels: List<Channel>,
    browse: BrowseState,
    entryFocus: FocusRequester,
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
    val scope = rememberCoroutineScope()
    var listFocused by remember { mutableStateOf(false) }
    val showFocus = remember { FocusRequester() }

    // Shows of the current source: MRT shows, or the station's YouTube playlists ("" = Најново)
    val mrtSection = source.removePrefix("mrt:").toIntOrNull().takeIf { source.startsWith("mrt:") }
    val ytChannel = youtubeChannels.firstOrNull { "yt:${it.catchup?.youtubeChannelId}" == source }
    var shows by remember(source) { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    LaunchedEffect(source, ytChannel) {
        shows = runCatching {
            when {
                mrtSection != null -> MrtArchive.shows(mrtSection).map { it.id.toString() to it.title }
                ytChannel != null -> YouTubeArchive.playlists(ytChannel.catchup!!.youtubeChannelId!!).map { it.id to it.title }
                else -> emptyList()
            }
        }.getOrDefault(emptyList())
    }
    val latestKey = mrtSection?.toString() ?: ""
    val selectedShow = browse.selection(source) ?: latestKey

    Row(Modifier.fillMaxSize().padding(top = Insets.Top + 8.dp)) {
        AnimatedVisibility(
            visible = !listFocused,
            enter = expandHorizontally() + fadeIn(),
            exit = shrinkHorizontally() + fadeOut(),
        ) {
            Row {
                SourcesColumn(youtubeChannels, browse, entryFocus, youtubeApp, onYoutubeApp)
                Spacer(Modifier.width(14.dp))
                ShowsColumn(
                    source = source,
                    items = listOf(latestKey to "Најново") + shows,
                    selected = selectedShow,
                    browse = browse,
                    selectedFocus = showFocus,
                )
            }
        }
        Spacer(Modifier.width(18.dp))
        Box(
            // starts below the clock in the top-right corner
            Modifier.weight(1f).fillMaxHeight().padding(top = 40.dp)
                .onFocusChanged { listFocused = it.hasFocus }
                .onPreviewKeyEvent { ev ->
                    // ◀ from the list goes back to the shows menu (and brings the menus back)
                    if (ev.type == KeyEventType.KeyDown && ev.key.nativeKeyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT) {
                        listFocused = false
                        scope.launch { delay(80); runCatching { showFocus.requestFocus() } }
                        true
                    } else false
                },
        ) {
            val wide = listFocused
            when {
                mrtSection != null -> {
                    val category = selectedShow.toIntOrNull() ?: mrtSection
                    val title = shows.firstOrNull { it.first == category.toString() }?.second
                        ?: MrtArchive.sections.firstOrNull { it.id == mrtSection }?.title.orEmpty()
                    EpisodeList(category, browse, wide, onPlay = { onPlayEpisode(it, title) })
                }
                ytChannel != null -> VideoList(
                    ytChannel.catchup!!.youtubeChannelId!!, selectedShow.ifEmpty { null }, browse, wide,
                    onPlay = { videos, i -> onPlayYoutube(videos, i, ytChannel.name) },
                )
            }
        }
    }
}

/** Column 1: MRT sections, then the stations (Albanian-language last), then the Premium switch. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SourcesColumn(
    youtubeChannels: List<Channel>,
    browse: BrowseState,
    entryFocus: FocusRequester,
    youtubeApp: Boolean,
    onYoutubeApp: (Boolean) -> Unit,
) {
    val source = browse.archiveSource
    val selectedRow = MrtArchive.sections.indexOfFirst { source == "mrt:${it.id}" }.takeIf { it >= 0 }?.plus(1)
        ?: youtubeChannels.indexOfFirst { source == "yt:${it.catchup?.youtubeChannelId}" }.takeIf { it >= 0 }
            ?.plus(MrtArchive.sections.size + 2) ?: 0
    val menuState = browse.listState("archive-menu")
    LaunchedEffect(selectedRow) { scrollIntoView(menuState, selectedRow) }
    LazyColumn(
        // Entering the column (▶ from the main menu, ◀ from shows) lands on the selected source
        Modifier.width(210.dp).fillMaxHeight().focusProperties { enter = { entryFocus } }.focusGroup(),
        state = menuState,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item { MenuHeader("МРТ архива") }
        items(MrtArchive.sections, key = { it.id }) { s ->
            val key = "mrt:${s.id}"
            MenuItem(
                s.title, selected = source == key, onSelect = { browse.selectSource(key) },
                modifier = if (source == key) Modifier.focusRequester(entryFocus) else Modifier,
            )
        }
        if (youtubeChannels.isNotEmpty()) {
            item { Spacer(Modifier.height(10.dp)); MenuHeader("Други станици") }
            items(youtubeChannels, key = { "yt-${it.id}" }) { ch ->
                val key = "yt:${ch.catchup!!.youtubeChannelId}"
                MenuItem(
                    ch.name, selected = source == key, onSelect = { browse.selectSource(key) },
                    modifier = if (source == key) Modifier.focusRequester(entryFocus) else Modifier,
                )
            }
            item { Spacer(Modifier.height(10.dp)); MenuHeader("YouTube Premium") }
            item { PremiumToggle(youtubeApp, onYoutubeApp) }
        }
    }
}

/** Column 2: the selected source's shows / playlists, vertically so long lists stay easy to scroll. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ShowsColumn(
    source: String,
    items: List<Pair<String, String>>,
    selected: String,
    browse: BrowseState,
    selectedFocus: FocusRequester,
) {
    val state = browse.listState("shows:$source")
    val selectedIndex = items.indexOfFirst { it.first == selected }
    LaunchedEffect(source, selectedIndex) { scrollIntoView(state, selectedIndex + 1) }
    LazyColumn(
        // Entering the column lands on the selected show, never on a neighbour
        Modifier.width(240.dp).fillMaxHeight().focusProperties { enter = { selectedFocus } }.focusGroup(),
        state = state,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item { MenuHeader("Емисии") }
        items(items, key = { it.first }) { (key, title) ->
            MenuItem(
                title, selected = key == selected, onSelect = { browse.select(source, key) },
                modifier = if (key == selected) Modifier.focusRequester(selectedFocus) else Modifier,
                maxLines = 2,
            )
        }
    }
}

/** Scrolls [row] into view only if it isn't already fully visible (so browsing never jumps). */
private suspend fun scrollIntoView(state: androidx.compose.foundation.lazy.LazyListState, row: Int) {
    if (row <= 0) return
    val info = state.layoutInfo
    val fullyVisible = info.visibleItemsInfo.any {
        it.index == row && it.offset >= info.viewportStartOffset && it.offset + it.size <= info.viewportEndOffset
    }
    if (!fullyVisible) state.scrollToItem((row - 3).coerceAtLeast(0))
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
private fun MenuItem(title: String, selected: Boolean, onSelect: () -> Unit, modifier: Modifier = Modifier, maxLines: Int = 1) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onSelect,
        modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused; if (it.isFocused) onSelect() },
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
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun EpisodeList(categoryId: Int, browse: BrowseState, wide: Boolean, onPlay: (ArchiveEpisode) -> Unit) {
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
            wide = wide,
            modifier = focusModifier,
            onFocus = { browse.focus(listKey, "ep:${ep.id}", i) },
            onClick = { browse.requestFocusRestore(); onPlay(ep) },
        )
    }
}

@Composable
private fun VideoList(channelId: String, listId: String?, browse: BrowseState, wide: Boolean, onPlay: (List<YtVideo>, Int) -> Unit) {
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
            wide = wide,
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
    wide: Boolean,
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
            Box(Modifier.width(if (wide) 192.dp else 128.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(Palette.SurfaceHigh)) {
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
                    maxLines = if (wide) 2 else 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}
