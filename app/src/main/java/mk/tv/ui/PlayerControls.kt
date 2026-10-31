package mk.tv.ui

import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.SubcomposeAsyncImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What the archive players expose to [ArchiveControls]. Times are in seconds. */
interface ArchivePlayback {
    val position: Float
    val duration: Float
    val playing: Boolean
    val index: Int
    val count: Int
    fun togglePlay()
    fun seekTo(seconds: Float)
    fun playIndex(i: Int)
}

class PlayerButton(val label: String, val icon: ImageVector, val onClick: () -> Unit)

/** One entry of the "other videos" carousel. */
class UpNextItem(val title: String, val image: String?, val meta: String = "")

/**
 * Remote handling shared by the archive players (MRT episodes and YouTube videos). The overlay is a
 * vertical stack the remote moves through: scrubber (▲) · buttons (OK) · other videos (▼).
 * With nothing selected, ◀/▶ jump 10 s and OK pauses and selects the buttons.
 */
@Stable
class ArchiveControls(private val scope: CoroutineScope) {
    enum class Row { None, Scrubber, Buttons, UpNext }

    var visible by mutableStateOf(true)
        private set
    var row by mutableStateOf(Row.None)
        private set

    /** Where the scrubber dot is while scrubbing (seconds); null = follows playback. */
    var scrubTo by mutableStateOf<Float?>(null)
        private set
    var button by mutableIntStateOf(0)
        private set
    var pick by mutableIntStateOf(0)
        private set

    private var hideJob: Job? = null
    private var commitJob: Job? = null

    fun show() {
        visible = true
        hideJob?.cancel()
        hideJob = scope.launch {
            delay(if (row == Row.None) 4_000 else 10_000)
            visible = false
            row = Row.None
        }
    }

    /** Handles a key press; false if the key isn't for the player controls. */
    fun onKey(code: Int, repeat: Int, p: ArchivePlayback, buttons: List<PlayerButton>): Boolean {
        val handled = when (code) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { p.togglePlay(); true }
            KeyEvent.KEYCODE_MEDIA_REWIND -> { p.seekTo((p.position - 10f).coerceAtLeast(0f)); true }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { p.seekTo(p.position + 10f); true }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_CHANNEL_UP -> { p.playIndex(p.index - 1); true }
            KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_CHANNEL_DOWN -> { p.playIndex(p.index + 1); true }
            KeyEvent.KEYCODE_INFO, KeyEvent.KEYCODE_MENU -> true
            else -> when (row) {
                Row.None -> when (code) {
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                        p.togglePlay(); row = Row.Buttons; button = 0; true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> { p.seekTo((p.position - 10f).coerceAtLeast(0f)); true }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> { p.seekTo(p.position + 10f); true }
                    KeyEvent.KEYCODE_DPAD_UP -> { row = Row.Scrubber; true }
                    KeyEvent.KEYCODE_DPAD_DOWN -> { openUpNext(p); true }
                    else -> false
                }
                Row.Scrubber -> when (code) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> { scrub(-1, repeat, p); true }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> { scrub(1, repeat, p); true }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { commit(p); row = Row.None; true }
                    KeyEvent.KEYCODE_DPAD_DOWN -> { commit(p); row = Row.Buttons; true }
                    KeyEvent.KEYCODE_DPAD_UP -> true
                    else -> false
                }
                Row.Buttons -> when (code) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> { button = (button - 1).coerceAtLeast(0); true }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> { button = (button + 1).coerceAtMost(buttons.lastIndex); true }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { buttons.getOrNull(button)?.onClick?.invoke(); true }
                    KeyEvent.KEYCODE_DPAD_UP -> { row = Row.Scrubber; true }
                    KeyEvent.KEYCODE_DPAD_DOWN -> { openUpNext(p); true }
                    else -> false
                }
                Row.UpNext -> when (code) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> { pick = (pick - 1).coerceAtLeast(0); true }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> { pick = (pick + 1).coerceAtMost(p.count - 1); true }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                        row = Row.None
                        if (pick != p.index) p.playIndex(pick)
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> { row = Row.Buttons; true }
                    KeyEvent.KEYCODE_DPAD_DOWN -> true
                    else -> false
                }
            }
        }
        if (handled) show()
        return handled
    }

    /** Back: leaves the selected row first. False when the player itself should close. */
    fun back(p: ArchivePlayback): Boolean {
        if (row == Row.None) return false
        commit(p)
        row = Row.None
        show()
        return true
    }

    private fun openUpNext(p: ArchivePlayback) {
        if (p.count < 2) return
        pick = p.index
        row = Row.UpNext
    }

    private fun scrub(dir: Int, repeat: Int, p: ArchivePlayback) {
        if (p.duration <= 0f) return
        // Holding the key speeds up
        val step = when {
            repeat < 4 -> 10f
            repeat < 12 -> 30f
            repeat < 24 -> 60f
            else -> 120f
        }
        scrubTo = ((scrubTo ?: p.position) + dir * step).coerceIn(0f, (p.duration - 1f).coerceAtLeast(0f))
        commitJob?.cancel()
        commitJob = scope.launch { delay(700); seek(p) }
    }

    private fun commit(p: ArchivePlayback) {
        commitJob?.cancel()
        seek(p)
    }

    private fun seek(p: ArchivePlayback) {
        val target = scrubTo ?: return
        p.seekTo(target)
        // Keep the dot at the target until playback has caught up
        scope.launch { delay(1_200); if (scrubTo == target) scrubTo = null }
    }
}

