package mk.tv.ui

import android.view.KeyEvent
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mk.tv.data.YtVideo

/**
 * Plays a station's YouTube video inside the app with YouTube's official embedded player.
 * YouTube's own controls are hidden; the remote drives our overlay: OK play/pause, ◀/▶ 10 s,
 * ▲/▼ previous/next video in the list (and the next one starts automatically at the end).
 */
@Composable
fun YouTubePlayerScreen(
    videos: List<YtVideo>,
    startIndex: Int,
    station: String,
    onOpenInYoutube: (YtVideo) -> Unit,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val rootFocus = remember { FocusRequester() }

    var index by remember { mutableIntStateOf(startIndex.coerceIn(0, videos.lastIndex)) }
    var player by remember { mutableStateOf<YouTubePlayer?>(null) }
    var state by remember { mutableStateOf(PlayerConstants.PlayerState.UNSTARTED) }
    var position by remember { mutableFloatStateOf(0f) }
    var duration by remember { mutableFloatStateOf(0f) }
    var error by remember { mutableStateOf<PlayerConstants.PlayerError?>(null) }
    var showControls by remember { mutableStateOf(true) }
    var hideJob by remember { mutableStateOf<Job?>(null) }
    // YouTube reports "not started" while a pre-roll ad runs; only cover the screen briefly
    var coverLoading by remember { mutableStateOf(true) }
    var coverJob by remember { mutableStateOf<Job?>(null) }
    val video = videos[index]

    fun coverBriefly() {
        coverLoading = true
        coverJob?.cancel()
        coverJob = scope.launch { delay(4_000); coverLoading = false }
    }

    fun flash() {
        showControls = true
        hideJob?.cancel()
        hideJob = scope.launch { delay(4_000); showControls = false }
    }

    fun play(i: Int) {
        if (i !in videos.indices) return
        index = i
        error = null
        position = 0f
        duration = 0f
        player?.loadVideo(videos[i].id, 0f)
        coverBriefly()
        flash()
    }

    val playerView = remember {
        YouTubePlayerView(context).apply {
            enableAutomaticInitialization = false
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            // The remote is handled in Compose; the web player must never take focus
            isFocusable = false
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            val options = IFramePlayerOptions.Builder()
                .controls(0)
                .rel(0)
                .ivLoadPolicy(3)
                .ccLoadPolicy(0)
                // YouTube rejects embeds without an identifying web origin (error 152); use the project's site
                .origin(EMBED_ORIGIN)
                .build()
            initialize(object : AbstractYouTubePlayerListener() {
                override fun onReady(youTubePlayer: YouTubePlayer) {
                    player = youTubePlayer
                    youTubePlayer.loadVideo(videos[index].id, 0f)
                }

                override fun onStateChange(youTubePlayer: YouTubePlayer, state: PlayerConstants.PlayerState) {
                    @Suppress("NAME_SHADOWING") val s = state
                    this@apply.post {
                        stateSetter(s)
                    }
                }

                override fun onCurrentSecond(youTubePlayer: YouTubePlayer, second: Float) { position = second }
                override fun onVideoDuration(youTubePlayer: YouTubePlayer, duration: Float) { durationSetter(duration) }
                override fun onError(youTubePlayer: YouTubePlayer, error: PlayerConstants.PlayerError) { errorSetter(error) }

                private fun stateSetter(s: PlayerConstants.PlayerState) {
                    state = s
                    if (s == PlayerConstants.PlayerState.ENDED && index < videos.lastIndex) play(index + 1)
                    if (s == PlayerConstants.PlayerState.PAUSED) showControls = true
                }
                private fun durationSetter(d: Float) { duration = d }
                private fun errorSetter(e: PlayerConstants.PlayerError) { error = e; showControls = true }
            }, true, options)
            // Let the embedded player start on its own: a TV remote never "taps" the web page
            allWebViews(this).forEach { it.settings.mediaPlaybackRequiresUserGesture = false }
        }
    }

    DisposableEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.addObserver(playerView)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(playerView)
            playerView.release()
        }
    }
    LaunchedEffect(Unit) { flash(); coverBriefly(); runCatching { rootFocus.requestFocus() } }

    BackHandler { onExit() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val p = player
                val handled = when (ev.key.nativeKeyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        when {
                            error != null -> onOpenInYoutube(video)
                            state == PlayerConstants.PlayerState.PLAYING -> p?.pause()
                            else -> p?.play()
                        }
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> { p?.seekTo((position - 10f).coerceAtLeast(0f)); true }
                    KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { p?.seekTo(position + 10f); true }
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_CHANNEL_UP -> { play(index - 1); true }
                    KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_CHANNEL_DOWN -> { play(index + 1); true }
                    KeyEvent.KEYCODE_INFO, KeyEvent.KEYCODE_MENU -> true
                    else -> false
                }
                if (handled) flash()
                handled
            }
            .focusable(),
    ) {
        AndroidView(factory = { playerView }, modifier = Modifier.fillMaxSize())

        val loading = error == null && state != PlayerConstants.PlayerState.PLAYING && state != PlayerConstants.PlayerState.PAUSED
        if (loading && coverLoading) {
            Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) {
                Message("Се вчитува…", video.title)
            }
        } else if (loading) {
            Box(Modifier.align(Alignment.TopEnd).padding(top = Insets.Top, end = Insets.Horizontal)) {
                Pill { Text("Реклама / се вчитува…", style = MaterialTheme.typography.labelMedium, color = Color.White) }
            }
        }
        error?.let { e ->
            Box(Modifier.fillMaxSize().background(Color(0xE6000000)), contentAlignment = Alignment.Center) {
                Message(
                    if (e == PlayerConstants.PlayerError.VIDEO_NOT_PLAYABLE_IN_EMBEDDED_PLAYER) "Ова видео не може да се пушти тука"
                    else "Видеото моментално не е достапно",
                    "Притиснете OK за да го отворите во YouTube, или ▲▼ за друго видео.",
                )
            }
        }

        AnimatedVisibility(showControls || state == PlayerConstants.PlayerState.PAUSED, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize()) {
                Column(
                    Modifier.align(Alignment.TopStart).fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
                        .padding(horizontal = Insets.Horizontal, vertical = Insets.Top),
                ) {
                    Text(station.uppercase(), style = MaterialTheme.typography.labelMedium, color = Palette.Gold)
                    Text(video.title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (video.published.isNotBlank()) Text(video.published, style = MaterialTheme.typography.bodyMedium)
                }
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xEE000000))))
                        .padding(start = Insets.Horizontal, end = Insets.Horizontal, top = 48.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    ProgressBar(position, duration)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (state == PlayerConstants.PlayerState.PLAYING) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            null, tint = Palette.Text, modifier = Modifier.size(34.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("${clock(position)} / ${clock(duration)}", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.weight(1f))
                        Text("${index + 1} / ${videos.size}", style = MaterialTheme.typography.titleMedium, color = Palette.TextDim)
                        Spacer(Modifier.width(28.dp))
                        KeyHint("OK", "Пауза")
                        Spacer(Modifier.width(18.dp))
                        KeyHint("◀ ▶", "10 сек")
                        Spacer(Modifier.width(18.dp))
                        KeyHint("▲ ▼", "Претходно / следно")
                    }
                }
            }
        }
    }
}

private const val EMBED_ORIGIN = "https://rmilev.github.io"

private fun allWebViews(view: android.view.View): List<android.webkit.WebView> = when (view) {
    is android.webkit.WebView -> listOf(view)
    is ViewGroup -> (0 until view.childCount).flatMap { allWebViews(view.getChildAt(it)) }
    else -> emptyList()
}

@Composable
private fun ProgressBar(position: Float, duration: Float) {
    val fraction = if (duration > 0f) (position / duration).coerceIn(0f, 1f) else 0f
    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x55FFFFFF))) {
        Box(Modifier.fillMaxWidth(fraction).height(6.dp).background(Palette.Red))
    }
}

@Composable
private fun KeyHint(key: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            key,
            style = MaterialTheme.typography.labelMedium,
            color = Color.Black,
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Palette.Text).padding(horizontal = 8.dp, vertical = 3.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun clock(seconds: Float): String {
    val s = seconds.toInt().coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}
