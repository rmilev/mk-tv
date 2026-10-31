package mk.tv.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged

/**
 * A FocusRequester that knows whether its target is currently composed. Focus "enter" rules must
 * never return a requester whose item isn't on screen (e.g. a row scrolled out of a lazy list):
 * Compose throws "FocusRequester is not initialized". [target] falls back to default focus then.
 */
class SafeFocus {
    val requester = FocusRequester()
    private var attachments by mutableIntStateOf(0)
    private var focusedCount = 0

    /** True while the target item itself has focus. */
    val isFocused: Boolean get() = focusedCount > 0

    val target: FocusRequester get() = if (attachments > 0) requester else FocusRequester.Default

    fun request() { if (attachments > 0) runCatching { requester.requestFocus() } }

    /** Modifier for the item to focus; call from the item's composition. */
    @Composable
    fun modifier(): Modifier {
        var focused = false
        DisposableEffect(Unit) {
            attachments++
            onDispose {
                attachments--
                if (focused) focusedCount--
            }
        }
        return Modifier.focusRequester(requester).onFocusChanged {
            if (it.isFocused != focused) {
                focused = it.isFocused
                focusedCount += if (focused) 1 else -1
            }
        }
    }
}
