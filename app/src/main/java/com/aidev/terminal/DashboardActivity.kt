package com.aidev.terminal

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.PowerManager
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File

class DashboardActivity : Activity() {
    private lateinit var prefs: SharedPreferences
    private lateinit var ui: AIDevUi

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("aidev_ui", MODE_PRIVATE)
        ui = AIDevUi(this, prefs)
        if (prefs.getBoolean("keepalive_auto", true)) runCatching { KeepAliveService.start(this) }
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        ui = AIDevUi(this, prefs)
        buildUi()
    }

    private fun buildUi() {
        val root = ui.pageRoot()
        AppNav.attach(this, ui, root, DashboardActivity::class.java)
        val scrollContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(24))
        }
        scrollContent.addView(hero())
        scrollContent.addView(statusGrid())
        scrollContent.addView(sectionTitle("主要工作区", "进入终端、文件、任务、AI 与服务器模式"))
        scrollContent.addView(actionGrid())
        scrollContent.addView(sectionTitle("开发状态", "移动端 Android 开发工作站的关键状态"))
        scrollContent.addView(devStatus())
        root.addView(ScrollView(this).apply { addView(scrollContent) }, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(ui.bottomNav(AppNav.bottom(this)))
        setContentView(root)
    }

    private fun hero(): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = ui.card(hero = true)
            addView(TextView(this@DashboardActivity).apply {
                text = "AIDev Workstation"
                textSize = 26f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
            })
            addView(ui.heroSubtitle("手机上的 AI Native Linux 开发工作站"))
            addView(ui.heroMeta("终端 · Ubuntu · Android 调试 · AI Agent · 文件 · 长后台服务"))
        }.withMargins(bottom = 14)

    private fun statusGrid(): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(ui.rowOf(
                statusCard("Ubuntu", if (ubuntuInstalled()) "已安装" else "未安装", ubuntuInstalled()),
                statusCard("后台常驻", if (keepAliveGood()) "建议已开" else "需检查", keepAliveGood())
            ))
            addView(ui.rowOf(
                statusCard("任务", "${taskCount()} 个记录", taskCount() > 0),
                statusCard("存储", if (allFilesAllowed()) "完整访问" else "受限", allFilesAllowed())
            ))
        }

    private fun actionGrid(): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(ui.rowOf(
                actionCard("终端", "进入 Linux/Android Shell", "TER") { AppNav.openTerminal(this@DashboardActivity, "") },
                actionCard("文件", "双列开发文件管理", "FIL") { open(FileManagerActivity::class.java) }
            ))
            addView(ui.rowOf(
                actionCard("任务", "后台任务与日志", "TSK") { open(TaskCenterActivity::class.java) },
                actionCard("AI", "OpenCode 与代理中心", "AI") { open(AIAgentActivity::class.java) }
            ))
            addView(ui.rowOf(
                actionCard("服务器", "端口、常驻、服务诊断", "SRV") { open(ServerCenterActivity::class.java) },
                actionCard("设置", "主题、权限、工具链", "SET") { open(SettingsActivity::class.java) }
            ))
        }

    private fun devStatus(): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ui.infoPanelBackground()
            setPadding(dp(14), dp(12), dp(14), dp(12))
            addView(infoLine("Android 调试桥接", "使用 pmx/amx/getpropx/logcatx，避免 Ubuntu 内直接执行 /system/bin 命令"))
            addView(infoLine("服务访问", "同机浏览器访问 127.0.0.1；局域网访问需要服务监听 0.0.0.0"))
            addView(infoLine("长后台", "前台服务 + WakeLock + Wi‑Fi Lock；HyperOS 仍需手动设为无限制"))
        }.withMargins(bottom = 14)

    private fun statusCard(title: String, value: String, ok: Boolean): View = ui.statusCard(title, value, ok)

    private fun actionCard(title: String, desc: String, tag: String, action: () -> Unit): View = ui.actionCard(title, desc, tag, action)

    private fun sectionTitle(title: String, desc: String): View = ui.section(title, desc)

    private fun infoLine(title: String, body: String): View =
        ui.text("$title\n$body", 12f, ui.palette.text).apply { setPadding(0, dp(7), 0, dp(7)) }

    private fun open(cls: Class<out Activity>) = startActivity(Intent(this, cls))

    private fun ubuntuInstalled(): Boolean = File(filesDir, "home/ubuntu-rootfs/.aidev-rootfs-ready").exists()
    private fun taskCount(): Int = File(filesDir, "home/tasks").listFiles { f -> f.name.endsWith(".meta") }?.size ?: 0
    private fun allFilesAllowed(): Boolean = Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager()
    private fun keepAliveGood(): Boolean {
        val auto = prefs.getBoolean("keepalive_auto", true)
        if (Build.VERSION.SDK_INT < 23) return auto
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        return auto && pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun View.withMargins(bottom: Int = 0): View = apply {
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, dp(bottom)) }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
