package com.aidev.terminal

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File

/** 工作台内容页：生成滚动卡片，不包含底部导航和背景。 */
class DashboardPage : ShellPage {
    override fun create(activity: Activity, ui: AIDevUi, host: ShellHost): View {
        val scrollContent = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(18), ui.dp(18), ui.dp(18), ui.dp(24))
        }
        scrollContent.addView(hero(activity, ui))
        scrollContent.addView(statusGrid(activity, ui, host))
        currentProject(activity, ui, host)?.let {
            scrollContent.addView(ui.section("当前项目", "围绕已标记项目快速执行常用动作"))
            scrollContent.addView(it)
        }
        scrollContent.addView(ui.section("主要工作区", "进入终端、文件、任务、AI 与服务器模式"))
        scrollContent.addView(actionGrid(activity, ui, host))
        scrollContent.addView(ui.section("开发状态", "移动端 Android 开发工作站的关键状态"))
        scrollContent.addView(devStatus(activity, ui))
        return ScrollView(activity).apply { addView(scrollContent) }
    }

    private fun hero(activity: Activity, ui: AIDevUi): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(18), ui.dp(18), ui.dp(18), ui.dp(18))
            background = ui.card(hero = true)
            addView(TextView(activity).apply {
                text = "AIDev Workstation"
                textSize = 26f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
            })
            addView(ui.heroSubtitle("手机上的 AI Native Linux 开发工作站"))
            addView(ui.heroMeta("终端 · Ubuntu · Android 调试 · AI Agent · 文件 · 长后台服务"))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, ui.dp(14)) }
        }

    private fun statusGrid(activity: Activity, ui: AIDevUi, host: ShellHost): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(ui.rowOf(
                ui.statusCard("Ubuntu", if (ubuntuInstalled(activity)) "已安装" else "未安装", ubuntuInstalled(activity)),
                ui.statusCard("后台常驻", if (keepAliveGood(activity, host)) "建议已开" else "需检查", keepAliveGood(activity, host))
            ))
            addView(ui.rowOf(
                ui.statusCard("任务", "${taskCount(activity)} 个记录", taskCount(activity) > 0),
                ui.statusCard("存储", if (allFilesAllowed()) "完整访问" else "受限", allFilesAllowed())
            ))
        }

    private fun currentProject(activity: Activity, ui: AIDevUi, host: ShellHost): View? {
        val dir = host.prefs.getString("current_project_path", "")?.takeIf { it.isNotBlank() }?.let { File(it) }?.takeIf { it.isDirectory } ?: return null
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = ui.infoPanelBackground()
            setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12))
            addView(ui.text(dir.name, 16f, ui.palette.text, bold = true))
            addView(ui.muted(dir.absolutePath).apply { maxLines = 2 })
            addView(ui.muted(ProjectCommands.detectSummary(dir)).apply { maxLines = 3 })
            addView(ui.rowOf(
                ui.actionCard("打开", "进入项目目录", "PWD") { host.openTerminal("cd \"${dir.absolutePath}\" && pwd && ls -la") },
                ui.actionCard("AI", "启动当前项目代理", "AI") { host.openTerminal("cd \"${dir.absolutePath}\" && opencode") }.apply {
                    setOnLongClickListener {
                        host.openTerminal("cd \"${dir.absolutePath}\" && task-run opencode \"opencode\"")
                        true
                    }
                }
            ))
            addView(ui.rowOf(
                ui.actionCard("测试", "按项目类型执行测试", "TST") { host.openTerminal("cd \"${dir.absolutePath}\" && ${ProjectCommands.testCommand(dir)}") },
                ui.actionCard("更多", "Git、构建、诊断、修复等", "MORE") { currentProjectMore(activity, host, dir) }
            ))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, ui.dp(14)) }
        }
    }

    private fun currentProjectMore(activity: Activity, host: ShellHost, dir: File) {
        val actions = listOf(
            "代码 · Git 状态" to { host.openTerminal("cd \"${dir.absolutePath}\" && git status --short --branch") },
            "代码 · 构建" to { host.openTerminal("cd \"${dir.absolutePath}\" && ${ProjectCommands.buildCommand(dir)}") },
            "代码 · 诊断" to { host.openTerminal("cd \"${dir.absolutePath}\" && ${ProjectCommands.healthCommand(dir)}") },
            "日志 · 任务日志" to { host.openTerminal("ls -lt \"${activity.filesDir.absolutePath}/home/tasks\"/*.log 2>/dev/null | head -20") },
            "维护 · 修复" to { confirmProjectRepair(activity, host, dir) },
            "维护 · 依赖" to { host.openTerminal("cd \"${dir.absolutePath}\" && ${ProjectCommands.installCommand(dir)}") },
            "AI · 后台 AI" to { host.openTerminal("cd \"${dir.absolutePath}\" && task-run opencode \"opencode\"") },
            "AI · 上下文文件" to { host.openTerminal("cd \"${dir.absolutePath}\" && aidev-agent-context-file") },
            "历史 · 最近操作" to { showProjectHistory(activity, host) },
            "系统 · 命令面板" to { host.showCommandPalette() }
        )
        val recent = recentProjectMenuLabels(host).filter { label -> actions.any { it.first == label } }
        val display = recent.map { "最近 · ${it.substringAfter(" · ")}" to it } + actions.filterNot { recent.contains(it.first) }.map { it.first to it.first }
        AlertDialog.Builder(activity)
            .setTitle("当前项目更多")
            .setItems(display.map { it.first }.toTypedArray()) { _, which ->
                val original = display[which].second
                rememberProjectMenuLabel(host, original)
                actions.firstOrNull { it.first == original }?.second?.invoke()
            }
            .setNeutralButton("搜索") { _, _ -> searchCurrentProjectMore(activity, host, actions) }
            .show()
    }

    private fun searchCurrentProjectMore(activity: Activity, host: ShellHost, actions: List<Pair<String, () -> Unit>>) {
        val edit = android.widget.EditText(activity).apply { hint = "输入 git、ai、日志、修复" }
        AlertDialog.Builder(activity)
            .setTitle("搜索当前项目更多")
            .setView(edit)
            .setPositiveButton("搜索") { _, _ ->
                val keyword = edit.text.toString().trim()
                val matches = actions.filter { keyword.isBlank() || it.first.contains(keyword, true) }.take(30)
                if (matches.isEmpty()) return@setPositiveButton
                AlertDialog.Builder(activity)
                    .setTitle("搜索结果")
                    .setItems(matches.map { it.first }.toTypedArray()) { _, which ->
                        rememberProjectMenuLabel(host, matches[which].first)
                        matches[which].second.invoke()
                    }
                    .show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun recentProjectMenuLabels(host: ShellHost): List<String> =
        host.prefs.getString("recent_project_more", "")?.lines()?.filter { it.isNotBlank() }.orEmpty().takeLast(3).reversed()

    private fun rememberProjectMenuLabel(host: ShellHost, label: String) {
        val old = host.prefs.getString("recent_project_more", "")?.lines()?.filter { it.isNotBlank() && it != label }.orEmpty()
        host.prefs.edit().putString("recent_project_more", (old + label).takeLast(6).joinToString("\n")).apply()
    }

    private fun confirmProjectRepair(activity: Activity, host: ShellHost, dir: File) {
        val command = ProjectCommands.repairCommand(dir)
        AlertDialog.Builder(activity)
            .setTitle("确认修复项目")
            .setMessage("项目：${dir.name}\n路径：${dir.absolutePath}\n\n执行：\n$command\n\n注意：某些修复会删除缓存、依赖目录或锁文件。")
            .setPositiveButton("确认执行") { _, _ ->
                rememberProjectAction(host, "修复", dir, command)
                host.openTerminal("cd \"${dir.absolutePath}\" && $command")
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showProjectHistory(activity: Activity, host: ShellHost) {
        val rows = host.prefs.getString("project_action_history", "")?.lines()?.filter { it.isNotBlank() }.orEmpty().takeLast(20).reversed()
        if (rows.isEmpty()) return
        AlertDialog.Builder(activity)
            .setTitle("最近项目操作")
            .setItems(rows.map { row ->
                val parts = row.split("\t", limit = 4)
                "${parts.getOrNull(1) ?: "操作"}\n${parts.getOrNull(3) ?: row}"
            }.toTypedArray()) { _, which ->
                val parts = rows[which].split("\t", limit = 4)
                val path = parts.getOrNull(2).orEmpty()
                val command = parts.getOrNull(3).orEmpty()
                if (path.isNotBlank() && command.isNotBlank()) host.openTerminal("cd \"$path\" && $command")
            }
            .setPositiveButton("复制全部") { _, _ -> copyText(activity, "AIDev 项目历史", rows.joinToString("\n")) }
            .setNegativeButton("清空") { _, _ -> host.prefs.edit().remove("project_action_history").apply() }
            .show()
    }

    private fun copyText(activity: Activity, label: String, text: String) {
        val cm = activity.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText(label, text))
    }

    private fun rememberProjectAction(host: ShellHost, label: String, dir: File, command: String) {
        val line = "${System.currentTimeMillis()}\t$label\t${dir.absolutePath}\t$command"
        val old = host.prefs.getString("project_action_history", "") ?: ""
        val next = (old.lines().filter { it.isNotBlank() } + line).takeLast(20).joinToString("\n")
        host.prefs.edit().putString("project_action_history", next).apply()
    }

    private fun actionGrid(activity: Activity, ui: AIDevUi, host: ShellHost): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(ui.rowOf(
                ui.actionCard("终端", "进入 Linux/Android Shell", "TER") { host.openTerminal("") },
                ui.actionCard("文件", "双列开发文件管理", "FIL") { host.switchTab(ShellActivity.TAB_FILES) }
            ))
            addView(ui.rowOf(
                ui.actionCard("任务", "后台任务与日志", "TSK") { host.switchTab(ShellActivity.TAB_TASKS) },
                ui.actionCard("AI", "OpenCode 与代理中心", "AI") { host.open(AIAgentActivity::class.java) }
            ))
            addView(ui.rowOf(
                ui.actionCard("服务器", "端口、常驻、服务诊断", "SRV") { host.open(ServerCenterActivity::class.java) },
                ui.actionCard("设置", "主题、权限、工具链", "SET") { host.switchTab(ShellActivity.TAB_SETTINGS) }
            ))
        }

    private fun devStatus(activity: Activity, ui: AIDevUi): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = ui.infoPanelBackground()
            setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12))
            addView(infoLine(ui, "Android 调试桥接", "使用 pmx/amx/getpropx/logcatx，避免 Ubuntu 内直接执行 /system/bin 命令"))
            addView(infoLine(ui, "服务访问", "同机浏览器访问 127.0.0.1；局域网访问需要服务监听 0.0.0.0"))
            addView(infoLine(ui, "长后台", "前台服务 + WakeLock + Wi‑Fi Lock；HyperOS 仍需手动设为无限制"))
        }

    private fun infoLine(ui: AIDevUi, title: String, body: String): View =
        ui.text("$title\n$body", 12f, ui.palette.text).apply { setPadding(0, ui.dp(7), 0, ui.dp(7)) }

    private fun ubuntuInstalled(activity: Activity): Boolean = File(activity.filesDir, "home/ubuntu-rootfs/.aidev-rootfs-ready").exists()
    private fun taskCount(activity: Activity): Int = File(activity.filesDir, "home/tasks").listFiles { f -> f.name.endsWith(".meta") }?.size ?: 0
    private fun allFilesAllowed(): Boolean = Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager()
    private fun keepAliveGood(activity: Activity, host: ShellHost): Boolean {
        val auto = host.prefs.getBoolean("keepalive_auto", true)
        if (Build.VERSION.SDK_INT < 23) return auto
        val pm = activity.getSystemService(Context.POWER_SERVICE) as PowerManager
        return auto && pm.isIgnoringBatteryOptimizations(activity.packageName)
    }
}

