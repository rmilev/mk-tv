package mk.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.Icon
import androidx.tv.material3.NavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.NavigationDrawerItemDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import mk.tv.data.ArchiveEpisode
import mk.tv.data.Channel
import mk.tv.data.YtVideo

private data class Section(val title: String, val icon: ImageVector)

private val Sections = listOf(
    Section("Во живо", Icons.Filled.LiveTv),
    Section("Архива", Icons.Filled.VideoLibrary),
    Section("Радио", Icons.Filled.Radio),
)

/**
 * Home: a slim icon menu on the left (expands with labels when focused) and the selected section's
 * content to its right. Everything navigates left/right; nothing sits above the content, so holding
 * ▲ simply stops at the top instead of jumping between sections.
 */
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun HomeScreen(
    channels: List<Channel>,
    favorites: Set<String>,
    lastChannelId: String?,
    browse: BrowseState,
    onPlayChannel: (Channel) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
    onPlayEpisode: (List<ArchiveEpisode>, Int, String) -> Unit,
    onPlayYoutube: (List<YtVideo>, Int, String) -> Unit,
    youtubeApp: Boolean,
    onYoutubeApp: (Boolean) -> Unit,
) {
    val tab = browse.tab
    // Only the archive restores a focused list item; elsewhere don't let it grab focus later
    LaunchedEffect(tab) { if (tab != 1) browse.focusRestored() }
    // Focus target for the archive when the app starts there with nothing to restore
    val archiveEntry = remember { SafeFocus() }
    val sectionFocus = remember { List(Sections.size) { SafeFocus() } }
    LaunchedEffect(Unit) {
        if (tab == 1) browse.consumeLiveRestore()
        // If a list item is about to be restored (returning from a video), leave focus to it
        val restoring = browse.pendingRestoreKey() != null
        delay(250)
        if (tab == 1 && !restoring) archiveEntry.request()
    }

    NavigationDrawer(
        drawerContent = { drawer ->
            Column(
                Modifier.fillMaxHeight().background(Palette.Background).padding(horizontal = 12.dp, vertical = Insets.Top)
                    // ◀ from the content always lands on the current section, never a neighbour
                    .focusProperties { enter = { sectionFocus[tab].target } }
                    .focusGroup(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(Modifier.padding(start = 6.dp, bottom = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                    SunMark(36.dp)
                    if (drawer == DrawerValue.Open) {
                        Spacer(Modifier.width(12.dp))
                        Text("МК ТВ", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                    }
                }
                Sections.forEachIndexed { i, section ->
                    NavigationDrawerItem(
                        selected = tab == i,
                        onClick = { browse.selectTab(i) },
                        // Switch section as soon as an item is focused, like the rest of the app
                        modifier = sectionFocus[i].modifier().onFocusChanged { if (it.isFocused) browse.selectTab(i) },
                        leadingContent = { Icon(section.icon, contentDescription = section.title) },
                        colors = NavigationDrawerItemDefaults.colors(
                            focusedContainerColor = Palette.Gold,
                            focusedContentColor = Color.Black,
                            selectedContainerColor = Palette.SurfaceHigh,
                            selectedContentColor = Palette.Text,
                            contentColor = Palette.TextDim,
                        ),
                    ) {
                        Text(section.title, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        },
    ) {
        Box(Modifier.fillMaxSize().padding(start = 24.dp)) {
            when (tab) {
                0 -> ChannelsTab(channels.filter { it.live && !it.radio }, favorites, lastChannelId, browse, "tv", onPlayChannel, onToggleFavorite)
                1 -> ArchiveTab(channels, browse, archiveEntry, onPlayEpisode, onPlayYoutube, youtubeApp, onYoutubeApp)
                else -> ChannelsTab(channels.filter { it.live && it.radio }, favorites, lastChannelId, browse, "radio", onPlayChannel, onToggleFavorite)
            }
        }
    }
}

/** Live TV / radio: a hero panel for the focused channel above one continuous grid of channels. */
@Composable
private fun ChannelsTab(
    channels: List<Channel>,
    favorites: Set<String>,
    lastChannelId: String?,
    browse: BrowseState,
    gridKey: String,
    onPlay: (Channel) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
) {
    if (channels.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Message("Се вчитуваат каналите…") }
        return
    }
    // One grid, no category rows: favourites first, Albanian-language channels at the end
    val ordered = remember(channels, favorites) {
        channels.withIndex().sortedWith(
            compareBy({ it.value.id !in favorites }, { it.value.albanian }, { it.index })
        ).map { it.value }
    }
    var focusedChannel by remember(channels) { mutableStateOf(ordered.firstOrNull { it.id == lastChannelId } ?: ordered.first()) }
    val initialId = remember(channels) { ordered.firstOrNull { it.id == lastChannelId }?.id ?: ordered.first().id }
    val initialFocus = remember { FocusRequester() }
    val gridState = browse.gridState(gridKey, ordered.indexOfFirst { it.id == initialId }.coerceAtLeast(0))

    LaunchedEffect(initialId) {
        val i = ordered.indexOfFirst { it.id == initialId }
        if (i >= 0) gridState.scrollToItem(i)
        if (browse.consumeLiveRestore()) {
            delay(60)
            runCatching { initialFocus.requestFocus() }
        }
    }

    Column(Modifier.fillMaxSize()) {
        HeroPanel(focusedChannel, focusedChannel.id in favorites)
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            state = gridState,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding = PaddingValues(start = 8.dp, end = Insets.Horizontal, top = 12.dp, bottom = 48.dp),
        ) {
            items(ordered, key = { it.id }) { ch ->
                Box(if (ch.id == initialId) Modifier.focusRequester(initialFocus) else Modifier) {
                    ChannelCard(
                        channel = ch,
                        favorite = ch.id in favorites,
                        onClick = { browse.requestLiveRestore(); onPlay(ch) },
                        onLongClick = { onToggleFavorite(ch) },
                        onFocus = { focusedChannel = ch },
                        width = null,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroPanel(channel: Channel, favorite: Boolean) {
    Row(
        Modifier.fillMaxWidth().height(150.dp).padding(start = 8.dp, end = Insets.Horizontal, top = Insets.Top, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChannelArt(
            channel,
            Modifier.height(124.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp)),
            textSize = 38,
        )
        Spacer(Modifier.width(32.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(channel.name, style = MaterialTheme.typography.displaySmall)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LiveBadge()
                Text(channel.group, style = MaterialTheme.typography.titleMedium, color = Palette.TextDim)
                if (channel.catchup != null) Text("• Има архива", style = MaterialTheme.typography.titleMedium, color = Palette.TextDim)
            }
            Text(
                if (favorite) "★ Во омилени – задржете OK за отстранување." else "Задржете OK за омилени.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
