package com.aidev.terminal

import android.app.Activity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.content.DialogInterface
import android.graphics.Typeface
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File

class SshBookmarksPage : ShellPage {
    private var connections = listOf<SshConnection>()
    private var listContainer: LinearLayout? = null
    private var emptyHint: TextView? = null
    private var ui: AIDevUi? = null
    var dismiss: (() -> Unit)? = null

    override fun create(activity: Activity, ui: AIDevUi, host: ShellHost): View {
        this.ui = ui
        connections = SshConfigManager.getAll(activity).sortedByDescending { it.lastConnected }

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ui.palette.bg)
            minimumWidth = ui.dp(340)
        }

        // Title bar
        root.addView(LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ui.dp(DesignTokens.SPACE_12), ui.dp(10), ui.dp(DesignTokens.SPACE_8), ui.dp(10))
            addView(TextView(activity).apply {
                text = "SSH 连接管理"
                textSize = DesignTokens.TEXT_H2
                setTypeface(null, Typeface.BOLD)
                setTextColor(ui.palette.text)
            }, LinearLayout.LayoutParams(0, -1, 1f))
            addView(TextView(activity).apply {
                text = "+ 添加"
                textSize = DesignTokens.TEXT_BODY
                gravity = Gravity.CENTER
                setPadding(ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_8), ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_8))
                setTextColor(ui.palette.accent)
                setOnClickListener { showAddDialog(activity, ui, host) }
            }, LinearLayout.LayoutParams(-2, -1))
        })

        // Divider
        root.addView(View(activity).apply {
            setBackgroundColor(ui.palette.outline)
            layoutParams = LinearLayout.LayoutParams(-1, 1)
        })

        // List
        val scroll = ScrollView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
        }
        listContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(listContainer)
        root.addView(scroll)

        // Empty hint
        emptyHint = TextView(activity).apply {
            text = "暂无 SSH 连接\n点击右上角「+ 添加」开始"
            textSize = DesignTokens.TEXT_BODY
            gravity = Gravity.CENTER
            setTextColor(ui.palette.muted)
            visibility = if (connections.isEmpty()) View.VISIBLE else View.GONE
        }
        root.addView(emptyHint)

        // Bottom button
        root.addView(TextView(activity).apply {
            text = "关闭"
            textSize = DesignTokens.TEXT_BODY
            gravity = Gravity.CENTER
            setPadding(0, ui.dp(DesignTokens.SPACE_8), 0, ui.dp(DesignTokens.SPACE_8))
            setTextColor(ui.palette.accent)
            setBackgroundColor(ui.palette.surfaceAlt)
            setOnClickListener { dismiss?.invoke() }
        })

        renderConnections(activity, ui, host)
        return root
    }

    private fun renderConnections(activity: Activity, ui: AIDevUi, host: ShellHost) {
        val container = listContainer ?: return
        container.removeAllViews()

        if (connections.isEmpty()) {
            emptyHint?.visibility = View.VISIBLE
            return
        }
        emptyHint?.visibility = View.GONE

        connections.forEach { conn ->
            val itemView = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(ui.dp(DesignTokens.SPACE_12), ui.dp(10), ui.dp(DesignTokens.SPACE_12), ui.dp(10))
                setOnClickListener {
                    attemptConnect(activity, host, conn)
                }
                setOnLongClickListener {
                    showItemMenu(activity, ui, host, conn)
                    true
                }
                addView(TextView(activity).apply {
                    text = conn.name
                    textSize = DesignTokens.TEXT_H2
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(ui.palette.text)
                })
                addView(TextView(activity).apply {
                    text = "${conn.user}@${conn.host}:${conn.port}"
                    textSize = DesignTokens.TEXT_BODY
                    setTextColor(ui.palette.accent)
                })
                if (conn.lastConnected > 0L) {
                    addView(TextView(activity).apply {
                        val ago = (System.currentTimeMillis() - conn.lastConnected) / 1000
                        text = when {
                            ago < 60 -> "刚刚"
                            ago < 3600 -> "${ago / 60}分钟前"
                            ago < 86400 -> "${ago / 3600}小时前"
                            else -> "${ago / 86400}天前"
                        }
                        textSize = DesignTokens.TEXT_CAPTION
                        setTextColor(ui.palette.muted)
                    })
                }
                addView(View(activity).apply {
                    setBackgroundColor(ui.palette.outline)
                    layoutParams = LinearLayout.LayoutParams(-1, 1).apply { topMargin = ui.dp(DesignTokens.SPACE_4) }
                })
            }
            container.addView(itemView)
        }
    }

    private fun attemptConnect(activity: Activity, host: ShellHost, conn: SshConnection) {
        val rootfs = File(activity.filesDir, "home/ubuntu-rootfs")
        val rootfsReady = File(rootfs, ".aidev-rootfs-ready").exists()
        if (!rootfsReady) {
            Toast.makeText(activity, "请先安装 Ubuntu", Toast.LENGTH_SHORT).show()
            return
        }
        val sshReady = File(rootfs, "usr/bin/ssh").exists()
        if (!sshReady) {
            showSshInstallPrompt(activity, host, conn)
            return
        }
        SshConfigManager.touch(activity, conn.id)
        host.openTerminal(conn.connectCommand())
    }

    private fun showSshInstallPrompt(activity: Activity, host: ShellHost, conn: SshConnection) {
        val palette = ui?.palette ?: return
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle("SSH 客户端未安装")
            .setMessage("需要在 Ubuntu 中安装 openssh-client，是否继续？")
            .setPositiveButton("安装并连接") { _, _ ->
                host.openTerminal("apt-get update -qq && apt-get install -y openssh-client && ${conn.connectCommand()}")
            }
            .setNegativeButton("取消", null)
            .create()
        dialog.show()
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setTextColor(palette.accent)
        dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setTextColor(palette.muted)
    }

    private fun showAddDialog(activity: Activity, ui: AIDevUi, host: ShellHost) {
        val nameInput = EditText(activity).apply { hint = "显示名称（如 我的服务器）"; setTextColor(ui.palette.text); setHintTextColor(ui.palette.muted); setBackgroundColor(ui.palette.surfaceAlt) }
        val hostInput = EditText(activity).apply { hint = "主机地址（IP 或域名）"; setTextColor(ui.palette.text); setHintTextColor(ui.palette.muted); setBackgroundColor(ui.palette.surfaceAlt) }
        val portInput = EditText(activity).apply { hint = "端口（默认 22）"; inputType = InputType.TYPE_CLASS_NUMBER; setTextColor(ui.palette.text); setHintTextColor(ui.palette.muted); setBackgroundColor(ui.palette.surfaceAlt) }
        val userInput = EditText(activity).apply { hint = "用户名（默认 root）"; setTextColor(ui.palette.text); setHintTextColor(ui.palette.muted); setBackgroundColor(ui.palette.surfaceAlt) }

        val form = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(DesignTokens.SPACE_8), ui.dp(DesignTokens.SPACE_4), ui.dp(DesignTokens.SPACE_8), ui.dp(DesignTokens.SPACE_4))
            addView(nameInput); addView(hostInput); addView(portInput); addView(userInput)
        }

        MaterialAlertDialogBuilder(activity)
            .setTitle("添加 SSH 连接")
            .setView(form)
            .setPositiveButton("添加") { _, _ ->
                val name = nameInput.text.trim().toString()
                val hostStr = hostInput.text.trim().toString()
                if (name.isEmpty() || hostStr.isEmpty()) {
                    Toast.makeText(activity, "名称和主机地址不能为空", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val conn = SshConnection(
                    name = name,
                    host = hostStr,
                    port = portInput.text.trim().toString().toIntOrNull() ?: 22,
                    user = userInput.text.trim().toString().ifEmpty { "root" }
                )
                SshConfigManager.add(activity, conn)
                connections = SshConfigManager.getAll(activity).sortedByDescending { it.lastConnected }
                renderConnections(activity, ui, host)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showItemMenu(activity: Activity, ui: AIDevUi, host: ShellHost, conn: SshConnection) {
        val items = arrayOf("编辑", "删除", "取消")
        MaterialAlertDialogBuilder(activity)
            .setTitle(conn.name)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showEditDialog(activity, ui, host, conn)
                    1 -> {
                        SshConfigManager.delete(activity, conn.id)
                        connections = SshConfigManager.getAll(activity).sortedByDescending { it.lastConnected }
                        renderConnections(activity, ui, host)
                    }
                }
            }
            .show()
    }

    private fun showEditDialog(activity: Activity, ui: AIDevUi, host: ShellHost, conn: SshConnection) {
        val nameInput = EditText(activity).apply { setText(conn.name); setTextColor(ui.palette.text); setHintTextColor(ui.palette.muted); setBackgroundColor(ui.palette.surfaceAlt) }
        val hostInput = EditText(activity).apply { setText(conn.host); setTextColor(ui.palette.text); setHintTextColor(ui.palette.muted); setBackgroundColor(ui.palette.surfaceAlt) }
        val portInput = EditText(activity).apply { setText(conn.port.toString()); inputType = InputType.TYPE_CLASS_NUMBER; setTextColor(ui.palette.text); setHintTextColor(ui.palette.muted); setBackgroundColor(ui.palette.surfaceAlt) }
        val userInput = EditText(activity).apply { setText(conn.user); setTextColor(ui.palette.text); setHintTextColor(ui.palette.muted); setBackgroundColor(ui.palette.surfaceAlt) }

        val form = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(DesignTokens.SPACE_8), ui.dp(DesignTokens.SPACE_4), ui.dp(DesignTokens.SPACE_8), ui.dp(DesignTokens.SPACE_4))
            addView(nameInput); addView(hostInput); addView(portInput); addView(userInput)
        }

        MaterialAlertDialogBuilder(activity)
            .setTitle("编辑 SSH 连接")
            .setView(form)
            .setPositiveButton("保存") { _, _ ->
                val name = nameInput.text.trim().toString()
                val hostStr = hostInput.text.trim().toString()
                if (name.isEmpty() || hostStr.isEmpty()) {
                    Toast.makeText(activity, "名称和主机地址不能为空", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val updated = conn.copy(
                    name = name,
                    host = hostStr,
                    port = portInput.text.trim().toString().toIntOrNull() ?: conn.port,
                    user = userInput.text.trim().toString().ifEmpty { "root" }
                )
                SshConfigManager.update(activity, updated)
                connections = SshConfigManager.getAll(activity).sortedByDescending { it.lastConnected }
                renderConnections(activity, ui, host)
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
