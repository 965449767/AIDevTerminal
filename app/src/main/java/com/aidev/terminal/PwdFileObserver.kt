package com.aidev.terminal

import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import java.io.File

class PwdFileObserver(
    private val pwdFile: File,
    private val onPwdChanged: (ubuntuPwd: String) -> Unit
) : FileObserver(pwdFile, MODIFY) {

    private val handler = Handler(Looper.getMainLooper())
    private var lastValue = ""
    private var pendingRunnable: Runnable? = null

    override fun onEvent(event: Int, path: String?) {
        if (event != MODIFY) return
        pendingRunnable?.let { handler.removeCallbacks(it) }
        pendingRunnable = Runnable {
            val newValue = pwdFile.readText().trim()
            if (newValue != lastValue && newValue.isNotBlank()) {
                lastValue = newValue
                onPwdChanged(newValue)
            }
        }.also { handler.postDelayed(it, 200) }
    }

    fun start() {
        lastValue = pwdFile.takeIf { it.isFile }?.readText()?.trim().orEmpty()
        super.startWatching()
    }

    fun stop() {
        super.stopWatching()
        pendingRunnable?.let { handler.removeCallbacks(it) }
    }
}
