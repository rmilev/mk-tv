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
import androidx.compose.material.icons.filled.OpenInNew
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
 * YouTube's own controls are hidden; the remote drives [ArchiveControls]: OK play/pause and buttons
 * (incl. "open in YouTube"), ◀/▶ or ▲ scrubber, ▼ the other videos. The next video starts at the end.
 */
@Composable
fun YouTubePlayerScreen(
    videos: List<YtVideo>,
    startIndex: Int,
    station: String,
    onOpenInYoutube: (YtVideo) -> Unit,
    /** Called with the video on screen when leaving, so the list can focus it. */
    onExit: (YtVideo) -> Unit,
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
    val controls = remember { ArchiveControls(scope) }
    // YouTube reports "not started" while a pre-roll ad runs; only cover the screen briefly
    var coverLoading by remember { mutableStateOf(true) }
    var coverJob by remember { mutableStateOf<Job?>(null) }
    val video = videos[index]

    fun coverBriefly() {
        coverLoading = true
        coverJob?.cancel()
        coverJob = scope.launch { delay(4_000); coverLoading = false }
    }

    fun play(i: Int) {
        if (i !in videos.indices) return
        index = i
        error = null
        position = 0f
        duration = 0f
        player?.loadVideo(videos[i].id, 0f)
        coverBriefly()
        controls.show()
    }

    val setPosition = { s: Float -> position = s }
    // Outer state seen from inside the object (its own properties shadow the names)
    val now = { position }
    val length = { duration }
    val current = { index }
    val playback = object : ArchivePlayback {
        override val position get() = now()
        override val duration get() = length()
        override val playing get() = state == PlayerConstants.PlayerState.PLAYING
        override val index get() = current()
        override val count get() = videos.size
        override fun togglePlay() { if (playing) player?.pause() else player?.play() }
        override fun seekTo(seconds: Float) { player?.seekTo(seconds); setPosition(seconds) }
        override fun playIndex(i: Int) = play(i)
    }
    val buttons = listOf(
        PlayerButton(
            if (state == PlayerConstants.PlayerState.PLAYING) "Пауза" else "Пушти",
            if (state == PlayerConstants.PlayerState.PLAYING) Icons.Filled.Pause else Icons.Filled.PlayArrow,
        ) { playback.togglePlay() },
        PlayerButton("Отвори во YouTube", Icons.Filled.OpenInNew) { player?.pause(); onOpenInYoutube(video) },
    )
    val items = remember(videos) {
        videos.map { UpNextItem(it.title, it.thumbnail, listOf(it.duration, it.published).filter(String::isNotBlank).joinToString(" · ")) }
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
                    if (s == PlayerConstants.PlayerState.PAUSED) controls.show()
                }
                private fun durationSetter(d: Float) { duration = d }
                private fun errorSetter(e: PlayerConstants.PlayerError) { error = e; controls.show() }
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
    LaunchedEffect(Unit) { controls.show(); coverBriefly(); runCatching { rootFocus.requestFocus() } }

    BackHandler { if (!controls.back(playback)) onExit(videos[index]) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val code = ev.key.nativeKeyCode
                // A video that can't play embedded: OK opens it in YouTube
                if (error != null && controls.row == ArchiveControls.Row.None &&
                    (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER)
                ) {
                    onOpenInYoutube(video)
                    return@onPreviewKeyEvent true
                }
                controls.onKey(code, ev.nativeKeyEvent.repeatCount, playback, buttons)
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
                    "Притиснете OK за да го отворите во YouTube, или ▼ за друго видео.",
                )
            }
        }

        AnimatedVisibility(controls.visible || state == PlayerConstants.PlayerState.PAUSED, enter = fadeIn(), exit = fadeOut()) {
            ArchiveOverlay(
                controls = controls,
                kicker = station,
                title = video.title,
                subtitle = video.published,
                position = position,
                duration = duration,
                buttons = buttons,
                items = items,
                index = index,
            )
        }
    }
}

private const val EMBED_ORIGIN = "https://rmilev.github.io"

private fun allWebViews(view: android.view.View): List<android.webkit.WebView> = when (view) {
    is android.webkit.WebView -> listOf(view)
    is ViewGroup -> (0 until view.childCount).flatMap { allWebViews(view.getChildAt(it)) }
    else -> emptyList()
}
