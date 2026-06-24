package com.aidev.terminal
import android.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder

import android.app.Activity
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
import android.widget.Toast
import android.content.ClipData
import android.content.ClipboardManager
import java.io.File
import com.aidev.terminal.presentation.BackupRestorePage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class EmbeddedSettingsPage : ShellPage {
    private lateinit var activity: Activity
    private lateinit var ui: AIDevUi
    private lateinit var host: ShellHost
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun create(activity: Activity, ui: AIDevUi, host: ShellHost): View {
        this.activity = activity
        this.ui = ui
        this.host = host
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(18), ui.dp(12), ui.dp(18), ui.dp(24))
        }
        content.addView(ui.section("设置", "一级入口保持通用，二级动作以内嵌菜单展开，底部导航不离开 Shell"))
        content.addView(row("外观与交互", "主题、背景、触觉反馈") { appearanceMenu() })
        content.addView(row("开发环境", "环境检测、Shell 增强、网络诊断、系统监控、容器管理") { devMenu() })
content.addView(row("权限管理", "存储、通知、安装应用、修改系统设置、Shizuku") { permissionMenu() })
content.addView(row("系统与后台", "电池优化、后台常驻、应用详情") { systemMenu() })
        content.addView(row("数据备份", "备份和恢复 Ubuntu 环境、任务数据、设置和项目文件") { backupRestoreMenu() })
        content.addView(row("路径设置", "备份目录、项目目录、外部存储路径") { pathMenu() })
        return ScrollView(activity).apply { addView(content) }
    }

    override fun onDestroy(activity: Activity) {
        scope.cancel()
    }

    private fun row(title: String, desc: String, click: () -> Unit): View =
        ui.actionRow(title, desc) { click() }.apply {
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, ui.dp(8)) }
        }

    private val prefs by lazy { PreferencesManager(activity) }

    private fun appearanceMenu() {
        val items = listOf(
            MenuBottomSheet.MenuItem("主题预设", "切换深色/浅色/跟随系统") { themePresetDialog() },
            MenuBottomSheet.MenuItem("背景模式", "纯色/渐变/自定义图片") { backgroundModeDialog() },
            MenuBottomSheet.MenuItem("触觉反馈", "开启或关闭点击振动反馈") {
                prefs.hapticTap = !prefs.hapticTap
                toast(if (prefs.hapticTap) "触觉反馈已开启" else "触觉反馈已关闭")
            }
        )
        MenuBottomSheet(activity, ui).show("外观与交互", items)
    }

    private fun themePresetDialog() {
        val labels = arrayOf("深色", "亮色", "跟随系统")
        val values = arrayOf("dark", "light", "system")
        val checked = values.indexOf(prefs.themePreset).coerceAtLeast(0)
        MaterialAlertDialogBuilder(activity)
            .setTitle("主题预设")
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                prefs.themePreset = values[which]
                host.refreshShellSkin()
                toast("主题已切换为 ${labels[which]}")
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun backgroundModeDialog() {
        val labels = arrayOf("纯色背景", "主题渐变", "自定义图片")
        val values = arrayOf("solid", "gradient", "image")
        val checked = values.indexOf(prefs.bgMode).coerceAtLeast(0)
        MaterialAlertDialogBuilder(activity)
            .setTitle("背景模式")
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                prefs.bgMode = values[which]
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


    private fun openShellEnhancements() {
        val page = ShellEnhancementsPage()
        val view = page.create(activity, ui, host)
        MaterialAlertDialogBuilder(activity)
            .setTitle("Shell 增强")
            .setView(view)
            .setNegativeButton("关闭") { _, _ -> page.onSelected(activity, view) }
            .show()
    }

    private fun devMenu() {
        val items = listOf(
            MenuBottomSheet.MenuItem("环境检查与修复", "全面检测 Ubuntu 与开发工具状态") { devCheckAndRepair() },
            MenuBottomSheet.MenuItem("Shell 增强", "命令历史统计、别名管理、收藏夹与模板") { openShellEnhancements() },
            MenuBottomSheet.MenuItem("网络诊断工具", "检测端口监听与网络连通性") { openNetworkDiagnostics() },
            MenuBottomSheet.MenuItem("监听端口", "查看当前所有监听中的网络端口") { host.openTerminal("list-listen-ports") },
            MenuBottomSheet.MenuItem("系统监控", "实时查看 CPU、内存与进程状态") { openSystemMonitor() },
            MenuBottomSheet.MenuItem("安全审计", "检查权限、密钥与容器安全") { openSecurityAudit() },
            MenuBottomSheet.MenuItem("容器管理", "管理 Docker/Podman 容器生命周期") { openContainerManager() }
        )
        MenuBottomSheet(activity, ui).show("开发环境", items)
    }

    private fun openNetworkDiagnostics() {
        val page = NetworkDiagnosticsPage()
        val view = page.create(activity, ui, host)
        MaterialAlertDialogBuilder(activity)
            .setTitle("网络诊断")
            .setView(view)
            .setNegativeButton("关闭") { _, _ -> page.onSelected(activity, view) }
            .show()
    }

    private fun openSystemMonitor() {
        val page = SystemMonitorPage()
        val view = page.create(activity, ui, host)
        MaterialAlertDialogBuilder(activity)
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
        MaterialAlertDialogBuilder(activity)
            .setTitle("安全审计")
            .setView(view)
            .setNegativeButton("关闭") { _, _ -> page.onSelected(activity, view) }
            .show()
    }

    private fun openContainerManager() {
        val page = ContainerManagerPage()
        val view = page.create(activity, ui, host)
        MaterialAlertDialogBuilder(activity)
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
            MaterialAlertDialogBuilder(activity)
                .setTitle("开发环境检查")
                .setMessage(body.toString())
                .setPositiveButton("关闭", null)
                .show()
        } else {
            val fixLabels = failedChecks.filter { it.fixAction != null }.map { it.name }.toTypedArray()
            val fixActions = failedChecks.filter { it.fixAction != null }.mapNotNull { it.fixAction }.toTypedArray()
            MaterialAlertDialogBuilder(activity)
                .setTitle("开发环境检查")
                .setMessage(body.toString())
                .setPositiveButton(if (fixLabels.isNotEmpty()) "一键修复" else "关闭") { _, _ ->
                    if (fixLabels.isEmpty()) return@setPositiveButton
                    MaterialAlertDialogBuilder(activity)
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

    private val permissionItems = listOf(
        MenuBottomSheet.MenuItem("存储权限", "管理所有文件访问权限") { openStorageSettings() },
        MenuBottomSheet.MenuItem("通知权限", "管理通知显示权限") {
            if (Build.VERSION.SDK_INT >= 26) {
                activity.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
                })
            } else {
                toast("当前系统版本无需单独设置通知权限")
            }
        },
        MenuBottomSheet.MenuItem("安装未知应用", "允许安装来自未知来源的应用") {
            if (Build.VERSION.SDK_INT >= 26) {
                activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${activity.packageName}")
                })
            } else {
                toast("当前系统版本无需单独设置")
            }
        },
        MenuBottomSheet.MenuItem("修改系统设置", "允许应用修改系统设置（如亮度、超时）") {
            if (Build.VERSION.SDK_INT >= 23) {
                if (Settings.System.canWrite(activity)) {
                    toast("已允许修改系统设置")
                } else {
                    activity.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                        data = Uri.parse("package:${activity.packageName}")
                    })
                }
            }
        },
        MenuBottomSheet.MenuItem("Shizuku 状态", "实时检测 Shizuku 安装和授权状态") { showShizukuStatus() }
    )

    private fun permissionMenu() {
        MenuBottomSheet(activity, ui).show("权限管理", permissionItems)
    }

    private fun systemMenu() {
        val items = listOf(
            MenuBottomSheet.MenuItem("电池优化", "将应用加入电池优化白名单，防止后台被限制") {
                if (Build.VERSION.SDK_INT >= 23) {
                    activity.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${activity.packageName}")
                    })
                }
            },
            MenuBottomSheet.MenuItem("后台常驻", "启动保活服务防止进程被系统回收") {
                KeepAliveService.start(activity)
                prefs.keepaliveAuto = true
                toast("后台常驻已启动")
            },
            MenuBottomSheet.MenuItem("应用详情", "跳转到系统应用信息页") { openAppSettings() }
        )
        MenuBottomSheet(activity, ui).show("系统与后台", items)
    }

    private fun showShizukuStatus() {
        val installed = runCatching { activity.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0) }.isSuccess
        val available = installed && ShizukuLogcat.isAvailable()
        val statusText = if (installed) ShizukuLogcat.statusText() else "未安装"

        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(20), ui.dp(10), ui.dp(20), 0)
        }
        box.addView(ui.text("Shizuku 应用", 14f, ui.palette.muted))
        box.addView(ui.text(if (installed) "已安装" else "未安装", 16f, if (installed) ui.palette.success else ui.palette.danger, bold = true).apply {
            setPadding(0, ui.dp(4), 0, ui.dp(12))
        })
        box.addView(ui.text("Shizuku 授权", 14f, ui.palette.muted))
        box.addView(ui.text(statusText, 16f, if (available) ui.palette.success else ui.palette.danger, bold = true).apply {
            setPadding(0, ui.dp(4), 0, ui.dp(12))
        })
        if (available) {
            val testResult = ui.text("", 14f, ui.palette.muted).apply {
                setPadding(0, ui.dp(4), 0, 0)
            }
            val testBtn = ui.text("▶ 测试命令执行", 14f, ui.palette.accent, bold = true).apply {
                setPadding(0, ui.dp(8), 0, 0)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    testResult.text = "执行中..."
                    testResult.setTextColor(ui.palette.muted)
                    scope.launch {
                        val result = ShizukuLogcat.executeCommand("echo SHIZUKU_TEST_OK")
                        if (result.isSuccess) {
                            testResult.text = "✅ 成功：${result.stdout.trim()}"
                            testResult.setTextColor(ui.palette.success)
                        } else {
                            testResult.text = "❌ 失败：${result.stderr.take(100)}"
                            testResult.setTextColor(ui.palette.danger)
                        }
                    }
                }
            }
            box.addView(testBtn)
            box.addView(testResult)
        }

        MaterialAlertDialogBuilder(activity)
            .setTitle("Shizuku 状态")
            .setView(box)
            .setPositiveButton(if (!installed) "去安装" else if (!available) "打开 Shizuku" else "关闭") { _, _ ->
                if (!installed) {
                    try {
                        activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=moe.shizuku.privileged.api")))
                    } catch (_: Exception) {
                        toast("请在应用商店搜索 Shizuku")
                    }
                } else if (!available) {
                    val intent = activity.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                    if (intent != null) activity.startActivity(intent)
                    else toast("无法打开 Shizuku 应用")
                }
            }
            .setNegativeButton(if (available) "" else "取消", null)
            .show()
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
        MaterialAlertDialogBuilder(activity)
            .setTitle(if (mode == BackupRestorePage.Mode.BACKUP) "数据备份" else "数据恢复")
            .setView(view)
            .setNegativeButton("关闭", null)
            .show()
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

    private fun pathMenu() {
        val sheet = AIDevBottomSheet(activity, ui)
        val ctx = activity
        sheet.show("路径设置") { content ->
            content.addView(editablePathRow("备份目录", "备份文件和恢复数据的存储路径，可修改",
                PathConfig.backupDir(ctx).absolutePath) {
                pathEditDialog("备份目录", PathConfig.backupDir(ctx).absolutePath) { prefs.backupDir = it }
            })
            content.addView(editablePathRow("项目目录", "Ubuntu 内新建项目的默认位置（相对 rootfs），可修改",
                PathConfig.projectsDir(ctx).absolutePath) {
                pathEditDialog("项目目录（相对 rootfs）", prefs.projectsDirRel.ifBlank { "root/projects" }) { prefs.projectsDirRel = it }
            })
            content.addView(editablePathRow("外部 AIDev 目录", "Android 侧项目数据存放路径，用于外部备份，可修改",
                PathConfig.externalAidevDir(ctx).absolutePath) {
                pathEditDialog("外部 AIDev 目录", PathConfig.externalAidevDir(ctx).absolutePath) { prefs.externalAidevDir = it }
            })
            content.addView(readonlyPathRow("AIDev Home", "核心数据目录，含 Ubuntu 环境和全部配置（只读）",
                PathConfig.aidevHome(ctx).absolutePath))
            content.addView(readonlyPathRow("Ubuntu Rootfs", "Ubuntu 根文件系统，完整 Linux 环境所在（只读）",
                PathConfig.rootfs(ctx).absolutePath))
            content.addView(readonlyPathRow("任务日志目录", "后台任务日志和元数据的存储位置（只读）",
                PathConfig.tasksDir(ctx).absolutePath))
        }
    }

    private fun editablePathRow(title: String, desc: String, path: String, onEdit: () -> Unit): View {
        return ui.actionRow(title, desc) { onEdit() }.apply {
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, ui.dp(8)) }
        }
    }

    private fun readonlyPathRow(title: String, desc: String, path: String): View {
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(8))
            isClickable = true
            isFocusable = true
            setOnClickListener { copyPath(path) }
            addView(ui.text(title, DesignTokens.TEXT_BODY, ui.palette.muted, bold = true).apply {
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            addView(ui.text(desc, DesignTokens.TEXT_CAPTION, ui.palette.muted).apply {
                setPadding(0, ui.dp(2), 0, 0)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            addView(ui.text(path, DesignTokens.TEXT_CAPTION, ui.palette.muted).apply {
                setPadding(0, ui.dp(2), 0, 0)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
        }
    }

    private fun pathEditDialog(title: String, current: String, onSave: (String) -> Unit) {
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(20), ui.dp(10), ui.dp(20), 0)
        }
        val input = EditText(activity).apply {
            setText(current)
            selectAll()
        }
        box.addView(input)
        MaterialAlertDialogBuilder(activity)
            .setTitle(title)
            .setView(box)
            .setPositiveButton("保存") { _, _ ->
                val v = input.text.toString().trim()
                if (v.isNotEmpty()) {
                    onSave(v)
                    toast("路径已更新")
                }
            }
            .setNeutralButton("重置默认") { _, _ ->
                onSave("")
                toast("已恢复默认路径")
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun copyPath(path: String) {
        val clip = ClipData.newPlainText("path", path)
        (activity.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(clip)
        toast("路径已复制到剪贴板")
    }

    private fun toast(text: String) = Toast.makeText(activity, text, Toast.LENGTH_SHORT).show()
}
