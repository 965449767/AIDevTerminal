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
        content.addView(row("开发环境", "检测开发环境、部署 Android 工具链、修复环境") { devMenu() })
        content.addView(row("AI 与服务器", "安装 OpenCode、后台常驻、端口诊断") { aiServerMenu() })
        content.addView(row("文件与权限", "存储访问、安装权限、Shizuku、应用详情") { permissionMenu() })
        content.addView(row("环境诊断", "存储、电池、Ubuntu、OpenCode、SDK、Shizuku 状态") { diagnosticsCenter() })
        content.addView(ui.section("当前效果说明", ui.effectNotice()))
        return ScrollView(activity).apply { addView(content) }
    }

    private fun row(title: String, desc: String, click: () -> Unit): View =
        ui.actionCard(title, desc, title.take(2).uppercase()) { click() }.apply {
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, ui.dp(8)) }
        }

    private fun appearanceMenu() {
        AlertDialog.Builder(activity).setTitle("外观与交互").setItems(arrayOf("主题预设", "背景模式", "透明度", "模糊感", "空间密度", "开启/关闭触觉反馈", "查看效果说明")) { _, which ->
            when (which) {
                0 -> themePresetDialog()
                1 -> backgroundModeDialog()
                2 -> sliderDialog("透明度", "ui_alpha", 70, 100, 94, "%")
                3 -> sliderDialog("模糊感", "ui_blur", 0, 40, 18, "")
                4 -> sliderDialog("空间密度", "ui_density", 86, 116, 100, "%")
                5 -> {
                    val prefs = activity.getSharedPreferences("aidev_ui", Activity.MODE_PRIVATE)
                    val next = !prefs.getBoolean("haptic_tap", true)
                    prefs.edit().putBoolean("haptic_tap", next).apply()
                    toast(if (next) "触觉反馈已开启" else "触觉反馈已关闭")
                }
                6 -> detail("效果说明", ui.effectNotice())
            }
        }.show()
    }

    private fun themePresetDialog() {
        val prefs = activity.getSharedPreferences("aidev_ui", Activity.MODE_PRIVATE)
        val labels = arrayOf("深空蓝", "矩阵绿", "紫色专业", "亮色", "动态系统跟随")
        val values = arrayOf("midnight", "matrix", "violet", "light", "dynamic")
        val checked = values.indexOf(prefs.getString("theme_preset", "midnight")).coerceAtLeast(0)
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
        AlertDialog.Builder(activity).setTitle("终端设置").setItems(arrayOf("终端字号", "新增快捷键", "管理快捷键", "清除快捷键", "快捷键说明", "会话说明")) { _, which ->
            when (which) {
                0 -> terminalFontDialog()
                1 -> customKeyDialog()
                2 -> manageCustomKeys()
                3 -> {
                    activity.getSharedPreferences("aidev_ui", Activity.MODE_PRIVATE).edit()
                        .remove("terminal_custom_keys")
                        .remove("terminal_key_overrides")
                        .apply()
                    toast("已清除自定义快捷键")
                }
                4 -> detail("快捷键说明", "内嵌终端保持两行六列：点击是主功能，上滑是拓展功能，长按可自定义单个键。\n\n例如 C 点击输入 c，上滑 clear；SPC 点击空格，上滑 pwd。\n\n自定义输入支持 \\n、\\t 和 \\e 转义。")
                5 -> detail("会话说明", "终端 Tab 支持多会话标签、新建会话、关闭当前会话、点击标签切换、长按标签重命名。关闭最后一个会话时会自动创建新会话。")
            }
        }.show()
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

    private fun devMenu() {
        AlertDialog.Builder(activity).setTitle("开发环境").setItems(arrayOf("检测开发环境", "部署 Android 工具链", "修复环境", "Android 调试桥接")) { _, which ->
            when (which) {
                0 -> host.openTerminal("check-dev-env")
                1 -> host.openTerminal("deploy-android-dev")
                2 -> host.openTerminal("repair-dev-env")
                3 -> detail("Android 调试桥接", "在 Ubuntu/终端中优先使用 pmx、amx、getpropx、logcatx，避免直接执行 /system/bin/pm 造成 PRoot 链接器错误。")
            }
        }.show()
    }

    private fun aiServerMenu() {
        AlertDialog.Builder(activity).setTitle("AI 与服务器").setItems(arrayOf("安装 OpenCode", "监听端口", "后台常驻")) { _, which ->
            when (which) {
                0 -> host.openTerminal("install-aitool")
                1 -> host.openTerminal("list-listen-ports")
                2 -> {
                    KeepAliveService.start(activity)
                    activity.getSharedPreferences("aidev_ui", Activity.MODE_PRIVATE).edit().putBoolean("keepalive_auto", true).apply()
                    toast("后台常驻已启动")
                }
            }
        }.show()
    }

    private fun permissionMenu() {
        AlertDialog.Builder(activity).setTitle("文件与权限").setItems(arrayOf("存储权限", "应用详情", "Shizuku 状态")) { _, which ->
            when (which) {
                0 -> openStorageSettings()
                1 -> openAppSettings()
                2 -> detail("Shizuku 状态", "如果 Shizuku 未运行，请先打开 Shizuku 应用并启动服务。应用已声明 Shizuku Provider，用于后续更高权限能力。")
            }
        }.show()
    }

    private fun diagnosticsCenter() {
        val home = File(activity.filesDir, "home")
        val rootfs = File(home, "ubuntu-rootfs")
        val sdk = File(rootfs, "opt/android-sdk")
        val opencode = File(rootfs, "root/.opencode/bin/opencode")
        val shizukuInstalled = runCatching { activity.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0) }.isSuccess
        val batteryOk = if (Build.VERSION.SDK_INT < 23) true else (activity.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(activity.packageName)
        val storageHint = if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager() else true
        val body = listOf(
            diagLine("存储权限", storageHint, "用于读取下载目录、项目目录和 APK"),
            diagLine("电池优化", batteryOk, "后台任务与长时间服务更稳定"),
            diagLine("AIDev Home", home.exists(), home.absolutePath),
            diagLine("Ubuntu rootfs", rootfs.exists(), rootfs.absolutePath),
            diagLine("Android SDK", sdk.exists(), sdk.absolutePath),
            diagLine("OpenCode", opencode.exists(), opencode.absolutePath),
            diagLine("Shizuku 应用", shizukuInstalled, "用于后续更高权限能力"),
            diagLine("PRoot 依赖", File(home, "proot-lib/libtalloc.so.2").exists(), "终端 Ubuntu 入口依赖")
        ).joinToString("\n")
        AlertDialog.Builder(activity)
            .setTitle("权限与环境诊断")
            .setMessage(body)
            .setPositiveButton("终端检测") { _, _ -> host.openTerminal("check-dev-env") }
            .setNeutralButton("存储权限") { _, _ -> openStorageSettings() }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun diagLine(name: String, ok: Boolean, desc: String): String =
        "${if (ok) "✓" else "!"} $name：${if (ok) "正常" else "待处理"}\n  $desc"

    private fun advancedMenu() {
        AlertDialog.Builder(activity).setTitle("系统与高级").setItems(arrayOf("命令速查")) { _, which ->
            when (which) {
                0 -> detail("命令速查", "ubuntu\npmx list packages\namx start ...\ngetpropx ro.product.model\nlogcatx -d\ntask-list\ncheck-dev-env")
            }
        }.show()
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
