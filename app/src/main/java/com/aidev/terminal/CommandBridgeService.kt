package com.aidev.terminal

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

@Suppress("StaticFieldLeak")
object CommandBridgeService {

    private const val BRIDGE_DIR = ".aidev-cmd"
    private var job: Job? = null
    private var appCtx: Context? = null

    fun start(context: Context, homeDir: File) {
        if (job != null) return
        appCtx = context.applicationContext
        val dir = File(homeDir, BRIDGE_DIR)
        dir.mkdirs()
        AIDevLogger.i("CommandBridge", "start polling $dir")
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
            when (obj.optString("action")) {
                "volume" -> {
                    val stream = obj.optInt("stream", 3)
                    val vol = obj.optInt("volume", -1)
                    if (vol >= 0) AIDevCommandDispatcher.setVolume(ctx, stream, vol)
                }
                "brightness" -> {
                    val b = obj.optInt("brightness", -1)
                    val auto = obj.optBoolean("auto", false)
                    if (auto) {
                        AIDevCommandDispatcher.setBrightness(ctx, 0, true)
                    } else if (b >= 0) {
                        AIDevCommandDispatcher.setBrightness(ctx, b, false)
                    }
                }
                "clipboard" -> {
                    val text = obj.optString("text", "")
                    if (text.isNotEmpty()) AIDevCommandDispatcher.setClipboard(ctx, text)
                }
                "startapp" -> {
                    val pkg = obj.optString("package", "")
                    if (pkg.isNotEmpty()) AIDevCommandDispatcher.startApp(ctx, pkg)
                }
                "stopapp" -> {
                    val pkg = obj.optString("package", "")
                    if (pkg.isNotEmpty()) AIDevCommandDispatcher.stopApp(ctx, pkg)
                }
                "screencap" -> {
                    val path = obj.optString("path", "")
                    AIDevCommandDispatcher.takeScreenshot(ctx, path.ifEmpty { null })
                }
                "installapk" -> {
                    val path = obj.optString("path", "")
                    if (path.isNotEmpty()) AIDevCommandDispatcher.installApk(ctx, path)
                }
                "uninstallapp" -> {
                    val pkg = obj.optString("package", "")
                    if (pkg.isNotEmpty()) AIDevCommandDispatcher.uninstallApp(ctx, pkg)
                }
            }
        }
    }
}
