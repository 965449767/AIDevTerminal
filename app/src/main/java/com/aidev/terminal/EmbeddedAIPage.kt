package com.aidev.terminal

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File

/**
 * AI 代理页面：OpenCode AI 助手、代理会话、任务管理
 */
class EmbeddedAIPage : ShellPage {
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

        // 状态概览
        list.addView(ui.section("AI 代理", "OpenCode AI 助手、代理会话和后台任务管理"))
        list.addView(ui.rowOf(
            ui.listItem("运行中", "${running} 个", running > 0),
            ui.listItem("OpenCode", if (opencodeInstalled()) "已安装" else "未安装", opencodeInstalled())
        ))
        list.addView(ui.rowOf(
            ui.listItem("AI 配置", if (providerConfigured()) "已配置" else "待配置", providerConfigured()),
            ui.listItem("监听端口", "${listeningPorts().size} 个", listeningPorts().isNotEmpty())
        ))

        list.addView(ui.divider())

        // AI 操作
        list.addView(ui.section("AI 操作", "启动 OpenCode 或创建新会话"))
        list.addView(ui.actionRow("原生 OpenCode", "多会话、Prompt、SSE、TODO、Diff") {
            com.aidev.terminal.opencode.OpencodeNativePanel.showHome(activity)
        })
        list.addView(ui.actionRow("新建原生会话", "直接通过 HTTP API 提交任务") {
            com.aidev.terminal.opencode.OpencodeNativePanel.createSessionAndPrompt(activity)
        })

        currentProject()?.let { project ->
            list.addView(ui.actionRow("OpenCode 项目", "在当前项目启动 AI 代理") {
                host.openTerminal("cd \"${project.absolutePath}\" && opencode")
            })
            list.addView(ui.actionRow("后台代理", "后台运行 OpenCode 并记录日志") {
                host.openTerminal("cd \"${project.absolutePath}\" && task-run opencode \"opencode\"")
            })
        }

        list.addView(ui.divider())

        // 日志与诊断
        list.addView(ui.section("日志与诊断", "查看代理日志和系统状态"))
        list.addView(ui.actionRow("日志摘要", "抽取错误、命令和修改线索") { showAgentLogSummary() })
        list.addView(ui.actionRow("追踪代理", "tail 最近 AI/任务日志") { host.openTerminal("aidev-agent-tail") })
        list.addView(ui.actionRow("端口详情", "查看 LISTEN 端口") { showPortDetails() })
        list.addView(ui.actionRow("安装 OpenCode", "在终端执行 install-aitool") { host.openTerminal("install-aitool") })

        list.addView(ui.divider())

