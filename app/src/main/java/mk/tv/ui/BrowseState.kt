package mk.tv.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import mk.tv.data.ArchiveEpisode
import mk.tv.data.Prefs
import mk.tv.data.YtVideo

/**
 * Where the viewer is in the app: tab, archive section/station, selected show, scroll position and
 * focused item. Lives in the ViewModel, so opening a video (in-app or in YouTube) and coming back
 * returns to the same place; it is also saved to prefs, so it survives the TV closing the app.
 */
class BrowseState(private val prefs: Prefs) {
    var tab by mutableIntStateOf(prefs.getString(K_TAB)?.toIntOrNull() ?: 0)
        private set

    /** "mrt:<sectionId>" or "yt:<youtubeChannelId>". */
    var archiveSource by mutableStateOf(prefs.getString(K_SOURCE) ?: "mrt:2")
        private set

    /** Archive menu level: 1 = sources (МРТ sections, stations), 2 = shows of [archiveSource]. */
    var archiveLevel by mutableIntStateOf(prefs.getString(K_LEVEL)?.toIntOrNull()?.coerceIn(1, 2) ?: 1)
        private set

    fun showArchiveLevel(level: Int) {
        if (level == archiveLevel) return
        archiveLevel = level
        prefs.putString(K_LEVEL, level.toString())
    }

    /** Per MRT section: selected show (category id). Per YouTube channel: selected playlist id ("" = latest). */
    private val selection = mutableStateMapOf<String, String>().apply { putAll(decode(prefs.getString(K_SELECTION))) }

    /** Item to give focus to when a list is shown again (e.g. "ep:598557", "yt:aP_8uOHiOYs"). */
    var focusKey: String? = prefs.getString(K_FOCUS)
        private set
    private var focusIndex: Int = prefs.getString(K_FOCUS_INDEX)?.toIntOrNull() ?: 0

    fun selectTab(t: Int) {
        if (t == tab) return
        tab = t
        prefs.putString(K_TAB, t.toString())
    }

    fun selectSource(s: String) {
        if (s == archiveSource) return
        archiveSource = s
        prefs.putString(K_SOURCE, s)
    }

    fun selection(source: String): String? = selection[source]

    fun select(source: String, value: String) {
        if (selection[source] == value) return
        selection[source] = value
        prefs.putString(K_SELECTION, encode(selection))
    }

    /** Remembers the focused item of the list [listKey] (so it can be scrolled to and re-focused). */
    fun focus(listKey: String, itemKey: String, index: Int) {
        focusKey = itemKey
        focusIndex = index
        lastList = listKey
        prefs.putString(K_FOCUS, itemKey)
        prefs.putString(K_FOCUS_INDEX, index.toString())
    }

    private var lastList: String? = null

    /**
     * True right after the app (re)starts or a video was opened: the next list that contains [focusKey]
     * scrolls to it and takes focus. False while browsing, so lists never steal focus from the menu.
     */
    private var restorePending by mutableStateOf(true)

    fun requestFocusRestore() { restorePending = true }

    /** After a player, focus [itemKey] (e.g. the last video watched after ▲▼ in the player). */
    fun returnTo(itemKey: String) {
        focusKey = itemKey
        prefs.putString(K_FOCUS, itemKey)
        restorePending = true
    }

    /** Live/radio grid: focus the last channel at app start and after returning from a channel only. */
    private var liveRestorePending = true
    fun requestLiveRestore() { liveRestorePending = true }
    fun consumeLiveRestore(): Boolean = liveRestorePending.also { liveRestorePending = false }
    fun pendingRestoreKey(): String? = if (restorePending) focusKey else null
    fun focusRestored() { restorePending = false }

    /** Index to start a list at when it is (re)created, e.g. after the app was closed. */
    fun restoreIndex(itemKeys: List<String>): Int = focusKey?.let { k -> itemKeys.indexOf(k).takeIf { it >= 0 } } ?: 0

    // Scroll states and loaded pages outlive the composables, so lists come back instantly, in place.
    private val listStates = mutableMapOf<String, LazyListState>()
    private val gridStates = mutableMapOf<String, LazyGridState>()

    fun listState(key: String, initialIndex: Int = 0): LazyListState =
        listStates.getOrPut(key) { LazyListState(firstVisibleItemIndex = initialIndex.coerceAtLeast(0)) }

    fun gridState(key: String, initialIndex: Int = 0): LazyGridState =
        gridStates.getOrPut(key) { LazyGridState(firstVisibleItemIndex = initialIndex.coerceAtLeast(0)) }

    class Episodes {
        val items: SnapshotStateList<ArchiveEpisode> = mutableStateListOf()
        var page by mutableIntStateOf(0)
        var pageCount by mutableIntStateOf(1)
        var loading by mutableStateOf(false)
        var failed by mutableStateOf(false)
    }

    class Videos {
        val items: SnapshotStateList<YtVideo> = mutableStateListOf()
        var next by mutableStateOf<String?>(null)
        var started by mutableStateOf(false)
        var loading by mutableStateOf(false)
        var failed by mutableStateOf(false)
    }

    private val episodes = mutableMapOf<Int, Episodes>()
    private val videos = mutableMapOf<String, Videos>()

    fun episodes(categoryId: Int): Episodes = episodes.getOrPut(categoryId) { Episodes() }
    fun videos(key: String): Videos = videos.getOrPut(key) { Videos() }

    /** Index of the remembered item if the app was restarted and the list has to be reloaded up to it. */
    fun pendingIndex(): Int = focusIndex

    private companion object {
        const val K_TAB = "nav_tab"
        const val K_SOURCE = "nav_source"
        const val K_LEVEL = "nav_level"
        const val K_SELECTION = "nav_selection"
        const val K_FOCUS = "nav_focus"
        const val K_FOCUS_INDEX = "nav_focus_index"

        fun encode(m: Map<String, String>) = m.entries.joinToString("\n") { "${it.key}\t${it.value}" }
        fun decode(s: String?): Map<String, String> = s.orEmpty().lines().mapNotNull { line ->
            line.split('\t').takeIf { it.size == 2 }?.let { it[0] to it[1] }
        }.toMap()
    }
}
