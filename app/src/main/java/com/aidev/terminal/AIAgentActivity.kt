package com.aidev.terminal

import android.app.Activity
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import java.io.File

class AIAgentActivity : Activity() {
    private lateinit var ui: AIDevUi

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = AIDevUi(this, getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE))
        buildUi()
    }

    private fun buildUi() {
        val root = ui.pageRoot()
        AppNav.attach(this, ui, root, AIAgentActivity::class.java)
        root.addView(ui.topBar("AI 助手中心", "终端" to { openTerminal("env-menu\n") }, "关闭" to { finish() }))

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(18), ui.dp(12), ui.dp(18), ui.dp(24))
            addView(ui.section("OpenCode", "安装、启动、Web 前端和日志管理会逐步集中到这里"))
            addView(ui.rowOf(
                ui.listItem("安装状态", if (opencodeInstalled()) "可能已安装" else "未检测到", opencodeInstalled()),
                ui.listItem("配置状态", if (providerConfigured()) "有配置文件" else "待配置", providerConfigured())
            ))
            addView(ui.rowOf(
                ui.actionRow("安装 OpenCode", "调用官方安装入口") { openTerminal("install-aitool\n") },
                ui.actionRow("检测环境", "查看 AI/Web 通信与工具链") { openTerminal("check-dev-env\naidev-net-explain\n") }
            ))
            addView(ui.actionRow("查看日志", "打开终端查看任务输出") { AppNav.openTerminal(this@AIAgentActivity, "aidev-agent-log\n") })

            addView(ui.section("设计目标", "AI 中心不再让用户记命令，而是把安装、配置、启动、端口和日志组织成一组清晰操作"))
            addView(infoRow())
        }

        root.addView(ScrollView(this).apply { addView(content) }, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(ui.bottomNav(AppNav.bottom(this)))
        setContentView(root)
    }

    private fun infoRow() =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12))
            background = ui.surfaceBackground()
            addView(ui.text("后续这里会接入 OpenCode provider 检测、API Key 配置状态、Web UI 端口、打开浏览器和停止服务。", 13f, ui.palette.text))
            addView(ui.muted("当前版本先提供稳定入口和统一视觉结构，避免 AI 能力继续散落在设置和命令菜单里。").apply {
                setPadding(0, ui.dp(8), 0, 0)
            })
        }

    private fun opencodeInstalled(): Boolean =
        File(filesDir, "home/ubuntu-rootfs/root/.opencode/bin/opencode").exists() ||
            File(filesDir, "home/.opencode/bin/opencode").exists()

    private fun providerConfigured(): Boolean =
        File(filesDir, "home/ubuntu-rootfs/root/.config/opencode").exists() ||
            File(filesDir, "home/ubuntu-rootfs/root/.opencode").exists()

    private fun openTerminal(command: String) {
        AppNav.openTerminal(this, command)
    }

}