/** 文件页的 Shell 内容：入口卡片，跳转到 ShellActivity 文件页。 */
class FilesPage : ShellPage {
    override fun create(activity: Activity, ui: AIDevUi, host: ShellHost): View {
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(18), ui.dp(18), ui.dp(18), ui.dp(24))
        }
        container.addView(ui.section("文件", "双列文件管理、项目目录、APK 与日志"))
        container.addView(ui.actionCard("打开双列文件管理器", "在独立场景里进行复制/移动/删除/重命名/安装", "FIL") {
            host.switchTab(ShellActivity.TAB_FILES)
        })
        container.addView(ui.actionCard("项目目录", "进入 Ubuntu 后默认工作区 /root/projects", "DIR") {
            host.openTerminal("ubuntu")
        })
        container.addView(ui.actionCard("下载目录", "查看 /sdcard/Download 内最近的下载文件", "DL") {
            host.openTerminal("ls -al /sdcard/Download")
        })
        return ScrollView(activity).apply { addView(container) }
    }
}

/** 任务页的 Shell 内容：入口卡片，跳转到 ShellActivity 任务页。 */
class TasksPage : ShellPage {
    override fun create(activity: Activity, ui: AIDevUi, host: ShellHost): View {
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(18), ui.dp(18), ui.dp(18), ui.dp(24))
        }
        container.addView(ui.section("任务", "后台任务、长任务日志、服务运行状态"))
        container.addView(ui.actionCard("打开任务中心", "查看运行中任务、PID 和日志", "TSK") {
            host.switchTab(ShellActivity.TAB_TASKS)
        })
        container.addView(ui.actionCard("AI 助手中心", "OpenCode、代理服务和 Web UI", "AI") {
            host.open(AIAgentActivity::class.java)
        })
        container.addView(ui.actionCard("服务器中心", "端口、常驻、本机访问诊断", "SRV") {
            host.open(ServerCenterActivity::class.java)
        })
        return ScrollView(activity).apply { addView(container) }
    }
}

/** 设置页的 Shell 内容：保留入口卡片，详细配置仍在 SettingsActivity 中处理。 */
class SettingsPage : ShellPage {
    override fun create(activity: Activity, ui: AIDevUi, host: ShellHost): View {
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(18), ui.dp(18), ui.dp(18), ui.dp(24))
        }
        container.addView(ui.section("设置", "一级入口保持通用简洁，详细配置进入二级页"))
        container.addView(ui.actionCard("外观与交互", "主题、背景、空间尺寸、触觉反馈", "UX") {
            host.switchTab(ShellActivity.TAB_SETTINGS)
        })
        container.addView(ui.actionCard("打开设置中心", "开发环境、AI、文件、权限、系统高级", "SET") {
            host.open(SettingsActivity::class.java)
        })
        container.addView(ui.actionCard("权限管理", "存储、安装、Shizuku、电池优化", "PERM") {
            host.open(SettingsActivity::class.java)
        })
        return ScrollView(activity).apply { addView(container) }
    }
}
