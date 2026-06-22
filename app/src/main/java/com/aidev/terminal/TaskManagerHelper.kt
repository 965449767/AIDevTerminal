package com.aidev.terminal

import android.app.Activity
import android.app.AlertDialog
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import java.io.File

/**
 * 任务管理公共辅助方法。
 * 从 EmbeddedAIPage 和 EmbeddedTasksPage 中提取的重复逻辑。
 *
 * 所有方法均为静态工具方法，不持有 Activity/UI 状态。
 * 需要回调（如 reload、copyText）通过参数传入。
 */
object TaskManagerHelper {

    /** 解析 .meta 文件为 EmbeddedTaskInfo */
    fun parseTask(meta: File, dir: File): EmbeddedTaskInfo? {
        val map = meta.readLines().mapNotNull {
            val i = it.indexOf('=')
            if (i <= 0) null else it.substring(0, i) to it.substring(i + 1)
        }.toMap()
        val id = map["id"] ?: meta.name.removeSuffix(".meta")
        val log = map["log"] ?: File(dir, "$id.log").absolutePath
        return EmbeddedTaskInfo(id, map["name"] ?: "", map["pid"] ?: "", map["started"] ?: "", map["cmd"] ?: "", log, File(log))
    }

    /** 检查指定 PID 的进程是否仍在运行 */
    fun isRunning(pid: String): Boolean = pid.toIntOrNull()?.let {
        runCatching {
            val p = ProcessBuilder("kill", "-0", it.toString())
                .redirectErrorStream(true)
                .start()
            p.inputStream.use { it.readBytes() }
            p.waitFor() == 0
        }.getOrDefault(false)
    } ?: false

    /** 停止指定任务（发送 SIGTERM） */
    fun stopTask(task: EmbeddedTaskInfo) {
        task.pid.toIntOrNull()?.let {
            runCatching {
                val p = ProcessBuilder("kill", it.toString())
                    .redirectErrorStream(true)
                    .start()
                p.inputStream.use { it.readBytes() }
                p.waitFor()
            }
        }
    }

