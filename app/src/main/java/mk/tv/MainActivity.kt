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
import mk.tv.data.Channel
import mk.tv.ui.AppViewModel
import mk.tv.ui.HomeScreen
import mk.tv.ui.MkTvTheme
import mk.tv.ui.Palette
import mk.tv.ui.PlayerScreen
import mk.tv.ui.PlaybackItem

sealed interface Route {
    data object Home : Route
    data class Live(val channelId: String) : Route
    data class Episode(val episode: ArchiveEpisode, val show: String) : Route
}

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MkTvTheme {
                val channels by vm.channels.collectAsStateWithLifecycle()
                val favorites by vm.favorites.collectAsStateWithLifecycle()
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
                            onOpenYoutube = ::openYoutube,
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

    /** Private stations publish their shows on YouTube; hand off to the YouTube (TV) app. */
    private fun openYoutube(channel: Channel) {
        val id = channel.catchup?.youtubeChannelId ?: return
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/channel/$id/videos"))
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Инсталирајте YouTube за архивата на ${channel.name}", Toast.LENGTH_LONG).show()
        }
    }
}
