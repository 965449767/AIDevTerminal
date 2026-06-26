package com.aidev.terminal

import android.content.pm.PackageManager
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.reflect.Method
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import rikka.shizuku.Shizuku

sealed class ShizukuState {
    object NotInstalled : ShizukuState()
    object NotRunning : ShizukuState()
    object NotAuthorized : ShizukuState()
    object Ready : ShizukuState()
}

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
     * @param level 日志级别过滤，如 "ERROR", "WARN", "INFO", "DEBUG", "VERBOSE"
     * @param tag 按标签过滤，如 "ActivityManager"
     * @param callback 结果回调（在主线程）
     */
    fun fetchLog(
        packageName: String = "",
        lines: Int = 500,
        filters: List<String> = emptyList(),
        level: String = "",
        tag: String = "",
        callback: (Result<String>) -> Unit
    ) {
        if (!isAvailable()) {
            callback(Result.failure(IllegalStateException("Shizuku 不可用: ${statusText()}")))
            return
        }

        Thread {
            try {
                val cmd = buildLogcatCommand(packageName, lines, filters, follow = false, level = level, tag = tag)
                Log.d(TAG, "Executing: $cmd")

                val method = newProcessMethod ?: run {
                    callback(Result.failure(RuntimeException("Shizuku newProcess 方法不可用")))
                    return@Thread
                }
                val process = method.invoke(
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
        level: String = "",
        tag: String = "",
        onLine: (String) -> Unit,
        onError: (String) -> Unit
    ): java.lang.Process? {
        if (!isAvailable()) {
            onError("Shizuku 不可用: ${statusText()}")
            return null
        }

        return try {
            val cmd = buildLogcatCommand(packageName, 0, filters, follow = true, level = level, tag = tag)
            Log.d(TAG, "Starting stream: $cmd")

            val method = newProcessMethod ?: run {
                onError("Shizuku newProcess 方法不可用")
                return null
            }
            val process = method.invoke(
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
        follow: Boolean = false,
        level: String = "",
        tag: String = ""
    ): String {
        val sb = StringBuilder("logcat")

        if (packageName.isNotBlank()) {
            sb.append(" --pid=\$(pidof $packageName 2>/dev/null || echo 0)")
        }
        if (lines > 0) {
            sb.append(" -t $lines")
        }
        if (level.isNotEmpty()) {
            if (tag.isNotEmpty()) {
                sb.append(" $tag:$level *:S")
            } else {
                sb.append(" *:$level")
            }
        } else if (tag.isNotEmpty()) {
            sb.append(" $tag:V *:S")
        }
        filters.forEach { sb.append(" $it") }
        sb.append(if (follow) " -v threadtime" else " -d -v threadtime")

        return sb.toString()
    }

    /** 清空 logcat 缓冲区 */
    fun clearLogBuffer() {
        if (!isAvailable()) return
        Thread {
            try {
                val method = newProcessMethod ?: return@Thread
                val process = method.invoke(
                    null, arrayOf("sh", "-c", "logcat -c"), null, null
                ) as? java.lang.Process
                process?.waitFor()
                Log.d(TAG, "Log buffer cleared")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear log buffer", e)
            }
        }.apply { isDaemon = true }.start()
    }

    fun checkState(context: android.content.Context): ShizukuState {
        val installed = runCatching {
            context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
        }.isSuccess
        if (!installed) return ShizukuState.NotInstalled

        if (!Shizuku.pingBinder()) return ShizukuState.NotRunning

        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            return ShizukuState.NotAuthorized
        }

        return ShizukuState.Ready
    }

    suspend fun executeCommand(cmd: String): ShellResult = withContext(Dispatchers.IO) {
        val method = newProcessMethod
        if (method == null) {
            return@withContext ShellResult("", "Shizuku API 不兼容", -1)
        }
        try {
            withTimeout(60_000L) {
                val process = method.invoke(
                    null,
                    arrayOf("sh", "-c", cmd),
                    null,
                    null
                ) as? java.lang.Process

                if (process == null) {
                    return@withTimeout ShellResult("", "无法创建 Shizuku 进程", -1)
                }

                val stdout = process.inputStream.bufferedReader().use { it.readText() }
                val stderr = process.errorStream.bufferedReader().use { it.readText() }
                val exitCode = process.waitFor()
                ShellResult(stdout, stderr, exitCode)
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Command timed out after 60s: $cmd")
            ShellResult("", "命令执行超时（60 秒）", -1)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute command", e)
            ShellResult("", e.message ?: "未知错误", -1)
        }
    }

    fun executeFireAndForget(cmd: String) {
        val method = newProcessMethod ?: return
        Log.d(TAG, "Fire-and-forget: $cmd")
        Thread {
            try {
                val p = method.invoke(null, arrayOf("sh", "-c", cmd), null, null)
                Log.d(TAG, "Process spawned: $p")
            } catch (e: Exception) {
                Log.e(TAG, "Fire-and-forget failed", e)
            }
        }.start()
    }

    fun pmInstallErrorHint(result: ShellResult): String {
        val stderr = result.stderr
        val stdout = result.stdout
        return when {
            result.exitCode == 0 -> "安装成功"
            stderr.contains("INSTALL_FAILED_ALREADY_EXISTS") -> "应用已存在，可尝试卸载后重装"
            stderr.contains("INSUFFICIENT_STORAGE") -> "存储空间不足"
            stderr.contains("INVALID_APK") -> "APK 文件无效或损坏"
            stderr.contains("NO_MATCHING_ABIS") -> "APK 架构与此设备不兼容"
            stderr.contains("PERMISSION_MODEL_DOWNGRADE") -> "权限限制，请尝试手动安装"
            stderr.contains("USER_RESTRICTED") -> "用户限制，请检查工作资料或多用户设置"
            stderr.contains("INSTALL_FAILED_VERSION_DOWNGRADE") -> "已安装版本更高，降级被拒绝"
            else -> "安装失败 (exit=${result.exitCode})，请尝试手动安装"
        }
    }
}
