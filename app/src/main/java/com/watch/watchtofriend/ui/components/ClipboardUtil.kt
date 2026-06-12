package com.watch.watchtofriend.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

/**
 * Metni panoya kopyalar. Compose'un deprecated LocalClipboardManager'ı yerine
 * doğrudan Android ClipboardManager kullanır (kararlı, sürümden bağımsız).
 */
fun Context.copyToClipboard(text: String, label: String = "Watch with Friends") {
    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
}
