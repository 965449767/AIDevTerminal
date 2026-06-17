package com.aidev.terminal

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Toast

import java.io.File

class EmbeddedSettingsPage : ShellPage {
    private lateinit var activity: Activity
    private lateinit var ui: AIDevUi
    private lateinit var host: ShellHost

    override fun create(activity: Activity, ui: AIDevUi, host: ShellHost): View {
        this.activity = activity
        this.ui = ui
        this.host = host
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(18), ui.dp(12), ui.dp(18), ui.dp(24))
        }
        content.addView(ui.section("设置", "一级入口保持通用，二级动作以内嵌菜单展开，底部导航不离开 Shell"))
        content.addView(row("外观与交互", "主题、背景、透明度、模糊说明、触觉反馈") { appearanceMenu() })
        content.addView(row("终端设置", "字号、快捷键、会话行为和终端说明") { terminalMenu() })
        content.addView(row("Shell 增强", "命令历史统计、别名管理、收藏夹与模板") { openShellEnhancements() })
        content.addView(row("开发环境", "全面检测环境状态，一键修复问题") { devMenu() })
        content.addView(row("AI 与服务器", "安装 OpenCode、后台常驻、端口诊断") { aiServerMenu() })
        content.addView(row("文件与权限", "存储访问、安装权限、Shizuku、应用详情") { permissionMenu() })
        content.addView(row("数据备份", "备份和恢复 Ubuntu 环境、任务数据、设置和项目文件") { backupRestoreMenu() })
        content.addView(ui.section("当前效果说明", ui.effectNotice()))
        return ScrollView(activity).apply { addView(content) }
    }

    private fun row(title: String, desc: String, click: () -> Unit): View =
        ui.actionRow(title, desc) { click() }.apply {
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, ui.dp(8)) }
        }

    private fun appearanceMenu() {
        val items = listOf(
            MenuBottomSheet.MenuItem("主题预设", "切换深色/浅色/跟随系统") { themePresetDialog() },
            MenuBottomSheet.MenuItem("背景模式", "纯色/渐变/自定义图片") { backgroundModeDialog() },
            MenuBottomSheet.MenuItem("透明度", "调整界面透明度") { sliderDialog("透明度", "ui_alpha", 70, 100, 94, "%") },
            MenuBottomSheet.MenuItem("模糊感", "调整背景模糊程度") { sliderDialog("模糊感", "ui_blur", 0, 40, 18, "") },
            MenuBottomSheet.MenuItem("空间密度", "调整界面元素密度") { sliderDialog("空间密度", "ui_density", 86, 116, 100, "%") },
            MenuBottomSheet.MenuItem("触觉反馈", "开启或关闭点击振动反馈") {
                val prefs = activity.getSharedPreferences("aidev_ui", Activity.MODE_PRIVATE)
                val next = !prefs.getBoolean("haptic_tap", true)
                prefs.edit().putBoolean("haptic_tap", next).apply()
                toast(if (next) "触觉反馈已开启" else "触觉反馈已关闭")
            },
            MenuBottomSheet.MenuItem("查看效果说明", "当前主题、背景、透明度等状态") { detail("效果说明", ui.effectNotice()) }
        )
        MenuBottomSheet(activity, ui).show("外观与交互", items)
    }

    private fun themePresetDialog() {
        val prefs = activity.getSharedPreferences("aidev_ui", Activity.MODE_PRIVATE)
        val labels = arrayOf("深色", "亮色", "跟随系统")
        val values = arrayOf("dark", "light", "system")
        val checked = values.indexOf(prefs.getString("theme_preset", "system")).coerceAtLeast(0)
        AlertDialog.Builder(activity)
            .setTitle("主题预设")
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                prefs.edit().putString("theme_preset", values[which]).apply()
                host.refreshShellSkin()
                toast("主题已切换为 ${labels[which]}")
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun backgroundModeDialog() {
        val prefs = activity.getSharedPreferences("aidev_ui", Activity.MODE_PRIVATE)
        val labels = arrayOf("纯色背景", "主题渐变", "自定义图片")
        val values = arrayOf("solid", "gradient", "image")
        val checked = values.indexOf(prefs.getString("bg_mode", "solid")).coerceAtLeast(0)
        AlertDialog.Builder(activity)
            .setTitle("背景模式")
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                prefs.edit().putString("bg_mode", values[which]).apply()
                host.refreshShellSkin()
                if (values[which] == "image") {
                    host.pickBackgroundImage()
                } else {
                    toast("背景已切换为 ${labels[which]}")
                }
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun sliderDialog(title: String, key: String, min: Int, max: Int, defaultValue: Int, suffix: String) {
        val prefs = activity.getSharedPreferences("aidev_ui", Activity.MODE_PRIVATE)
        val current = prefs.getInt(key, defaultValue).coerceIn(min, max)
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(20), ui.dp(10), ui.dp(20), 0)
        }
        val value = ui.text("$current$suffix", 18f, ui.palette.text, bold = true)
        val seek = SeekBar(activity).apply {
            this.max = max - min
            progress = current - min
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    value.text = "${min + progress}$suffix"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        box.addView(value)
        box.addView(seek)
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setView(box)
            .setPositiveButton("应用") { _, _ ->
                prefs.edit().putInt(key, min + seek.progress).apply()
                host.refreshShellSkin()
                toast("$title 已更新")
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun terminalMenu() {
        val items = listOf(
            MenuBottomSheet.MenuItem("终端字号", "调整终端文字大小") { terminalFontDialog() },
            MenuBottomSheet.MenuItem("新增快捷键", "添加自定义虚拟按键") { customKeyDialog() },
            MenuBottomSheet.MenuItem("管理快捷键", "查看或删除已有快捷键") { manageCustomKeys() },
            MenuBottomSheet.MenuItem("清除快捷键", "一键清空所有自定义快捷键") {
                activity.getSharedPreferences("aidev_ui", Activity.MODE_PRIVATE).edit()
                    .remove("terminal_custom_keys")
                    .remove("terminal_key_overrides")
                    .apply()
                toast("已清除自定义快捷键")
            },
            MenuBottomSheet.MenuItem("快捷键说明", "虚拟按键布局与自定义规则") { detail("快捷键说明", "内嵌终端保持两行六列：点击是主功能，上滑是拓展功能，长按可自定义单个键。\n\n例如 C 点击输入 c，上滑 clear；SPC 点击空格，上滑 pwd。\n\n自定义输入支持 \\n、\\t 和 \\e 转义。") },
            MenuBottomSheet.MenuItem("会话说明", "多标签会话管理与操作说明") { detail("会话说明", "终端 Tab 支持多会话标签、新建会话、关闭当前会话、点击标签切换、长按标签重命名。关闭最后一个会话时会自动创建新会话。") }
        )
        MenuBottomSheet(activity, ui).show("终端设置", items)
    }

    private fun customKeyDialog() {
        val prefs = activity.getSharedPreferences("aidev_ui", Activity.MODE_PRIVATE)
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(20), ui.dp(10), ui.dp(20), 0)
        }
        val label = EditText(activity).apply {
            hint = "按钮名称，例如 npm"
        }
        val input = EditText(activity).apply {
            hint = "输入内容，例如 npm run dev\\n"
        }
        val swipe = EditText(activity).apply {
            hint = "上滑命令，可选，例如 pwd"
        }
        box.addView(label)
        box.addView(input)
        box.addView(swipe)
        AlertDialog.Builder(activity)
            .setTitle("自定义快捷键")
            .setView(box)
            .setPositiveButton("保存") { _, _ ->
                val name = label.text.toString().trim().take(8)
                val value = input.text.toString()
                if (name.isNotEmpty() && value.isNotEmpty()) {
                    val old = prefs.getString("terminal_custom_keys", "") ?: ""
                    val lines = old.lines().filter { it.isNotBlank() }.toMutableList()
                    lines.add("$name\t$value\t${swipe.text}")
                    prefs.edit().putString("terminal_custom_keys", lines.takeLast(8).joinToString("\n")).apply()
                    toast("已保存，重新进入终端页后显示")
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun manageCustomKeys() {
        val prefs = activity.getSharedPreferences("aidev_ui", Activity.MODE_PRIVATE)
        val lines = (prefs.getString("terminal_custom_keys", "") ?: "").lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return toast("暂无自定义快捷键")
        val labels = lines.map { it.substringBefore("\t").ifBlank { "未命名" } }.toTypedArray()
        AlertDialog.Builder(activity)
            .setTitle("管理快捷键")
            .setItems(labels) { _, which ->
                AlertDialog.Builder(activity)
                    .setTitle(labels[which])
                    .setMessage(lines[which].split("\t").let { parts ->
                        "点击：${parts.getOrNull(1).orEmpty()}\n上滑：${parts.getOrNull(2).orEmpty().ifBlank { "未设置" }}"
                    })
                    .setPositiveButton("删除") { _, _ ->
                        val next = lines.toMutableList().also { it.removeAt(which) }
                        prefs.edit().putString("terminal_custom_keys", next.joinToString("\n")).apply()
                        toast("已删除")
                    }
                    .setNegativeButton("关闭", null)
                    .show()
            }
            .show()
    }

    private fun terminalFontDialog() {
        val prefs = activity.getSharedPreferences("aidev_ui", Activity.MODE_PRIVATE)
        val current = prefs.getFloat("font_sp", 15f).toInt().coerceIn(10, 24)
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(20), ui.dp(10), ui.dp(20), 0)
        }
        val value = ui.text("${current}sp", 18f, ui.palette.text, bold = true)
        val seek = SeekBar(activity).apply {
            max = 14
            progress = current - 10
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    value.text = "${10 + progress}sp"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        box.addView(value)
        box.addView(seek)
        AlertDialog.Builder(activity)
            .setTitle("终端字号")
            .setView(box)
            .setPositiveButton("应用") { _, _ ->
                prefs.edit().putFloat("font_sp", (10 + seek.progress).toFloat()).apply()
                toast("终端字号已更新，终端页可用字号按钮立即刷新")
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun openShellEnhancements() {
        val page = ShellEnhancementsPage()
        val view = page.create(activity, ui, host)
        AlertDialog.Builder(activity)
            .setTitle("Shell 增强")
            .setView(view)
            .setNegativeButton("关闭") { _, _ -> page.onSelected(activity, view) }
            .show()
    }

    private fun devMenu() {
        val items = listOf(
            MenuBottomSheet.MenuItem("环境检查与修复", "全面检测 Ubuntu 与开发工具状态") { devCheckAndRepair() },
            MenuBottomSheet.MenuItem("网络诊断", "检测端口监听与网络连通性") { openNetworkDiagnostics() },
            MenuBottomSheet.MenuItem("系统监控", "实时查看 CPU、内存与进程状态") { openSystemMonitor() },
            MenuBottomSheet.MenuItem("安全审计", "检查权限、密钥与容器安全") { openSecurityAudit() },
            MenuBottomSheet.MenuItem("容器管理", "管理 Docker/Podman 容器生命周期") { openContainerManager() }
        )
        MenuBottomSheet(activity, ui).show("开发环境", items)
    }

    private fun openNetworkDiagnostics() {
        val page = NetworkDiagnosticsPage()
        val view = page.create(activity, ui, host)
        AlertDialog.Builder(activity)
            .setTitle("网络诊断")
            .setView(view)
            .setNegativeButton("关闭") { _, _ -> page.onSelected(activity, view) }
            .show()
    }

    private fun openSystemMonitor() {
        val page = SystemMonitorPage()
        val view = page.create(activity, ui, host)
        AlertDialog.Builder(activity)
            .setTitle("系统监控")
            .setView(view)
            .setNegativeButton("关闭") { _, _ -> page.onDestroy(activity) }
            .show()
        // 页面可见，启动自动刷新
        page.onSelected(activity, view)
    }

    private fun openSecurityAudit() {
        val page = SecurityAuditPage()
        val view = page.create(activity, ui, host)
        AlertDialog.Builder(activity)
            .setTitle("安全审计")
            .setView(view)
            .setNegativeButton("关闭") { _, _ -> page.onSelected(activity, view) }
            .show()
    }

    private fun openContainerManager() {
        val page = ContainerManagerPage()
        val view = page.create(activity, ui, host)
        AlertDialog.Builder(activity)
            .setTitle("容器管理")
            .setView(view)
            .setNegativeButton("关闭") { _, _ -> page.onSelected(activity, view) }
            .show()
    }

    private fun devCheckAndRepair() {
        val home = File(activity.filesDir, "home")
        val rootfs = File(home, "ubuntu-rootfs")
        val checks = mutableListOf<CheckItem>()

        // Android 系统权限（直接修复，不走终端）
        val storageOk = if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager() else true
        checks.add(CheckItem("存储权限", storageOk, "读取下载目录、项目目录和 APK", if (!storageOk) "action:storage" else null))
        val batteryOk = if (Build.VERSION.SDK_INT < 23) true else (activity.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isIgnoringBatteryOptimizations(activity.packageName) ?: false
        checks.add(CheckItem("电池优化白名单", batteryOk, "后台任务与长时间服务更稳定", if (!batteryOk) "action:battery" else null))

        // Ubuntu 环境
        checks.add(CheckItem("AIDev Home", home.exists(), home.absolutePath, null))
        checks.add(CheckItem("Ubuntu rootfs", rootfs.exists(), rootfs.absolutePath, null))
        val prootOk = File(home, "proot-lib/libtalloc.so.2").exists()
        checks.add(CheckItem("PRoot 依赖", prootOk, "终端 Ubuntu 入口依赖", null))

        // 开发工具（在 rootfs 内检测）
        val devTools = listOf(
            "node" to "Node.js",
            "python3" to "Python3",
            "git" to "Git",
            "java" to "JDK",
            "npm" to "npm"
        )
        var hasBaseMissing = false
        for ((cmd, label) in devTools) {
            val binPaths = listOf(
                File(rootfs, "usr/bin/$cmd"),
                File(rootfs, "usr/local/bin/$cmd"),
                File(rootfs, "root/.opencode/bin/$cmd"),
                File(rootfs, "bin/$cmd")
            )
            // Java 特殊处理：也检查 /usr/lib/jvm/ 目录
            val exists = if (cmd == "java") {
                binPaths.any { it.exists() } || File(rootfs, "usr/lib/jvm").listFiles()?.any { it.isDirectory } == true
            } else {
                binPaths.any { it.exists() }
            }
            if (!exists) hasBaseMissing = true
            checks.add(CheckItem(label, exists, cmd, null))
        }
        // 基础工具统一修复（deploy-dev-env 安装所有基础包）
        if (hasBaseMissing) {
            checks.add(CheckItem("基础开发工具包", false, "Node.js/Python3/Git/JDK/npm", "deploy-dev-env"))
        } else {
            checks.add(CheckItem("基础开发工具包", true, "Node.js/Python3/Git/JDK/npm", null))
        }

        // 可选工具（不显示在基础包中）
        val optionalTools = listOf(
            "opencode" to "OpenCode",
            "gradle" to "Gradle",
            "go" to "Go",
            "cargo" to "Rust/Cargo"
        )
        for ((cmd, label) in optionalTools) {
            val binPaths = listOf(
                File(rootfs, "usr/bin/$cmd"),
                File(rootfs, "usr/local/bin/$cmd"),
                File(rootfs, "root/.opencode/bin/$cmd"),
                File(rootfs, "bin/$cmd")
            )
            val exists = binPaths.any { it.exists() }
            val fixCmd = when (cmd) {
                "opencode" -> "install-aitool"
                else -> null
            }
            checks.add(CheckItem(label, exists, cmd, fixCmd))
        }

        // Shizuku
        val shizukuInstalled = runCatching { activity.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0) }.isSuccess
        checks.add(CheckItem("Shizuku 应用", shizukuInstalled, "高权限操作支持", null))

        // 构建显示内容
        val failedChecks = checks.filter { !it.ok }
        val allOk = failedChecks.isEmpty()

        val body = StringBuilder()
        body.append(if (allOk) "所有环境检查通过 ✓\n\n" else "发现 ${failedChecks.size} 个问题：\n\n")
        for (item in checks) {
            body.append("${if (item.ok) "✓" else "✗"} ${item.name}：${if (item.ok) "正常" else "未安装"}\n")
            if (!item.ok && item.fixAction != null) {
                body.append("  → 可修复\n")
            }
        }

        if (allOk) {
            AlertDialog.Builder(activity)
                .setTitle("开发环境检查")
                .setMessage(body.toString())
                .setPositiveButton("关闭", null)
                .show()
        } else {
            val fixLabels = failedChecks.filter { it.fixAction != null }.map { it.name }.toTypedArray()
            val fixActions = failedChecks.filter { it.fixAction != null }.mapNotNull { it.fixAction }.toTypedArray()
            AlertDialog.Builder(activity)
                .setTitle("开发环境检查")
                .setMessage(body.toString())
                .setPositiveButton(if (fixLabels.isNotEmpty()) "一键修复" else "关闭") { _, _ ->
                    if (fixLabels.isEmpty()) return@setPositiveButton
                    AlertDialog.Builder(activity)
                        .setTitle("选择要修复的项目")
                        .setMultiChoiceItems(fixLabels, null) { _, _, _ -> }
                        .setPositiveButton("执行修复") { dialog, _ ->
                            val selected = (dialog as? AlertDialog)?.listView?.checkedItemPositions
                            val cmds = mutableListOf<String>()
                            for (i in 0 until fixActions.size) {
                                if (selected?.get(i, false) == true) {
                                    when (fixActions[i]) {
                                        "action:storage" -> openStorageSettings()
                                        "action:battery" -> {
                                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                                data = Uri.parse("package:${activity.packageName}")
                                            }
                                            activity.startActivity(intent)
                                        }
                                        else -> cmds.add(fixActions[i])
                                    }
                                }
                            }
                            if (cmds.isNotEmpty()) {
                                host.openTerminal(cmds.joinToString(" && "))
                            }
                        }
                        .setNegativeButton("取消", null)
                        .show()
                }
                .setNeutralButton("终端详细检测") { _, _ ->
                    if (!rootfs.exists()) {
                        toast("Ubuntu 环境尚未初始化，请先进入终端")
                    } else {
                        host.openTerminal("check-dev-env")
                    }
                }
                .setNegativeButton("关闭", null)
                .show()
        }
    }

    private data class CheckItem(val name: String, val ok: Boolean, val desc: String, val fixAction: String?)

    private fun aiServerMenu() {
        val items = listOf(
            MenuBottomSheet.MenuItem("安装 OpenCode", "在 Ubuntu 环境中安装 AI 编程助手") { host.openTerminal("install-aitool") },
            MenuBottomSheet.MenuItem("监听端口", "查看当前所有监听中的网络端口") { host.openTerminal("list-listen-ports") },
            MenuBottomSheet.MenuItem("后台常驻", "启动保活服务防止进程被系统回收") {
                KeepAliveService.start(activity)
                activity.getSharedPreferences("aidev_ui", Activity.MODE_PRIVATE).edit().putBoolean("keepalive_auto", true).apply()
                toast("后台常驻已启动")
            }
        )
        MenuBottomSheet(activity, ui).show("AI 与服务器", items)
    }

    private fun permissionMenu() {
        val items = listOf(
            MenuBottomSheet.MenuItem("存储权限", "管理所有文件访问权限") { openStorageSettings() },
            MenuBottomSheet.MenuItem("应用详情", "跳转到系统应用信息页") { openAppSettings() },
            MenuBottomSheet.MenuItem("Shizuku 状态", "查看 Shizuku 服务运行状态") { detail("Shizuku 状态", "如果 Shizuku 未运行，请先打开 Shizuku 应用并启动服务。应用已声明 Shizuku Provider，用于后续更高权限能力。") }
        )
        MenuBottomSheet(activity, ui).show("文件与权限", items)
    }

    private fun backupRestoreMenu() {
        val items = listOf(
            MenuBottomSheet.MenuItem("备份数据", "备份 Ubuntu 环境、任务数据、设置和项目文件") { openBackupRestorePage(BackupRestorePage.Mode.BACKUP) },
            MenuBottomSheet.MenuItem("恢复数据", "从备份文件恢复环境、任务、设置和项目") { openBackupRestorePage(BackupRestorePage.Mode.RESTORE) }
        )
        MenuBottomSheet(activity, ui).show("数据备份", items)
    }

    private fun openBackupRestorePage(mode: BackupRestorePage.Mode) {
        val page = BackupRestorePage(mode)
        val view = page.create(activity, ui, host)
        AlertDialog.Builder(activity)
            .setTitle(if (mode == BackupRestorePage.Mode.BACKUP) "数据备份" else "数据恢复")
            .setView(view)
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun advancedMenu() {
        val items = listOf(
            MenuBottomSheet.MenuItem("命令速查", "常用内置命令快速参考") { detail("命令速查", "ubuntu\npmx list packages\namx start ...\ngetpropx ro.product.model\nlogcatx -d\ntask-list\ncheck-dev-env") }
        )
        MenuBottomSheet(activity, ui).show("系统与高级", items)
    }

    private fun openStorageSettings() {
        if (Build.VERSION.SDK_INT >= 30) {
            runCatching {
                activity.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply { data = Uri.parse("package:${activity.packageName}") })
            }.onFailure { activity.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
        } else openAppSettings()
    }

    private fun openAppSettings() {
        activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.parse("package:${activity.packageName}") })
    }

    private fun detail(title: String, body: String) {
        AlertDialog.Builder(activity).setTitle(title).setMessage(body).setPositiveButton("关闭", null).show()
    }
    private fun toast(text: String) = Toast.makeText(activity, text, Toast.LENGTH_SHORT).show()
}
