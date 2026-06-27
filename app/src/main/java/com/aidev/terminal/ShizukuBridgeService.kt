package com.aidev.terminal

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.concurrent.CountDownLatch

@Suppress("StaticFieldLeak")
object ShizukuBridgeService {

    private const val BRIDGE_DIR = ".aidev-shizuku-bridge"
    private const val REQUEST_DIR = "request"
    private const val RESULT_DIR = "result"

    private var job: Job? = null
    private var appCtx: Context? = null

    val isRunning: Boolean get() = job != null

    fun start(context: Context, homeDir: File) {
        if (job != null) return
        appCtx = context.applicationContext
        val bridgeDir = File(homeDir, BRIDGE_DIR)
        val requestDir = File(bridgeDir, REQUEST_DIR)
        val resultDir = File(bridgeDir, RESULT_DIR)
        requestDir.mkdirs()
        resultDir.mkdirs()
        AIDevLogger.i("ShizukuBridge", "start polling $requestDir")
        job = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                runCatching {
                    requestDir.listFiles()?.filter { it.name.startsWith("log_") || it.name.startsWith("camera_") || it.name.startsWith("exec_") }?.forEach { file ->
                        handleRequest(requestDir, resultDir, file)
                    }
                }
                delay(500)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        appCtx = null
    }

    private fun handleRequest(requestDir: File, resultDir: File, reqFile: File) {
        val resFile = File(resultDir, reqFile.name)
        if (resFile.exists()) return

        val content = runCatching { reqFile.readText() }.getOrNull() ?: return
        val fields = content.lines().associate {
            val parts = it.split("=", limit = 2)
            parts[0] to if (parts.size > 1) parts[1] else ""
        }

        val type = fields["TYPE"] ?: ""
        AIDevLogger.i("ShizukuBridge", "Processing: type=$type, file=${reqFile.name}")

        when (type) {
            "exec" -> handleExecRequest(resFile, fields["COMMAND"] ?: "")
            else -> handleLogRequest(resFile, fields)
        }
        reqFile.delete()
    }

    private fun handleExecRequest(resFile: File, command: String) {
        AIDevLogger.i("ShizukuBridge", "Executing: $command")
        val result = runBlocking { ShizukuLogcat.executeCommand(command) }
        val output = if (result.exitCode == 0) {
            result.stdout.ifBlank { "命令执行成功（无输出）" }
        } else {
            "ERROR: exit=${result.exitCode}\n${result.stderr.ifBlank { result.stdout }}"
        }
        atomicWriteText(resFile, output)
    }

    private fun handleLogRequest(resFile: File, fields: Map<String, String>) {
        val packageName = fields["PACKAGE"] ?: "com.aidev.terminal"
        val lineCount = fields["LINES"]?.toIntOrNull() ?: 200
        val follow = fields["FOLLOW"]?.isNotEmpty() == true
        val level = fields["LEVEL"] ?: ""
        val tag = fields["TAG"] ?: ""
        val clear = fields["CLEAR"]?.isNotEmpty() == true

        if (clear) {
            ShizukuLogcat.clearLogBuffer()
        }

        if (follow) {
            atomicWriteText(resFile, "[持续监听中... 按 Ctrl+C 停止]\n")
            ShizukuLogcat.startLogStream(
                packageName = packageName,
                level = level,
                tag = tag,
                onLine = { line ->
                    runCatching { resFile.appendText("$line\n") }
                },
                onError = { err ->
                    runCatching { resFile.appendText("\nERROR: $err\n") }
                }
            )
        } else {
            val done = CountDownLatch(1)
            ShizukuLogcat.fetchLog(
                packageName = packageName,
                lines = lineCount,
                level = level,
                tag = tag
            ) { result ->
                result.onSuccess { logs ->
                    atomicWriteText(resFile, logs)
                }.onFailure { e ->
                    AIDevLogger.e("ShizukuBridge", "fetchLog failed", e)
                    atomicWriteText(resFile, "ERROR: ${e.message}\n")
                }
                done.countDown()
            }
            runCatching { done.await(30, java.util.concurrent.TimeUnit.SECONDS) }
            if (done.count > 0) {
                atomicWriteText(resFile, "ERROR: 请求超时\n")
            }
        }
    }

    private fun atomicWriteText(file: File, text: String) {
        val tmp = File(file.parentFile, "${file.name}.tmp")
        runCatching {
            tmp.writeText(text)
            tmp.renameTo(file)
        }
    }
}
