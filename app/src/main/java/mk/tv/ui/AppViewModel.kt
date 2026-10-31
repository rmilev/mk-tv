package mk.tv.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import mk.tv.data.Channel
import mk.tv.data.ChannelRepository
import mk.tv.data.Prefs

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ChannelRepository(app)
    val prefs = Prefs(app)
    /** Where the viewer is (tab, archive section, show, focused item); survives leaving the app. */
    val browse = BrowseState(prefs)

    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    val channels: StateFlow<List<Channel>> = _channels

    private val _favorites = MutableStateFlow(prefs.favorites)
    val favorites: StateFlow<Set<String>> = _favorites

    private val _youtubeApp = MutableStateFlow(prefs.youtubeApp)
    val youtubeApp: StateFlow<Boolean> = _youtubeApp

    fun setYoutubeApp(on: Boolean) {
        prefs.youtubeApp = on
        _youtubeApp.value = on
    }

    init {
        viewModelScope.launch {
            _channels.value = repo.load()
            // Pick up new/fixed links published on GitHub without reinstalling the APK
            repo.refresh(_channels.value, repo.version())?.let { _channels.value = it }
        }
    }

    fun toggleFavorite(id: String) {
        _favorites.value = prefs.toggleFavorite(id)
    }

    fun channel(id: String): Channel? = _channels.value.firstOrNull { it.id == id }
}