/** Title at the top; scrubber, buttons and (on ▼) the other videos at the bottom. */
@Composable
fun ArchiveOverlay(
    controls: ArchiveControls,
    kicker: String,
    title: String,
    subtitle: String?,
    position: Float,
    duration: Float,
    buttons: List<PlayerButton>,
    items: List<UpNextItem>,
    index: Int,
) {
    val row = controls.row
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.align(Alignment.TopStart).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
                .padding(horizontal = Insets.Horizontal, vertical = Insets.Top),
        ) {
            Text(kicker.uppercase(), style = MaterialTheme.typography.labelMedium, color = Palette.Gold)
            Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodyMedium)
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xD9000000), Color(0xF2000000))))
                .padding(top = 64.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Scrubber(
                position = controls.scrubTo ?: position,
                duration = duration,
                selected = row == ArchiveControls.Row.Scrubber,
                modifier = Modifier.padding(horizontal = Insets.Horizontal),
            )
            Row(Modifier.padding(horizontal = Insets.Horizontal), verticalAlignment = Alignment.CenterVertically) {
                Text("${clock(controls.scrubTo ?: position)} / ${clock(duration)}", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(28.dp))
                buttons.forEachIndexed { i, b ->
                    ControlButton(b, focused = row == ArchiveControls.Row.Buttons && controls.button == i)
                    Spacer(Modifier.width(12.dp))
                }
                Spacer(Modifier.weight(1f))
                Text(
                    when (row) {
                        ArchiveControls.Row.Scrubber -> "◀ ▶ премотај · OK потврди"
                        ArchiveControls.Row.UpNext -> "◀ ▶ избери · OK пушти"
                        else -> "▲ премотување · ▼ други видеа"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextDim,
                )
            }
            AnimatedVisibility(
                row == ArchiveControls.Row.UpNext && items.size > 1,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                UpNextRow(items, current = index, pick = controls.pick)
            }
        }
    }
}

@Composable
private fun Scrubber(position: Float, duration: Float, selected: Boolean, modifier: Modifier = Modifier) {
    val fraction = if (duration > 0f) (position / duration).coerceIn(0f, 1f) else 0f
    val dot = if (selected) 22.dp else 12.dp
    BoxWithConstraints(modifier.fillMaxWidth().height(24.dp), contentAlignment = Alignment.CenterStart) {
        val track = if (selected) 8.dp else 5.dp
        Box(Modifier.fillMaxWidth().height(track).clip(RoundedCornerShape(4.dp)).background(Color(0x55FFFFFF))) {
            Box(Modifier.fillMaxWidth(fraction).height(track).background(if (selected) Palette.Gold else Palette.Red))
        }
        Box(
            Modifier.offset(x = maxWidth * fraction - dot / 2)
                .size(dot)
                .clip(CircleShape)
                .background(if (selected) Palette.Gold else Color.White),
        )
    }
}

@Composable
private fun ControlButton(button: PlayerButton, focused: Boolean) {
    Row(
        Modifier.clip(RoundedCornerShape(50))
            .background(if (focused) Palette.Gold else Color(0x33FFFFFF))
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = if (focused) Color.Black else Palette.Text
        Icon(button.icon, null, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(8.dp))
        Text(button.label, style = MaterialTheme.typography.titleSmall, color = tint)
    }
}

@Composable
private fun UpNextRow(items: List<UpNextItem>, current: Int, pick: Int) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (pick - 1).coerceAtLeast(0))
    // Keep the picked card second from the left, so the previous one stays in view
    LaunchedEffect(pick) { state.animateScrollToItem((pick - 1).coerceAtLeast(0)) }
    LazyRow(
        state = state,
        contentPadding = PaddingValues(horizontal = Insets.Horizontal),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        itemsIndexed(items) { i, item ->
            val picked = i == pick
            Column(Modifier.width(250.dp)) {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Palette.SurfaceHigh)
                        .then(if (picked) Modifier.border(BorderStroke(3.dp, Palette.Gold), RoundedCornerShape(10.dp)) else Modifier),
                ) {
                    if (item.image != null) {
                        SubcomposeAsyncImage(
                            model = item.image,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().padding(if (picked) 3.dp else 0.dp).clip(RoundedCornerShape(8.dp)),
                        )
                    }
                    if (i == current) {
                        Box(Modifier.align(Alignment.TopStart).padding(8.dp)) {
                            Pill { Text("Сега", style = MaterialTheme.typography.labelMedium, color = Palette.Gold) }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (picked) Palette.Gold else Palette.Text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.meta.isNotBlank()) {
                    Text(item.meta, style = MaterialTheme.typography.bodySmall, color = Palette.TextDim, maxLines = 1)
                }
            }
        }
    }
}

fun clock(seconds: Float): String {
    val s = seconds.toInt().coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}
