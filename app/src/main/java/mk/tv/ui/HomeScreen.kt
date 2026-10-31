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
    tab: Int,
    onTab: (Int) -> Unit,
    onPlayChannel: (Channel) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
    onPlayEpisode: (ArchiveEpisode, String) -> Unit,
    onPlayYoutube: (List<YtVideo>, Int, String) -> Unit,
    youtubeApp: Boolean,
    onYoutubeApp: (Boolean) -> Unit,
) {
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
                        onFocus = { onTab(i) },
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
            0 -> ChannelsTab(channels.filter { it.live && !it.radio }, favorites, lastChannelId, onPlayChannel, onToggleFavorite)
            1 -> ArchiveTab(channels, onPlayEpisode, onPlayYoutube, youtubeApp, onYoutubeApp)
            else -> ChannelsTab(channels.filter { it.live && it.radio }, favorites, lastChannelId, onPlayChannel, onToggleFavorite)
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

/** Live TV / radio: a hero panel for the focused channel above rows grouped by category. */
@Composable
private fun ChannelsTab(
    channels: List<Channel>,
    favorites: Set<String>,
    lastChannelId: String?,
    onPlay: (Channel) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
) {
    if (channels.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Message("Се вчитуваат каналите…") }
        return
    }
    var focusedChannel by remember(channels) { mutableStateOf(channels.firstOrNull { it.id == lastChannelId } ?: channels.first()) }
    val rows = remember(channels, favorites) {
        buildList {
            val favs = channels.filter { it.id in favorites }
            if (favs.isNotEmpty()) add("Омилени" to favs)
            channels.groupBy { it.group }.forEach { (group, list) -> add(group to list) }
        }
    }
    // Return focus to the channel last watched (or the first one) so OK replays it instantly
    val initialFocus = remember { FocusRequester() }
    val initialId = remember(channels) { channels.firstOrNull { it.id == lastChannelId }?.id ?: channels.first().id }
    var initialRequested by remember(channels) { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        HeroPanel(focusedChannel, focusedChannel.id in favorites)
        LazyColumn(
            contentPadding = PaddingValues(start = Insets.Horizontal - 16.dp, end = 0.dp, top = 8.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            rows.forEachIndexed { rowIndex, (title, list) ->
                item(key = "row-$title") {
                    Column {
                        SectionTitle(title, Modifier.padding(start = 16.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                            contentPadding = PaddingValues(vertical = 12.dp, horizontal = 16.dp),
                        ) {
                            items(list, key = { "$title-${it.id}" }) { ch ->
                                val isInitial = ch.id == initialId && (title != "Омилени" || rows.none { it.first != "Омилени" && it.second.any { c -> c.id == initialId } })
                                Box(if (isInitial) Modifier.focusRequester(initialFocus) else Modifier) {
                                    ChannelCard(
                                        channel = ch,
                                        favorite = ch.id in favorites,
                                        onClick = { onPlay(ch) },
                                        onLongClick = { onToggleFavorite(ch) },
                                        onFocus = { focusedChannel = ch },
                                    )
                                }
                                if (isInitial && !initialRequested) {
                                    LaunchedEffect(Unit) {
                                        runCatching { initialFocus.requestFocus() }
                                        initialRequested = true
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroPanel(channel: Channel, favorite: Boolean) {
    Row(
        Modifier.fillMaxWidth().height(170.dp).padding(horizontal = Insets.Horizontal, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChannelArt(
            channel,
            Modifier.height(150.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp)),
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
