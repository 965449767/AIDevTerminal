package com.aidev.terminal

import android.os.FileObserver
import android.util.Log
import java.io.File
import java.lang.ref.WeakReference

/**
 * Shizuku 文件桥服务。
 * 监听 Ubuntu 环境写入的日志请求，通过 Shizuku 获取 logcat 后写回结果文件。
 *
 * 通信协议：
 * - Ubuntu 写入: /home/.aidev-shizuku-bridge/request/log_<timestamp>_<pid>
 * - Android 写入: /home/.aidev-shizuku-bridge/result/log_<timestamp>_<pid>
 *
 * 请求文件格式：
 *   PACKAGE=com.aidev.terminal
 *   LINES=200
 *   FOLLOW=
 */
object ShizukuBridgeService {

    private const val TAG = "ShizukuBridge"
    private const val BRIDGE_DIR = ".aidev-shizuku-bridge"
    private const val REQUEST_DIR = "request"
    private const val RESULT_DIR = "result"

    private var observer: FileObserver? = null
    private var isRunning = false

    /** 启动桥服务 */
    fun start(homeDir: File) {
        if (isRunning) {
            AIDevLogger.w(TAG, "Bridge service already running, skipping start")
            return
        }

        val requestDir = File(File(homeDir, BRIDGE_DIR), REQUEST_DIR)
        requestDir.mkdirs()
        File(File(homeDir, BRIDGE_DIR), RESULT_DIR).mkdirs()

        observer = object : FileObserver(requestDir, FileObserver.CREATE or FileObserver.MODIFY) {
            override fun onEvent(event: Int, path: String?) {
                if (path == null) return
                when {
                    path.startsWith("log_") -> handleRequest(requestDir, File(homeDir, BRIDGE_DIR), path)
                    path.startsWith("camera_") -> handleCameraRequest(requestDir, File(homeDir, BRIDGE_DIR), path)
                }
            }
        }
        observer?.startWatching()
        isRunning = true
        AIDevLogger.d(TAG, "Bridge service started, watching $requestDir")
    }

    /** 停止桥服务 */
    fun stop() {
        observer?.stopWatching()
        observer = null
        isRunning = false
        AIDevLogger.d(TAG, "Bridge service stopped")
    }

    private fun handleRequest(requestDir: File, bridgeDir: File, fileName: String) {
        val reqFile = File(requestDir, fileName)
        if (!reqFile.exists()) return

        // 防止重复处理
        val resFile = File(File(bridgeDir, RESULT_DIR), fileName)
        if (resFile.exists()) return

        Thread {
            try {
                Thread.sleep(100) // 等待写入完成

                val content = reqFile.readText()
                val lines = content.lines().associate {
                    val parts = it.split("=", limit = 2)
                    parts[0] to if (parts.size > 1) parts[1] else ""
                }

                val packageName = lines["PACKAGE"] ?: "com.aidev.terminal"
                val lineCount = lines["LINES"]?.toIntOrNull() ?: 200
                val follow = lines["FOLLOW"]?.isNotEmpty() == true

                AIDevLogger.d(TAG, "Processing request: pkg=$packageName, lines=$lineCount, follow=$follow")

                if (follow) {
                    // 流式模式：写入 header 后持续追加
                    atomicWriteText(resFile, "[持续监听中... 按 Ctrl+C 停止]\n")
                    val process = ShizukuLogcat.startLogStream(
                        packageName = packageName,
                        onLine = { line ->
                            try {
                                resFile.appendText("$line\n")
                            } catch (e: Exception) {
                                AIDevLogger.w(TAG, "Failed to append log line", e)
                            }
                        },
                        onError = { err ->
                            resFile.appendText("\nERROR: $err\n")
                        }
                    )
                    // 流式模式下不删除请求文件，让用户 Ctrl+C 后手动清理
                } else {
                    // 批量模式
                    val done = java.util.concurrent.CountDownLatch(1)
                    ShizukuLogcat.fetchLog(
                        packageName = packageName,
                        lines = lineCount
                    ) { result ->
                        result.onSuccess { logs ->
                            atomicWriteText(resFile, logs)
                        }.onFailure { e ->
                            AIDevLogger.e(TAG, "Failed to fetch log", e)
                            atomicWriteText(resFile, "ERROR: ${e.message}\n")
                        }
                        done.countDown()
                    }
                    done.await(30, java.util.concurrent.TimeUnit.SECONDS)
                    if (done.count > 0) {
                        atomicWriteText(resFile, "ERROR: 请求超时\n")
                    }
                    // 清理请求文件
                    reqFile.delete()
                }
            } catch (e: Exception) {
                AIDevLogger.e(TAG, "Failed to handle request: $fileName", e)
                atomicWriteText(resFile, "ERROR: ${e.message}\n")
                reqFile.delete()
            }
        }.apply { isDaemon = true }.start()
    }

    private fun handleCameraRequest(requestDir: File, bridgeDir: File, fileName: String) {
        val reqFile = File(requestDir, fileName)
        if (!reqFile.exists()) return

        val resFile = File(File(bridgeDir, RESULT_DIR), fileName)
        if (resFile.exists()) return

        // 获取当前前台 Activity（保留供后续 IPC 使用）
        val activity = AIDevApp.getCurrentActivity() ?: run {
            resFile.writeText("""{"status":"error","error":"No foreground activity available","timestamp":${System.currentTimeMillis()}}""")
            reqFile.delete()
            return
        }

        resFile.writeText("""{"status":"error","error":"Camera bridge removed","timestamp":${System.currentTimeMillis()}}""")
        reqFile.delete()
    }

    /** 原子写入：先写 .tmp 再 rename，避免进程被杀导致文件损坏 */
    private fun atomicWriteText(file: File, text: String) {
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(text)
        tmp.renameTo(file)
    }
}
