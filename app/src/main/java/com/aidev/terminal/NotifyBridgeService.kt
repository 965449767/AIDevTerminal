package com.aidev.terminal

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

object NotifyBridgeService {

    private const val BRIDGE_DIR = ".aidev-notify"
    private var job: Job? = null
    private var appCtx: Context? = null

    fun start(context: Context, homeDir: File) {
        if (job != null) return
        appCtx = context.applicationContext
        val dir = File(homeDir, BRIDGE_DIR)
        dir.mkdirs()
        AIDevLogger.i("NotifyBridge", "start polling $dir")
        job = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                runCatching {
                    dir.listFiles()?.filter { it.name.startsWith("req-") }?.forEach { file ->
                        val text = runCatching { file.readText() }.getOrNull()
                        file.delete()
                        if (text != null) dispatch(text)
                    }
                }
                delay(500)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private fun dispatch(json: String) {
        val ctx = appCtx ?: return
        runCatching {
            val obj = org.json.JSONObject(json)
            val title = obj.optString("title", "AIDev Terminal")
            val msg = obj.optString("message", "")
            val priority = obj.optString("priority", "")
            val ongoing = obj.optBoolean("ongoing", false)
            val alertOnlyOnce = obj.optBoolean("alert_only_once", false)
            AIDevCommandDispatcher.notify(ctx, title, msg,
                priority = priority.ifEmpty { null },
                ongoing = ongoing,
                alertOnlyOnce = alertOnlyOnce
            )
        }
    }
}
