package mk.tv.ui

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
import androidx.compose.runtime.produceState
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
import androidx.tv.material3.Tab
import androidx.tv.material3.TabDefaults
import androidx.tv.material3.TabRow
import androidx.tv.material3.TabRowDefaults
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import mk.tv.data.ArchiveEpisode
import mk.tv.data.Channel
import mk.tv.data.YtVideo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Tabs = listOf("Во живо", "Архива", "Радио")

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(
    channels: List<Channel>,
    favorites: Set<String>,
    lastChannelId: String?,
    browse: BrowseState,
    onPlayChannel: (Channel) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
    onPlayEpisode: (ArchiveEpisode, String) -> Unit,
    onPlayYoutube: (List<YtVideo>, Int, String) -> Unit,
    youtubeApp: Boolean,
    onYoutubeApp: (Boolean) -> Unit,
) {
    val tab = browse.tab
    val onTab: (Int) -> Unit = browse::selectTab
    // Only the archive restores a focused list item; elsewhere don't let it grab focus later
    LaunchedEffect(tab) { if (tab != 1) browse.focusRestored() }
    // On (re)start Android focuses the first tab, which would switch to "Во живо": ignore focus until the
    // saved tab has been focused, then let the tab's content restore its own item.
    val tabRequesters = remember { List(Tabs.size) { FocusRequester() } }
    var tabsReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(150)
        if (tab != 0) runCatching { tabRequesters[tab].requestFocus() }
        tabsReady = true
    }
    Column(Modifier.fillMaxSize()) {
        // Top bar: brand, tabs (switch on focus, like every TV app), clock
        Row(
            Modifier.fillMaxWidth().padding(start = Insets.Horizontal, end = Insets.Horizontal, top = Insets.Top, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SunMark()
            Spacer(Modifier.width(12.dp))
            Text("МК ТВ", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.width(36.dp))
            TabRow(
                selectedTabIndex = tab,
                indicator = { tabPositions, doesTabRowHaveFocus ->
                    TabRowDefaults.PillIndicator(
                        currentTabPosition = tabPositions[tab],
                        doesTabRowHaveFocus = doesTabRowHaveFocus,
                        activeColor = Palette.Gold,
                        inactiveColor = Palette.SurfaceHigh,
                    )
                },
            ) {
                Tabs.forEachIndexed { i, title ->
                    Tab(
                        selected = tab == i,
                        onFocus = { if (tabsReady) onTab(i) },
                        modifier = Modifier.focusRequester(tabRequesters[i]),
                        colors = TabDefaults.pillIndicatorTabColors(
                            contentColor = Palette.TextDim,
                            selectedContentColor = Palette.Text,
                            focusedContentColor = androidx.compose.ui.graphics.Color.Black,
                            focusedSelectedContentColor = androidx.compose.ui.graphics.Color.Black,
                        ),
                    ) {
                        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Clock()
        }

        when (tab) {
            0 -> ChannelsTab(channels.filter { it.live && !it.radio }, favorites, lastChannelId, browse, "tv", onPlayChannel, onToggleFavorite)
            1 -> ArchiveTab(channels, browse, onPlayEpisode, onPlayYoutube, youtubeApp, onYoutubeApp)
            else -> ChannelsTab(channels.filter { it.live && it.radio }, favorites, lastChannelId, browse, "radio", onPlayChannel, onToggleFavorite)
        }
    }
}

@Composable
private fun Clock() {
    val time by produceState(initialValue = "") {
        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        while (true) {
            value = fmt.format(Date())
            delay(10_000)
        }
    }
    Text(time, style = MaterialTheme.typography.headlineSmall, color = Palette.TextDim)
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
        delay(60)
        runCatching { initialFocus.requestFocus() }
    }

    Column(Modifier.fillMaxSize()) {
        HeroPanel(focusedChannel, focusedChannel.id in favorites)
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            state = gridState,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding = PaddingValues(start = Insets.Horizontal, end = Insets.Horizontal, top = 12.dp, bottom = 48.dp),
        ) {
            items(ordered, key = { it.id }) { ch ->
                Box(if (ch.id == initialId) Modifier.focusRequester(initialFocus) else Modifier) {
                    ChannelCard(
                        channel = ch,
                        favorite = ch.id in favorites,
                        onClick = { onPlay(ch) },
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
        Modifier.fillMaxWidth().height(140.dp).padding(horizontal = Insets.Horizontal, vertical = 6.dp),
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
                when {
                    channel.mkOnly -> "Достапно само од Македонија – вклучете VPN (North Macedonia)."
                    else -> "Достапно насекаде."
                } + if (favorite) "  ★ Во омилени – задржете OK за отстранување." else "  Задржете OK за омилени.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
