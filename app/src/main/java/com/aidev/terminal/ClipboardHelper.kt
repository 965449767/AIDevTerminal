package com.aidev.terminal

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

object ClipboardHelper {
    fun copy(activity: Activity, label: String, text: String) {
        (activity.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
            ?.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    fun paste(activity: Activity): String? {
        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
        val clip = clipboard.primaryClip ?: return null
        return if (clip.itemCount > 0) clip.getItemAt(0).text?.toString() else null
    }

    fun hasContent(activity: Activity): Boolean =
        (activity.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
            ?.primaryClip?.itemCount?.let { it > 0 } == true
}
