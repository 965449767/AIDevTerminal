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
