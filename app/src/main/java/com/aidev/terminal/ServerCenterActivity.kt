package com.aidev.terminal

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.widget.LinearLayout
import android.widget.ScrollView
import java.io.File

class ServerCenterActivity : Activity() {
    private lateinit var ui: AIDevUi

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = AIDevUi(this, getSharedPreferences("aidev_ui", MODE_PRIVATE))
        buildUi()
    }

    private fun buildUi() {
        val root = ui.pageRoot()
        AppNav.attach(this, ui, root, ServerCenterActivity::class.java)
        root.addView(ui.topBar("服务器中心", "常驻" to { KeepAliveService.start(this) }, "关闭" to { finish() }))

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(18), ui.dp(12), ui.dp(18), ui.dp(24))
            addView(ui.section("运行状态", "作为移动 Linux 服务器时最关键的状态"))
            addView(ui.rowOf(
                ui.statusCard("后台常驻", if (keepAliveEnabled()) "已启用" else "未启用", keepAliveEnabled()),
                ui.statusCard("电池优化", if (batteryIgnored()) "已忽略" else "受限制", batteryIgnored())
            ))
            addView(ui.rowOf(
                ui.statusCard("任务记录", "${taskCount()} 个", taskCount() > 0),
                ui.statusCard("Ubuntu", if (ubuntuInstalled()) "可用" else "未安装", ubuntuInstalled())
            ))

            addView(ui.section("服务操作", "端口、常驻、OpenCode Web 前端和局域网访问"))
            addView(ui.rowOf(
                ui.actionCard("监听端口", "查看当前本机服务端口", "PORT") { openTerminal("list-listen-ports\n") },
                ui.actionCard("访问诊断", "检查 127.0.0.1 服务", "HTTP") { openTerminal("check-local-server 3000\n") }
            ))
            addView(ui.rowOf(
                ui.actionCard("后台说明", "HyperOS 长后台建议", "KEEP") { openTerminal("keepalive-explain\ncheck-keepalive\n") },
                ui.actionCard("任务中心", "查看服务日志和任务", "TASK") { startActivity(Intent(this@ServerCenterActivity, TaskCenterActivity::class.java)) }
            ))

            addView(ui.section("服务器模式原则", "服务类任务不应该依赖前台终端页面"))
            addView(infoCard())
        }

        root.addView(ScrollView(this).apply { addView(content) }, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(ui.bottomNav(AppNav.bottom(this)))
        setContentView(root)
    }

    private fun infoCard() =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12))
            background = ui.infoPanelBackground()
            addView(ui.text("建议用 task-run 启动下载、AI 后端、Web 前端和构建任务，再从任务中心查看日志。", 13f, ui.palette.text))
            addView(ui.muted("同机浏览器访问 127.0.0.1；如果要让局域网设备访问，服务需要监听 0.0.0.0，同时 HyperOS 不能限制后台网络。").apply {
                setPadding(0, ui.dp(8), 0, 0)
            })
        }

    private fun keepAliveEnabled(): Boolean =
        getSharedPreferences("aidev_ui", MODE_PRIVATE).getBoolean("keepalive_auto", true)

    private fun batteryIgnored(): Boolean {
        if (Build.VERSION.SDK_INT < 23) return true
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun ubuntuInstalled(): Boolean = File(filesDir, "home/ubuntu-rootfs/.aidev-rootfs-ready").exists()
    private fun taskCount(): Int = File(filesDir, "home/tasks").listFiles { f -> f.name.endsWith(".meta") }?.size ?: 0

    private fun openTerminal(command: String) {
        AppNav.openTerminal(this, command)
    }

}
