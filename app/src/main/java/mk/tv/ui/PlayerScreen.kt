package mk.tv.ui

import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
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
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mk.tv.data.ArchiveEpisode
import mk.tv.data.Channel
import mk.tv.data.GeoBlockedException
import mk.tv.data.Http
import mk.tv.data.MrtArchive
import mk.tv.data.ResolvedStream
import mk.tv.data.StreamResolver

sealed interface PlaybackItem {
    data class Live(val channel: Channel) : PlaybackItem
    data class Vod(val episodes: List<ArchiveEpisode>, val index: Int, val show: String) : PlaybackItem
}

private sealed interface PlayerStatus {
    data object Loading : PlayerStatus
    data object Playing : PlayerStatus
    data object GeoBlocked : PlayerStatus
    data class Failed(val reason: String) : PlayerStatus
}

/**
 * Full-screen player. Live: ▲/▼ (or CH+/CH−) zap, ◀ opens the channel list, OK pauses/resumes (and shows info).
 * Catch-up: [ArchiveControls] (OK play/pause, ◀/▶ or ▲ scrubber, ▼ the show's other episodes).
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    item: PlaybackItem,
    lineup: List<Channel>,
    favorites: Set<String>,
    onChannelChanged: (Channel) -> Unit,
    onEpisodeChanged: (ArchiveEpisode) -> Unit = {},
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val player = remember {
        ExoPlayer.Builder(context).setSeekBackIncrementMs(10_000).setSeekForwardIncrementMs(10_000).build().apply { playWhenReady = true }
    }
    val isLive = item is PlaybackItem.Live

    var current by remember { mutableStateOf((item as? PlaybackItem.Live)?.channel) }
    var status by remember { mutableStateOf<PlayerStatus>(PlayerStatus.Loading) }
    var showInfo by remember { mutableStateOf(true) }
    var showList by remember { mutableStateOf(false) }
    var retried by remember { mutableStateOf(false) }
    var infoTimer by remember { mutableStateOf<Job?>(null) }
    val rootFocus = remember { FocusRequester() }
    val vod = item as? PlaybackItem.Vod
    var vodIndex by remember { mutableIntStateOf(vod?.index ?: 0) }
    val episode = vod?.episodes?.getOrNull(vodIndex)
    val controls = remember { ArchiveControls(scope) }
    var position by remember { mutableFloatStateOf(0f) }
    var duration by remember { mutableFloatStateOf(0f) }
    var playing by remember { mutableStateOf(false) }
    // Live pause is user intent (playWhenReady), not "not playing yet" while buffering
    var livePaused by remember { mutableStateOf(false) }

    fun flashInfo() {
        showInfo = true
        infoTimer?.cancel()
        infoTimer = scope.launch { delay(5_000); showInfo = false }
    }

    fun play(stream: ResolvedStream) {
        val dataSource = OkHttpDataSource.Factory(Http.client)
            .setUserAgent(Http.USER_AGENT)
            .setDefaultRequestProperties(stream.headers)
        val source = HlsMediaSource.Factory(dataSource).createMediaSource(MediaItem.fromUri(stream.url))
        player.setMediaSource(source)
        player.prepare()
    }

    fun load(fresh: Boolean = false) {
        status = PlayerStatus.Loading
        scope.launch {
            runCatching {
                when (item) {
                    is PlaybackItem.Live -> StreamResolver.resolve(current!!, fresh)
                    is PlaybackItem.Vod -> MrtArchive.episodeStream(item.episodes[vodIndex].id)
                }
            }.onSuccess { play(it) }
                .onFailure { e -> status = if (e is GeoBlockedException) PlayerStatus.GeoBlocked else PlayerStatus.Failed(e.message ?: "Грешка") }
        }
    }

    fun tune(channel: Channel) {
        current = channel
        retried = false
        player.playWhenReady = true
        onChannelChanged(channel)
        player.stop()
        load()
        flashInfo()
    }

    fun zap(step: Int) {
        val ch = current ?: return
        if (lineup.isEmpty()) return
        val i = lineup.indexOfFirst { it.id == ch.id }.coerceAtLeast(0)
        tune(lineup[(i + step + lineup.size) % lineup.size])
    }

    // Resumes where it was paused while the stream still holds that moment, otherwise at the live edge
    fun toggleLivePause() {
        if (player.playWhenReady) player.pause() else player.play()
        flashInfo()
    }

    fun playEpisode(i: Int) {
        val list = vod?.episodes ?: return
        if (i !in list.indices) return
        vodIndex = i
        onEpisodeChanged(list[i])
        player.stop()
        position = 0f
        duration = 0f
        load()
        controls.show()
    }

    val nowPos = { position }
    val nowLength = { duration }
    val nowPlaying = { playing }
    val nowIndex = { vodIndex }
    val playback = object : ArchivePlayback {
        override val position get() = nowPos()
        override val duration get() = nowLength()
        override val playing get() = nowPlaying()
        override val index get() = nowIndex()
        override val count get() = vod?.episodes?.size ?: 0
        override fun togglePlay() { if (player.isPlaying) player.pause() else player.play() }
        override fun seekTo(seconds: Float) { player.seekTo((seconds * 1000).toLong()) }
        override fun playIndex(i: Int) = playEpisode(i)
    }
    val buttons = listOf(
        PlayerButton(if (playing) "Пауза" else "Пушти", if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow) { playback.togglePlay() },
    )
    val upNext = remember(vod) { vod?.episodes?.map { UpNextItem(it.title, it.thumbnail) }.orEmpty() }

    // ExoPlayer has no position callbacks: sample it for the overlay
    if (vod != null) LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition / 1000f
            duration = player.duration.takeIf { it > 0 }?.div(1000f) ?: 0f
            playing = player.isPlaying
            delay(250)
        }
    }

    // Leaving the app (Home, another app) pauses playback; coming back resumes it, live at the live edge
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, player) {
        var resume = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    resume = player.playWhenReady
                    player.pause()
                }
                Lifecycle.Event.ON_START -> if (resume) {
                    if (isLive) player.seekToDefaultPosition()
                    player.play()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                livePaused = !playWhenReady
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) status = PlayerStatus.Playing
                if (state == Player.STATE_BUFFERING && status !is PlayerStatus.Playing) status = PlayerStatus.Loading
            }

            override fun onPlayerError(error: PlaybackException) {
                val code = (error.cause as? HttpDataSource.InvalidResponseCodeException)?.responseCode
                // Some MK-only servers answer foreign viewers with an HTML "403" page and status 200
                val blockPage = error.cause is androidx.media3.common.ParserException && current?.mkOnly == true
                when {
                    // Paused longer than the stream's rewind window: jump to the live edge
                    error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> { player.seekToDefaultPosition(); player.prepare() }
                    code == 403 || code == 451 || blockPage -> status = PlayerStatus.GeoBlocked
                    // Signed links can expire mid-session: fetch a fresh one once
                    !retried && current?.type == "page" -> { retried = true; load(fresh = true) }
                    else -> status = PlayerStatus.Failed(error.errorCodeName)
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(Unit) {
        current?.let(onChannelChanged)
        episode?.let(onEpisodeChanged)
        load()
        flashInfo()
        runCatching { rootFocus.requestFocus() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .onPreviewKeyEvent { ev ->
                if (showList || ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (!isLive) {
                    val code = ev.key.nativeKeyCode
                    if (status is PlayerStatus.Failed && controls.row == ArchiveControls.Row.None &&
                        (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER)
                    ) {
                        load()
                        return@onPreviewKeyEvent true
                    }
                    return@onPreviewKeyEvent controls.onKey(code, ev.nativeKeyEvent.repeatCount, playback, buttons)
                }
                when (ev.key.nativeKeyCode) {
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> { zap(-1); true }
                    KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> { zap(1); true }
                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MENU -> { showList = true; true }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        if (status is PlayerStatus.GeoBlocked || status is PlayerStatus.Failed) { retried = false; load(fresh = true) } else toggleLivePause()
                        true
                    }
                    KeyEvent.KEYCODE_MEDIA_PAUSE -> { if (player.playWhenReady) toggleLivePause(); true }
                    KeyEvent.KEYCODE_MEDIA_PLAY -> { if (!player.playWhenReady) toggleLivePause(); true }
                    KeyEvent.KEYCODE_INFO -> { flashInfo(); true }
                    else -> false
                }
            }
            .focusable(),
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    keepScreenOn = true
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Radio has no picture: show a large station card instead of a black screen
        current?.takeIf { it.radio }?.let { ch ->
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                ChannelArt(ch, Modifier.width(420.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(24.dp)), textSize = 44)
                Spacer(Modifier.height(20.dp))
                Icon(Icons.Filled.Radio, null, tint = Palette.Gold, modifier = Modifier.size(36.dp))
            }
        }

        when (val s = status) {
            PlayerStatus.Loading -> Centered { Message("Се вчитува…", (current?.name ?: episode?.title)) }
            PlayerStatus.GeoBlocked -> Centered {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Lock, null, tint = Palette.Gold, modifier = Modifier.size(44.dp))
                    Message(
                        "Достапно само од Македонија",
                        "Вклучете VPN со локација North Macedonia, па притиснете OK за повторен обид.",
                    )
                }
            }
            is PlayerStatus.Failed -> Centered {
                Message("Програмата моментално не е достапна", "Притиснете OK за повторен обид или ▲▼ за друг канал.\n(${s.reason})")
            }
            PlayerStatus.Playing -> Unit
        }

        if (isLive) {
            if (livePaused && status is PlayerStatus.Playing) Box(Modifier.align(Alignment.Center)) {
                Icon(
                    Icons.Filled.Pause, contentDescription = "Паузирано", tint = Color.White,
                    modifier = Modifier.size(96.dp).clip(RoundedCornerShape(50)).background(Color(0x99000000)).padding(18.dp),
                )
            }
            AnimatedVisibility((showInfo || livePaused) && !showList, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
                current?.let { LiveInfoBar(it, lineup, it.id in favorites, livePaused) }
            }
            AnimatedVisibility(
                showList,
                enter = slideInHorizontally { -it } + fadeIn(),
                exit = slideOutHorizontally { -it } + fadeOut(),
                modifier = Modifier.align(Alignment.CenterStart),
            ) {
                ChannelListPanel(
                    lineup = lineup,
                    currentId = current?.id,
                    onSelect = { showList = false; tune(it); scope.launch { delay(50); runCatching { rootFocus.requestFocus() } } },
                    onClose = { showList = false; scope.launch { delay(50); runCatching { rootFocus.requestFocus() } } },
                )
            }
        } else if (vod != null && episode != null) {
            AnimatedVisibility(controls.visible || !playing, enter = fadeIn(), exit = fadeOut()) {
                ArchiveOverlay(
                    controls = controls,
                    kicker = vod.show,
                    title = episode.title,
                    subtitle = null,
                    position = position,
                    duration = duration,
                    buttons = buttons,
                    items = upNext,
                    index = vodIndex,
                )
            }
        }
    }

    // Back closes the channel list first, then leaves the player
    androidx.activity.compose.BackHandler {
        when {
            showList -> { showList = false; scope.launch { delay(50); runCatching { rootFocus.requestFocus() } } }
            vod != null && controls.back(playback) -> Unit
            else -> onExit()
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun LiveInfoBar(channel: Channel, lineup: List<Channel>, favorite: Boolean, paused: Boolean) {
    val position = lineup.indexOfFirst { it.id == channel.id } + 1
    Row(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xEE000000))))
            .padding(start = Insets.Horizontal, end = Insets.Horizontal, top = 60.dp, bottom = 36.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChannelArt(channel, Modifier.height(96.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)), textSize = 24)
        Spacer(Modifier.width(24.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("%02d".format(position), style = MaterialTheme.typography.headlineSmall, color = Palette.Gold)
                Text(channel.name + if (favorite) "  ★" else "", style = MaterialTheme.typography.displaySmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LiveBadge()
                Text(channel.group, style = MaterialTheme.typography.titleMedium, color = Palette.TextDim)
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Hint("▲ ▼", "Канал")
            Hint("◀", "Листа на канали")
            Hint("OK", if (paused) "Продолжи" else "Пауза")
        }
    }
}

@Composable
private fun Hint(key: String, label: String) {
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

@Composable
private fun ChannelListPanel(lineup: List<Channel>, currentId: String?, onSelect: (Channel) -> Unit, onClose: () -> Unit) {
    val start = lineup.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (start - 3).coerceAtLeast(0))
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(80); runCatching { focus.requestFocus() } }

    Column(
        Modifier.fillMaxHeight().width(440.dp)
            .background(Brush.horizontalGradient(listOf(Color(0xF20B0D12), Color(0xD90B0D12))))
            .padding(top = Insets.Top, start = 32.dp, end = 20.dp)
            .onPreviewKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyDown && ev.key.nativeKeyCode == KeyEvent.KEYCODE_DPAD_RIGHT) { onClose(); true } else false
            },
    ) {
        Text("Канали", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 12.dp))
        LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 40.dp, end = 12.dp)) {
            itemsIndexed(lineup, key = { _, c -> c.id }) { i, ch ->
                FocusCard(
                    onClick = { onSelect(ch) },
                    modifier = Modifier.fillMaxWidth().then(if (i == start) Modifier.focusRequester(focus) else Modifier),
                ) { focused ->
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("%02d".format(i + 1), style = MaterialTheme.typography.titleMedium, color = if (focused) Palette.Gold else Palette.TextDim, modifier = Modifier.width(40.dp))
                        ChannelArt(ch, Modifier.height(44.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)), textSize = 12)
                        Spacer(Modifier.width(14.dp))
                        Text(ch.name, style = MaterialTheme.typography.titleMedium, color = if (focused) Palette.Gold else Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        if (ch.id == currentId) LiveDot()
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveDot() {
    Box(Modifier.size(10.dp).clip(RoundedCornerShape(50)).background(Palette.Live))
}
