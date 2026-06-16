package com.aidev.terminal

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import java.io.File

private data class EmbeddedTaskInfo(
    val id: String,
    val name: String,
    val pid: String,
    val started: String,
    val cmd: String,
    val logPath: String,
    val logFile: File
)

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
            setPadding(ui.dp(18), ui.dp(12), ui.dp(18), ui.dp(24))
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
        list.addView(ui.section("任务与服务", "后台任务、AI Agent、Web 服务和最近日志统一放在一个 Shell 内容层"))
        list.addView(ui.rowOf(ui.statusCard("运行中", "${running} 个", running > 0), ui.statusCard("任务记录", "${tasks.size} 个", tasks.isNotEmpty())))
        list.addView(ui.rowOf(
            ui.statusCard("OpenCode", if (opencodeInstalled()) "已检测到" else "未安装", opencodeInstalled()),
            ui.statusCard("AI 配置", if (providerConfigured()) "有配置" else "待配置", providerConfigured())
        ))
        list.addView(ui.rowOf(
            ui.statusCard("监听端口", "${listeningPorts().size} 个", listeningPorts().isNotEmpty()),
            ui.statusCard("电池优化", if (batteryIgnored()) "已忽略" else "受限制", batteryIgnored())
        ))
        list.addView(ui.section("AI 代理运行面板", "围绕 OpenCode 的启动、上下文、日志、端口和复盘集中处理"))
        list.addView(ui.rowOf(
            ui.actionCard("原生 OpenCode", "多会话、Prompt、SSE、TODO、Diff", "NATIVE") {
                com.aidev.terminal.opencode.OpencodeNativePanel.showHome(activity)
            },
            ui.actionCard("新建原生会话", "直接通过 HTTP API 提交任务", "API") {
                com.aidev.terminal.opencode.OpencodeNativePanel.createSessionAndPrompt(activity)
            }
        ))
        list.addView(ui.rowOf(
            ui.statusCard("当前项目", currentProject()?.name ?: "未标记", currentProject() != null),
            ui.statusCard("最近代理日志", recentAgentLog()?.name ?: "暂无", recentAgentLog() != null)
        ))
        currentProject()?.let { project ->
            list.addView(ui.rowOf(
                ui.actionCard("OpenCode 项目", "在当前项目启动 AI 代理", "AI") { host.openTerminal("cd \"${project.absolutePath}\" && opencode") },
                ui.actionCard("后台代理", "后台运行 OpenCode 并记录日志", "BG") { host.openTerminal("cd \"${project.absolutePath}\" && task-run opencode \"opencode\"") }
            ))
            list.addView(ui.rowOf(
                ui.actionCard("代理上下文", "输出项目/文件/Git/任务摘要", "CTX") { host.openTerminal("cd \"${project.absolutePath}\" && aidev-agent-context") },
                ui.actionCard("更多 AI", "日志、诊断、端口和上下文文件", "MORE") { showAgentPanelMore(project) }
            ))
        }
        list.addView(ui.rowOf(
            ui.actionCard("日志摘要", "抽取错误、命令和修改线索", "SUM") { showAgentLogSummary() },
            ui.actionCard("追踪代理", "tail 最近 AI/任务日志", "TAIL") { host.openTerminal("aidev-agent-tail") }
        ))
        list.addView(ui.rowOf(
            ui.actionCard("安装 OpenCode", "在终端执行 install-aitool", "AI") { host.openTerminal("install-aitool") },
            ui.actionCard("端口详情", "直接查看 LISTEN 端口", "PORT") { showPortDetails() }
        ))
        list.addView(ui.rowOf(
            ui.actionCard("任务模板", "常用后台任务一键生成", "TPL") { showTaskTemplates() },
            ui.actionCard("Shizuku 日志", "通过 Shizuku 获取应用日志", "LOG") { showShizukuLogcat() }
        ))
        list.addView(ui.rowOf(
            ui.actionCard("Shizuku 诊断", "测试 Shizuku 权限和基本命令", "TEST") { testShizukuDiagnostics() },
            ui.statusCard("Shizuku 状态", ShizukuLogcat.statusText(), ShizukuLogcat.isAvailable())
        ))
        list.addView(ui.rowOf(
            ui.actionCard("启动常驻", "启动前台服务与 WakeLock", "KEEP") {
                KeepAliveService.start(activity)
                Toast.makeText(activity, "已启动后台常驻", Toast.LENGTH_SHORT).show()
            },
            ui.actionCard("搜索任务", "按名称、命令或 ID 查找", "FIND") { searchTasks(tasks) }
        ))
        list.addView(ui.rowOf(
            ui.actionCard("环境检测", "检查开发与服务环境", "CHECK") { host.openTerminal("check-dev-env") },
            ui.actionCard("刷新列表", "重新读取任务与端口状态", "REF") { reload() }
        ))
        list.addView(ui.section("任务列表", "点击任务查看命令、PID 和最近 80 行日志"))
        if (tasks.isEmpty()) {
            list.addView(emptyCard())
        } else {
            tasks.forEach { list.addView(taskRow(it)) }
        }
    }

    private fun taskDir() = File(activity.filesDir, "home/tasks").apply { mkdirs() }

    private fun showAgentPanelMore(project: File) {
        val actions = listOf(
            "原生 · OpenCode 面板" to { com.aidev.terminal.opencode.OpencodeNativePanel.showHome(activity) },
            "原生 · 新建会话并提问" to { com.aidev.terminal.opencode.OpencodeNativePanel.createSessionAndPrompt(activity) },
            "原生 · 会话列表" to { com.aidev.terminal.opencode.OpencodeNativePanel.showSessions(activity) },
            "原生 · 当前 TODO" to { com.aidev.terminal.opencode.OpencodeNativePanel.showActiveTodo(activity) },
            "原生 · 当前 Diff" to { com.aidev.terminal.opencode.OpencodeNativePanel.showActiveDiff(activity) },
            "原生 · SSE 事件监听" to { com.aidev.terminal.opencode.OpencodeNativePanel.showEventConsole(activity) },
            "项目 · 当前项目目录" to { host.openTerminal("cd \"${project.absolutePath}\" && pwd && ls -la") },
            "项目 · 项目 Git" to { host.openTerminal("cd \"${project.absolutePath}\" && git status --short --branch") },
            "项目 · 项目诊断" to { host.openTerminal(currentProjectTask(ProjectCommands.healthCommand(project))) },
            "日志 · 异常日志" to { searchErrorLogs() },
            "日志 · 代理日志" to { host.openTerminal("aidev-agent-log") },
            "上下文 · 导出上下文" to { host.openTerminal("cd \"${project.absolutePath}\" && aidev-agent-context-file") },
            "检查 · 启动检查" to { host.openTerminal("cd \"${project.absolutePath}\" && opencode --help") },
            "日志 · 日志摘要" to { showAgentLogSummary() },
            "日志 · 追踪代理" to { host.openTerminal("aidev-agent-tail") },
            "系统 · 端口详情" to { showPortDetails() },
            "安装 · 安装 OpenCode" to { host.openTerminal("install-aitool") },
            "任务 · 任务模板" to { showTaskTemplates() }
        )
        val recent = recentTaskMenuLabels().filter { label -> actions.any { it.first == label } }
        val display = recent.map { "最近 · ${it.substringAfter(" · ")}" to it } + actions.filterNot { recent.contains(it.first) }.map { it.first to it.first }
        AlertDialog.Builder(activity)
            .setTitle("AI 代理更多")
            .setItems(display.map { it.first }.toTypedArray()) { _, which ->
                val original = display[which].second
                rememberTaskMenuLabel(original)
                actions.firstOrNull { it.first == original }?.second?.invoke()
            }
            .setNeutralButton("搜索") { _, _ -> searchTaskMoreMenu(actions) }
            .show()
    }

    private fun searchTaskMoreMenu(actions: List<Pair<String, () -> Unit>>) {
        val edit = EditText(activity).apply { hint = "输入 日志、项目、上下文、端口" }
        AlertDialog.Builder(activity)
            .setTitle("搜索 AI 更多")
            .setView(edit)
            .setPositiveButton("搜索") { _, _ ->
                val keyword = edit.text.toString().trim()
                val matches = actions.filter { keyword.isBlank() || it.first.contains(keyword, true) }.take(30)
                if (matches.isEmpty()) return@setPositiveButton Toast.makeText(activity, "没有匹配项", Toast.LENGTH_SHORT).show()
                AlertDialog.Builder(activity)
                    .setTitle("搜索结果")
                    .setItems(matches.map { it.first }.toTypedArray()) { _, which ->
                        rememberTaskMenuLabel(matches[which].first)
                        matches[which].second.invoke()
                    }
                    .show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun recentTaskMenuLabels(): List<String> =
        activity.getSharedPreferences("aidev_ui", Activity.MODE_PRIVATE).getString("recent_task_more", "")?.lines()?.filter { it.isNotBlank() }.orEmpty().takeLast(3).reversed()

    private fun rememberTaskMenuLabel(label: String) {
        val prefs = activity.getSharedPreferences("aidev_ui", Activity.MODE_PRIVATE)
        val old = prefs.getString("recent_task_more", "")?.lines()?.filter { it.isNotBlank() && it != label }.orEmpty()
        prefs.edit().putString("recent_task_more", (old + label).takeLast(6).joinToString("\n")).apply()
    }

    private fun currentProject(): File? =
        activity.getSharedPreferences("aidev_ui", Activity.MODE_PRIVATE)
            .getString("current_project_path", "")
            ?.takeIf { it.isNotBlank() }
            ?.let { File(it) }
            ?.takeIf { it.isDirectory }

    private fun opencodeInstalled(): Boolean =
        File(activity.filesDir, "home/ubuntu-rootfs/root/.opencode/bin/opencode").exists() ||
            File(activity.filesDir, "home/.opencode/bin/opencode").exists() ||
            File(activity.filesDir, "home/ubuntu-rootfs/usr/local/bin/opencode").exists()

    private fun providerConfigured(): Boolean =
        File(activity.filesDir, "home/ubuntu-rootfs/root/.config/opencode").exists() ||
            File(activity.filesDir, "home/ubuntu-rootfs/root/.opencode").exists() ||
            File(activity.filesDir, "home/.config/opencode").exists()

    private fun recentAgentLog(): File? =
        taskDir().listFiles { f -> f.name.endsWith(".log") }
            ?.sortedByDescending { it.lastModified() }
            ?.firstOrNull { it.name.contains("opencode", true) || runCatching { it.readText().contains("opencode", true) }.getOrDefault(false) }
            ?: taskDir().listFiles { f -> f.name.endsWith(".log") }?.maxByOrNull { it.lastModified() }

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
                copyText("AIDev 代理日志摘要", summary)
                Toast.makeText(activity, "已复制摘要", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("追踪日志") { _, _ -> host.openTerminal("tail -f \"${log.absolutePath}\"") }
            .setNegativeButton("关闭", null)
            .show()
    }

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

    private fun batteryIgnored(): Boolean {
        if (Build.VERSION.SDK_INT < 23) return true
        val pm = activity.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return pm?.isIgnoringBatteryOptimizations(activity.packageName) ?: false
    }

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

    private fun showShizukuLogcat() {
        if (!ShizukuLogcat.isAvailable()) {
            AlertDialog.Builder(activity)
                .setTitle("Shizuku 日志")
                .setMessage("Shizuku 不可用：${ShizukuLogcat.statusText()}\n\n请确保：\n1. 已安装 Shizuku 应用\n2. Shizuku 服务已启动\n3. 已授权 AIDev 使用 Shizuku")
                .setPositiveButton("打开 Shizuku") { _, _ ->
                    val intent = activity.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                    if (intent != null) activity.startActivity(intent)
                }
                .setNegativeButton("关闭", null)
                .show()
            return
        }

        val packages = arrayOf(
            "com.aidev.terminal" to "AIDev Terminal",
            "moe.shizuku.privileged.api" to "Shizuku",
            "android" to "系统"
        )
        val packageNames = packages.map { it.second }.toTypedArray()

        AlertDialog.Builder(activity)
            .setTitle("选择要查看日志的应用")
            .setItems(packageNames) { _, which ->
                val (pkg, label) = packages[which]
                fetchAndShowLog(pkg, label)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun fetchAndShowLog(packageName: String, label: String) {
        val dialog = AlertDialog.Builder(activity)
            .setTitle("正在获取 $label 日志...")
            .setMessage("通过 Shizuku 执行 logcat，请稍候")
            .setCancelable(false)
            .show()

        ShizukuLogcat.fetchLog(packageName = packageName, lines = 500) { result ->
            activity.runOnUiThread {
                dialog.dismiss()
                result.onSuccess { logs ->
                    AlertDialog.Builder(activity)
                        .setTitle("$label 日志 (最近 500 行)")
                        .setMessage(if (logs.length > 3000) logs.takeLast(3000) else logs)
                        .setNeutralButton("复制") { _, _ ->
                            copyText("$label logcat", logs)
                            Toast.makeText(activity, "已复制日志", Toast.LENGTH_SHORT).show()
                        }
                        .setPositiveButton("终端查看") { _, _ ->
                            host.openTerminal("logcat -d --pid=\$(pidof $packageName) -v threadtime | tail -200")
                        }
                        .setNegativeButton("关闭", null)
                        .show()
                }.onFailure { e ->
                    AlertDialog.Builder(activity)
                        .setTitle("获取日志失败")
                        .setMessage(e.message ?: "未知错误")
                        .setPositiveButton("重试") { _, _ -> fetchAndShowLog(packageName, label) }
                        .setNegativeButton("关闭", null)
                        .show()
                }
            }
        }
    }

    private fun testShizukuDiagnostics() {
        if (!ShizukuLogcat.isAvailable()) {
            AlertDialog.Builder(activity)
                .setTitle("Shizuku 诊断")
                .setMessage("Shizuku 不可用：${ShizukuLogcat.statusText()}\n\n请先安装并启动 Shizuku，然后授权 AIDev。")
                .setPositiveButton("打开 Shizuku") { _, _ ->
                    val intent = activity.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                    if (intent != null) activity.startActivity(intent)
                }
                .setNegativeButton("关闭", null)
                .show()
            return
        }

        val dialog = AlertDialog.Builder(activity)
            .setTitle("Shizuku 诊断")
            .setMessage("正在测试 Shizuku 权限...")
            .setCancelable(false)
            .show()

        // 测试 1: 执行 whoami
        ShizukuLogcat.fetchLog(packageName = "", lines = 1, filters = listOf("*S")) { _ -> }
        // 用 echo 测试基本命令执行
        val testCmd = "echo 'shizuku_test_ok' && id && echo '---' && ps -A | grep shizuku | head -1"
        Thread {
            try {
                val method = rikka.shizuku.Shizuku::class.java.getDeclaredMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java
                ).apply { isAccessible = true }
                val process = method.invoke(null, arrayOf("sh", "-c", testCmd), null, null) as? java.lang.Process
                val output = process?.inputStream?.bufferedReader()?.use { it.readText() } ?: "无输出"
                val exit = process?.waitFor() ?: -1

                activity.runOnUiThread {
                    dialog.dismiss()
                    AlertDialog.Builder(activity)
                        .setTitle("Shizuku 诊断结果")
                        .setMessage("命令执行测试：\n$output\n\n退出码：$exit\n\n说明：\n- 如果显示 shell 用户（uid=2000），Shizuku 拥有 adb 权限\n- 如果显示 root（uid=0），Shizuku 拥有 root 权限\n- 如果失败，请检查 Shizuku 是否已正确启动")
                        .setPositiveButton("测试 logcat") { _, _ -> showShizukuLogcat() }
                        .setNegativeButton("关闭", null)
                        .show()
                }
            } catch (e: Exception) {
                activity.runOnUiThread {
                    dialog.dismiss()
                    AlertDialog.Builder(activity)
                        .setTitle("Shizuku 诊断失败")
                        .setMessage("错误：${e.message}\n\n可能原因：\n1. Shizuku 未正确启动\n2. AIDev 未获得 Shizuku 授权\n3. Shizuku API 版本不兼容")
                        .setNegativeButton("关闭", null)
                        .show()
                }
            }
        }.apply { isDaemon = true }.start()
    }

    private fun showPortDetails() {
        val ports = listeningPorts().distinct().sorted()
        val body = if (ports.isEmpty()) {
            "当前没有检测到 LISTEN 端口。\n\n如果你启动了 Web 服务，请确认服务监听的是 127.0.0.1 或 0.0.0.0。"
        } else {
            ports.joinToString("\n") { port ->
                "端口 $port    本机：http://127.0.0.1:$port"
            } + "\n\n局域网访问需要服务监听 0.0.0.0，并确保 HyperOS 没有限制后台网络。"
        }
        AlertDialog.Builder(activity)
            .setTitle("监听端口详情")
            .setMessage(body)
            .setNeutralButton("复制地址") { _, _ ->
                val text = if (ports.isEmpty()) "" else ports.joinToString("\n") { "http://127.0.0.1:$it" }
                if (text.isNotEmpty()) {
                    copyText("AIDev 监听端口", text)
                    Toast.makeText(activity, "已复制端口地址", Toast.LENGTH_SHORT).show()
                }
            }
            .setPositiveButton("终端检查") { _, _ -> host.openTerminal("list-listen-ports") }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun showTaskTemplates() {
        val names = arrayOf(
            "OpenCode 服务",
            "Python HTTP 8000",
            "Node Dev",
            "Gradle Debug 构建",
            "Logcat 记录",
            "当前端口检查",
            "Git 状态",
            "Python 测试",
            "Node 测试",
            "Go 测试",
            "当前项目 Git",
            "当前项目测试",
            "当前项目构建",
            "当前项目诊断",
            "当前项目修复",
            "当前项目 OpenCode",
            "当前项目后台代理",
            "当前项目代理上下文",
            "AI 代理日志"
        )
        val commands = arrayOf(
            "task-run opencode 'opencode serve'",
            "task-run pyserver 'python3 -m http.server 8000'",
            "task-run npm-dev 'npm run dev'",
            "task-run gradle './gradlew assembleDebug'",
            "task-run logcat 'logcat'",
            "list-listen-ports",
            "task-run git-status 'git status --short --branch'",
            "task-run pytest 'python3 -m pytest'",
            "task-run npm-test 'npm test'",
            "task-run go-test 'go test ./...'",
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
            .setItems(names) { _, which ->
                executeTaskTemplate(names[which], commands[which])
            }
            .setNeutralButton("搜索") { _, _ -> searchTaskTemplates(names, commands) }
            .show()
    }

    private fun searchTaskTemplates(names: Array<String>, commands: Array<String>) {
        val edit = EditText(activity).apply { hint = "输入 git、test、build、fix、log、port" }
        AlertDialog.Builder(activity)
            .setTitle("搜索任务模板")
            .setView(edit)
            .setPositiveButton("搜索") { _, _ ->
                val keyword = edit.text.toString().trim()
                val matches = names.mapIndexed { i, name -> i to name }
                    .filter { keyword.isBlank() || templateMatches(it.second, commands[it.first], keyword) }
                    .take(30)
                if (matches.isEmpty()) {
                    Toast.makeText(activity, "没有匹配模板", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                AlertDialog.Builder(activity)
                    .setTitle("模板结果")
                    .setItems(matches.map { "${it.second}\n${commands[it.first]}" }.toTypedArray()) { _, which ->
                        val index = matches[which].first
                        executeTaskTemplate(names[index], commands[index])
                    }
                    .show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun templateMatches(name: String, command: String, keyword: String): Boolean {
        if (name.contains(keyword, true) || command.contains(keyword, true)) return true
        return when (keyword.lowercase()) {
            "test", "pytest" -> name.contains("测试")
            "build" -> name.contains("构建")
            "fix", "repair" -> name.contains("修复")
            "log" -> name.contains("日志") || name.contains("Logcat", true)
            "ai", "agent", "opencode" -> name.contains("AI", true) || name.contains("OpenCode", true) || name.contains("代理")
            "port" -> name.contains("端口")
            else -> false
        }
    }

    private fun executeTaskTemplate(name: String, command: String) {
        if (name.contains("修复")) {
            confirmTaskRepair(command)
        } else {
            try {
                host.openTerminal(command)
            } catch (e: Exception) {
                Toast.makeText(activity, "任务启动失败：${e.message ?: "未知错误"}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun confirmTaskRepair(command: String) {
        AlertDialog.Builder(activity)
            .setTitle("确认执行修复模板")
            .setMessage("将执行：\n$command\n\n注意：修复模板可能删除缓存、依赖目录或锁文件。")
            .setPositiveButton("确认执行") { _, _ -> host.openTerminal(command) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun currentProjectTask(command: String): String {
        val path = activity.getSharedPreferences("aidev_ui", Activity.MODE_PRIVATE).getString("current_project_path", "").orEmpty()
        return if (path.isBlank()) "pwd && $command" else "cd \"$path\" && $command"
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

    private fun errorSnippet(file: File): String {
        val lines = runCatching { file.readLines().takeLast(300) }.getOrDefault(emptyList())
        val index = lines.indexOfLast { it.contains("error", true) || it.contains("failed", true) || it.contains("exception", true) }
        if (index < 0) return ""
        val start = (index - 3).coerceAtLeast(0)
        val end = (index + 4).coerceAtMost(lines.size)
        return lines.subList(start, end).joinToString("\n")
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
            .setNegativeButton("项目诊断") { _, _ ->
                currentProject()?.let { host.openTerminal(currentProjectTask(ProjectCommands.healthCommand(it))) } ?: Toast.makeText(activity, "未标记当前项目", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun searchTasks(tasks: List<EmbeddedTaskInfo>) {
        val edit = EditText(activity).apply { setHint("输入任务名、命令、PID 或 ID") }
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

    private fun parseTask(meta: File): EmbeddedTaskInfo? {
        val map = meta.readLines().mapNotNull {
            val i = it.indexOf('=')
            if (i <= 0) null else it.substring(0, i) to it.substring(i + 1)
        }.toMap()
        val id = map["id"] ?: meta.name.removeSuffix(".meta")
        val log = map["log"] ?: File(taskDir(), "$id.log").absolutePath
        return EmbeddedTaskInfo(id, map["name"] ?: "", map["pid"] ?: "", map["started"] ?: "", map["cmd"] ?: "", log, File(log))
    }

    private fun taskRow(task: EmbeddedTaskInfo): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12))
            background = ui.subtleCommandButtonBackground()
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, ui.dp(8)) }
            val running = isRunning(task.pid)
            addView(ui.text("${task.id}  ${if (running) "运行中" else "已结束"}", 13f, if (running) ui.palette.primary else ui.palette.text, bold = true))
            addView(ui.muted("PID：${task.pid.ifBlank { "-" }}    开始：${task.started.ifBlank { "-" }}"))
            addView(ui.muted("命令：${task.cmd.ifBlank { "-" }}").apply { maxLines = 2 })
            setOnClickListener { showTask(task) }
        }

    private fun emptyCard(): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12))
            background = ui.infoPanelBackground()
            addView(ui.text("暂无后台任务", 16f, ui.palette.text, bold = true))
            addView(ui.muted("可以在终端中运行：task-run opencode 'opencode serve'"))
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
