package com.aidev.terminal

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import rikka.shizuku.Shizuku

class SettingsActivity : Activity() {
    private lateinit var ui: AIDevUi

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
    }

    private fun buildUi() {
        ui = AIDevUi(this, getSharedPreferences("aidev_ui", MODE_PRIVATE))
        val root = ui.pageRoot()
        AppNav.attach(this, ui, root, SettingsActivity::class.java)
        root.addView(ui.topBar("设置中心", "主题" to { ShellActivity.open(this, ShellActivity.TAB_SETTINGS) }, "关闭" to { finish() }))

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(18), ui.dp(12), ui.dp(18), ui.dp(24))
        }

        addSection(content, "设置", "一级入口保持通用简洁，具体能力进入二级菜单逐步展开")
        addItem(content, "外观与交互", "主题、背景、空间尺寸、触觉反馈、手势和终端交互体验。") {
            showAppearanceMenu()
        }
        addItem(content, "开发环境", "Ubuntu 容器、基础工具链、Android 开发环境、命令速查和修复工具。") {
            showDevelopmentMenu()
        }
        addItem(content, "AI 与服务器", "AI 代理、OpenCode、后台任务、本机 Web 服务、端口诊断和长后台常驻。") {
            showAiServerMenu()
        }
        addItem(content, "文件与权限", "双列文件管理器、存储权限、安装权限、Shizuku 和应用权限状态。") {
            showFilesPermissionMenu()
        }
        addItem(content, "系统与高级", "下载策略、命令速查、环境诊断、应用设置和高级维护入口。") {
            showSystemAdvancedMenu()
        }

        root.addView(ScrollView(this).apply { addView(content) }, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(ui.bottomNav(AppNav.bottom(this)))
        setContentView(root)
    }

    private fun topBar(): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, dp(10), 0)
            background = bg(0xFF111418.toInt(), 0)
            layoutParams = LinearLayout.LayoutParams(-1, dp(46))
            addView(TextView(this@SettingsActivity).apply {
                text = "设置中心"
                textSize = 17f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(0xFFE5E7EB.toInt())
                layoutParams = LinearLayout.LayoutParams(0, -1, 1f)
                gravity = Gravity.CENTER_VERTICAL
            })
            addView(TextView(this@SettingsActivity).apply {
                text = "关闭"
                textSize = 14f
                setTextColor(0xFF93C5FD.toInt())
                gravity = Gravity.CENTER
                setOnClickListener { finish() }
            }, LinearLayout.LayoutParams(dp(56), -1))
        }

    private fun addSection(parent: LinearLayout, title: String, desc: String) {
        parent.addView(ui.section(title, desc))
    }

    private fun addItem(parent: LinearLayout, title: String, desc: String, click: () -> Unit) {
        parent.addView(row(title, desc).apply { setOnClickListener { click() } })
    }

    private fun addActionItem(parent: LinearLayout, title: String, desc: String, action: String) {
        parent.addView(row(title, desc).apply {
            setOnClickListener {
                setResult(RESULT_OK, Intent().putExtra("action", action))
                finish()
            }
        })
    }

    private fun showAppearanceMenu() {
        val items = arrayOf("主题中心", "触觉反馈", "空间尺寸与风格（规划）", "手势交互说明")
        AlertDialog.Builder(this)
            .setTitle("外观与交互")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> ShellActivity.open(this, ShellActivity.TAB_SETTINGS)
                    1 -> showHapticDialog()
                    2 -> ShellActivity.open(this, ShellActivity.TAB_SETTINGS)
                    3 -> detail(
                        "手势交互",
                        """
                        顶级页面正在逐步接入左右滑动导航。

                        已接入：
                        终端、任务中心、AI 助手中心、服务器中心、主题中心、设置中心。

                        后续需要继续处理：
                        文件管理器和终端页面，因为这两个页面存在横向内容、选择态和终端输入区域，手势冲突要单独设计。
                        """.trimIndent(),
                        "关闭",
                        "",
                        execute = false
                    )
                }
            }
            .show()
    }

    private fun showDevelopmentMenu() {
        val items = arrayOf("Ubuntu 容器", "基础开发环境", "Android 开发环境", "环境修复", "命令速查")
        AlertDialog.Builder(this)
            .setTitle("开发环境")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showUbuntuMenu()
                    1 -> showBaseDevMenu()
                    2 -> showAndroidDevMenu()
                    3 -> detail("环境修复", "用于修复 dpkg 中断、apt 破损依赖和未配置完成的包。\n\n执行命令：repair-dev-env", "执行修复", "repair-dev-env")
                    4 -> detail("命令速查", "进入 Ubuntu 后可运行 env-menu 查看容器内菜单。\n\n常用命令：\nenv-menu\ncheck-dev-env\ndeploy-dev-env\nrepair-dev-env\ninstall-aitool\ntask-run\ntask-list", "复制 env-menu", "env-menu", execute = false)
                }
            }
            .show()
    }

    private fun showUbuntuMenu() {
        val items = arrayOf("进入 Ubuntu", "安装/修复 Ubuntu", "清理重装")
        AlertDialog.Builder(this)
            .setTitle("Ubuntu 容器")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> detail("进入 Ubuntu", "进入已安装的 Ubuntu PRoot 容器。\n\n执行命令：ubuntu", "执行 ubuntu", "ubuntu")
                    1 -> detail("安装/修复 Ubuntu", "自动下载 Ubuntu Base 24.04 arm64，并解包为 PRoot rootfs；已安装时直接提示就绪。\n\n执行命令：install-ubuntu --fast", "执行安装/修复", "install-ubuntu --fast")
                    2 -> detail("清理重装", "清理 rootfs 和下载缓存后重新安装。\n\n执行命令：install-ubuntu --clean", "复制清理命令", "install-ubuntu --clean", execute = false)
                }
            }
            .show()
    }

    private fun showBaseDevMenu() {
        val items = arrayOf("检测开发环境", "部署开发环境", "修复开发环境")
        AlertDialog.Builder(this)
            .setTitle("基础开发环境")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> detail("检测开发环境", "检测 curl、git、node、npm、JDK、opencode、证书、网络和 dpkg 状态。\n\n执行命令：check-dev-env", "执行检测", "check-dev-env")
                    1 -> detail("部署开发环境", "安装基础层、编译层和 JS 层；先检测，缺什么装什么。\n\n执行命令：deploy-dev-env", "执行部署", "deploy-dev-env")
                    2 -> detail("修复开发环境", "修复 dpkg 中断、apt 破损依赖和未配置完成的包。\n\n执行命令：repair-dev-env", "执行修复", "repair-dev-env")
                }
            }
            .show()
    }

    private fun showAndroidDevMenu() {
        val items = arrayOf("Android 开发说明", "检测 Android 环境", "部署 Android 工具链", "Android 调试工具")
        AlertDialog.Builder(this)
            .setTitle("Android 开发环境")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> detail("Android 开发说明", "面向手机端 Android 编程、编译、签名、安装和调试。\n\n执行命令：android-dev-explain", "查看说明", "android-dev-explain")
                    1 -> detail("检测 Android 环境", "检测 JDK、Gradle、Android 工具、系统调试桥接命令。\n\n执行命令：check-android-dev", "执行检测", "check-android-dev")
                    2 -> detail("部署 Android 工具链", "安装可在 ARM64 Ubuntu/手机环境运行的 Android 开发基础层。\n\n执行命令：deploy-android-dev", "执行部署", "deploy-android-dev")
                    3 -> detail("Android 调试工具", "查看 pmx、amx、getpropx、logcatx 等 Android 调试桥接命令。\n\n执行命令：android-debug-tools", "查看工具", "android-debug-tools")
                }
            }
            .show()
    }

    private fun showAiServerMenu() {
        val items = arrayOf("AI 助手中心", "AI 代理管理", "服务器中心", "后台常驻", "服务通信诊断", "任务中心")
        AlertDialog.Builder(this)
            .setTitle("AI 与服务器")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> startActivity(Intent(this, AIAgentActivity::class.java))
                    1 -> showAiAgentMenu()
                    2 -> startActivity(Intent(this, ServerCenterActivity::class.java))
                    3 -> showKeepAliveMenu()
                    4 -> detail("服务通信诊断", "用于检查 OpenCode/Web 前端服务端口是否能被浏览器访问。\n\n常用命令：\naidev-net-explain\nlist-listen-ports\ncheck-local-server 3000", "查看说明", "aidev-net-explain")
                    5 -> AppNav.openTerminal(this, "aidev-agent-log\n")
                }
            }
            .show()
    }

    private fun showKeepAliveMenu() {
        val items = arrayOf("启动后台常驻", "停止后台常驻", "忽略电池优化", "HyperOS 后台设置", "后台常驻说明")
        AlertDialog.Builder(this)
            .setTitle("后台常驻")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> startKeepAlive()
                    1 -> {
                        KeepAliveService.stop(this)
                        getSharedPreferences("aidev_ui", MODE_PRIVATE).edit().putBoolean("keepalive_auto", false).apply()
                        Toast.makeText(this, "已停止后台常驻服务", Toast.LENGTH_SHORT).show()
                    }
                    2 -> requestIgnoreBatteryOptimization()
                    3 -> showHyperOsMenu()
                    4 -> detail("后台常驻说明", "AIDev 已加入前台服务、常驻通知、CPU WakeLock、Wi‑Fi Lock 和开机恢复入口。\n\n小米/HyperOS 仍建议手动设置为无限制、允许自启动、锁定最近任务卡片。", "启动常驻服务", "", execute = false, positiveAction = { startKeepAlive() })
                }
            }
            .show()
    }

    private fun showFilesPermissionMenu() {
        val items = arrayOf("双列文件管理器", "权限状态", "应用权限设置", "Shizuku 状态")
        AlertDialog.Builder(this)
            .setTitle("文件与权限")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> ShellActivity.open(this, ShellActivity.TAB_FILES)
                    1 -> detail("权限状态", permissionStatusText(), "打开应用权限设置", "", execute = false, positiveAction = { openAppSettings() })
                    2 -> openAppSettings()
                    3 -> detail("Shizuku 状态与启动", "当前状态：\n${shizukuStatusText()}\n\n如果 Shizuku 未运行，请先打开 Shizuku 应用并启动服务。", "打开 Shizuku", "", execute = false, positiveAction = { openShizukuApp() })
                }
            }
            .show()
    }

    private fun showSystemAdvancedMenu() {
        val items = arrayOf("快速下载说明", "环境诊断", "命令速查", "应用详情")
        AlertDialog.Builder(this)
            .setTitle("系统与高级")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> detail("快速下载", "fast-download 优先 aria2c，没有则降级 curl/wget。\n\n命令：fast-download <url> <output> [threads]", "复制示例命令", "fast-download <url> <output> 8", execute = false)
                    1 -> detail("环境诊断", "基础环境：check-dev-env\nAndroid 环境：check-android-dev\n后台常驻：check-keepalive", "复制 check-dev-env", "check-dev-env", execute = false)
                    2 -> detail("命令速查", "进入 Ubuntu 后输入 env-menu 查看容器内菜单。", "复制 env-menu", "env-menu", execute = false)
                    3 -> openAppSettings()
                }
            }
            .show()
    }

    private fun showAiAgentMenu() {
        val items = arrayOf(
            "opencode 官方 CLI / 代理客户端",
            "Hermes（预留）",
            "AI 代理说明"
        )
        AlertDialog.Builder(this)
            .setTitle("AI 代理管理")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> detail(
                        "opencode 官方 CLI",
                        """
                        安装对象：
                        opencode 官方 CLI / 代理客户端。

                        官方入口：
                        https://opencode.ai/install

                        安装方式：
                        AIDev 直接调用 opencode 官方安装脚本：
                        curl -fsSL https://opencode.ai/install | bash

                        AIDev 负责：
                        检测 curl/unzip、设置安装目录、确认 PATH、避免重复安装。

                        官方脚本负责：
                        选择版本、选择下载地址、显示下载进度、下载、解包和安装。

                        安装位置：
                        ~/.opencode/bin/opencode。

                        说明：
                        它是 opencode 官方工具，但不是 OpenAI 或 Anthropic 的官方客户端；实际调用哪家模型取决于后续 provider / API Key 配置。

                        执行命令：
                        install-aitool
                        """.trimIndent(),
                        "安装 opencode",
                        "install-aitool"
                    )
                    1 -> detail(
                        "Hermes（预留）",
                        """
                        Hermes 代理入口已预留。

                        目前状态：
                        尚未内置 Hermes 的官方安装源、安装命令和配置检测规则。

                        后续接入时会补齐：
                        官方来源、安装位置、配置文件、API Key 检测、版本检测、卸载/升级命令。
                        """.trimIndent(),
                        "关闭",
                        "",
                        execute = false
                    )
                    2 -> detail(
                        "AI 代理说明",
                        """
                        这里管理的是“终端 AI 编程代理客户端”，不是模型本身。

                        代理客户端负责：
                        读取项目文件、执行命令、调用模型服务、组织上下文。

                        模型服务取决于后续配置：
                        例如 OpenAI、Anthropic、OpenRouter、本地模型或其他 provider。

                        AIDev Terminal 的职责：
                        提供 Ubuntu/工具链/权限/下载/安装脚本，让这些代理可以在手机终端环境中运行。
                        """.trimIndent(),
                        "关闭",
                        "",
                        execute = false
                    )
                }
            }
            .show()
    }

    private fun row(title: String, desc: String): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ui.subtleButtonBackground()
            setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12))
            val lp = LinearLayout.LayoutParams(-1, -2)
            lp.setMargins(0, 0, 0, ui.dp(8))
            layoutParams = lp
            addView(ui.text(title, 14f, ui.palette.text, bold = true))
            addView(ui.muted(desc).apply { setPadding(0, ui.dp(4), 0, 0) })
        }

    private fun detail(
        title: String,
        body: String,
        positive: String,
        command: String,
        execute: Boolean = true,
        positiveAction: (() -> Unit)? = null
    ) {
        val builder = AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(body)
            .setPositiveButton(positive) { _, _ ->
                if (positiveAction != null) {
                    positiveAction()
                } else if (execute) {
                    setResult(RESULT_OK, Intent().putExtra("action", "cmd").putExtra("cmd", command))
                    finish()
                } else if (command.isNotBlank()) {
                    copy(command)
                }
            }
            .setNeutralButton("关闭", null)
        if (command.isNotBlank()) {
            builder.setNegativeButton("复制命令") { _, _ -> copy(command) }
        }
        builder.show()
    }

    private fun copy(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("AIDev 命令", text))
        Toast.makeText(this, "已复制：$text", Toast.LENGTH_SHORT).show()
    }

    private fun showThemePresetDialog() {
        val labels = arrayOf("深色", "亮色", "跟随系统")
        val values = arrayOf("dark", "light", "system")
        val prefs = getSharedPreferences("aidev_ui", MODE_PRIVATE)
        val current = values.indexOf(prefs.getString("theme_preset", "system")).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("主题预设")
            .setSingleChoiceItems(labels, current) { d, which ->
                prefs.edit().putString("theme_preset", values[which]).apply()
                Toast.makeText(this, "主题已切换：${labels[which]}", Toast.LENGTH_SHORT).show()
                d.dismiss()
                buildUi()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showPercentDialog(title: String, key: String, defaultValue: Int, min: Int, max: Int) {
        val prefs = getSharedPreferences("aidev_ui", MODE_PRIVATE)
        val current = prefs.getInt(key, defaultValue).coerceIn(min, max)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }
        val valueText = TextView(this).apply {
            text = "$current%"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFFE5E7EB.toInt())
            gravity = Gravity.CENTER
        }
        val seek = SeekBar(this).apply {
            this.max = max - min
            progress = current - min
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    valueText.text = "${progress + min}%"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        box.addView(valueText)
        box.addView(seek)
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(box)
            .setPositiveButton("保存") { _, _ ->
                prefs.edit().putInt(key, seek.progress + min).apply()
                Toast.makeText(this, "已保存：${seek.progress + min}%", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showHapticDialog() {
        val prefs = getSharedPreferences("aidev_ui", MODE_PRIVATE)
        val labels = arrayOf("点击触觉反馈", "终端流式输出反馈")
        val checked = booleanArrayOf(
            prefs.getBoolean("haptic_tap", true),
            prefs.getBoolean("haptic_stream", false)
        )
        AlertDialog.Builder(this)
            .setTitle("触觉反馈")
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton("保存") { _, _ ->
                prefs.edit()
                    .putBoolean("haptic_tap", checked[0])
                    .putBoolean("haptic_stream", checked[1])
                    .apply()
                Toast.makeText(this, "触觉反馈设置已保存", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun permissionStatusText(): String {
        val notification = if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) "已授权" else "未授权"
        } else {
            "系统无需运行时授权"
        }
        val installPackages = if (Build.VERSION.SDK_INT >= 26) {
            if (packageManager.canRequestPackageInstalls()) "已允许" else "未允许"
        } else {
            "系统无需单独授权"
        }
        val readStorage = if (Build.VERSION.SDK_INT <= 32) {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) "已授权或系统兼容" else "未授权"
        } else {
            "Android 13+ 不使用旧版读存储权限"
        }
        val allFiles = if (Build.VERSION.SDK_INT >= 30) {
            if (android.os.Environment.isExternalStorageManager()) "已允许" else "未允许"
        } else {
            "系统不需要 MANAGE_EXTERNAL_STORAGE"
        }
        val battery = if (Build.VERSION.SDK_INT >= 23) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (pm.isIgnoringBatteryOptimizations(packageName)) "已忽略电池优化" else "仍受电池优化限制"
        } else {
            "系统不需要单独设置"
        }
        val keepAliveAuto = if (getSharedPreferences("aidev_ui", MODE_PRIVATE).getBoolean("keepalive_auto", true)) {
            "已启用自动常驻"
        } else {
            "未启用自动常驻"
        }
        return """
            通知权限：
            $notification

            安装未知来源应用：
            $installPackages

            旧版外部存储读取：
            $readStorage

            所有文件访问权限：
            $allFiles

            网络访问：
            已在 Manifest 声明 INTERNET，属于普通权限，无需手动授权。

            电池优化：
            $battery

            后台常驻：
            $keepAliveAuto

            Shizuku：
            ${shizukuStatusText()}

            说明：
            AIDev Terminal 的 Ubuntu rootfs、开发环境、opencode 默认都安装在应用私有目录内；双列文件管理器访问完整共享存储时建议授予所有文件访问权限。
        """.trimIndent()
    }

    private fun shizukuStatusText(): String =
        try {
            if (!Shizuku.pingBinder()) {
                "Shizuku 服务未运行"
            } else if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                "Shizuku 服务已运行，且 AIDev 已授权"
            } else {
                "Shizuku 服务已运行，但 AIDev 尚未授权"
            }
        } catch (_: Throwable) {
            "无法连接 Shizuku，请确认已安装并启动 Shizuku"
        }

    private fun openAppSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        })
    }

    private fun openShizukuApp() {
        val intent = packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
        if (intent != null) {
            startActivity(intent)
        } else {
            Toast.makeText(this, "未找到 Shizuku 应用，请先安装 Shizuku", Toast.LENGTH_LONG).show()
        }
    }

    private fun startKeepAlive() {
        getSharedPreferences("aidev_ui", MODE_PRIVATE).edit().putBoolean("keepalive_auto", true).apply()
        KeepAliveService.start(this)
        Toast.makeText(this, "已启动后台常驻服务", Toast.LENGTH_SHORT).show()
    }

    private fun requestIgnoreBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= 23) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    })
                    return
                } catch (_: Throwable) {
                    // 回退到通用页面
                }
            } else {
                Toast.makeText(this, "系统已允许忽略电池优化", Toast.LENGTH_SHORT).show()
                return
            }
        }
        try {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        } catch (_: Throwable) {
            openAppSettings()
        }
    }

    private fun showHyperOsMenu() {
        val items = arrayOf("小米自启动管理", "小米省电策略", "应用详情", "电池优化白名单")
        AlertDialog.Builder(this)
            .setTitle("HyperOS 后台设置")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> openIntentFallback(
                        Intent().setClassName(
                            "com.miui.securitycenter",
                            "com.miui.permcenter.autostart.AutoStartManagementActivity"
                        )
                    )
                    1 -> openIntentFallback(
                        Intent().setClassName(
                            "com.miui.powerkeeper",
                            "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
                        ).putExtra("package_name", packageName)
                            .putExtra("package_label", "AIDev Terminal")
                    )
                    2 -> openAppSettings()
                    3 -> requestIgnoreBatteryOptimization()
                }
            }
            .show()
    }

    private fun openIntentFallback(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: Throwable) {
            Toast.makeText(this, "无法直接打开该页面，已转到应用详情", Toast.LENGTH_SHORT).show()
            openAppSettings()
        }
    }

    private fun navItems(): List<Pair<String, () -> Unit>> = listOf(
        "终端" to { ShellActivity.open(this, ShellActivity.TAB_TERMINAL) },
        "文件" to { ShellActivity.open(this, ShellActivity.TAB_FILES) },
        "主题" to { ShellActivity.open(this, ShellActivity.TAB_SETTINGS) }
    )

    private fun bg(fill: Int, stroke: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(8).toFloat()
            if (stroke != 0) setStroke(dp(1), stroke)
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
