package com.aidev.terminal

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import java.io.File

private data class EmbeddedTermSession(
    val id: Int,
    var title: String,
    val session: TerminalSession,
    var aiSession: Boolean = false
)

private data class EmbeddedVirtualKey(
    val label: String,
    val input: String,
    val swipeCommand: String = "",
    val id: String = label
)

class EmbeddedTerminalPage : ShellPage {
    private companion object {
        const val DEFAULT_FONT_SP = 15f
        const val MIN_FONT_SP = 10f
        const val MAX_FONT_SP = 24f
    }

    private var activity: Activity? = null
    private var ui: AIDevUi? = null
    private var terminalView: TerminalView? = null
    private lateinit var tabBar: LinearLayout
    private lateinit var statusText: TextView
    private var session: TerminalSession? = null
    private var current: EmbeddedTermSession? = null
    private var homeDir: File? = null
    private val sessions = mutableListOf<EmbeddedTermSession>()
    private var ctrlLatched = false
    private var autoBootstrapDispatched = false
    private var pendingFontSp = DEFAULT_FONT_SP
    private var fontApplyScheduled = false

    override fun create(activity: Activity, ui: AIDevUi, host: ShellHost): View {
        this.activity = activity
        this.ui = ui
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }
        root.addView(topBar(activity, ui, host), LinearLayout.LayoutParams(-1, ui.dp(40)))
        tabBar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(ui.dp(6), ui.dp(2), ui.dp(6), ui.dp(2))
        }
        root.addView(HorizontalScrollView(activity).apply {
            isHorizontalScrollBarEnabled = false
            setBackgroundColor(0xFF0B0E12.toInt())
            addView(tabBar)
        }, LinearLayout.LayoutParams(-1, ui.dp(32)))
        root.addView(statusBar(activity, ui), LinearLayout.LayoutParams(-1, ui.dp(24)))
        terminalView = TerminalView(activity, null).apply {
            setBackgroundColor(Color.BLACK)
            isFocusable = true
            isFocusableInTouchMode = true
            pendingFontSp = currentFontSp(activity)
            setTextSize(fontPx(activity))
            setTerminalViewClient(viewClient(activity))
        }
        root.addView(terminalView, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(keys(activity, ui), LinearLayout.LayoutParams(-1, ui.dp(70)))
        ensureSession(activity)
        terminalView?.postDelayed({
            consumePendingCommand()
            maybeAutoBootstrapUbuntu(activity)
        }, 600)
        return root
    }

    private fun statusBar(activity: Activity, ui: AIDevUi): View =
        TextView(activity).apply {
            statusText = this
            text = terminalStatus(activity)
            textSize = 11f
            gravity = Gravity.CENTER_VERTICAL
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(0xFF9CA3AF.toInt())
            setBackgroundColor(0xFF0A0D12.toInt())
            setPadding(ui.dp(12), 0, ui.dp(12), 0)
            setOnClickListener {
                AlertDialog.Builder(activity)
                    .setTitle("终端状态")
                    .setMessage("Ubuntu：自动进入已开启\n字号：${currentFontSp(activity).toInt()}sp\n运行：PRoot\n手势：双指缩放已开启\n虚拟键：点击为主功能，上滑为拓展功能，长按可自定义")
                    .setPositiveButton("知道了", null)
                    .show()
            }
            setOnLongClickListener {
                showFontDialog(activity)
                true
            }
        }

    private fun terminalStatus(activity: Activity): String =
        "Ubuntu · ${currentFontSp(activity).toInt()}sp · PRoot · Auto"

    private fun refreshStatus(activity: Activity) {
        if (::statusText.isInitialized) statusText.text = terminalStatus(activity)
    }

    override fun onSelected(activity: Activity, view: View) {
        this.activity = activity
        ensureSession(activity)
        terminalView?.requestFocus()
        consumePendingCommand()
        terminalView?.postDelayed({ maybeAutoBootstrapUbuntu(activity) }, 600)
    }

    private fun topBar(activity: Activity, ui: AIDevUi, host: ShellHost): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ui.dp(12), 0, ui.dp(8), 0)
            setBackgroundColor(0xFF111418.toInt())
            addView(ui.text("终端", 15f, Color.WHITE, bold = true).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(0, -1, 1f))
            addView(button(activity, ui, "+") { newSession(activity) }, LinearLayout.LayoutParams(ui.dp(42), ui.dp(30)))
            addView(button(activity, ui, "退出") { host.switchTab(ShellActivity.TAB_DASHBOARD) }, LinearLayout.LayoutParams(ui.dp(54), ui.dp(30)).apply {
                leftMargin = ui.dp(4)
            })
            addView(button(activity, ui, "更多") { showTerminalTopMore(activity, host) }, LinearLayout.LayoutParams(ui.dp(54), ui.dp(30)).apply {
                leftMargin = ui.dp(4)
            })
        }

    private fun agentBar(activity: Activity, ui: AIDevUi): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xFF05070A.toInt())
            setPadding(ui.dp(6), ui.dp(3), ui.dp(6), ui.dp(3))
            addView(button(activity, ui, "AI") { com.aidev.terminal.opencode.OpencodeNativePanel.showHome(activity) }.apply {
                setOnLongClickListener {
                    sendAgentCommand("aidev-opencode")
                    true
                }
            }, LinearLayout.LayoutParams(0, ui.dp(30), 1f).apply { setMargins(0, 0, ui.dp(3), 0) })
            addView(button(activity, ui, "上下文") { sendAgentCommand("aidev-agent-context") }, LinearLayout.LayoutParams(0, ui.dp(30), 1f).apply { setMargins(ui.dp(3), 0, ui.dp(3), 0) })
            addView(button(activity, ui, "任务") { com.aidev.terminal.opencode.OpencodeNativePanel.showActiveTodo(activity) }.apply {
                setOnLongClickListener {
                    send("task-list")
                    true
                }
            }, LinearLayout.LayoutParams(0, ui.dp(30), 1f).apply { setMargins(ui.dp(3), 0, ui.dp(3), 0) })
            addView(button(activity, ui, "事件") { com.aidev.terminal.opencode.OpencodeNativePanel.showEventConsole(activity) }.apply {
                setOnLongClickListener {
                    send("ls -lt \"${'$'}AIDEV_HOME/tasks\"/*.log 2>/dev/null | head -20")
                    true
                }
            }, LinearLayout.LayoutParams(0, ui.dp(30), 1f).apply { setMargins(ui.dp(3), 0, ui.dp(3), 0) })
            addView(button(activity, ui, "更多") { showAgentMenu(activity) }, LinearLayout.LayoutParams(0, ui.dp(30), 1f).apply { setMargins(ui.dp(3), 0, 0, 0) })
        }

    private fun showTerminalTopMore(activity: Activity, host: ShellHost) {
        showGroupedActionMenu(activity, "更多设置", "recent_terminal_more", listOf(
            "会话 · 新建会话" to { newSession(activity) },
            "会话 · 重命名会话" to { renameCurrentSession(activity) },
            "会话 · 关闭当前会话" to { closeCurrent(activity) },
            "导航 · 退出到工作台" to { host.switchTab(ShellActivity.TAB_DASHBOARD) },
            "终端 · 进入 Ubuntu" to { send("ubuntu") },
            "终端 · 清屏" to { send("clear") },
            "OpenCode · CLI 界面" to { sendAgentCommand("aidev-opencode") },
            "OpenCode · Serve 后台服务" to { sendAgentCommand("task-run opencode-serve 'opencode serve --port 4096 --hostname 127.0.0.1'") },
            "OpenCode · 原生协议面板" to { com.aidev.terminal.opencode.OpencodeNativePanel.showHome(activity) },
            "协议 · 协议状态详情" to {
                com.aidev.terminal.opencode.OpencodeStatusBadge.showDetail(
                    activity,
                    com.aidev.terminal.opencode.OpencodeManager.lastStatus
                )
            },
            "协议 · 重新探测健康" to { com.aidev.terminal.opencode.OpencodeManager.probeNow(activity.applicationContext) },
            "设置 · 字号" to { showFontDialog(activity) },
            "设置 · 紧凑显示" to { applyFontPreset(activity, 12f) },
            "设置 · 大字显示" to { applyFontPreset(activity, 18f) },
            "系统 · 端口检查" to { send("list-listen-ports") }
        ))
    }

    private fun showAgentMenu(activity: Activity) {
        showGroupedActionMenu(activity, "AI 代理终端", "recent_agent_more", listOf(
            "原生 · OpenCode 面板" to { com.aidev.terminal.opencode.OpencodeNativePanel.showHome(activity) },
            "原生 · 新建会话并提问" to { com.aidev.terminal.opencode.OpencodeNativePanel.createSessionAndPrompt(activity) },
            "原生 · 原生多会话" to { com.aidev.terminal.opencode.OpencodeNativePanel.showSessions(activity) },
            "原生 · 当前 TODO" to { com.aidev.terminal.opencode.OpencodeNativePanel.showActiveTodo(activity) },
            "原生 · 当前 Diff" to { com.aidev.terminal.opencode.OpencodeNativePanel.showActiveDiff(activity) },
            "原生 · SSE 事件监听" to { com.aidev.terminal.opencode.OpencodeNativePanel.showEventConsole(activity) },
            "原生 · 中止当前会话" to { com.aidev.terminal.opencode.OpencodeNativePanel.abortActive(activity) },
            "启动 · OpenCode 前台" to { sendAgentCommand("aidev-opencode") },
            "启动 · OpenCode 后台任务" to { sendAgentCommand("aidev-opencode-task") },
            "启动 · OpenCode Serve" to { sendAgentCommand("task-run opencode-serve 'opencode serve'") },
            "上下文 · 生成代理上下文" to { sendAgentCommand("aidev-agent-context") },
            "上下文 · 导出上下文文件" to { sendAgentCommand("aidev-agent-context-file") },
            "日志 · 代理日志摘要" to { send("aidev-agent-summary") },
            "检查 · OpenCode 启动检查" to { sendAgentCommand("aidev-opencode-preflight") },
            "项目 · 当前项目目录" to { sendAgentCommand("pwd && git status --short --branch 2>/dev/null || true && ls -la") },
            "日志 · 最近任务日志" to { send("ls -lt \"${'$'}AIDEV_HOME/tasks\"/*.log 2>/dev/null | head -20") },
            "系统 · 监听端口" to { send("list-listen-ports") },
            "显示 · 终端紧凑显示" to { applyFontPreset(activity, 12f) },
            "显示 · 终端大字显示" to { applyFontPreset(activity, 18f) }
        ))
    }

    private fun showGroupedActionMenu(activity: Activity, title: String, prefKey: String, actions: List<Pair<String, () -> Unit>>) {
        val recent = recentMenuLabels(activity, prefKey).filter { label -> actions.any { it.first == label } }
        val display = (recent.map { "最近 · ${it.substringAfter(" · ")}" to it } + actions.filterNot { recent.contains(it.first) }.map { it.first to it.first })
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setItems(display.map { it.first }.toTypedArray()) { _, which ->
                val original = display[which].second
                rememberMenuLabel(activity, prefKey, original)
                actions.firstOrNull { it.first == original }?.second?.invoke()
            }
            .setNeutralButton("搜索") { _, _ -> searchGroupedActionMenu(activity, title, prefKey, actions) }
            .show()
    }

    private fun searchGroupedActionMenu(activity: Activity, title: String, prefKey: String, actions: List<Pair<String, () -> Unit>>) {
        val edit = EditText(activity).apply { hint = "输入 ai、log、端口、会话、显示" }
        AlertDialog.Builder(activity)
            .setTitle("搜索$title")
            .setView(edit)
            .setPositiveButton("搜索") { _, _ ->
                val keyword = edit.text.toString().trim()
                val matches = actions.filter { keyword.isBlank() || it.first.contains(keyword, true) }.take(30)
                if (matches.isEmpty()) return@setPositiveButton Toast.makeText(activity, "没有匹配项", Toast.LENGTH_SHORT).show()
                AlertDialog.Builder(activity)
                    .setTitle("搜索结果")
                    .setItems(matches.map { it.first }.toTypedArray()) { _, which ->
                        rememberMenuLabel(activity, prefKey, matches[which].first)
                        matches[which].second.invoke()
                    }
                    .show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun recentMenuLabels(activity: Activity, key: String): List<String> =
        activity.getSharedPreferences("aidev_ui", Activity.MODE_PRIVATE).getString(key, "")?.lines()?.filter { it.isNotBlank() }.orEmpty().takeLast(3).reversed()

    private fun rememberMenuLabel(activity: Activity, key: String, label: String) {
        val prefs = activity.getSharedPreferences("aidev_ui", Activity.MODE_PRIVATE)
        val old = prefs.getString(key, "")?.lines()?.filter { it.isNotBlank() && it != label }.orEmpty()
        prefs.edit().putString(key, (old + label).takeLast(6).joinToString("\n")).apply()
    }

    private fun sendAgentCommand(command: String) {
        val dir = currentProjectDir()
        if (dir != null) send("cd \"${dir.absolutePath}\" && $command") else send(command)
    }

    private fun currentProjectDir(): File? =
        activity?.getSharedPreferences("aidev_ui", Activity.MODE_PRIVATE)
            ?.getString("current_project_path", "")
            ?.takeIf { it.isNotBlank() }
            ?.let { File(it) }
            ?.takeIf { it.isDirectory }

    private fun applyFontPreset(activity: Activity, sp: Float) {
        val value = sp.coerceIn(MIN_FONT_SP, MAX_FONT_SP)
        activity.getSharedPreferences("aidev_ui", Activity.MODE_PRIVATE).edit().putFloat("font_sp", value).apply()
        pendingFontSp = value
        terminalView?.setTextSize((value * activity.resources.displayMetrics.scaledDensity).toInt())
        terminalView?.onScreenUpdated()
        refreshStatus(activity)
    }

    private fun keys(activity: Activity, ui: AIDevUi): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF111418.toInt())
            setPadding(ui.dp(4), ui.dp(3), ui.dp(4), ui.dp(3))
            embeddedKeys(activity).chunked(6).forEach { rowKeys ->
                addView(LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    rowKeys.forEachIndexed { index, key ->
                        addView(shortcutKey(activity, ui, key), LinearLayout.LayoutParams(0, ui.dp(30), 1f).apply {
                            setMargins(if (index == 0) 0 else ui.dp(2), ui.dp(1), ui.dp(2), ui.dp(1))
                        })
                    }
                }, LinearLayout.LayoutParams(-1, 0, 1f))
            }
        }

    private fun embeddedKeys(activity: Activity): List<EmbeddedVirtualKey> {
        val prefs = activity.getSharedPreferences("aidev_ui", Activity.MODE_PRIVATE)
        val defaults = listOf(
            EmbeddedVirtualKey("ESC", "\u001B", "clear", "esc"),
            EmbeddedVirtualKey("CTRL", "__CTRL__", "", "ctrl"),
            EmbeddedVirtualKey("↑", "\u001B[A", "history", "up"),
            EmbeddedVirtualKey("TAB", "\t", "help", "tab"),
            EmbeddedVirtualKey("/", "/", "cd /", "slash"),
            EmbeddedVirtualKey("-", "-", "cd -", "dash"),
            EmbeddedVirtualKey("C", "c", "clear", "c"),
            EmbeddedVirtualKey("←", "\u001B[D", "\u001B[H", "left"),
            EmbeddedVirtualKey("↓", "\u001B[B", "ls", "down"),
            EmbeddedVirtualKey("→", "\u001B[C", "\u001B[F", "right"),
            EmbeddedVirtualKey("|", "|", "grep ", "pipe"),
            EmbeddedVirtualKey("SPC", " ", "pwd", "space")
        )
        val overrides = parseKeyOverrides(prefs.getString("terminal_key_overrides", "") ?: "")
        val customizedDefaults = defaults.map { key -> overrides[key.id] ?: key }
        val custom = parseCustomKeys(prefs.getString("terminal_custom_keys", "") ?: "")
        return (customizedDefaults + custom).take(12)
    }

    private fun shortcutKey(activity: Activity, ui: AIDevUi, key: EmbeddedVirtualKey): TextView =
        TextView(activity).apply {
            text = key.label
            textSize = 11f
            setTextColor(0xFFD1D5DB.toInt())
            gravity = Gravity.CENTER
            includeFontPadding = false
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(if (key.input == "__CTRL__" && ctrlLatched) 0xFF374151.toInt() else 0xFF1F2937.toInt())
                cornerRadius = ui.dp(8).toFloat()
            }
            var downY = 0f
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downY = event.rawY
                        false
                    }
                    MotionEvent.ACTION_UP -> {
                        val swipeUp = downY - event.rawY > ui.dp(24)
                        if (swipeUp && key.swipeCommand.isNotBlank()) {
                            sendSwipeAction(key.swipeCommand)
                            true
                        } else false
                    }
                    else -> false
                }
            }
            setOnClickListener { handleVirtualKeyTap(activity, key) }
            setOnLongClickListener {
                showVirtualKeyMenu(activity, key)
                true
            }
        }

    private fun handleVirtualKeyTap(activity: Activity, key: EmbeddedVirtualKey) {
        if (key.input == "__CTRL__") {
            ctrlLatched = !ctrlLatched
            refreshKeyboard(activity)
            return
        }
        val input = key.input
        if (input.isEmpty()) return
        if (ctrlLatched && input.length == 1) {
            val ch = input[0].uppercaseChar()
            if (ch in '@'..'_') {
                val b = byteArrayOf((ch.code and 0x1F).toByte())
                session?.write(b, 0, 1)
                ctrlLatched = false
                refreshKeyboard(activity)
                return
            }
        }
        session?.write(input)
        if (ctrlLatched) {
            ctrlLatched = false
            refreshKeyboard(activity)
        }
    }

    private fun sendSwipeAction(action: String) {
        if (action.isEmpty()) return
        if (action.startsWith("\u001B")) {
            session?.write(action)
        } else {
            send(action)
        }
    }

    private fun refreshKeyboard(activity: Activity) {
        val uiRef = ui ?: return
        val parent = terminalView?.parent as? LinearLayout ?: return
        val keyboard = parent.getChildAt(parent.childCount - 1)
        val idx = parent.indexOfChild(keyboard)
        if (idx >= 0) {
            parent.removeViewAt(idx)
            parent.addView(keys(activity, uiRef), idx, LinearLayout.LayoutParams(-1, uiRef.dp(70)))
        }
    }

    private fun showExtraKeysMenu(activity: Activity) {
        val prefs = activity.getSharedPreferences("aidev_ui", Activity.MODE_PRIVATE)
        val keys = listOf(
            EmbeddedVirtualKey("HOME", "\u001b[H"),
            EmbeddedVirtualKey("END", "\u001b[F"),
            EmbeddedVirtualKey("PGUP", "\u001b[5~"),
            EmbeddedVirtualKey("PGDN", "\u001b[6~"),
            EmbeddedVirtualKey("~", "~"),
            EmbeddedVirtualKey("清屏", "clear\n"),
            EmbeddedVirtualKey("Ubuntu", "ubuntu\n"),
            EmbeddedVirtualKey("OpenCode CLI", "aidev-opencode\n"),
            EmbeddedVirtualKey("任务", "task-list\n")
        ).toMutableList()
        keys.addAll(parseCustomKeys(prefs.getString("terminal_custom_keys", "") ?: ""))
        AlertDialog.Builder(activity)
            .setTitle("扩展键盘更多")
            .setItems(keys.map { it.label }.toTypedArray()) { _, which -> session?.write(keys[which].input) }
            .show()
    }

    private fun showVirtualKeyMenu(activity: Activity, key: EmbeddedVirtualKey) {
        val swipe = key.swipeCommand.ifBlank { "未设置" }
        AlertDialog.Builder(activity)
            .setTitle("${key.label} 键")
            .setItems(arrayOf("执行上滑功能：$swipe", "自定义此键", "更多快捷键")) { _, which ->
                when (which) {
                    0 -> sendSwipeAction(key.swipeCommand)
                    1 -> editVirtualKey(activity, key)
                    2 -> showExtraKeysMenu(activity)
                }
            }
            .show()
    }

    private fun editVirtualKey(activity: Activity, key: EmbeddedVirtualKey) {
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui?.dp(20) ?: 20, ui?.dp(10) ?: 10, ui?.dp(20) ?: 20, 0)
        }
        val label = EditText(activity).apply {
            hint = "按钮名称"
            setText(key.label)
        }
        val tap = EditText(activity).apply {
            hint = "点击输入，例如 c、\\t、\\e[A"
            setText(encodeKeyInput(key.input))
        }
        val swipe = EditText(activity).apply {
            hint = "上滑命令，例如 clear、pwd、grep "
            setText(encodeKeyInput(key.swipeCommand))
        }
        box.addView(label)
        box.addView(tap)
        box.addView(swipe)
        AlertDialog.Builder(activity)
            .setTitle("自定义虚拟键")
            .setView(box)
            .setPositiveButton("保存") { _, _ ->
                saveKeyOverride(
                    activity,
                    key.id,
                    EmbeddedVirtualKey(
                        label.text.toString().trim().ifBlank { key.label }.take(8),
                        decodeKeyInput(tap.text.toString()),
                        decodeKeyInput(swipe.text.toString()),
                        key.id
                    )
                )
                refreshKeyboard(activity)
            }
            .setNeutralButton("恢复默认") { _, _ ->
                removeKeyOverride(activity, key.id)
                refreshKeyboard(activity)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun parseCustomKeys(raw: String): List<EmbeddedVirtualKey> =
        raw.lines().mapNotNull { line ->
            val parts = line.split("\t")
            val label = parts.getOrNull(0)?.trim().orEmpty()
            val input = parts.getOrNull(1).orEmpty()
            val swipe = parts.getOrNull(2).orEmpty()
            if (label.isEmpty() || input.isEmpty()) null else EmbeddedVirtualKey(label.take(8), decodeKeyInput(input), decodeKeyInput(swipe), "custom_$label")
        }.take(8)

    private fun parseKeyOverrides(raw: String): Map<String, EmbeddedVirtualKey> =
        raw.lines().mapNotNull { line ->
            val parts = line.split("\t")
            val id = parts.getOrNull(0)?.trim().orEmpty()
            val label = parts.getOrNull(1)?.trim().orEmpty()
            val input = parts.getOrNull(2).orEmpty()
            val swipe = parts.getOrNull(3).orEmpty()
            if (id.isEmpty() || label.isEmpty()) null else id to EmbeddedVirtualKey(label.take(8), decodeKeyInput(input), decodeKeyInput(swipe), id)
        }.toMap()

    private fun saveKeyOverride(activity: Activity, id: String, key: EmbeddedVirtualKey) {
        val prefs = activity.getSharedPreferences("aidev_ui", Activity.MODE_PRIVATE)
        val old = prefs.getString("terminal_key_overrides", "") ?: ""
        val lines = old.lines().filter { it.isNotBlank() && it.substringBefore("\t") != id }
        val line = listOf(id, key.label, encodeKeyInput(key.input), encodeKeyInput(key.swipeCommand)).joinToString("\t")
        prefs.edit().putString("terminal_key_overrides", (lines + line).joinToString("\n")).apply()
    }

    private fun removeKeyOverride(activity: Activity, id: String) {
        val prefs = activity.getSharedPreferences("aidev_ui", Activity.MODE_PRIVATE)
        val old = prefs.getString("terminal_key_overrides", "") ?: ""
        val lines = old.lines().filter { it.isNotBlank() && it.substringBefore("\t") != id }
        prefs.edit().putString("terminal_key_overrides", lines.joinToString("\n")).apply()
    }

    private fun decodeKeyInput(input: String): String =
        input.replace("\\n", "\n").replace("\\t", "\t").replace("\\e", "\u001b")

    private fun encodeKeyInput(input: String): String =
        input.replace("\u001b", "\\e").replace("\n", "\\n").replace("\t", "\\t")

    private fun fontPx(activity: Activity): Int =
        (currentFontSp(activity) * activity.resources.displayMetrics.scaledDensity).toInt()

    private fun currentFontSp(activity: Activity): Float =
        activity.getSharedPreferences("aidev_ui", Activity.MODE_PRIVATE)
            .getFloat("font_sp", DEFAULT_FONT_SP)
            .coerceIn(MIN_FONT_SP, MAX_FONT_SP)

    private fun showFontDialog(activity: Activity) {
        val prefs = activity.getSharedPreferences("aidev_ui", Activity.MODE_PRIVATE)
        val current = currentFontSp(activity)
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui?.dp(20) ?: 20, ui?.dp(10) ?: 10, ui?.dp(20) ?: 20, 0)
        }
        val value = TextView(activity).apply {
            text = "${current.toInt()}sp"
            textSize = 18f
            setTextColor(ui?.palette?.text ?: Color.WHITE)
        }
        val seek = SeekBar(activity).apply {
            max = (MAX_FONT_SP - MIN_FONT_SP).toInt()
            progress = current.toInt() - MIN_FONT_SP.toInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    value.text = "${MIN_FONT_SP.toInt() + progress}sp"
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
                val sp = (MIN_FONT_SP.toInt() + seek.progress).toFloat()
                prefs.edit().putFloat("font_sp", sp).apply()
                pendingFontSp = sp
                terminalView?.setTextSize((sp * activity.resources.displayMetrics.scaledDensity).toInt())
                terminalView?.onScreenUpdated()
                refreshStatus(activity)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun button(activity: Activity, ui: AIDevUi, label: String, click: () -> Unit): TextView =
        TextView(activity).apply {
            text = label
            textSize = 12f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            includeFontPadding = false
            setPadding(ui.dp(4), 0, ui.dp(4), 0)
            background = roundedButtonBg()
            setOnClickListener {
                ui.pulse()
                click()
            }
        }

    private fun roundedButtonBg(): android.graphics.drawable.GradientDrawable =
        android.graphics.drawable.GradientDrawable().apply {
            setColor(0xFF1C2430.toInt())
            cornerRadius = (activity?.resources?.displayMetrics?.density ?: 1f) * 9f
            setStroke(((activity?.resources?.displayMetrics?.density ?: 1f) * 1f).toInt().coerceAtLeast(1), 0xFF334155.toInt())
        }

    private fun ensureSession(activity: Activity) {
        val shellAssets = TerminalShellAssets.ensure(activity)
        homeDir = shellAssets.home
        val entry = shellAssets.entry
        if (current != null) {
            session = current?.session
            terminalView?.attachSession(session)
            terminalView?.requestFocus()
            refreshTabs(activity)
            return
        }
        newSession(activity)
    }

    private fun createTerminalSession(activity: Activity, id: Int): TerminalSession {
        val rc = File(homeDir, ".aidevrc")
        val entry = File(homeDir, ".aidev_shell_entry")
        val nativeDir = activity.applicationInfo.nativeLibraryDir
        val aidevBin = File(homeDir, "dev-env/bin").absolutePath
        val prootLibDir = File(homeDir, "proot-lib").absolutePath
        val env = arrayOf(
            "TERM=xterm-256color",
            "COLORTERM=truecolor",
            "HOME=${homeDir!!.absolutePath}",
            "PWD=${homeDir!!.absolutePath}",
            "TMPDIR=${File(activity.cacheDir, "tmp").apply { mkdirs() }.absolutePath}",
            "AIDEV_HOME=${homeDir!!.absolutePath}",
            "AIDEV_BIN=$aidevBin",
            "AIDEV_NATIVE=$nativeDir",
            "AIDEV_PROOT=$nativeDir/libproot.so",
            "AIDEV_PROOT_LOADER=$nativeDir/libproot_loader.so",
            "PROOT_LOADER=$nativeDir/libproot_loader.so",
            "PROOT_TMP_DIR=${File(activity.cacheDir, "proot_tmp").apply { mkdirs() }.absolutePath}",
            "LD_LIBRARY_PATH=$prootLibDir:$nativeDir",
            "ENV=${rc.absolutePath}",
            "PATH=$aidevBin:/system/bin:/system/xbin"
        )
        return TerminalSession("/system/bin/sh", homeDir!!.absolutePath, arrayOf("sh", entry.absolutePath), env, 5000, sessionClient(activity)).apply {
            mSessionName = "AIDev-Shell-$id"
        }
    }

    private fun newSession(activity: Activity) {
        homeDir = File(activity.filesDir, "home").apply { mkdirs() }
        val id = (sessions.maxOfOrNull { it.id } ?: 0) + 1
        val item = EmbeddedTermSession(id, "会话$id", createTerminalSession(activity, id))
        sessions.add(item)
        switchSession(activity, item)
    }

    private fun switchSession(activity: Activity, item: EmbeddedTermSession) {
        current = item
        session = item.session
        terminalView?.attachSession(item.session)
        terminalView?.requestFocus()
        terminalView?.onScreenUpdated()
        refreshTabs(activity)
    }

    private fun closeCurrent(activity: Activity) {
        val item = current ?: return
        if (item.aiSession) {
            AlertDialog.Builder(activity)
                .setTitle("关闭 AI 会话？")
                .setMessage("该会话已标记为 AI 代理会话，可能正在执行长时间代码任务。确认关闭会终止当前终端会话。")
                .setPositiveButton("确认关闭") { _, _ -> closeCurrentForce(activity) }
                .setNegativeButton("取消", null)
                .show()
            return
        }
        closeCurrentForce(activity)
    }

    private fun closeCurrentForce(activity: Activity) {
        val item = current ?: return
        item.session.finishIfRunning()
        sessions.remove(item)
        current = null
        session = null
        if (sessions.isEmpty()) {
            newSession(activity)
        } else {
            switchSession(activity, sessions.last())
        }
    }

    private fun newAiSession(activity: Activity) {
        homeDir = File(activity.filesDir, "home").apply { mkdirs() }
        val id = (sessions.maxOfOrNull { it.id } ?: 0) + 1
        val item = EmbeddedTermSession(id, "AI-会话$id", createTerminalSession(activity, id), aiSession = true)
        sessions.add(item)
        switchSession(activity, item)
        terminalView?.postDelayed({ sendAgentCommand("aidev-opencode-preflight") }, 250)
    }

    private fun renameCurrentSession(activity: Activity) {
        val item = current ?: return
        val edit = EditText(activity).apply {
            setText(item.title)
            selectAll()
        }
        AlertDialog.Builder(activity)
            .setTitle("重命名会话")
            .setView(edit)
            .setPositiveButton("确定") { _, _ ->
                val name = edit.text.toString().trim()
                if (name.isNotEmpty()) {
                    item.title = name.take(18)
                    refreshTabs(activity)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun refreshTabs(activity: Activity) {
        if (!::tabBar.isInitialized) return
        val currentId = current?.id
        tabBar.removeAllViews()
        sessions.forEach { item ->
            tabBar.addView(tabChip(activity, item, item.id == currentId), LinearLayout.LayoutParams(-2, -1))
        }
    }

    private fun tabChip(activity: Activity, item: EmbeddedTermSession, active: Boolean): View {
        val uiRef = ui
        val chip = TextView(activity).apply {
            val prefix = if (item.aiSession) "AI-" else ""
            text = if (active) "● $prefix${item.title}" else "$prefix${item.title}"
            textSize = 12f
            setTextColor(if (active) 0xFFE5E7EB.toInt() else 0xFF9CA3AF.toInt())
            gravity = Gravity.CENTER
            includeFontPadding = false
            setPadding(uiRef?.dp(10) ?: 10, 0, uiRef?.dp(10) ?: 10, 0)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(if (active) 0xFF1F2937.toInt() else 0xFF14181E.toInt())
                cornerRadius = (uiRef?.dp(6) ?: 6).toFloat()
            }
            setOnLongClickListener {
                switchSession(activity, item)
                renameCurrentSession(activity)
                true
            }
        }
        attachSwipeDownToClose(
            chip,
            onClick = { switchSession(activity, item) },
            onClose = { closeSessionItem(activity, item) }
        )
        return chip
    }

    private fun attachSwipeDownToClose(view: View, onClick: () -> Unit, onClose: () -> Unit) {
        val threshold = (ui?.dp(18) ?: 18).toFloat()
        val touchSlop = android.view.ViewConfiguration.get(view.context).scaledTouchSlop
        var startY = 0f
        var startX = 0f
        var moved = false
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                    startY = event.rawY
                    startX = event.rawX
                    moved = false
                    v.alpha = 1f
                    v.translationY = 0f
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = event.rawY - startY
                    val dx = kotlin.math.abs(event.rawX - startX)
                    if (dy > touchSlop * 0.5f && dy > dx * 0.4f) {
                        moved = true
                        v.translationY = dy * 0.75f
                        v.alpha = (1f - (dy / (threshold * 2f))).coerceIn(0.25f, 1f)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    v.parent?.requestDisallowInterceptTouchEvent(false)
                    val dy = event.rawY - startY
                    if (dy >= threshold) {
                        onClose()
                    } else {
                        v.animate().translationY(0f).alpha(1f).setDuration(120).start()
                        if (!moved) onClick()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    v.parent?.requestDisallowInterceptTouchEvent(false)
                    v.animate().translationY(0f).alpha(1f).setDuration(120).start()
                    true
                }
                else -> false
            }
        }
    }

    private fun closeSessionItem(activity: Activity, item: EmbeddedTermSession) {
        item.session.finishIfRunning()
        sessions.remove(item)
        if (current == item) {
            current = null
            session = null
            if (sessions.isEmpty()) newSession(activity) else switchSession(activity, sessions.last())
        } else {
            refreshTabs(activity)
        }
    }

    private fun send(command: String) {
        ensureSession(activity ?: return)
        session?.write(command.trimEnd() + "\n")
        terminalView?.requestFocus()
    }

    private fun consumePendingCommand() {
        val command = TerminalCommandBus.pending.trim()
        if (command.isNotEmpty()) {
            TerminalCommandBus.pending = ""
            if (command == "aidev-auto-bootstrap") autoBootstrapDispatched = true
            send(command)
        }
    }

    private fun maybeAutoBootstrapUbuntu(activity: Activity) {
        if (autoBootstrapDispatched) return
        autoBootstrapDispatched = true
        session?.write("aidev-auto-bootstrap\n")
        terminalView?.requestFocus()
    }

    private fun sessionClient(activity: Activity): TerminalSessionClient =
        object : TerminalSessionClient {
            override fun onTextChanged(changedSession: TerminalSession) { terminalView?.onScreenUpdated() }
            override fun onTitleChanged(changedSession: TerminalSession) {}
            override fun onSessionFinished(finishedSession: TerminalSession) { terminalView?.onScreenUpdated() }
            override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
                (activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("AIDev Terminal", text))
            }
            override fun onPasteTextFromClipboard(session: TerminalSession) {
                val text = (activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(activity)?.toString()
                if (!text.isNullOrEmpty()) session.write(text)
            }
            override fun onBell(session: TerminalSession) { terminalView?.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP) }
            override fun onColorsChanged(session: TerminalSession) { terminalView?.invalidate() }
            override fun onTerminalCursorStateChange(state: Boolean) { terminalView?.invalidate() }
            override fun getTerminalCursorStyle(): Int = TerminalEmulator.DEFAULT_TERMINAL_CURSOR_STYLE
            override fun logError(tag: String, message: String) { Log.e(tag, message) }
            override fun logWarn(tag: String, message: String) { Log.w(tag, message) }
            override fun logInfo(tag: String, message: String) { Log.i(tag, message) }
            override fun logDebug(tag: String, message: String) { Log.d(tag, message) }
            override fun logVerbose(tag: String, message: String) { Log.v(tag, message) }
            override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) { Log.e(tag, message, e) }
            override fun logStackTrace(tag: String, e: Exception) { Log.e(tag, "Terminal exception", e) }
        }

    private fun viewClient(activity: Activity): TerminalViewClient =
        object : TerminalViewClient {
            override fun onScale(scale: Float): Float {
                val damped = 1f + (scale - 1f) * 0.55f
                pendingFontSp = (pendingFontSp * damped).coerceIn(MIN_FONT_SP, MAX_FONT_SP)
                if (!fontApplyScheduled) {
                    fontApplyScheduled = true
                    terminalView?.post {
                        fontApplyScheduled = false
                        val next = pendingFontSp.coerceIn(MIN_FONT_SP, MAX_FONT_SP)
                        val current = currentFontSp(activity)
                        if (kotlin.math.abs(next - current) >= 0.2f) {
                            activity.getSharedPreferences("aidev_ui", Activity.MODE_PRIVATE).edit().putFloat("font_sp", next).apply()
                            terminalView?.setTextSize((next * activity.resources.displayMetrics.scaledDensity).toInt())
                            terminalView?.onScreenUpdated()
                            refreshStatus(activity)
                        }
                    }
                }
                return 1f
            }
            override fun onSingleTapUp(e: MotionEvent) {
                terminalView?.requestFocus()
                (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(terminalView, InputMethodManager.SHOW_IMPLICIT)
            }
            override fun shouldBackButtonBeMappedToEscape(): Boolean = true
            override fun shouldEnforceCharBasedInput(): Boolean = false
            override fun shouldUseCtrlSpaceWorkaround(): Boolean = false
            override fun isTerminalViewSelected(): Boolean = terminalView?.hasFocus() == true
            override fun copyModeChanged(copyMode: Boolean) {}
            override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean = false
            override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean = false
            override fun onLongPress(event: MotionEvent): Boolean = false
            override fun readControlKey(): Boolean = false
            override fun readAltKey(): Boolean = false
            override fun readShiftKey(): Boolean = false
            override fun readFnKey(): Boolean = false
            override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean = false
            override fun onEmulatorSet() { terminalView?.onScreenUpdated() }
            override fun logError(tag: String, message: String) { Log.e(tag, message) }
            override fun logWarn(tag: String, message: String) { Log.w(tag, message) }
            override fun logInfo(tag: String, message: String) { Log.i(tag, message) }
            override fun logDebug(tag: String, message: String) { Log.d(tag, message) }
            override fun logVerbose(tag: String, message: String) { Log.v(tag, message) }
            override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) { Log.e(tag, message, e) }
            override fun logStackTrace(tag: String, e: Exception) { Log.e(tag, "TerminalView exception", e) }
        }
}
