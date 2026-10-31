package mk.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.SubcomposeAsyncImage
import mk.tv.data.ArchiveEpisode
import mk.tv.data.Channel

private val CardShape = RoundedCornerShape(14.dp)

/** Runs [action] when the element gains focus (TV menus select on focus, not on click). */
fun Modifier.onFocusSelect(action: () -> Unit): Modifier = onFocusChanged { if (it.isFocused) action() }

/** Focus treatment shared by every card: gold ring + gentle zoom, so the cursor is obvious from the sofa. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun FocusCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onFocus: () -> Unit = {},
    content: @Composable (focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.onFocusChanged { if (it.isFocused) onFocus(); focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(CardShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Palette.Surface,
            focusedContainerColor = Palette.SurfaceHigh,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(androidx.compose.foundation.BorderStroke(3.dp, Palette.Gold), shape = CardShape),
        ),
        glow = ClickableSurfaceDefaults.glow(),
    ) { content(focused) }
}

/** Channel logo, or a typographic badge in MRT style when the channel has no logo image. Scales with its box. */
@Composable
fun ChannelArt(channel: Channel, modifier: Modifier = Modifier, textSize: Int = 30) {
    BoxWithConstraints(
        modifier
            .background(
                Brush.linearGradient(
                    if (channel.group.startsWith("МРТ")) listOf(Palette.Red, Palette.RedDark)
                    else listOf(Color(0xFF2A3140), Color(0xFF151922))
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        val label = channel.badge ?: channel.name
        // Fit the longest word on one line: never break "СОБРАНИЕ" mid-word
        val longestWord = label.split(' ').maxOf { it.length }.coerceAtLeast(3)
        val fitted = (maxWidth.value * 0.82f / (longestWord * 0.66f)).coerceAtMost(textSize.toFloat())
        val pad = (maxWidth.value * 0.08f).dp
        val fallback: @Composable () -> Unit = {
            Text(
                label,
                fontSize = fitted.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2,
                lineHeight = (fitted * 1.12f).sp,
                modifier = Modifier.padding(pad / 2),
            )
        }
        if (channel.logo != null) {
            SubcomposeAsyncImage(
                model = channel.logo,
                contentDescription = channel.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(pad),
                loading = { fallback() },
                error = { fallback() },
            )
        } else {
            fallback()
        }
        if (channel.radio && maxWidth > 120.dp) {
            Icon(
                Icons.Filled.Radio, null, tint = Palette.Gold,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp).size(20.dp),
            )
        }
    }
}

@Composable
fun ChannelCard(
    channel: Channel,
    favorite: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFocus: () -> Unit,
    width: Dp = 200.dp,
) {
    FocusCard(onClick = onClick, onLongClick = onLongClick, onFocus = onFocus, modifier = Modifier.width(width)) { focused ->
        Column {
            Box {
                ChannelArt(channel, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                Row(
                    Modifier.align(Alignment.TopEnd).padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (favorite) Pill { Icon(Icons.Filled.Star, null, tint = Palette.Gold, modifier = Modifier.size(14.dp)) }
                    if (channel.mkOnly) Pill {
                        Icon(Icons.Filled.Lock, null, tint = Color.White, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("МК", style = MaterialTheme.typography.labelMedium, color = Color.White)
                    }
                }
            }
            Text(
                channel.name,
                style = MaterialTheme.typography.titleSmall,
                color = if (focused) Palette.Gold else Palette.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
fun EpisodeCard(episode: ArchiveEpisode, onClick: () -> Unit, onFocus: () -> Unit = {}, modifier: Modifier = Modifier) {
    FocusCard(onClick = onClick, onFocus = onFocus, modifier = modifier) { focused ->
        Column {
            SubcomposeAsyncImage(
                model = episode.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Palette.SurfaceHigh),
            )
            Text(
                episode.title,
                style = MaterialTheme.typography.titleSmall,
                color = if (focused) Palette.Gold else Palette.Text,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
fun Pill(content: @Composable () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Color(0xCC000000)).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
fun LiveBadge() {
    Row(
        Modifier.clip(RoundedCornerShape(6.dp)).background(Palette.Live).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(Color.White))
        Spacer(Modifier.width(6.dp))
        Text("ВО ЖИВО", style = MaterialTheme.typography.labelMedium, color = Color.White)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.headlineSmall, modifier = modifier.padding(bottom = 14.dp))
}

/** Small sun mark used in the top bar (same motif as the launcher icon). */
@Composable
fun SunMark(size: Dp = 34.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(Palette.Red).border(3.dp, Palette.Gold, CircleShape), contentAlignment = Alignment.Center) {
        Box(Modifier.size(size / 3).clip(CircleShape).background(Palette.Gold))
    }
}

@Composable
fun Message(title: String, body: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        if (body != null) {
            Spacer(Modifier.height(10.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
    }
}
