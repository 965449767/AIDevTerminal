package com.aidev.terminal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

class PwdFileObserver(
    private val pwdFile: File,
    private val scope: CoroutineScope,
    private val onPwdChanged: (ubuntuPwd: String) -> Unit
) {
    private var job: Job? = null
    private var lastValue = ""
    private var isReading = false

    fun start() {
        stop()
        lastValue = pwdFile.takeIf { it.isFile }?.readText()?.trim().orEmpty()
        job = scope.launch {
            while (true) {
                delay(500)
                if (isReading) continue
                isReading = true
                try {
                    if (!pwdFile.isFile) continue
                    val line = pwdFile.readText().trim()
                    if (line.isNotBlank() && line != lastValue) {
                        delay(50)
                        val confirm = pwdFile.readText().trim()
                        if (confirm == line) {
                            lastValue = line
                            onPwdChanged(line)
                        }
                    }
                } catch (_: Exception) {
                } finally {
                    isReading = false
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
