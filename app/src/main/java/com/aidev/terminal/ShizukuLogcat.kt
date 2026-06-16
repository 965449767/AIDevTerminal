package com.aidev.terminal

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.reflect.Method
import rikka.shizuku.Shizuku

/**
 * 通过 Shizuku 执行 logcat 获取应用日志。
 * 不需要 root 权限，不需要 USB 调试，只需要 Shizuku 已授权。
 */
object ShizukuLogcat {

    private const val TAG = "ShizukuLogcat"

    /** 反射获取 Shizuku.newProcess 方法（新版中为 private） */
    private val newProcessMethod: Method? by lazy {
        try {
            Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply { isAccessible = true }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get newProcess method", e)
            null
        }
    }

    /** 检查是否可用 */
    fun isAvailable(): Boolean {
        return Shizuku.pingBinder() &&
               Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED &&
               newProcessMethod != null
    }

    /** 获取 Shizuku 状态描述 */
    fun statusText(): String = when {
        !Shizuku.pingBinder() -> "Shizuku 未运行"
        Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED -> "Shizuku 未授权"
        newProcessMethod == null -> "Shizuku API 不兼容"
        else -> "可用"
    }

    /**
     * 执行 logcat 命令获取日志。
     * @param packageName 应用包名，为空则获取所有日志
     * @param lines 获取最近多少行，默认 500
     * @param filters 额外的过滤标签，如 "AIDEV:*"
     * @param callback 结果回调（在主线程）
     */
    fun fetchLog(
        packageName: String = "",
        lines: Int = 500,
        filters: List<String> = emptyList(),
        callback: (Result<String>) -> Unit
    ) {
        if (!isAvailable()) {
            callback(Result.failure(IllegalStateException("Shizuku 不可用: ${statusText()}")))
            return
        }

        Thread {
            try {
                val cmd = buildLogcatCommand(packageName, lines, filters)
                Log.d(TAG, "Executing: $cmd")

                val process = newProcessMethod!!.invoke(
                    null,
                    arrayOf("sh", "-c", cmd),
                    null,
                    null
                ) as? java.lang.Process

                if (process == null) {
                    callback(Result.failure(RuntimeException("无法创建 Shizuku 进程")))
                    return@Thread
                }

                val output = process.inputStream.bufferedReader().use { it.readText() }
                val exitCode = process.waitFor()

                if (exitCode != 0) {
                    val error = process.errorStream.bufferedReader().use { it.readText() }
                    Log.w(TAG, "logcat exit=$exitCode, error=$error")
                    callback(Result.failure(RuntimeException("logcat 失败 (exit=$exitCode): $error")))
                } else {
                    callback(Result.success(output))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch log", e)
                callback(Result.failure(e))
            }
        }.apply { isDaemon = true }.start()
    }

    /**
     * 持续监听日志（流式输出）。
     * 返回的 Process 需要调用方管理生命周期。
     */
    fun startLogStream(
        packageName: String = "",
        filters: List<String> = emptyList(),
        onLine: (String) -> Unit,
        onError: (String) -> Unit
    ): java.lang.Process? {
        if (!isAvailable()) {
            onError("Shizuku 不可用: ${statusText()}")
            return null
        }

        return try {
            val cmd = buildLogcatCommand(packageName, 0, filters, follow = true)
            Log.d(TAG, "Starting stream: $cmd")

            val process = newProcessMethod!!.invoke(
                null,
                arrayOf("sh", "-c", cmd),
                null,
                null
            ) as? java.lang.Process

            if (process == null) {
                onError("无法创建 Shizuku 进程")
                return null
            }

            // 在后台线程读取输出
            Thread {
                try {
                    BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            line?.let { onLine(it) }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Log stream ended", e)
                }
            }.apply { isDaemon = true }.start()

            process
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start log stream", e)
            onError(e.message ?: "启动日志流失败")
            null
        }
    }

    /** 构建 logcat 命令 */
    private fun buildLogcatCommand(
        packageName: String,
        lines: Int,
        filters: List<String>,
        follow: Boolean = false
    ): String {
        val sb = StringBuilder("logcat")

        // 按包名过滤（通过 PID 映射）
        if (packageName.isNotBlank()) {
            sb.append(" --pid=\$(pidof $packageName 2>/dev/null || echo 0)")
        }

        // 行数限制
        if (lines > 0) {
            sb.append(" -t $lines")
        }

        // 额外过滤标签
        filters.forEach { sb.append(" $it") }

        // 持续监听模式
        if (follow) {
            sb.append(" -v threadtime")
        } else {
            sb.append(" -d -v threadtime")
        }

        return sb.toString()
    }
}
