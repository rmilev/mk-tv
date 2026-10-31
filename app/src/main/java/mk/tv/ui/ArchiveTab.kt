package mk.tv.ui

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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
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
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import mk.tv.data.ArchiveEpisode
import mk.tv.data.ArchiveShow
import mk.tv.data.Channel
import mk.tv.data.MrtArchive

private const val YOUTUBE_SOURCE = -1

/**
 * Catch-up: MRT's own archive (sections -> shows -> episodes, played in-app) plus the official
 * YouTube channels of the private stations.
 */
@Composable
fun ArchiveTab(
    channels: List<Channel>,
    onPlayEpisode: (ArchiveEpisode, String) -> Unit,
    onOpenYoutube: (Channel) -> Unit,
) {
    var source by remember { mutableIntStateOf(MrtArchive.sections.first().id) }
    val youtubeChannels = remember(channels) { channels.filter { it.catchup?.type == "youtube" } }

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
                item { MenuItem("ТВ станици на YouTube", selected = source == YOUTUBE_SOURCE, onSelect = { source = YOUTUBE_SOURCE }) }
            }
        }
        Spacer(Modifier.width(28.dp))
        Box(Modifier.weight(1f).fillMaxHeight()) {
            if (source == YOUTUBE_SOURCE) {
                YoutubeStations(youtubeChannels, onOpenYoutube)
            } else {
                MrtSection(source, onPlayEpisode)
            }
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
    Surface(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth().onFocusSelect(onSelect),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Palette.SurfaceHigh else androidx.compose.ui.graphics.Color.Transparent,
            contentColor = if (selected) Palette.Text else Palette.TextDim,
            focusedContainerColor = Palette.Gold,
            focusedContentColor = androidx.compose.ui.graphics.Color.Black,
        ),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = androidx.compose.ui.graphics.Color.Unspecified, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
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
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Palette.Red else Palette.Surface,
            contentColor = Palette.Text,
            focusedContainerColor = Palette.Gold,
            focusedContentColor = androidx.compose.ui.graphics.Color.Black,
        ),
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = androidx.compose.ui.graphics.Color.Unspecified, maxLines = 1, modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp))
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

@Composable
private fun YoutubeStations(channels: List<Channel>, onOpen: (Channel) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Text(
            "Емисиите на приватните станици се отвораат во YouTube апликацијата.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
            contentPadding = PaddingValues(bottom = 48.dp, end = Insets.Horizontal, start = 4.dp),
        ) {
            items(channels, key = { it.id }) { ch ->
                FocusCard(onClick = { onOpen(ch) }) { focused ->
                    Column {
                        ChannelArt(ch, Modifier.fillMaxWidth().height(120.dp))
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(ch.name, style = MaterialTheme.typography.titleSmall, color = if (focused) Palette.Gold else Palette.Text, modifier = Modifier.weight(1f))
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = Palette.TextDim, modifier = Modifier.width(18.dp))
                        }
                    }
                }
            }
        }
    }
}
