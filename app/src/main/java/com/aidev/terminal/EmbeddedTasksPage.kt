package com.aidev.terminal

import android.app.Activity
import android.app.AlertDialog
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
        val dir = TaskManagerHelper.taskDir(activity)
        val tasks = dir.listFiles { f -> f.name.endsWith(".meta") }?.mapNotNull { TaskManagerHelper.parseTask(it, dir) }?.sortedByDescending { it.id } ?: emptyList()
        val running = tasks.count { TaskManagerHelper.isRunning(it.pid) }

        list.addView(ui.section("后台任务", "管理运行中的后台进程和日志"))
        list.addView(ui.listItem("运行中", "${running} 个", running > 0))
        list.addView(ui.listItem("任务记录", "${tasks.size} 个", tasks.isNotEmpty()))

        list.addView(ui.divider())

        list.addView(ui.actionRow("新建任务", "从模板创建后台任务") { showTaskTemplates() })
        list.addView(ui.actionRow("搜索任务", "按名称、PID 或命令搜索") { searchTasks(tasks) })
        list.addView(ui.actionRow("异常日志", "查看最近日志中的错误") { searchErrorLogs() })

        list.addView(ui.divider())

        if (tasks.isEmpty()) {
            list.addView(TaskManagerHelper.emptyState(activity, ui, "可以在终端中运行：task-run <name> '<command>'"))
        } else {
            tasks.forEach { task ->
                list.addView(TaskManagerHelper.taskRow(activity, ui, task) { TaskManagerHelper.showTask(activity, ui, host, task) { reload() } })
            }
        }
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
                            TaskManagerHelper.showTask(activity, ui, host, matches[which]) { reload() }
                        }
                        .show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun searchErrorLogs() {
        val dir = TaskManagerHelper.taskDir(activity)
        val matches = dir.listFiles { f -> f.name.endsWith(".log") }
            ?.mapNotNull { file ->
                val snippet = TaskManagerHelper.errorSnippet(file)
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
                ClipboardHelper.copy(activity, "AIDev 异常日志", snippet)
                Toast.makeText(activity, "已复制异常片段", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("追踪日志") { _, _ -> host.openTerminal("tail -f \"${file.absolutePath}\"") }
            .setNegativeButton("关闭", null)
            .show()
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