        // 任务列表
        list.addView(ui.section("后台任务", "点击任务查看命令、PID 和最近日志"))
        if (tasks.isEmpty()) {
            list.addView(TaskManagerHelper.emptyState(activity, ui, "可以在终端中运行：task-run opencode 'opencode serve'"))
        } else {
            tasks.forEach { task ->
                list.addView(TaskManagerHelper.taskRow(activity, ui, task) { TaskManagerHelper.showTask(activity, ui, host, task) { reload() } })
            }
        }
    }

    private fun opencodeInstalled(): Boolean =
        File(activity.filesDir, "home/ubuntu-rootfs/root/.opencode/bin/opencode").exists() ||
            File(activity.filesDir, "home/.opencode/bin/opencode").exists() ||
            File(activity.filesDir, "home/ubuntu-rootfs/usr/local/bin/opencode").exists()

    private fun providerConfigured(): Boolean =
        File(activity.filesDir, "home/ubuntu-rootfs/root/.config/opencode").exists() ||
            File(activity.filesDir, "home/ubuntu-rootfs/root/.opencode").exists() ||
            File(activity.filesDir, "home/.config/opencode").exists()

    private fun currentProject(): File? =
        activity.getSharedPreferences("aidev_ui", Activity.MODE_PRIVATE)
            .getString("current_project_path", "")
            ?.takeIf { it.isNotBlank() }
            ?.let { File(it) }
            ?.takeIf { it.isDirectory }

    private fun listeningPorts(): List<Int> =
        parseTcpPorts(File("/proc/net/tcp")) + parseTcpPorts(File("/proc/net/tcp6"))

    private fun parseTcpPorts(file: File): List<Int> =
        runCatching {
            file.readLines().drop(1).mapNotNull { line ->
                val columns = line.trim().split(Regex("\\s+"))
                val local = columns.getOrNull(1) ?: return@mapNotNull null
                val state = columns.getOrNull(3) ?: return@mapNotNull null
                if (state != "0A") return@mapNotNull null
                local.substringAfterLast(":").toIntOrNull(16)
            }
        }.getOrDefault(emptyList()).distinct().sorted()

    private fun showAgentLogSummary() {
        val log = recentAgentLog()
        if (log == null || !log.isFile) {
            Toast.makeText(activity, "暂无代理日志", Toast.LENGTH_SHORT).show()
            return
        }
        val summary = summarizeAgentLog(log)
        AlertDialog.Builder(activity)
            .setTitle("代理日志摘要")
            .setMessage(summary)
            .setPositiveButton("复制摘要") { _, _ ->
                ClipboardHelper.copy(activity, "AIDev 代理日志摘要", summary)
                Toast.makeText(activity, "已复制摘要", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("追踪日志") { _, _ -> host.openTerminal("tail -f \"${log.absolutePath}\"") }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun recentAgentLog(): File? =
        TaskManagerHelper.taskDir(activity).listFiles { f -> f.name.endsWith(".log") }
            ?.sortedByDescending { it.lastModified() }
            ?.firstOrNull { it.name.contains("opencode", true) || runCatching { it.readText().contains("opencode", true) }.getOrDefault(false) }
            ?: TaskManagerHelper.taskDir(activity).listFiles { f -> f.name.endsWith(".log") }?.maxByOrNull { it.lastModified() }

    private fun summarizeAgentLog(log: File): String {
        val lines = runCatching { log.readLines().takeLast(600) }.getOrDefault(emptyList())
        fun hits(vararg keys: String, limit: Int = 20): List<String> =
            lines.filter { line -> keys.any { line.contains(it, ignoreCase = true) } }.takeLast(limit)
        val commands = hits("command", "running", "exec", "npm ", "python", "gradle", "cargo", "go ", "git ", limit = 18)
        val errors = hits("error", "failed", "exception", "traceback", "cannot", "not found", "denied", limit = 30)
        val files = hits("modified", "created", "updated", "wrote", "write", "saved", "changed", limit = 18)
        return listOf(
            "日志：${log.absolutePath}",
            "",
            "最近命令：",
            commands.ifEmpty { listOf("未识别到命令线索") }.joinToString("\n"),
            "",
            "错误/失败：",
            errors.ifEmpty { listOf("未识别到明显错误") }.joinToString("\n"),
            "",
            "文件修改线索：",
            files.ifEmpty { listOf("未识别到文件修改线索") }.joinToString("\n")
        ).joinToString("\n")
    }

    private fun showPortDetails() {
        val ports = listeningPorts().distinct().sorted()
        val body = if (ports.isEmpty()) {
            "当前没有检测到 LISTEN 端口。\n\n如果你启动了 Web 服务，请确认服务监听的是 127.0.0.1 或 0.0.0.0。"
        } else {
            ports.joinToString("\n") { port ->
                "端口 $port    本机：http://127.0.0.1:$port"
            } + "\n\n局域网访问需要服务监听 0.0.0.0，并确保系统没有限制后台网络。"
        }
        AlertDialog.Builder(activity)
            .setTitle("监听端口详情")
            .setMessage(body)
            .setNeutralButton("复制地址") { _, _ ->
                val text = if (ports.isEmpty()) "" else ports.joinToString("\n") { "http://127.0.0.1:$it" }
                if (text.isNotEmpty()) {
                    ClipboardHelper.copy(activity, "AIDev 监听端口", text)
                    Toast.makeText(activity, "已复制端口地址", Toast.LENGTH_SHORT).show()
                }
            }
            .setPositiveButton("终端检查") { _, _ -> host.openTerminal("list-listen-ports") }
            .setNegativeButton("关闭", null)
            .show()
    }
}
