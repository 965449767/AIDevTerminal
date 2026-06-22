package com.aidev.terminal

import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import android.graphics.Color
import android.graphics.Typeface
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File

class SshBookmarksPage : ShellPage {
    private var connections = listOf<SshConnection>()
    private var listContainer: LinearLayout? = null
    private var emptyHint: TextView? = null

    override fun create(activity: Activity, ui: AIDevUi, host: ShellHost): View {
        connections = SshConfigManager.getAll(activity).sortedByDescending { it.lastConnected }

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF111418.toInt())
            minimumWidth = ui.dp(340)
        }

        // Title bar
        val titleBar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ui.dp(12), ui.dp(10), ui.dp(8), ui.dp(10))
            addView(TextView(activity).apply {
                text = "SSH 连接管理"
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
            }, LinearLayout.LayoutParams(0, -1, 1f))
            addView(textButton(activity, ui, "+ 添加") {
                showAddDialog(activity, ui, host)
            }, LinearLayout.LayoutParams(-2, -1))
        }
        root.addView(titleBar)

        // Divider
        root.addView(View(activity).apply {
            setBackgroundColor(0xFF2A2E35.toInt())
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
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(0xFF6B7280.toInt())
            visibility = if (connections.isEmpty()) View.VISIBLE else View.GONE
        }
        root.addView(emptyHint)

        // Bottom button
        root.addView(textButton(activity, ui, "关闭") {
            (root.parent as? DialogInterface)?.dismiss()
        }.apply {
            setPadding(0, ui.dp(8), 0, ui.dp(8))
            gravity = Gravity.CENTER
            setBackgroundColor(0xFF1F2937.toInt())
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
            container.addView(connectionItem(activity, ui, host, conn))
        }
    }

    private fun connectionItem(activity: Activity, ui: AIDevUi, host: ShellHost, conn: SshConnection): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10))
            setBackgroundColor(Color.TRANSPARENT)
            isClickable = true
            setOnClickListener {
                attemptConnect(activity, host, conn)
            }
            setOnLongClickListener {
                showItemMenu(activity, ui, host, conn)
                true
            }

            addView(TextView(activity).apply {
                text = conn.name
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
            })
            addView(TextView(activity).apply {
                text = "${conn.user}@${conn.host}:${conn.port}"
                textSize = 13f
                setTextColor(0xFF00FF41.toInt())
            })
            if (conn.lastConnected > 0L) {
                addView(TextView(activity).apply {
                    val ago = (System.currentTimeMillis() - conn.lastConnected) / 1000
                    text = "上次连接: ${when {
                        ago < 60 -> "刚刚"
                        ago < 3600 -> "${ago / 60}分钟前"
                        ago < 86400 -> "${ago / 3600}小时前"
                        else -> "${ago / 86400}天前"
                    }}"
                    textSize = 12f
                    setTextColor(0xFF6B7280.toInt())
                })
            }
            // Divider
            addView(View(activity).apply {
                setBackgroundColor(0xFF2A2E35.toInt())
                layoutParams = LinearLayout.LayoutParams(-1, 1).apply { topMargin = ui.dp(6) }
            })
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
            AlertDialog.Builder(activity)
                .setTitle("SSH 客户端未安装")
                .setMessage("需要在 Ubuntu 中安装 openssh-client，是否继续？")
                .setPositiveButton("安装并连接") { _, _ ->
                    host.openTerminal("apt-get update -qq && apt-get install -y openssh-client && ${conn.connectCommand()}")
                }
                .setNegativeButton("取消", null)
                .show()
            return
        }
        SshConfigManager.touch(activity, conn.id)
        host.openTerminal(conn.connectCommand())
    }

    private fun showAddDialog(activity: Activity, ui: AIDevUi, host: ShellHost) {
        val nameInput = EditText(activity).apply { hint = "显示名称（如 我的服务器）"; setTextColor(Color.WHITE) }
        val hostInput = EditText(activity).apply { hint = "主机地址（IP 或域名）"; setTextColor(Color.WHITE) }
        val portInput = EditText(activity).apply { hint = "端口（默认 22）"; inputType = InputType.TYPE_CLASS_NUMBER; setTextColor(Color.WHITE) }
        val userInput = EditText(activity).apply { hint = "用户名（默认 root）"; setTextColor(Color.WHITE) }

        val form = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(8), ui.dp(4), ui.dp(8), ui.dp(4))
            addView(nameInput)
            addView(hostInput)
            addView(portInput)
            addView(userInput)
        }

        AlertDialog.Builder(activity)
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
        AlertDialog.Builder(activity)
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
        val nameInput = EditText(activity).apply { setText(conn.name); setTextColor(Color.WHITE) }
        val hostInput = EditText(activity).apply { setText(conn.host); setTextColor(Color.WHITE) }
        val portInput = EditText(activity).apply { setText(conn.port.toString()); inputType = InputType.TYPE_CLASS_NUMBER; setTextColor(Color.WHITE) }
        val userInput = EditText(activity).apply { setText(conn.user); setTextColor(Color.WHITE) }

        val form = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(8), ui.dp(4), ui.dp(8), ui.dp(4))
            addView(nameInput); addView(hostInput); addView(portInput); addView(userInput)
        }

        AlertDialog.Builder(activity)
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

    private fun textButton(activity: Activity, ui: AIDevUi, text: String, click: () -> Unit): TextView =
        TextView(activity).apply {
            this.text = text
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(8))
            setTextColor(0xFF00FF41.toInt())
            setOnClickListener { click() }
        }
}
