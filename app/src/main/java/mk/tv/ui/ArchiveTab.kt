package mk.tv.ui

import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import mk.tv.data.ArchiveEpisode
import mk.tv.data.ArchiveShow
import mk.tv.data.Channel
import mk.tv.data.MrtArchive
import mk.tv.data.YouTubeArchive
import mk.tv.data.YtPlaylist
import mk.tv.data.YtVideo


/**
 * Catch-up: MRT's own archive (sections -> shows -> episodes) and the private stations' official
 * YouTube channels (latest + playlists), all played inside the app.
 */
@Composable
fun ArchiveTab(
    channels: List<Channel>,
    onPlayEpisode: (ArchiveEpisode, String) -> Unit,
    onPlayYoutube: (List<YtVideo>, Int, String) -> Unit,
    youtubeApp: Boolean,
    onYoutubeApp: (Boolean) -> Unit,
) {
    // Positive ids = MRT sections; negative = YouTube station at index -(id + 1)
    var source by remember { mutableIntStateOf(MrtArchive.sections.first().id) }
    val youtubeChannels = remember(channels) { channels.filter { it.catchup?.type == "youtube" && it.catchup.youtubeChannelId != null } }

    Row(Modifier.fillMaxSize().padding(start = Insets.Horizontal, top = 8.dp)) {
        // Left menu: switches content on focus, so browsing needs no extra clicks
        LazyColumn(
            Modifier.width(250.dp).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            item { MenuHeader("МРТ архива") }
            items(MrtArchive.sections, key = { it.id }) { s ->
                MenuItem(s.title, selected = source == s.id, onSelect = { source = s.id })
            }
            if (youtubeChannels.isNotEmpty()) {
                item { Spacer(Modifier.height(12.dp)); MenuHeader("Други станици") }
                itemsIndexed(youtubeChannels, key = { _, c -> "yt-${c.id}" }) { i, ch ->
                    MenuItem(ch.name, selected = source == -(i + 1), onSelect = { source = -(i + 1) })
                }
                item { Spacer(Modifier.height(12.dp)); MenuHeader("YouTube Premium") }
                item { PremiumToggle(youtubeApp, onYoutubeApp) }
            }
        }
        Spacer(Modifier.width(28.dp))
        Box(Modifier.weight(1f).fillMaxHeight()) {
            if (source < 0) {
                youtubeChannels.getOrNull(-source - 1)?.let { YouTubeStation(it, onPlayYoutube) }
            } else {
                MrtSection(source, onPlayEpisode)
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
                if (on) "Видеата се отвораат во YouTube апликацијата. Назад враќа во МК ТВ." else "Видеата се пуштаат во МК ТВ (со реклами).",
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

/** Shows of one MRT section as chips, then a paged grid of episodes. */
@Composable
private fun MrtSection(sectionId: Int, onPlayEpisode: (ArchiveEpisode, String) -> Unit) {
    val sectionTitle = MrtArchive.sections.first { it.id == sectionId }.title
    var shows by remember(sectionId) { mutableStateOf<List<ArchiveShow>>(emptyList()) }
    var category by remember(sectionId) { mutableIntStateOf(sectionId) }
    LaunchedEffect(sectionId) { shows = runCatching { MrtArchive.shows(sectionId) }.getOrDefault(emptyList()) }

    Column(Modifier.fillMaxSize()) {
        if (shows.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 14.dp, end = Insets.Horizontal),
            ) {
                item(key = "all") { Chip("Најново", category == sectionId) { category = sectionId } }
                items(shows, key = { it.id }) { show -> Chip(show.title, category == show.id) { category = show.id } }
            }
        }
        val showTitle = shows.firstOrNull { it.id == category }?.title ?: sectionTitle
        EpisodeGrid(category, onPlay = { onPlayEpisode(it, showTitle) })
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = Modifier.onFocusChanged { focused = it.isFocused },
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

@Composable
private fun EpisodeGrid(categoryId: Int, onPlay: (ArchiveEpisode) -> Unit) {
    val episodes = remember(categoryId) { mutableStateListOf<ArchiveEpisode>() }
    var page by remember(categoryId) { mutableIntStateOf(0) }
    var pageCount by remember(categoryId) { mutableIntStateOf(1) }
    var loading by remember(categoryId) { mutableStateOf(false) }
    var error by remember(categoryId) { mutableStateOf(false) }
    val grid = rememberLazyGridState()

    suspend fun loadNext() {
        if (loading || page >= pageCount) return
        loading = true
        runCatching { MrtArchive.episodes(categoryId, page + 1) }
            .onSuccess { result ->
                val known = episodes.map { it.id }.toHashSet()
                episodes.addAll(result.episodes.filter { it.id !in known })
                page = result.page
                pageCount = result.pageCount
                error = false
            }
            .onFailure { error = episodes.isEmpty() }
        loading = false
    }

    LaunchedEffect(categoryId) { loadNext() }
    // Infinite scroll: fetch the next page when focus nears the end
    val nearEnd by remember(categoryId) {
        derivedStateOf {
            val last = grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            episodes.isNotEmpty() && last >= episodes.size - 8
        }
    }
    LaunchedEffect(nearEnd, categoryId) { if (nearEnd) loadNext() }

    when {
        error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Message("Архивата не е достапна", "Проверете ја интернет врската и обидете се повторно.")
        }
        episodes.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Message(if (loading) "Се вчитува…" else "Нема емисии")
        }
        else -> LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            state = grid,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
            contentPadding = PaddingValues(top = 6.dp, bottom = 48.dp, end = Insets.Horizontal, start = 4.dp),
        ) {
            items(episodes, key = { it.id }) { ep -> EpisodeCard(ep, onClick = { onPlay(ep) }) }
        }
    }
}

/** One station's YouTube catch-up: "Најново" plus its playlists (shows) as chips, then a paged video grid. */
@Composable
private fun YouTubeStation(channel: Channel, onPlay: (List<YtVideo>, Int, String) -> Unit) {
    val channelId = channel.catchup?.youtubeChannelId ?: return
    var playlists by remember(channelId) { mutableStateOf<List<YtPlaylist>>(emptyList()) }
    var listId by remember(channelId) { mutableStateOf<String?>(null) }
    LaunchedEffect(channelId) { playlists = runCatching { YouTubeArchive.playlists(channelId) }.getOrDefault(emptyList()) }

    Column(Modifier.fillMaxSize()) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 14.dp, end = Insets.Horizontal),
        ) {
            item(key = "latest") { Chip("Најново", listId == null) { listId = null } }
            items(playlists, key = { it.id }) { pl -> Chip(pl.title, listId == pl.id) { listId = pl.id } }
        }
        YouTubeGrid(channelId, listId, onPlay = { videos, i -> onPlay(videos, i, channel.name) })
    }
}

