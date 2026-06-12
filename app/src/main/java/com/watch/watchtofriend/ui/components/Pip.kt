package com.watch.watchtofriend.ui.components

import androidx.compose.runtime.compositionLocalOf

/**
 * Picture-in-Picture köprüsü. Activity bunu sağlar; ekranlar:
 *  - setEligible(true): "şu an PiP'e uygunum" (örn. videolu izleme ekranı)
 *  - enterPip(): elle PiP'e geç
 *  - isInPip: şu an PiP modunda mıyız (UI'yı sadeleştirmek için)
 */
class PipController(
    val isInPip: Boolean,
    val setEligible: (Boolean) -> Unit,
    val enterPip: () -> Unit
)

val LocalPip = compositionLocalOf<PipController?> { null }
