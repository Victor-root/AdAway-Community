package org.adaway.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.currentCompositeKeyHash
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import org.adaway.ui.theme.LocalTelevision

/**
 * On TV, the row of a screen focused last, by its place in the screen, focused again when the
 * screen comes back from one it opened, as Android does.
 */
class FocusMemory(key: Int?) {
    var key by mutableStateOf(key)
    val requester = FocusRequester()
}

private val LocalFocusMemory = staticCompositionLocalOf<FocusMemory?> { null }

private val FocusMemorySaver = Saver<FocusMemory, Int>(save = { it.key }, restore = { FocusMemory(it) })

/** Gives [content] a memory of its focused row on TV, and focuses that row again when it comes back. */
@Composable
fun RememberFocus(content: @Composable () -> Unit) {
    if (!LocalTelevision.current) return content()
    val memory = rememberSaveable(saver = FocusMemorySaver) { FocusMemory(null) }
    // Before the content, whose own initial focus comes after and wins.
    LaunchedEffect(Unit) { if (memory.key != null) memory.requester.requestFocus() }
    CompositionLocalProvider(LocalFocusMemory provides memory, content = content)
}

/** Puts this row in the memory of [RememberFocus], to come before its focusable modifier. */
@Composable
fun Modifier.rememberedFocus(): Modifier {
    val memory = LocalFocusMemory.current ?: return this
    val key = currentCompositeKeyHash
    return this
        .then(if (memory.key == key) Modifier.focusRequester(memory.requester) else Modifier)
        .onFocusChanged { if (it.isFocused) memory.key = key }
}