@Composable
private fun YouTubeGrid(channelId: String, listId: String?, onPlay: (List<YtVideo>, Int) -> Unit) {
    val key = listId ?: channelId
    val videos = remember(key) { mutableStateListOf<YtVideo>() }
    var next by remember(key) { mutableStateOf<String?>(null) }
    var started by remember(key) { mutableStateOf(false) }
    var loading by remember(key) { mutableStateOf(false) }
    var error by remember(key) { mutableStateOf(false) }
    val grid = rememberLazyGridState()

    suspend fun load() {
        if (loading || (started && next == null)) return
        loading = true
        runCatching {
            when {
                !started && listId == null -> YouTubeArchive.latest(channelId)
                !started -> YouTubeArchive.playlist(listId!!)
                else -> YouTubeArchive.more(next!!)
            }
        }.onSuccess { page ->
            val known = videos.map { it.id }.toHashSet()
            videos.addAll(page.videos.filter { it.id !in known })
            next = page.continuation
            started = true
            error = false
        }.onFailure { error = videos.isEmpty() }
        loading = false
    }

    LaunchedEffect(key) { load() }
    val nearEnd by remember(key) {
        derivedStateOf {
            val last = grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            videos.isNotEmpty() && last >= videos.size - 8
        }
    }
    LaunchedEffect(nearEnd, key) { if (nearEnd) load() }

    when {
        error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Message("Видеата не се достапни", "Проверете ја интернет врската и обидете се повторно.")
        }
        videos.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Message(if (loading || !started) "Се вчитува…" else "Нема видеа")
        }
        else -> LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            state = grid,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
            contentPadding = PaddingValues(top = 6.dp, bottom = 48.dp, end = Insets.Horizontal, start = 4.dp),
        ) {
            itemsIndexed(videos, key = { _, v -> v.id }) { i, v ->
                VideoCard(v, onClick = { onPlay(videos.toList(), i) })
            }
        }
    }
}