    /** 构建单条任务的行视图 */
    fun taskRow(
        activity: Activity,
        ui: AIDevUi,
        task: EmbeddedTaskInfo,
        onClick: () -> Unit
    ): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_8), ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_8))
            val running = isRunning(task.pid)
            addView(ui.text("${task.id}  ${if (running) "运行中" else "已结束"}", DesignTokens.TEXT_BODY, if (running) ui.palette.accent else ui.palette.text, bold = true))
            addView(ui.muted("PID：${task.pid.ifBlank { "-" }}    开始：${task.started.ifBlank { "-" }}"))
            addView(ui.muted("命令：${task.cmd.ifBlank { "-" }}").apply { maxLines = 2 })
            setOnClickListener { onClick() }
        }

    /** 显示任务详情弹窗 */
    fun showTask(
        activity: Activity,
        ui: AIDevUi,
        host: ShellHost,
        task: EmbeddedTaskInfo,
        onRefresh: () -> Unit
    ) {
        val log = if (task.logFile.exists()) task.logFile.readLines().takeLast(80).joinToString("\n") else "日志不存在：${task.logPath}"
        AlertDialog.Builder(activity)
            .setTitle(task.id)
            .setMessage("状态：${if (isRunning(task.pid)) "运行中" else "已结束"}\n名称：${task.name}\nPID：${task.pid}\n命令：${task.cmd}\n日志：${task.logPath}\n\n最近日志：\n$log")
            .setPositiveButton("刷新") { _, _ -> onRefresh() }
            .setNegativeButton(if (isRunning(task.pid)) "停止任务" else "关闭") { _, _ -> if (isRunning(task.pid)) { stopTask(task); onRefresh() } }
            .setNeutralButton("更多") { _, _ -> showTaskMore(activity, ui, host, task, log, onRefresh) }
            .show()
    }

    /** 显示任务更多操作菜单 */
    fun showTaskMore(
        activity: Activity,
        ui: AIDevUi,
        host: ShellHost,
        task: EmbeddedTaskInfo,
        log: String,
        onRefresh: () -> Unit
    ) {
        AlertDialog.Builder(activity)
            .setTitle("任务操作")
            .setItems(arrayOf("日志查看器", "复制日志", "复制日志路径", "终端追踪日志", "导出日志副本", "复制异常片段", "刷新任务页")) { _, which ->
                when (which) {
                    0 -> showLogViewer(activity, ui, task)
                    1 -> {
                        ClipboardHelper.copy(activity, "AIDev 任务日志", log)
                        Toast.makeText(activity, "已复制日志", Toast.LENGTH_SHORT).show()
                    }
                    2 -> {
                        ClipboardHelper.copy(activity, "AIDev 日志路径", task.logPath)
                        Toast.makeText(activity, "已复制日志路径", Toast.LENGTH_SHORT).show()
                    }
                    3 -> host.openTerminal("tail -f \"${task.logPath}\"")
                    4 -> exportTaskLog(activity, ui, task, taskDir(activity))
                    5 -> copyTaskErrorSnippet(activity, ui, task)
                    6 -> onRefresh()
                }
            }
            .show()
    }

    /** 显示日志查看器弹窗 */
    fun showLogViewer(activity: Activity, ui: AIDevUi, task: EmbeddedTaskInfo) {
        val latest = if (task.logFile.exists()) task.logFile.readLines().takeLast(180).joinToString("\n") else "日志不存在：${task.logPath}"
        AlertDialog.Builder(activity)
            .setTitle("日志：${task.id}")
            .setMessage(latest)
            .setPositiveButton("刷新") { _, _ -> showLogViewer(activity, ui, task) }
            .setNeutralButton("复制") { _, _ ->
                ClipboardHelper.copy(activity, "AIDev 任务日志", latest)
                Toast.makeText(activity, "已复制日志", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    /** 导出任务日志副本到 taskDir */
    fun exportTaskLog(activity: Activity, ui: AIDevUi, task: EmbeddedTaskInfo, dir: File) {
        if (!task.logFile.isFile) {
            Toast.makeText(activity, "日志不存在", Toast.LENGTH_SHORT).show()
            return
        }
        val dst = File(dir, "${task.id}-export.log")
        runCatching { task.logFile.copyTo(dst, overwrite = true) }
            .onSuccess {
                ClipboardHelper.copy(activity, "AIDev 导出日志路径", dst.absolutePath)
                Toast.makeText(activity, "已导出日志并复制路径", Toast.LENGTH_SHORT).show()
            }
            .onFailure { Toast.makeText(activity, "导出失败：${it.message}", Toast.LENGTH_SHORT).show() }
    }

    /** 复制任务日志中的异常片段到剪贴板 */
    fun copyTaskErrorSnippet(activity: Activity, ui: AIDevUi, task: EmbeddedTaskInfo) {
        val snippet = errorSnippet(task.logFile)
        if (snippet.isBlank()) {
            Toast.makeText(activity, "未发现明显异常片段", Toast.LENGTH_SHORT).show()
            return
        }
        ClipboardHelper.copy(activity, "AIDev 异常日志", snippet)
        Toast.makeText(activity, "已复制异常片段", Toast.LENGTH_SHORT).show()
    }

    /** 从日志文件中提取最后一条 error/failed/exception 附近的上下文片段 */
    fun errorSnippet(file: File): String {
        val lines = runCatching { file.readLines().takeLast(300) }.getOrDefault(emptyList())
        val index = lines.indexOfLast { it.contains("error", true) || it.contains("failed", true) || it.contains("exception", true) }
        if (index < 0) return ""
        val start = (index - 3).coerceAtLeast(0)
        val end = (index + 4).coerceAtMost(lines.size)
        return lines.subList(start, end).joinToString("\n")
    }

    /** 获取任务目录 */
    fun taskDir(activity: Activity): File = File(activity.filesDir, "home/tasks").apply { mkdirs() }

    /** 构建空状态占位视图 */
    fun emptyState(activity: Activity, ui: AIDevUi, hint: String): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_8), ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_8))
            addView(ui.text("暂无后台任务", DesignTokens.TEXT_H2, ui.palette.text, bold = true))
            addView(ui.muted(hint))
        }
}
