package com.aidev.terminal

import android.util.Log

/**
 * AIDev 统一日志框架。
 * 所有日志通过此对象输出，便于统一开关、分级和标签管理。
 */
object AIDevLogger {

    private const val GLOBAL_TAG = "AIDev"

    // 日志开关（Release 包可关闭）
    var enabled: Boolean = true

    @JvmStatic
    fun d(tag: String, message: String) {
        if (enabled) Log.d("$GLOBAL_TAG.$tag", message)
    }

    @JvmStatic
    fun i(tag: String, message: String) {
        if (enabled) Log.i("$GLOBAL_TAG.$tag", message)
    }

    @JvmStatic
    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (enabled) {
            if (throwable != null) Log.w("$GLOBAL_TAG.$tag", message, throwable)
            else Log.w("$GLOBAL_TAG.$tag", message)
        }
    }

    @JvmStatic
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (enabled) {
            if (throwable != null) Log.e("$GLOBAL_TAG.$tag", message, throwable)
            else Log.e("$GLOBAL_TAG.$tag", message)
        }
    }
}
