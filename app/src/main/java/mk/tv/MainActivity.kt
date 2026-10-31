package mk.tv

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mk.tv.data.ArchiveEpisode
import mk.tv.data.YtVideo
import mk.tv.ui.AppViewModel
import mk.tv.ui.HomeScreen
import mk.tv.ui.MkTvTheme
import mk.tv.ui.Palette
import mk.tv.ui.PlayerScreen
import mk.tv.ui.PlaybackItem
import mk.tv.ui.YouTubePlayerScreen

sealed interface Route {
    data object Home : Route
    data class Live(val channelId: String) : Route
    data class Episode(val episode: ArchiveEpisode, val show: String) : Route
    data class YouTube(val videos: List<YtVideo>, val index: Int, val station: String) : Route
}

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MkTvTheme {
                val channels by vm.channels.collectAsStateWithLifecycle()
                val favorites by vm.favorites.collectAsStateWithLifecycle()
                val youtubeApp by vm.youtubeApp.collectAsStateWithLifecycle()
                val stack = remember { mutableStateListOf<Route>(Route.Home) }
                var tab by rememberSaveable { mutableIntStateOf(0) }

                BackHandler(enabled = stack.size > 1) { stack.removeAt(stack.lastIndex) }

                Box(Modifier.fillMaxSize().background(Palette.Background)) {
                    when (val route = stack.last()) {
                        Route.Home -> HomeScreen(
                            channels = channels,
                            favorites = favorites,
                            lastChannelId = vm.prefs.lastChannel,
                            tab = tab,
                            onTab = { tab = it },
                            onPlayChannel = { stack.add(Route.Live(it.id)) },
                            onToggleFavorite = { vm.toggleFavorite(it.id) },
                            onPlayEpisode = { ep, show -> stack.add(Route.Episode(ep, show)) },
                            onPlayYoutube = { videos, i, station ->
                                // Default: the signed-in YouTube app (Premium, no ads); in-app player when switched off or missing
                                if (youtubeApp && openYoutubeVideo(videos[i])) Unit else stack.add(Route.YouTube(videos, i, station))
                            },
                            youtubeApp = youtubeApp,
                            onYoutubeApp = vm::setYoutubeApp,
                        )
                        is Route.Live -> {
                            val start = channels.firstOrNull { it.id == route.channelId }
                            if (start != null) {
                                // Zapping stays within TV or within radio, in home-screen order
                                val lineup = channels.filter { it.live && it.radio == start.radio }
                                PlayerScreen(
                                    item = PlaybackItem.Live(start),
                                    lineup = lineup,
                                    favorites = favorites,
                                    onChannelChanged = { vm.prefs.lastChannel = it.id },
                                    onExit = { stack.removeAt(stack.lastIndex) },
                                )
                            }
                        }
                        is Route.YouTube -> YouTubePlayerScreen(
                            videos = route.videos,
                            startIndex = route.index,
                            station = route.station,
                            onOpenInYoutube = { openYoutubeVideo(it) },
                            onExit = { stack.removeAt(stack.lastIndex) },
                        )
                        is Route.Episode -> PlayerScreen(
                            item = PlaybackItem.Vod(route.episode, route.show),
                            lineup = emptyList(),
                            favorites = favorites,
                            onChannelChanged = {},
                            onExit = { stack.removeAt(stack.lastIndex) },
                        )
                    }
                }
            }
        }
    }

    /**
     * Opens a video in the official YouTube app (signed in, so Premium plays it without ads). Used for the
     * "без реклами" setting and when a video's owner disabled embedding. Back returns to МК ТВ.
     */
    private fun openYoutubeVideo(video: YtVideo): Boolean {
        val uri = Uri.parse("https://www.youtube.com/watch?v=${video.id}")
        val tvApp = Intent(Intent.ACTION_VIEW, uri).setPackage("com.google.android.youtube.tv")
        val intent = if (tvApp.resolveActivity(packageManager) != null) tvApp else Intent(Intent.ACTION_VIEW, uri)
        return try {
            startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Инсталирајте YouTube за ова видео", Toast.LENGTH_LONG).show()
            false
        }
    }
}
