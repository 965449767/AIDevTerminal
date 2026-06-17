package com.aidev.terminal

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import java.io.File

/**
 * 后台任务管理页面（设置内子页面）。
 * 原 AI 相关内容已迁移到 EmbeddedAIPage。
 */
class EmbeddedTasksPage : ShellPage {
    private lateinit var activity: Activity
    private lateinit var ui: AIDevUi
    private lateinit var list: LinearLayout
    private lateinit var host: ShellHost

    override fun create(activity: Activity, ui: AIDevUi, host: ShellHost): View {
        this.activity = activity
        this.ui = ui
        this.host = host
        list = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_8), ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_24))
        }
        reload()
        return ScrollView(activity).apply { addView(list) }
    }

    override fun onSelected(activity: Activity, view: View) {
        if (::list.isInitialized) reload()
    }

    private fun reload() {
        list.removeAllViews()
        val tasks = taskDir().listFiles { f -> f.name.endsWith(".meta") }?.mapNotNull { parseTask(it) }?.sortedByDescending { it.id } ?: emptyList()
        val running = tasks.count { isRunning(it.pid) }

        list.addView(ui.section("后台任务", "管理运行中的后台进程和日志"))
        list.addView(ui.listItem("运行中", "${running} 个", running > 0))
        list.addView(ui.listItem("任务记录", "${tasks.size} 个", tasks.isNotEmpty()))

        list.addView(ui.divider())

        list.addView(ui.actionRow("新建任务", "从模板创建后台任务") { showTaskTemplates() })
        list.addView(ui.actionRow("搜索任务", "按名称、PID 或命令搜索") { searchTasks(tasks) })
        list.addView(ui.actionRow("异常日志", "查看最近日志中的错误") { searchErrorLogs() })

        list.addView(ui.divider())

        if (tasks.isEmpty()) {
            list.addView(emptyState())
        } else {
            tasks.forEach { list.addView(taskRow(it)) }
        }
    }

    private fun taskDir() = File(activity.filesDir, "home/tasks").apply { mkdirs() }

    private fun emptyState(): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_8), ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_8))
            addView(ui.text("暂无后台任务", DesignTokens.TEXT_H2, ui.palette.text, bold = true))
            addView(ui.muted("可以在终端中运行：task-run <name> '<command>'"))
        }

    private fun taskRow(task: EmbeddedTaskInfo): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_8), ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_8))
            val running = isRunning(task.pid)
            addView(ui.text("${task.id}  ${if (running) "运行中" else "已结束"}", DesignTokens.TEXT_BODY, if (running) ui.palette.accent else ui.palette.text, bold = true))
            addView(ui.muted("PID：${task.pid.ifBlank { "-" }}    开始：${task.started.ifBlank { "-" }}"))
            addView(ui.muted("命令：${task.cmd.ifBlank { "-" }}").apply { maxLines = 2 })
            setOnClickListener { showTask(task) }
        }

    private fun showTask(task: EmbeddedTaskInfo) {
        val log = if (task.logFile.exists()) task.logFile.readLines().takeLast(80).joinToString("\n") else "日志不存在：${task.logPath}"
        AlertDialog.Builder(activity)
            .setTitle(task.id)
            .setMessage("状态：${if (isRunning(task.pid)) "运行中" else "已结束"}\n名称：${task.name}\nPID：${task.pid}\n命令：${task.cmd}\n日志：${task.logPath}\n\n最近日志：\n$log")
            .setPositiveButton("刷新") { _, _ -> reload() }
            .setNegativeButton(if (isRunning(task.pid)) "停止任务" else "关闭") { _, _ -> if (isRunning(task.pid)) stopTask(task) }
            .setNeutralButton("更多") { _, _ -> showTaskMore(task, log) }
            .show()
    }

    private fun showTaskMore(task: EmbeddedTaskInfo, log: String) {
        AlertDialog.Builder(activity)
            .setTitle("任务操作")
            .setItems(arrayOf("日志查看器", "复制日志", "复制日志路径", "终端追踪日志", "导出日志副本", "复制异常片段", "刷新任务页")) { _, which ->
                when (which) {
                    0 -> showLogViewer(task)
                    1 -> {
                        copyText("AIDev 任务日志", log)
                        Toast.makeText(activity, "已复制日志", Toast.LENGTH_SHORT).show()
                    }
                    2 -> {
                        copyText("AIDev 日志路径", task.logPath)
                        Toast.makeText(activity, "已复制日志路径", Toast.LENGTH_SHORT).show()
                    }
                    3 -> host.openTerminal("tail -f \"${task.logPath}\"")
                    4 -> exportTaskLog(task)
                    5 -> copyTaskErrorSnippet(task)
                    6 -> reload()
                }
            }
            .show()
    }

    private fun showLogViewer(task: EmbeddedTaskInfo) {
        val latest = if (task.logFile.exists()) task.logFile.readLines().takeLast(180).joinToString("\n") else "日志不存在：${task.logPath}"
        AlertDialog.Builder(activity)
            .setTitle("日志：${task.id}")
            .setMessage(latest)
            .setPositiveButton("刷新") { _, _ -> showLogViewer(task) }
            .setNeutralButton("复制") { _, _ ->
                copyText("AIDev 任务日志", latest)
                Toast.makeText(activity, "已复制日志", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun exportTaskLog(task: EmbeddedTaskInfo) {
        if (!task.logFile.isFile) return Toast.makeText(activity, "日志不存在", Toast.LENGTH_SHORT).show()
        val dst = File(taskDir(), "${task.id}-export.log")
        runCatching { task.logFile.copyTo(dst, overwrite = true) }
            .onSuccess {
                copyText("AIDev 导出日志路径", dst.absolutePath)
                Toast.makeText(activity, "已导出日志并复制路径", Toast.LENGTH_SHORT).show()
            }
            .onFailure { Toast.makeText(activity, "导出失败：${it.message}", Toast.LENGTH_SHORT).show() }
    }

    private fun copyTaskErrorSnippet(task: EmbeddedTaskInfo) {
        val snippet = errorSnippet(task.logFile)
        if (snippet.isBlank()) return Toast.makeText(activity, "未发现明显异常片段", Toast.LENGTH_SHORT).show()
        copyText("AIDev 异常日志", snippet)
        Toast.makeText(activity, "已复制异常片段", Toast.LENGTH_SHORT).show()
    }

    private fun errorSnippet(file: File): String {
        val lines = runCatching { file.readLines().takeLast(300) }.getOrDefault(emptyList())
        val index = lines.indexOfLast { it.contains("error", true) || it.contains("failed", true) || it.contains("exception", true) }
        if (index < 0) return ""
        val start = (index - 3).coerceAtLeast(0)
        val end = (index + 4).coerceAtMost(lines.size)
        return lines.subList(start, end).joinToString("\n")
    }

    private fun showTaskTemplates() {
        val names = arrayOf(
            "OpenCode 服务", "Python HTTP 8000", "Node Dev", "Gradle Debug 构建",
            "Logcat 记录", "当前端口检查", "Git 状态", "Python 测试", "Node 测试",
            "Go 测试", "当前项目 Git", "当前项目测试", "当前项目构建",
            "当前项目诊断", "当前项目修复", "当前项目 OpenCode",
            "当前项目后台代理", "当前项目代理上下文", "AI 代理日志"
        )
        val commands = arrayOf(
            "task-run opencode 'opencode serve'", "task-run pyserver 'python3 -m http.server 8000'",
            "task-run npm-dev 'npm run dev'", "task-run gradle './gradlew assembleDebug'",
            "task-run logcat 'logcat'", "list-listen-ports",
            "task-run git-status 'git status --short --branch'", "task-run pytest 'python3 -m pytest'",
            "task-run npm-test 'npm test'", "task-run go-test 'go test ./...'",
            currentProjectTask("git status --short --branch"),
            currentProjectTask("if [ -f package.json ]; then npm test; elif [ -f build.gradle ] || [ -f build.gradle.kts ]; then ./gradlew test; elif [ -f go.mod ]; then go test ./...; else python3 -m pytest; fi"),
            currentProjectTask("if [ -f package.json ]; then npm run build; elif [ -f build.gradle ] || [ -f build.gradle.kts ]; then ./gradlew assembleDebug; elif [ -f go.mod ]; then go build ./...; elif [ -f Cargo.toml ]; then cargo build; else ls -la; fi"),
            currentProjectTask("if [ -f package.json ]; then npm pkg get scripts; elif [ -f build.gradle ] || [ -f build.gradle.kts ]; then ./gradlew tasks --all | head -80; elif [ -f go.mod ]; then go list ./...; elif [ -f Cargo.toml ]; then cargo metadata --no-deps; else python3 -m pytest --collect-only; fi"),
            currentProjectTask("if [ -f package.json ]; then rm -rf node_modules package-lock.json && npm install; elif [ -f build.gradle ] || [ -f build.gradle.kts ]; then ./gradlew --stop; ./gradlew clean; elif [ -f go.mod ]; then go clean -cache && go mod tidy; elif [ -f Cargo.toml ]; then cargo clean && cargo fetch; elif [ -f requirements.txt ]; then python3 -m pip install -r requirements.txt --break-system-packages; else ls -la; fi"),
            currentProjectTask("opencode"),
            currentProjectTask("task-run opencode \"opencode\""),
            currentProjectTask("aidev-agent-context"),
            "aidev-agent-log"
        )
        AlertDialog.Builder(activity)
            .setTitle("任务模板")
            .setItems(names) { _, which -> executeTaskTemplate(names[which], commands[which]) }
            .show()
    }

    private fun executeTaskTemplate(name: String, command: String) {
        if (name.contains("修复")) {
            AlertDialog.Builder(activity)
                .setTitle("确认执行修复模板")
                .setMessage("将执行：\n$command\n\n注意：修复模板可能删除缓存、依赖目录或锁文件。")
                .setPositiveButton("确认执行") { _, _ -> host.openTerminal(command) }
                .setNegativeButton("取消", null)
                .show()
        } else {
            try {
                host.openTerminal(command)
            } catch (e: Exception) {
                Toast.makeText(activity, "任务启动失败：${e.message ?: "未知错误"}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun currentProjectTask(command: String): String {
        val path = activity.getSharedPreferences("aidev_ui", Activity.MODE_PRIVATE).getString("current_project_path", "").orEmpty()
        return if (path.isBlank()) "pwd && $command" else "cd \"$path\" && $command"
    }

    private fun searchTasks(tasks: List<EmbeddedTaskInfo>) {
        val edit = android.widget.EditText(activity).apply { setHint("输入任务名、命令、PID 或 ID") }
        AlertDialog.Builder(activity)
            .setTitle("搜索任务")
            .setView(edit)
            .setPositiveButton("搜索") { _, _ ->
                val keyword = edit.text.toString().trim()
                if (keyword.isEmpty()) return@setPositiveButton
                val matches = tasks.filter {
                    it.id.contains(keyword, ignoreCase = true) ||
                        it.name.contains(keyword, ignoreCase = true) ||
                        it.pid.contains(keyword, ignoreCase = true) ||
                        it.cmd.contains(keyword, ignoreCase = true)
                }
                if (matches.isEmpty()) {
                    Toast.makeText(activity, "没有匹配任务", Toast.LENGTH_SHORT).show()
                } else {
                    AlertDialog.Builder(activity)
                        .setTitle("搜索结果")
                        .setItems(matches.map { "${it.id}\n${it.cmd.ifBlank { it.name }}" }.toTypedArray()) { _, which ->
                            showTask(matches[which])
                        }
                        .show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun searchErrorLogs() {
        val matches = taskDir().listFiles { f -> f.name.endsWith(".log") }
            ?.mapNotNull { file ->
                val snippet = errorSnippet(file)
                if (snippet.isBlank()) null else file to snippet
            }.orEmpty()
        if (matches.isEmpty()) {
            Toast.makeText(activity, "最近日志未发现明显异常", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(activity)
            .setTitle("异常日志")
            .setItems(matches.map { "${it.first.name}\n${it.second.lineSequence().firstOrNull().orEmpty()}" }.toTypedArray()) { _, which ->
                showErrorLogDetail(matches[which].first, matches[which].second)
            }
            .show()
    }

    private fun showErrorLogDetail(file: File, snippet: String) {
        AlertDialog.Builder(activity)
            .setTitle("异常片段：${file.name}")
            .setMessage(snippet)
            .setPositiveButton("复制片段") { _, _ ->
                copyText("AIDev 异常日志", snippet)
                Toast.makeText(activity, "已复制异常片段", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("追踪日志") { _, _ -> host.openTerminal("tail -f \"${file.absolutePath}\"") }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun parseTask(meta: File): EmbeddedTaskInfo? {
        val map = meta.readLines().mapNotNull {
            val i = it.indexOf('=')
            if (i <= 0) null else it.substring(0, i) to it.substring(i + 1)
        }.toMap()
        val id = map["id"] ?: meta.name.removeSuffix(".meta")
        val log = map["log"] ?: File(taskDir(), "$id.log").absolutePath
        return EmbeddedTaskInfo(id, map["name"] ?: "", map["pid"] ?: "", map["started"] ?: "", map["cmd"] ?: "", log, File(log))
    }

    private fun isRunning(pid: String): Boolean = pid.toIntOrNull()?.let {
        runCatching {
            val p = Runtime.getRuntime().exec(arrayOf("kill", "-0", it.toString()))
            p.waitFor() == 0
        }.getOrDefault(false)
    } ?: false

    private fun stopTask(task: EmbeddedTaskInfo) {
        task.pid.toIntOrNull()?.let { Runtime.getRuntime().exec(arrayOf("kill", it.toString())).waitFor() }
        reload()
    }

    private fun copyText(label: String, text: String) {
        (activity.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(ClipData.newPlainText(label, text))
    }
}

data class EmbeddedTaskInfo(
    val id: String,
    val name: String,
    val pid: String,
    val started: String,
    val cmd: String,
    val logPath: String,
    val logFile: File
)
