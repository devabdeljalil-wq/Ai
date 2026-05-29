package com.example.security

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object ClipboardHelper {
    private val scope = CoroutineScope(Dispatchers.Main)

    fun copyToClipboard(context: Context, text: String, label: String = "VaultData") {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)

        // Read dynamic clipboard delay from secure preferences
        val delaySeconds = SecurePreferenceHelper.getClipboardDelay()
        
        scope.launch {
            delay(delaySeconds * 1000L)
            // Check if the clipboard still holds our copied data
            val currentPrimary = clipboard.primaryClip
            if (currentPrimary != null && currentPrimary.itemCount > 0) {
                val currentText = currentPrimary.getItemAt(0).text?.toString()
                if (currentText == text) {
                    // Wipe clipboard safely
                    val emptyClip = ClipData.newPlainText("", "")
                    clipboard.setPrimaryClip(emptyClip)
                }
            }
        }
    }
}
