package com.aidev.terminal

import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import java.io.File
import java.lang.ref.WeakReference

/**
 * PWD 文件观察者。
 * 使用 WeakReference 避免持有 Activity 引用导致内存泄漏。
 */
class PwdFileObserver(
    private val pwdFile: File,
    onPwdChanged: (ubuntuPwd: String) -> Unit
) : FileObserver(pwdFile, MODIFY) {

    private val handler = Handler(Looper.getMainLooper())
    private var lastValue = ""
    private var pendingRunnable: Runnable? = null
    // 使用 WeakReference 避免内存泄漏
    private val callbackRef = WeakReference(onPwdChanged)

    override fun onEvent(event: Int, path: String?) {
        if (event != MODIFY) return
        pendingRunnable?.let { handler.removeCallbacks(it) }
        pendingRunnable = Runnable { confirmRead() }.also { handler.postDelayed(it, 200) }
    }

    private fun confirmRead() {
        val callback = callbackRef.get() ?: return
        val firstRead = pwdFile.readText().trim()
        pendingRunnable = Runnable {
            val secondRead = pwdFile.readText().trim()
            if (secondRead != firstRead) return@Runnable
            if (secondRead != lastValue && secondRead.isNotBlank()) {
                lastValue = secondRead
                callback(secondRead)
            }
        }.also { handler.postDelayed(it, 50) }
    }

    fun start() {
        lastValue = pwdFile.takeIf { it.isFile }?.readText()?.trim().orEmpty()
        super.startWatching()
    }

    fun stop() {
        super.stopWatching()
        pendingRunnable?.let { handler.removeCallbacks(it) }
        pendingRunnable = null
    }
}
