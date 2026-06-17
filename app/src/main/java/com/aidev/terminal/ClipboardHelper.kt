package com.aidev.terminal

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

/**
 * 剪贴板操作统一入口。
 * 替代各页面中重复的 copyText() 私有方法。
 */
object ClipboardHelper {
    /**
     * 将文本复制到系统剪贴板。
     * @param activity 当前 Activity，用于获取 ClipboardManager
     * @param label 剪贴板条目标签，用于标识来源
     * @param text 要复制的文本内容
     */
    fun copy(activity: Activity, label: String, text: String) {
        (activity.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
            ?.setPrimaryClip(ClipData.newPlainText(label, text))
    }
}
