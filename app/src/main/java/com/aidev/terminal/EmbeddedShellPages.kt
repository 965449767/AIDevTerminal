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

private data class EmbeddedTaskInfo(
    val id: String,
    val name: String,
    val pid: String,
    val started: String,
    val cmd: String,
    val logPath: String,
    val logFile: File
)

private data class EmbeddedTermSession(
    val id: Int,
    var title: String,
    val session: TerminalSession,
    var aiSession: Boolean = false
)

private data class EmbeddedVirtualKey(
    val label: String,
    val input: String,
    val swipeCommand: String = ""
)

class EmbeddedTerminalPage : ShellPage {
    private var activity: Activity? = null
    private var ui: AIDevUi? = null
    private var terminalView: TerminalView? = null
    private lateinit var tabBar: LinearLayout
    private var session: TerminalSession? = null
    private var current: EmbeddedTermSession? = null
    private var homeDir: File? = null
    private val sessions = mutableListOf<EmbeddedTermSession>()
    private var ctrlLatched = false
    private var autoBootstrapDispatched = false

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
        terminalView = TerminalView(activity, null).apply {
            setBackgroundColor(Color.BLACK)
            isFocusable = true
            isFocusableInTouchMode = true
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
        val value = sp.coerceIn(8f, 24f)
        activity.getSharedPreferences("aidev_ui", Activity.MODE_PRIVATE).edit().putFloat("font_sp", value).apply()
        terminalView?.setTextSize((value * activity.resources.displayMetrics.scaledDensity).toInt())
        terminalView?.onScreenUpdated()
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
            EmbeddedVirtualKey("ESC", "\u001B", "clear"),
            EmbeddedVirtualKey("CTRL", "__CTRL__", ""),
            EmbeddedVirtualKey("↑", "\u001B[A", "history"),
            EmbeddedVirtualKey("TAB", "\t", "help"),
            EmbeddedVirtualKey("/", "/", "cd /"),
            EmbeddedVirtualKey("-", "-", "cd -"),
            EmbeddedVirtualKey("C", "c", ""),
            EmbeddedVirtualKey("←", "\u001B[D", ""),
            EmbeddedVirtualKey("↓", "\u001B[B", ""),
            EmbeddedVirtualKey("→", "\u001B[C", ""),
            EmbeddedVirtualKey("|", "|", ""),
            EmbeddedVirtualKey("SPC", " ", "pwd")
        )
        val custom = parseCustomKeys(prefs.getString("terminal_custom_keys", "") ?: "")
            .map { EmbeddedVirtualKey(it.first, it.second, "") }
        return (defaults + custom).take(12)
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
                            send(key.swipeCommand)
                            true
                        } else false
                    }
                    else -> false
                }
            }
            setOnClickListener { handleVirtualKeyTap(activity, key) }
            setOnLongClickListener {
                showExtraKeysMenu(activity)
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
            "HOME" to "\u001b[H",
            "END" to "\u001b[F",
            "PGUP" to "\u001b[5~",
            "PGDN" to "\u001b[6~",
            "~" to "~",
            "清屏" to "clear\n",
            "Ubuntu" to "ubuntu\n",
            "OpenCode CLI" to "aidev-opencode\n",
            "任务" to "task-list\n"
        ).toMutableList()
        keys.addAll(parseCustomKeys(prefs.getString("terminal_custom_keys", "") ?: ""))
        AlertDialog.Builder(activity)
            .setTitle("扩展键盘更多")
            .setItems(keys.map { it.first }.toTypedArray()) { _, which -> session?.write(keys[which].second) }
            .show()
    }

    private fun parseCustomKeys(raw: String): List<Pair<String, String>> =
        raw.lines().mapNotNull { line ->
            val parts = line.split("\t", limit = 2)
            val label = parts.getOrNull(0)?.trim().orEmpty()
            val input = parts.getOrNull(1).orEmpty()
            if (label.isEmpty() || input.isEmpty()) null else label.take(8) to decodeKeyInput(input)
        }.take(8)

    private fun decodeKeyInput(input: String): String =
        input.replace("\\n", "\n").replace("\\t", "\t").replace("\\e", "\u001b")

    private fun fontPx(activity: Activity): Int =
        (activity.getSharedPreferences("aidev_ui", Activity.MODE_PRIVATE).getFloat("font_sp", 16f).coerceIn(8f, 24f) * activity.resources.displayMetrics.scaledDensity).toInt()

    private fun showFontDialog(activity: Activity) {
        val prefs = activity.getSharedPreferences("aidev_ui", Activity.MODE_PRIVATE)
        val current = prefs.getFloat("font_sp", 16f).coerceIn(8f, 24f)
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
            max = 16
            progress = current.toInt() - 8
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    value.text = "${8 + progress}sp"
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
                val sp = (8 + seek.progress).toFloat()
                prefs.edit().putFloat("font_sp", sp).apply()
                terminalView?.setTextSize((sp * activity.resources.displayMetrics.scaledDensity).toInt())
                terminalView?.onScreenUpdated()
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
        homeDir = File(activity.filesDir, "home").apply { mkdirs() }
        val rc = File(homeDir, ".aidevrc")
        val entry = File(homeDir, ".aidev_shell_entry")
        installProotSupportLibraries(activity)
        installAidevCommandScripts(activity)
        writeCanonicalRc(activity, rc)
        writeShellEntry(activity, rc, entry)
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
        session?.write("\n# AIDev 自动初始化/进入 Ubuntu 环境\n")
        session?.write("aidev-auto-bootstrap\n")
        terminalView?.requestFocus()
    }

    private fun installProotSupportLibraries(activity: Activity) {
        val outDir = File(activity.filesDir, "home/proot-lib").apply { mkdirs() }
        listOf("libtalloc.so.2", "libandroid-shmem.so").forEach { name ->
            val out = File(outDir, name)
            val marker = File(outDir, "$name.v2")
            if (out.exists() && marker.exists()) return@forEach
            runCatching {
                activity.assets.open("proot-libs/arm64-v8a/$name").use { input ->
                    out.outputStream().use { output -> input.copyTo(output) }
                }
                out.setReadable(true, false)
                marker.writeText("ok\n")
            }
        }
    }

    private fun installAidevCommandScripts(activity: Activity) {
        val home = File(activity.filesDir, "home").apply { mkdirs() }
        val bin = File(home, "dev-env/bin").apply { mkdirs() }
        val core = File(bin, "aidev-ubuntu-core")
        core.writeText(aidevUbuntuCommandScript())
        core.setReadable(true, false)
        listOf("ubuntu", "install-ubuntu", "aidev-auto-bootstrap").forEach { name ->
            val out = File(bin, name)
            out.writeText("# AIDev command marker. Android 私有目录禁止直接执行脚本；实际入口由 .aidevrc 函数转发。\n")
            out.setReadable(true, false)
        }
        // 清理旧入口残留，避免多套 rc/兼容入口同时生效。
        File(home, ".aidev_shell_fallback").delete()
    }

    private fun writeCanonicalRc(activity: Activity, rc: File) {
        val home = File(activity.filesDir, "home").absolutePath
        val nativeDir = activity.applicationInfo.nativeLibraryDir
        rc.writeText(
            """
            # AIDev canonical shell rc. 自动生成，请不要在这里保存个人配置。
            AIDEV_VERSION="0.12.9-oobe-final-debug"
            AIDEV_HOME="$home"
            AIDEV_BIN="${'$'}AIDEV_HOME/dev-env/bin"
            AIDEV_ROOTFS="${'$'}AIDEV_HOME/ubuntu-rootfs"
            AIDEV_NATIVE="$nativeDir"
            AIDEV_PROOT="${'$'}AIDEV_NATIVE/libproot.so"
            AIDEV_PROOT_LOADER="${'$'}AIDEV_NATIVE/libproot_loader.so"
            PROOT_LOADER="${'$'}AIDEV_PROOT_LOADER"
            PROOT_TMP_DIR="${'$'}AIDEV_HOME/proot-tmp"
            export AIDEV_VERSION AIDEV_HOME AIDEV_BIN AIDEV_ROOTFS AIDEV_NATIVE AIDEV_PROOT AIDEV_PROOT_LOADER PROOT_LOADER PROOT_TMP_DIR
            export PATH="${'$'}AIDEV_BIN:/system/bin:/system/xbin:${'$'}PATH"
            export PS1='aidev:${'$'}{PWD##*/}# '
            alias ll='ls -lah'
            android-sh() { /system/bin/sh -lc "${'$'}*"; }
            pmx() { android-sh "pm ${'$'}*"; }
            amx() { android-sh "am ${'$'}*"; }
            getpropx() { android-sh "getprop ${'$'}*"; }
            logcatx() { android-sh "logcat ${'$'}*"; }
            ubuntu() { /system/bin/sh "${'$'}AIDEV_BIN/aidev-ubuntu-core" ubuntu "${'$'}@"; }
            install-ubuntu() { /system/bin/sh "${'$'}AIDEV_BIN/aidev-ubuntu-core" install-ubuntu "${'$'}@"; }
            aidev-auto-bootstrap() { /system/bin/sh "${'$'}AIDEV_BIN/aidev-ubuntu-core" aidev-auto-bootstrap "${'$'}@"; }
            ${agentShellFunctions()}
            """.trimIndent() + "\n"
        )
    }

    private fun writeShellEntry(activity: Activity, rc: File, entry: File) {
        // Android /system/bin/sh 通常是 mksh。交互 shell 会读取 ENV 指向的 rc。
        // 不能先 source rc 再 exec sh -i：exec 后函数会丢失，这正是 ubuntu 找不到的根因。
        entry.writeText("export ENV=\"${rc.absolutePath}\"\nexec sh -i\n")
    }

    private fun agentShellFunctions(): String =
        """

        # AIDev AI 代理辅助命令：服务 OpenCode / AI Agent 闭环开发。
        aidev-current-project() {
          pwd
          [ -d .git ] && git status --short --branch 2>/dev/null
          [ -f package.json ] && node -e "const p=require('./package.json'); console.log('package:',p.name||'-'); console.log('scripts:',Object.keys(p.scripts||{}).join(','))" 2>/dev/null
          [ -f pyproject.toml ] && echo "python: pyproject.toml"
          [ -f requirements.txt ] && echo "python: requirements.txt"
          [ -f build.gradle ] || [ -f build.gradle.kts ] && echo "gradle project"
          [ -f go.mod ] && echo "go module"
          [ -f Cargo.toml ] && echo "rust cargo"
        }
        aidev-agent-context() {
          echo "== AIDev Agent Context =="
          echo "time: ${'$'}(date '+%F %T')"
          echo "pwd: ${'$'}(pwd)"
          echo "version: ${'$'}AIDEV_VERSION"
          echo
          echo "== project =="
          aidev-current-project
          echo
          echo "== files =="
          find . -maxdepth 2 -type f 2>/dev/null | sed 's#^\./##' | head -120
          echo
          echo "== recent git =="
          git status --short --branch 2>/dev/null || true
          git diff --stat 2>/dev/null | head -80 || true
          echo
          echo "== tasks =="
          task-list 2>/dev/null || true
        }
        aidev-agent-context-file() {
          out="aidev-agent-context.txt"
          aidev-agent-context > "${'$'}out"
          echo >> "${'$'}out"
          echo "== recent errors ==" >> "${'$'}out"
          aidev-agent-summary >> "${'$'}out" 2>/dev/null || true
          echo "已导出上下文文件：${'$'}(pwd)/${'$'}out"
        }
        aidev-agent-summary() {
          log="${'$'}(ls -t "${'$'}AIDEV_HOME/tasks"/*.log 2>/dev/null | head -1)"
          [ -n "${'$'}log" ] || { echo "暂无任务日志"; return 0; }
          echo "== log =="
          echo "${'$'}log"
          echo
          echo "== recent commands =="
          grep -iE "command|running|exec|npm |python|gradle|go |cargo|git " "${'$'}log" 2>/dev/null | tail -20 || true
          echo
          echo "== recent errors =="
          grep -iE "error|failed|exception|traceback|cannot|not found|denied" "${'$'}log" 2>/dev/null | tail -40 || true
          echo
          echo "== possible modified files =="
          grep -iE "modified|created|updated|wrote|write|saved|changed" "${'$'}log" 2>/dev/null | tail -30 || true
        }
        aidev-opencode-preflight() {
          echo "== OpenCode 启动前检查 =="
          command -v opencode >/dev/null 2>&1 && echo "OpenCode: OK" || echo "OpenCode: 未安装，运行 install-aitool"
          [ -d .git ] && echo "Git: OK" || echo "Git: 当前目录不是 Git 仓库"
          [ -f README.md ] || [ -f README.txt ] || [ -f readme.md ] && echo "README: OK" || echo "README: 未发现"
          [ -f package.json ] && echo "项目: Node/package.json"
          [ -f pyproject.toml ] && echo "项目: Python/pyproject"
          [ -f requirements.txt ] && echo "项目: Python/requirements"
          [ -f build.gradle ] || [ -f build.gradle.kts ] && echo "项目: Gradle"
          [ -f go.mod ] && echo "项目: Go"
          [ -f Cargo.toml ] && echo "项目: Rust"
          echo
          git status --short --branch 2>/dev/null || true
          echo
          echo "建议："
          echo "  aidev-agent-context-file   # 导出上下文"
          echo "  aidev-opencode             # 前台启动"
          echo "  aidev-opencode-task        # 后台启动"
          echo "  aidev-agent-log            # 查看日志"
        }
        aidev-opencode() {
          command -v opencode >/dev/null 2>&1 || { echo "opencode 未安装，先运行 install-aitool"; return 1; }
          aidev-opencode-preflight
          echo "启动 OpenCode。建议先在项目目录运行 aidev-agent-context-file。"
          opencode "${'$'}@"
        }
        aidev-opencode-task() {
          command -v opencode >/dev/null 2>&1 || { echo "opencode 未安装，先运行 install-aitool"; return 1; }
          if command -v task-run >/dev/null 2>&1; then
            task-run opencode "opencode"
          else
            mkdir -p "${'$'}AIDEV_HOME/tasks"
            log="${'$'}AIDEV_HOME/tasks/opencode-${'$'}(date +%Y%m%d-%H%M%S).log"
            nohup sh -lc "opencode" > "${'$'}log" 2>&1 &
            echo "OpenCode 后台任务已启动，日志：${'$'}log"
          fi
        }
        aidev-agent-log() {
          ls -lt "${'$'}AIDEV_HOME/tasks"/*.log 2>/dev/null | head -20
        }
        aidev-agent-tail() {
          log="${'$'}(ls -t "${'$'}AIDEV_HOME/tasks"/*.log 2>/dev/null | head -1)"
          [ -n "${'$'}log" ] || { echo "暂无任务日志"; return 1; }
          tail -f "${'$'}log"
        }
        """.trimIndent() + "\n"

    private fun aidevUbuntuCommandScript(): String =
        """
        #!/system/bin/sh
        set -u

        cmd="${'$'}{1:-ubuntu}"
        shift 2>/dev/null || true

        AIDEV_HOME="${'$'}{AIDEV_HOME:-${homeDir?.absolutePath ?: ""}}"
        AIDEV_BIN="${'$'}{AIDEV_BIN:-${'$'}AIDEV_HOME/dev-env/bin}"
        AIDEV_ROOTFS="${'$'}{AIDEV_ROOTFS:-${'$'}AIDEV_HOME/ubuntu-rootfs}"
        AIDEV_NATIVE="${'$'}{AIDEV_NATIVE:-}"
        AIDEV_PROOT="${'$'}{AIDEV_PROOT:-${'$'}AIDEV_NATIVE/libproot.so}"
        AIDEV_PROOT_LOADER="${'$'}{AIDEV_PROOT_LOADER:-${'$'}AIDEV_NATIVE/libproot_loader.so}"
        PROOT_LOADER="${'$'}AIDEV_PROOT_LOADER"
        PROOT_TMP_DIR="${'$'}{PROOT_TMP_DIR:-${'$'}AIDEV_HOME/proot-tmp}"
        export AIDEV_HOME AIDEV_BIN AIDEV_ROOTFS AIDEV_NATIVE AIDEV_PROOT AIDEV_PROOT_LOADER PROOT_LOADER PROOT_TMP_DIR

        ubuntu_url="${'$'}{AIDEV_UBUNTU_URL:-https://mirrors.tuna.tsinghua.edu.cn/ubuntu-cdimage/ubuntu-base/releases/24.04.4/release/ubuntu-base-24.04.4-base-arm64.tar.gz}"

        has_ubuntu() {
          [ -f "${'$'}AIDEV_ROOTFS/.aidev-rootfs-ready" ] &&
          [ -f "${'$'}AIDEV_ROOTFS/etc/os-release" ] &&
          { [ -x "${'$'}AIDEV_ROOTFS/bin/sh" ] || [ -x "${'$'}AIDEV_ROOTFS/bin/bash" ]; }
        }

        link_or_symlink() {
          left="${'$'}1"
          right="${'$'}2"
          if [ -e "${'$'}AIDEV_ROOTFS.tmp/${'$'}left" ] && [ ! -e "${'$'}AIDEV_ROOTFS.tmp/${'$'}right" ]; then
            ( cd "${'$'}AIDEV_ROOTFS.tmp/${'$'}(dirname "${'$'}right")" && ln -sf "${'$'}(basename "${'$'}left")" "${'$'}(basename "${'$'}right")" ) 2>/dev/null || true
          fi
          if [ -e "${'$'}AIDEV_ROOTFS.tmp/${'$'}right" ] && [ ! -e "${'$'}AIDEV_ROOTFS.tmp/${'$'}left" ]; then
            ( cd "${'$'}AIDEV_ROOTFS.tmp/${'$'}(dirname "${'$'}left")" && ln -sf "${'$'}(basename "${'$'}right")" "${'$'}(basename "${'$'}left")" ) 2>/dev/null || true
          fi
        }

        download_file() {
          url="${'$'}1"; out="${'$'}2"; part="${'$'}out.part"
          mkdir -p "${'$'}(dirname "${'$'}out")"
          echo "下载地址：${'$'}url"
          if command -v curl >/dev/null 2>&1; then
            curl -fsSL --retry 3 -C - -o "${'$'}part" "${'$'}url" || return ${'$'}?
          elif command -v wget >/dev/null 2>&1; then
            wget -q -c -O "${'$'}part" "${'$'}url" || return ${'$'}?
          elif /system/bin/toybox wget --help >/dev/null 2>&1; then
            /system/bin/toybox wget -O "${'$'}part" "${'$'}url" || return ${'$'}?
          else
            echo "没有可用下载器：curl/wget/toybox wget 都不可用。"
            echo "下一步将改为 APK assets 内置 rootfs 或 Kotlin 下载器，避免依赖系统命令。"
            return 1
          fi
          [ -s "${'$'}part" ] && mv "${'$'}part" "${'$'}out"
        }

        install_ubuntu() {
          case "${'$'}{1:-}" in
            --clean)
              rm -rf "${'$'}AIDEV_ROOTFS" "${'$'}AIDEV_ROOTFS.tmp" "${'$'}AIDEV_HOME/dev-env/tmp/ubuntu-base.tar.gz" "${'$'}AIDEV_HOME/dev-env/tmp/ubuntu-base.tar.gz.part"
              echo "已清理 Ubuntu rootfs 与下载缓存。"
              return 0
              ;;
          esac

          if has_ubuntu; then
            echo "Ubuntu 已就绪：${'$'}AIDEV_ROOTFS"
            return 0
          fi

          abi="${'$'}(getprop ro.product.cpu.abi 2>/dev/null || echo arm64-v8a)"
          case "${'$'}abi" in
            arm64-v8a|aarch64) ;;
            *) echo "当前自动 Ubuntu 仅支持 arm64，设备 ABI=${'$'}abi"; return 1 ;;
          esac

          [ -x "${'$'}AIDEV_PROOT" ] || { echo "proot 不存在：${'$'}AIDEV_PROOT"; return 1; }

          mkdir -p "${'$'}AIDEV_HOME/dev-env/tmp" "${'$'}PROOT_TMP_DIR"
          tar_file="${'$'}AIDEV_HOME/dev-env/tmp/ubuntu-base.tar.gz"
          if [ ! -s "${'$'}tar_file" ]; then
            echo "[1/4] 自动下载 Ubuntu Base 24.04 arm64..."
            download_file "${'$'}ubuntu_url" "${'$'}tar_file" || return ${'$'}?
          else
            echo "[1/4] 使用已下载缓存：${'$'}tar_file"
          fi

          echo "[2/4] 准备 rootfs 目录..."
          rm -rf "${'$'}AIDEV_ROOTFS.tmp" "${'$'}AIDEV_ROOTFS"
          mkdir -p "${'$'}AIDEV_ROOTFS.tmp"

          echo "[3/4] 解包 Ubuntu rootfs..."
          tar_log="${'$'}AIDEV_HOME/dev-env/tmp/tar-install.log"
          if command -v tar >/dev/null 2>&1; then
            ( cd "${'$'}AIDEV_ROOTFS.tmp" && tar --no-same-owner --no-same-permissions -xzf "${'$'}tar_file" ) 2>"${'$'}tar_log"
            tar_rc="${'$'}?"
          else
            echo "系统缺少 tar，无法解包 rootfs。"
            return 1
          fi

          link_or_symlink "usr/bin/perl" "usr/bin/perl5.38.2"
          link_or_symlink "usr/bin/gunzip" "usr/bin/uncompress"

          if [ "${'$'}tar_rc" -ne 0 ]; then
            if [ -f "${'$'}AIDEV_ROOTFS.tmp/etc/os-release" ] && { [ -x "${'$'}AIDEV_ROOTFS.tmp/bin/sh" ] || [ -x "${'$'}AIDEV_ROOTFS.tmp/bin/bash" ]; }; then
              echo "检测到 Android hardlink 限制，已自动转为 symlink 继续。"
            else
              echo "解包失败，请检查 tar 能力、存储空间或下载完整性。"
              [ -s "${'$'}tar_log" ] && tail -20 "${'$'}tar_log"
              rm -rf "${'$'}AIDEV_ROOTFS.tmp"
              return 1
            fi
          fi

          echo "[4/4] 初始化 apt 源、DNS 和完成标记..."
          mkdir -p "${'$'}AIDEV_ROOTFS.tmp/etc/apt" "${'$'}AIDEV_ROOTFS.tmp/root/projects"
          cat > "${'$'}AIDEV_ROOTFS.tmp/etc/apt/sources.list" <<'AIDEV_APT_EOF'
deb http://mirrors.tuna.tsinghua.edu.cn/ubuntu-ports/ noble main universe multiverse restricted
deb http://mirrors.tuna.tsinghua.edu.cn/ubuntu-ports/ noble-updates main universe multiverse restricted
deb http://mirrors.tuna.tsinghua.edu.cn/ubuntu-ports/ noble-security main universe multiverse restricted
AIDEV_APT_EOF
          echo "nameserver 223.5.5.5" > "${'$'}AIDEV_ROOTFS.tmp/etc/resolv.conf"
          date '+%F %T' > "${'$'}AIDEV_ROOTFS.tmp/.aidev-rootfs-ready"
          mv "${'$'}AIDEV_ROOTFS.tmp" "${'$'}AIDEV_ROOTFS"
          echo "Ubuntu 初始化完成。"
        }

        enter_ubuntu() {
          has_ubuntu || install_ubuntu --fast || return ${'$'}?
          shell="/bin/bash"
          [ -x "${'$'}AIDEV_ROOTFS/bin/bash" ] || shell="/bin/sh"
          echo "进入 Ubuntu：${'$'}AIDEV_ROOTFS"
          cd "${'$'}AIDEV_HOME" || exit 1
          exec "${'$'}AIDEV_PROOT" --link2symlink -0 -r "${'$'}AIDEV_ROOTFS" \
            -b /dev -b /proc -b /sys -b /sdcard -b "${'$'}AIDEV_HOME:/host-home" \
            -w /root /usr/bin/env -i \
            HOME=/root PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin \
            TERM="${'$'}{TERM:-xterm-256color}" "${'$'}shell" -l
        }

        case "${'$'}cmd" in
          ubuntu) enter_ubuntu "${'$'}@" ;;
          install-ubuntu) install_ubuntu "${'$'}@" ;;
          aidev-auto-bootstrap)
            echo "AIDev 自动初始化/进入 Ubuntu 环境。"
            enter_ubuntu "${'$'}@"
            ;;
          *) echo "未知命令：${'$'}cmd"; exit 2 ;;
        esac
        """.trimIndent() + "\n"

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
            override fun onScale(scale: Float): Float = 1f
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

class EmbeddedFilesPage : ShellPage {
    private lateinit var activity: Activity
    private lateinit var ui: AIDevUi
    private lateinit var leftList: LinearLayout
    private lateinit var rightList: LinearLayout
    private lateinit var leftPath: TextView
    private lateinit var rightPath: TextView
    private var leftDir: File = Environment.getExternalStorageDirectory()
    private var rightDir: File = File("/")
    private var activeLeft = true
    private var leftSelected: File? = null
    private var rightSelected: File? = null

    override fun create(activity: Activity, ui: AIDevUi, host: ShellHost): View {
        this.activity = activity
        this.ui = ui
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8))
        }
        root.addView(toolbar(host), LinearLayout.LayoutParams(-1, ui.dp(42)))
        val panes = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        leftList = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        rightList = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        panes.addView(pane(true), LinearLayout.LayoutParams(0, -1, 1f).apply { setMargins(0, 0, ui.dp(4), 0) })
        panes.addView(pane(false), LinearLayout.LayoutParams(0, -1, 1f).apply { setMargins(ui.dp(4), 0, 0, 0) })
        root.addView(panes, LinearLayout.LayoutParams(-1, 0, 1f))
        reloadAll()
        return root
    }

    override fun onSelected(activity: Activity, view: View) {
        if (::leftList.isInitialized) reloadAll()
    }

    private fun toolbar(host: ShellHost): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(action("复制") { copyToOther(false) })
            addView(action("移动") { copyToOther(true) })
            addView(action("新建") { newFolder() })
            addView(action("编辑") { editSelected() }.apply {
                setOnLongClickListener {
                    previewSelected()
                    true
                }
            })
            addView(action("搜索") { searchActiveDir() })
            addView(action("更多") { showFileMoreMenu(host) })
        }

    private fun showFileMoreMenu(host: ShellHost) {
        val actions = listOf(
            "文件 · 预览" to { previewSelected() },
            "文件 · 重命名" to { renameSelected() },
            "文件 · 删除" to { deleteSelected() },
            "文件 · 另存" to { saveSelectedAs() },
            "文件 · 复制路径" to { copySelectedPath() },
            "位置 · 收藏当前目录" to { addFavorite() },
            "位置 · 收藏/跳转" to { showFavorites() },
            "位置 · 常用目录" to { showQuickDirs() },
            "位置 · 最近项目" to { showRecentProjects() },
            "项目 · 项目识别" to { inspectProject() },
            "项目 · 项目工作区" to { projectWorkspace() },
            "项目 · 标记当前项目" to { markCurrentProject() },
            "项目 · 跳到当前项目" to { jumpCurrentProject() },
            "系统 · 安装 APK" to { installSelectedApk(host) },
            "系统 · 存储权限" to { openStorageSettings() },
            "系统 · 旧版文件管理" to { host.open(FileManagerActivity::class.java) }
        )
        val recent = recentFileMenuLabels().filter { label -> actions.any { it.first == label } }
        val display = recent.map { "最近 · ${it.substringAfter(" · ")}" to it } + actions.filterNot { recent.contains(it.first) }.map { it.first to it.first }
        AlertDialog.Builder(activity)
            .setTitle("文件更多")
            .setItems(display.map { it.first }.toTypedArray()) { _, which ->
                val original = display[which].second
                rememberFileMenuLabel(original)
                actions.firstOrNull { it.first == original }?.second?.invoke()
            }
            .setNeutralButton("搜索") { _, _ -> searchFileMoreMenu(actions) }
            .show()
    }

    private fun searchFileMoreMenu(actions: List<Pair<String, () -> Unit>>) {
        val edit = EditText(activity).apply { hint = "输入 删除、项目、权限、路径" }
        AlertDialog.Builder(activity)
            .setTitle("搜索文件更多")
            .setView(edit)
            .setPositiveButton("搜索") { _, _ ->
                val keyword = edit.text.toString().trim()
                val matches = actions.filter { keyword.isBlank() || it.first.contains(keyword, true) }.take(30)
                if (matches.isEmpty()) return@setPositiveButton toast("没有匹配项")
                AlertDialog.Builder(activity)
                    .setTitle("搜索结果")
                    .setItems(matches.map { it.first }.toTypedArray()) { _, which ->
                        rememberFileMenuLabel(matches[which].first)
                        matches[which].second.invoke()
                    }
                    .show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun recentFileMenuLabels(): List<String> =
        activity.getSharedPreferences("aidev_ui", Activity.MODE_PRIVATE).getString("recent_file_more", "")?.lines()?.filter { it.isNotBlank() }.orEmpty().takeLast(3).reversed()

    private fun rememberFileMenuLabel(label: String) {
        val prefs = activity.getSharedPreferences("aidev_ui", Activity.MODE_PRIVATE)
        val old = prefs.getString("recent_file_more", "")?.lines()?.filter { it.isNotBlank() && it != label }.orEmpty()
        prefs.edit().putString("recent_file_more", (old + label).takeLast(6).joinToString("\n")).apply()
    }

    private fun pane(isLeft: Boolean): View {
        val outer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(4), ui.dp(4), ui.dp(4), ui.dp(4))
            background = ui.infoPanelBackground()
        }
        val path = ui.text("", 12f, ui.palette.primary, bold = true).apply {
            setPadding(ui.dp(8), ui.dp(7), ui.dp(8), ui.dp(7))
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.START
            background = paneHeaderBg()
        }
        if (isLeft) leftPath = path else rightPath = path
        outer.addView(path)
        outer.addView(ScrollView(activity).apply { addView(if (isLeft) leftList else rightList) }, LinearLayout.LayoutParams(-1, 0, 1f))
        return outer
    }

    private fun reloadAll() {
        loadPane(true)
        loadPane(false)
    }

    private fun loadPane(isLeft: Boolean) {
        val dir = if (isLeft) leftDir else rightDir
        val list = if (isLeft) leftList else rightList
        val path = if (isLeft) leftPath else rightPath
        path.text = if (isLeft) "左：${dir.absolutePath}" else "右：${dir.absolutePath}"
        list.removeAllViews()
        dir.parentFile?.let { list.addView(row("..", it, isLeft, true)) }
        val files = dir.listFiles()?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
        if (files == null) {
            path.text = "${if (isLeft) "左" else "右"}：${dir.absolutePath}\n无法读取"
            list.addView(info("无法读取，可能需要存储权限。"))
            return
        }
        path.text = "${if (isLeft) "左" else "右"}${if (activeLeft == isLeft) " ●" else ""}：${dir.absolutePath}\n${files.size} 项"
        files.take(300).forEach { list.addView(row(label(it), it, isLeft, false)) }
    }

    private fun row(text: String, file: File, isLeft: Boolean, parent: Boolean): View =
        TextView(activity).apply {
            this.text = text
            textSize = 12f
            setTextColor(ui.palette.text)
            setPadding(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8))
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.MIDDLE
            includeFontPadding = false
            setBackgroundColor(if ((if (isLeft) leftSelected else rightSelected)?.absolutePath == file.absolutePath) ui.palette.primary else Color.TRANSPARENT)
            setOnClickListener {
                ui.pulse()
                activeLeft = isLeft
                if (file.isDirectory) {
                    if (isLeft) leftDir = file else rightDir = file
                    if (isLeft) leftSelected = null else rightSelected = null
                    rememberRecentDir(file)
                } else if (!parent) {
                    if (isLeft) leftSelected = file else rightSelected = file
                }
                loadPane(isLeft)
            }
            setOnLongClickListener {
                activeLeft = isLeft
                if (isLeft) leftSelected = file else rightSelected = file
                fileActions(file)
                true
            }
        }

    private fun selected(): File? = if (activeLeft) leftSelected else rightSelected
    private fun activeDir(): File = if (activeLeft) leftDir else rightDir
    private fun otherDir(): File = if (activeLeft) rightDir else leftDir

    private fun copyToOther(move: Boolean) {
        val src = selected() ?: return toast("请先选择文件或目录")
        val dst = File(otherDir(), src.name)
        if (dst.exists()) return toast("目标已存在")
        runCatching {
            if (src.isDirectory) copyDir(src, dst) else src.copyTo(dst)
            if (move) src.deleteRecursively()
        }.onSuccess {
            clearSelection()
            reloadAll()
            toast(if (move) "移动完成" else "复制完成")
        }.onFailure { toast("操作失败：${it.message}") }
    }

    private fun newFolder() = input("新建文件夹", "folder") { name ->
        toast(if (File(activeDir(), name).mkdir()) "已创建" else "创建失败")
        reloadAll()
    }

    private fun renameSelected() {
        val src = selected() ?: return toast("请先选择文件或目录")
        input("重命名", src.name) { name ->
            toast(if (src.renameTo(File(src.parentFile, name))) "已重命名" else "重命名失败")
            clearSelection()
            reloadAll()
        }
    }

    private fun deleteSelected() {
        val src = selected() ?: return toast("请先选择文件或目录")
        AlertDialog.Builder(activity)
            .setTitle("删除")
            .setMessage(src.absolutePath)
            .setPositiveButton("删除") { _, _ ->
                val ok = if (src.isDirectory) src.deleteRecursively() else src.delete()
                clearSelection()
                reloadAll()
                toast(if (ok) "已删除" else "删除失败")
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun previewSelected() {
        val src = selected() ?: return toast("请先选择文件")
        if (!src.isFile) return toast("目录不能预览")
        if (src.name.endsWith(".apk", ignoreCase = true)) return showApkInfo(src)
        if (isImageFile(src)) return showImageInfo(src)
        if (!isLikelyText(src)) return showBinaryInfo(src)
        if (src.length() > 512 * 1024) return showBinaryInfo(src)
        val text = runCatching { src.readText() }.getOrElse { "无法读取：${it.message}" }
        AlertDialog.Builder(activity)
            .setTitle(src.name)
            .setMessage(text.take(12000))
            .setPositiveButton("复制内容") { _, _ ->
                copyText("AIDev 文件内容", text)
                toast("已复制内容")
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun editSelected() {
        val src = selected() ?: return toast("请先选择文本文件")
        if (!src.isFile) return toast("目录不能编辑")
        if (!isLikelyText(src)) return toast("该文件不像文本文件")
        if (src.length() > 1024 * 1024) return toast("文件过大，请用终端编辑")
        val original = runCatching { src.readText() }.getOrElse {
            toast("读取失败：${it.message}")
            return
        }
        val edit = EditText(activity).apply {
            setText(original)
            textSize = 13f
            setSingleLine(false)
            minLines = 14
            gravity = Gravity.START or Gravity.TOP
        }
        val scroll = ScrollView(activity).apply { addView(edit) }
        AlertDialog.Builder(activity)
            .setTitle("编辑：${src.name}")
            .setView(scroll)
            .setPositiveButton("保存") { _, _ ->
                runCatching {
                    val backup = File(src.parentFile ?: activeDir(), "${src.name}.bak")
                    if (src.exists()) src.copyTo(backup, overwrite = true)
                    src.writeText(edit.text.toString())
                    backup
                }
                    .onSuccess {
                        toast("已保存，已生成备份")
                        reloadAll()
                    }
                    .onFailure { toast("保存失败：${it.message}") }
            }
            .setNegativeButton("取消", null)
            .setNeutralButton("更多") { _, _ -> editorMore(src, edit.text.toString()) }
            .show()
    }

    private fun editorMore(src: File, content: String) {
        val backup = File(src.parentFile ?: activeDir(), "${src.name}.bak")
        AlertDialog.Builder(activity)
            .setTitle("编辑操作")
            .setItems(arrayOf("运行当前文件", "查找内容", "另存编辑内容", "恢复备份", "复制编辑内容")) { _, which ->
                when (which) {
                    0 -> runFile(src)
                    1 -> findInEditorContent(content)
                    2 -> saveEditorContentAs(src, content)
                    3 -> restoreBackup(src, backup)
                    4 -> {
                        copyText("AIDev 编辑内容", content)
                        toast("已复制编辑内容")
                    }
                }
            }
            .show()
    }

    private fun findInEditorContent(content: String) {
        inputAllowAny("查找内容", "输入关键词") { keyword ->
            val lines = content.lines()
            val matches = lines.mapIndexedNotNull { index, line ->
                if (line.contains(keyword, ignoreCase = true)) "${index + 1}: $line" else null
            }.take(80)
            if (matches.isEmpty()) return@inputAllowAny toast("未找到匹配内容")
            AlertDialog.Builder(activity)
                .setTitle("查找结果")
                .setMessage(matches.joinToString("\n"))
                .setPositiveButton("复制结果") { _, _ ->
                    copyText("AIDev 查找结果", matches.joinToString("\n"))
                    toast("已复制查找结果")
                }
                .setNegativeButton("关闭", null)
                .show()
        }
    }

    private fun runFile(src: File) {
        val cmd = when (src.extension.lowercase()) {
            "sh" -> "sh \"${src.absolutePath}\""
            "py" -> "python3 \"${src.absolutePath}\""
            "js" -> "node \"${src.absolutePath}\""
            "kt" -> "cat \"${src.absolutePath}\""
            else -> "cat \"${src.absolutePath}\""
        }
        runInTerminal(cmd)
    }

    private fun saveEditorContentAs(src: File, content: String) {
        inputAllowAny("另存编辑内容", "${src.name}.copy") { name ->
            val dst = File(src.parentFile ?: activeDir(), name)
            if (dst.exists()) return@inputAllowAny toast("目标已存在")
            runCatching { dst.writeText(content) }
                .onSuccess {
                    reloadAll()
                    toast("已另存编辑内容")
                }
                .onFailure { toast("另存失败：${it.message}") }
        }
    }

    private fun restoreBackup(src: File, backup: File) {
        if (!backup.isFile) return toast("未找到备份文件")
        AlertDialog.Builder(activity)
            .setTitle("恢复备份")
            .setMessage("将用备份覆盖当前文件：\n${backup.absolutePath}")
            .setPositiveButton("恢复") { _, _ ->
                runCatching { backup.copyTo(src, overwrite = true) }
                    .onSuccess {
                        reloadAll()
                        toast("已恢复备份")
                    }
                    .onFailure { toast("恢复失败：${it.message}") }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun saveSelectedAs() {
        val src = selected() ?: return toast("请先选择文件或目录")
        inputAllowAny("另存为", src.name) { name ->
            val dst = File(activeDir(), name)
            if (dst.exists()) return@inputAllowAny toast("目标已存在")
            runCatching {
                if (src.isDirectory) copyDir(src, dst) else src.copyTo(dst)
            }.onSuccess {
                reloadAll()
                toast("已另存为 $name")
            }.onFailure { toast("另存失败：${it.message}") }
        }
    }

    private fun inspectProject() {
        val dir = selected()?.takeIf { it.isDirectory } ?: activeDir()
        val markers = listOf(
            "package.json" to "Node / 前端项目",
            "build.gradle" to "Gradle 项目",
            "build.gradle.kts" to "Gradle Kotlin 项目",
            "settings.gradle" to "Gradle 多模块项目",
            "pyproject.toml" to "Python 项目",
            "requirements.txt" to "Python 依赖项目",
            "Cargo.toml" to "Rust 项目",
            "go.mod" to "Go 项目",
            ".git" to "Git 仓库",
            "README.md" to "README 文档"
        ).filter { File(dir, it.first).exists() }
        val body = if (markers.isEmpty()) {
            "未识别到常见项目标记。\n\n目录：${dir.absolutePath}"
        } else {
            markers.joinToString("\n") { "✓ ${it.second}：${it.first}" } + "\n\n目录：${dir.absolutePath}"
        }
        val runCmd = when {
            File(dir, "package.json").exists() -> "cd \"${dir.absolutePath}\" && npm install && npm run dev"
            File(dir, "build.gradle").exists() || File(dir, "build.gradle.kts").exists() -> "cd \"${dir.absolutePath}\" && ./gradlew assembleDebug"
            File(dir, "requirements.txt").exists() -> "cd \"${dir.absolutePath}\" && pip install -r requirements.txt --break-system-packages"
            File(dir, "pyproject.toml").exists() -> "cd \"${dir.absolutePath}\" && python3 -m pip install . --break-system-packages"
            else -> "cd \"${dir.absolutePath}\" && ls -la"
        }
        AlertDialog.Builder(activity)
            .setTitle("项目识别")
            .setMessage(body)
            .setPositiveButton("运行建议") { _, _ ->
                if (activity is ShellActivity) {
                    TerminalCommandBus.pending = runCmd
                    (activity as ShellActivity).switchTo(ShellActivity.TAB_TERMINAL)
                }
            }
            .setNeutralButton("复制路径") { _, _ ->
                copyText("AIDev 项目路径", dir.absolutePath)
                toast("已复制项目路径")
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun projectWorkspace() {
        val dir = selected()?.takeIf { it.isDirectory } ?: activeDir()
        val items = arrayOf("标记当前项目", "清除当前项目", "项目概览", "项目健康检查", "运行修复建议", "最近操作", "项目脚本", "复制诊断报告", "复制修复命令", "项目识别", "查看 README", "Git 状态", "Git Diff", "安装依赖", "运行开发服务", "运行测试", "构建项目", "终端进入目录", "复制项目命令", "导出项目摘要")
        AlertDialog.Builder(activity)
            .setTitle("项目工作区")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> markCurrentProject(dir)
                    1 -> clearCurrentProject()
                    2 -> projectOverview(dir)
                    3 -> runInTerminal(projectHealthCommand(dir))
                    4 -> confirmProjectRepair(dir)
                    5 -> showProjectHistory()
                    6 -> showProjectScripts(dir)
                    7 -> copyProjectReport(dir)
                    8 -> copyRepairCommand(dir)
                    9 -> inspectProject()
                    10 -> showReadme(dir)
                    11 -> runInTerminal("cd \"${dir.absolutePath}\" && git status --short --branch")
                    12 -> runInTerminal("cd \"${dir.absolutePath}\" && git diff --stat")
                    13 -> runInTerminal(projectInstallCommand(dir))
                    14 -> runInTerminal(projectDevCommand(dir))
                    15 -> runInTerminal(projectTestCommand(dir))
                    16 -> runInTerminal(projectBuildCommand(dir))
                    17 -> runInTerminal("cd \"${dir.absolutePath}\" && pwd && ls -la")
                    18 -> copyProjectCommands(dir)
                    19 -> exportProjectSummary(dir)
                }
            }
            .show()
    }

    private fun markCurrentProject(dir: File = selected()?.takeIf { it.isDirectory } ?: activeDir()) {
        activity.getSharedPreferences("aidev_ui", Activity.MODE_PRIVATE)
            .edit()
            .putString("current_project_path", dir.absolutePath)
            .apply()
        rememberRecentDir(dir)
        toast("已标记当前项目")
    }

    private fun jumpCurrentProject() {
        val path = activity.getSharedPreferences("aidev_ui", Activity.MODE_PRIVATE).getString("current_project_path", "").orEmpty()
        val dir = File(path)
        if (path.isBlank() || !dir.isDirectory) {
            toast("未标记当前项目")
            return
        }
        if (activeLeft) leftDir = dir else rightDir = dir
        clearSelection()
        loadPane(activeLeft)
    }

    private fun clearCurrentProject() {
        activity.getSharedPreferences("aidev_ui", Activity.MODE_PRIVATE).edit().remove("current_project_path").apply()
        toast("已清除当前项目")
    }

    private fun projectOverview(dir: File) {
        val current = activity.getSharedPreferences("aidev_ui", Activity.MODE_PRIVATE).getString("current_project_path", "") == dir.absolutePath
        val body = listOf(
            "名称：${dir.name}",
            "路径：${dir.absolutePath}",
            "当前项目：${if (current) "是" else "否"}",
            "README：${if (listOf("README.md", "README.txt", "readme.md").any { File(dir, it).isFile }) "有" else "无"}",
            "Git：${if (File(dir, ".git").exists()) "有" else "无"}",
            "健康摘要：${projectHealthSummary(dir)}",
            "安装命令：${projectInstallCommand(dir)}",
            "开发命令：${projectDevCommand(dir)}",
            "测试命令：${projectTestCommand(dir)}",
            "构建命令：${projectBuildCommand(dir)}",
            "修复命令：${projectRepairCommand(dir)}"
        ).joinToString("\n")
        AlertDialog.Builder(activity)
            .setTitle("项目概览")
            .setMessage(body)
            .setPositiveButton("复制") { _, _ ->
                copyText("AIDev 项目概览", body)
                toast("已复制项目概览")
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun copyProjectCommands(dir: File) {
        val body = listOf(
            "cd \"${dir.absolutePath}\"",
            projectInstallCommand(dir),
            projectDevCommand(dir),
            projectTestCommand(dir),
            projectBuildCommand(dir)
        ).joinToString("\n")
        copyText("AIDev 项目命令", body)
        toast("已复制项目命令")
    }

    private fun copyProjectReport(dir: File) {
        val body = listOf(
            "项目：${dir.name}",
            "路径：${dir.absolutePath}",
            "健康摘要：${projectHealthSummary(dir)}",
            "README：${if (listOf("README.md", "README.txt", "readme.md").any { File(dir, it).isFile }) "有" else "无"}",
            "Git：${if (File(dir, ".git").exists()) "有" else "无"}",
            "安装：${projectInstallCommand(dir)}",
            "开发：${projectDevCommand(dir)}",
            "测试：${projectTestCommand(dir)}",
            "构建：${projectBuildCommand(dir)}",
            "诊断：${projectHealthCommand(dir)}",
            "修复：${projectRepairCommand(dir)}"
        ).joinToString("\n")
        copyText("AIDev 项目诊断", body)
        toast("已复制诊断报告")
    }

    private fun copyRepairCommand(dir: File) {
        copyText("AIDev 修复命令", projectRepairCommand(dir))
        toast("已复制修复命令")
    }

    private fun confirmProjectRepair(dir: File) {
        val command = projectRepairCommand(dir)
        AlertDialog.Builder(activity)
            .setTitle("确认修复项目")
            .setMessage("项目：${dir.name}\n路径：${dir.absolutePath}\n\n执行：\n$command\n\n注意：某些修复会删除缓存、依赖目录或锁文件。")
            .setPositiveButton("确认执行") { _, _ ->
                rememberProjectAction("修复", dir, command)
                runInTerminal(command)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showProjectHistory() {
        val prefs = activity.getSharedPreferences("aidev_ui", Activity.MODE_PRIVATE)
        val rows = prefs.getString("project_action_history", "")?.lines()?.filter { it.isNotBlank() }.orEmpty().takeLast(20).reversed()
        if (rows.isEmpty()) return toast("暂无项目操作历史")
        AlertDialog.Builder(activity)
            .setTitle("最近项目操作")
            .setItems(rows.map { row ->
                val parts = row.split("\t", limit = 4)
                "${parts.getOrNull(1) ?: "操作"}\n${parts.getOrNull(3) ?: row}"
            }.toTypedArray()) { _, which ->
                val parts = rows[which].split("\t", limit = 4)
                val path = parts.getOrNull(2).orEmpty()
                val command = parts.getOrNull(3).orEmpty()
                if (path.isNotBlank() && command.isNotBlank()) runInTerminal("cd \"$path\" && $command")
            }
            .setPositiveButton("复制全部") { _, _ ->
                copyText("AIDev 项目历史", rows.joinToString("\n"))
                toast("已复制项目历史")
            }
            .setNegativeButton("清空") { _, _ ->
                prefs.edit().remove("project_action_history").apply()
                toast("已清空历史")
            }
            .show()
    }

    private fun showProjectScripts(dir: File) {
        val packageJson = File(dir, "package.json")
        if (!packageJson.isFile) return showNonNodeProjectScripts(dir)
        val text = runCatching { packageJson.readText() }.getOrDefault("")
        val scriptsBlock = Regex("\"scripts\"\\s*:\\s*\\{([\\s\\S]*?)\\}").find(text)?.groupValues?.getOrNull(1).orEmpty()
        val scripts = Regex("\"([^\"]+)\"\\s*:\\s*\"([^\"]+)\"")
            .findAll(scriptsBlock)
            .map { it.groupValues[1] to it.groupValues[2] }
            .take(30)
            .toList()
        if (scripts.isEmpty()) return toast("未识别到 npm scripts")
        AlertDialog.Builder(activity)
            .setTitle("项目脚本")
            .setItems(scripts.map { "${it.first}\n${it.second}" }.toTypedArray()) { _, which ->
                runInTerminal("cd \"${dir.absolutePath}\" && npm run ${scripts[which].first}")
            }
            .setPositiveButton("复制脚本") { _, _ ->
                copyText("AIDev 项目脚本", scripts.joinToString("\n") { "${it.first}: ${it.second}" })
                toast("已复制项目脚本")
            }
            .show()
    }

    private fun showNonNodeProjectScripts(dir: File) {
        val scripts = when {
            File(dir, "build.gradle").exists() || File(dir, "build.gradle.kts").exists() -> listOf(
                "Gradle 任务列表" to "./gradlew tasks --all",
                "Gradle 测试" to "./gradlew test",
                "Gradle 构建 Debug" to "./gradlew assembleDebug",
                "Gradle 清理" to "./gradlew clean"
            )
            File(dir, "manage.py").exists() -> listOf(
                "Django 开发服务" to "python3 manage.py runserver 0.0.0.0:8000",
                "Django 迁移检查" to "python3 manage.py showmigrations",
                "Django 测试" to "python3 manage.py test"
            )
            File(dir, "requirements.txt").exists() || File(dir, "pyproject.toml").exists() -> listOf(
                "Python 测试" to "python3 -m pytest",
                "Python HTTP 服务" to "python3 -m http.server 8000",
                "Python 版本" to "python3 --version"
            )
            File(dir, "go.mod").exists() -> listOf(
                "Go 测试" to "go test ./...",
                "Go 构建" to "go build ./...",
                "Go 整理依赖" to "go mod tidy"
            )
            File(dir, "Cargo.toml").exists() -> listOf(
                "Cargo 测试" to "cargo test",
                "Cargo 构建" to "cargo build",
                "Cargo 元数据" to "cargo metadata --no-deps"
            )
            else -> emptyList()
        }
        if (scripts.isEmpty()) return toast("未识别到可用项目脚本")
        AlertDialog.Builder(activity)
            .setTitle("项目脚本")
            .setItems(scripts.map { "${it.first}\n${it.second}" }.toTypedArray()) { _, which ->
                runInTerminal("cd \"${dir.absolutePath}\" && ${scripts[which].second}")
            }
            .setPositiveButton("复制脚本") { _, _ ->
                copyText("AIDev 项目脚本", scripts.joinToString("\n") { "${it.first}: ${it.second}" })
                toast("已复制项目脚本")
            }
            .show()
    }

    private fun exportProjectSummary(dir: File) {
        val out = File(dir, "aidev-project-summary.txt")
        val body = listOf(
            "AIDev 项目摘要",
            "项目：${dir.name}",
            "路径：${dir.absolutePath}",
            "健康摘要：${projectHealthSummary(dir)}",
            "安装：${projectInstallCommand(dir)}",
            "开发：${projectDevCommand(dir)}",
            "测试：${projectTestCommand(dir)}",
            "构建：${projectBuildCommand(dir)}",
            "诊断：${projectHealthCommand(dir)}",
            "修复：${projectRepairCommand(dir)}"
        ).joinToString("\n")
        runCatching { out.writeText(body) }
            .onSuccess {
                reloadAll()
                toast("已导出项目摘要")
            }
            .onFailure { toast("导出失败：${it.message}") }
    }

    private fun projectHealthSummary(dir: File): String {
        val markers = mutableListOf<String>()
        if (File(dir, "README.md").exists() || File(dir, "README.txt").exists()) markers.add("README")
        if (File(dir, ".git").exists()) markers.add("Git")
        if (File(dir, "package.json").exists()) markers.add("Node")
        if (File(dir, "build.gradle").exists() || File(dir, "build.gradle.kts").exists()) markers.add("Gradle")
        if (File(dir, "requirements.txt").exists() || File(dir, "pyproject.toml").exists()) markers.add("Python")
        if (File(dir, "go.mod").exists()) markers.add("Go")
        if (File(dir, "Cargo.toml").exists()) markers.add("Rust")
        return if (markers.isEmpty()) "未发现常见项目标记" else markers.joinToString(" · ")
    }

    private fun showReadme(dir: File) {
        val readme = listOf("README.md", "README.txt", "readme.md").map { File(dir, it) }.firstOrNull { it.isFile }
        if (readme == null) {
            toast("未找到 README")
            return
        }
        val text = runCatching { readme.readText().take(16000) }.getOrElse { "读取失败：${it.message}" }
        AlertDialog.Builder(activity)
            .setTitle(readme.name)
            .setMessage(text)
            .setPositiveButton("编辑") { _, _ ->
                if (activeLeft) leftSelected = readme else rightSelected = readme
                editSelected()
            }
            .setNeutralButton("复制") { _, _ ->
                copyText("AIDev README", text)
                toast("已复制 README")
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun projectInstallCommand(dir: File): String = when {
        File(dir, "package.json").exists() -> "cd \"${dir.absolutePath}\" && npm install"
        File(dir, "requirements.txt").exists() -> "cd \"${dir.absolutePath}\" && pip install -r requirements.txt --break-system-packages"
        File(dir, "pyproject.toml").exists() -> "cd \"${dir.absolutePath}\" && python3 -m pip install . --break-system-packages"
        File(dir, "Cargo.toml").exists() -> "cd \"${dir.absolutePath}\" && cargo fetch"
        File(dir, "go.mod").exists() -> "cd \"${dir.absolutePath}\" && go mod download"
        else -> "cd \"${dir.absolutePath}\" && ls -la"
    }

    private fun projectDevCommand(dir: File): String = when {
        File(dir, "package.json").exists() -> "cd \"${dir.absolutePath}\" && npm run dev"
        File(dir, "manage.py").exists() -> "cd \"${dir.absolutePath}\" && python3 manage.py runserver 0.0.0.0:8000"
        File(dir, "requirements.txt").exists() || File(dir, "pyproject.toml").exists() -> "cd \"${dir.absolutePath}\" && python3 -m http.server 8000"
        else -> "cd \"${dir.absolutePath}\" && python3 -m http.server 8000"
    }

    private fun projectTestCommand(dir: File): String = when {
        File(dir, "package.json").exists() -> "cd \"${dir.absolutePath}\" && npm test"
        File(dir, "build.gradle").exists() || File(dir, "build.gradle.kts").exists() -> "cd \"${dir.absolutePath}\" && ./gradlew test"
        File(dir, "requirements.txt").exists() || File(dir, "pyproject.toml").exists() -> "cd \"${dir.absolutePath}\" && python3 -m pytest"
        File(dir, "Cargo.toml").exists() -> "cd \"${dir.absolutePath}\" && cargo test"
        File(dir, "go.mod").exists() -> "cd \"${dir.absolutePath}\" && go test ./..."
        else -> "cd \"${dir.absolutePath}\" && ls -la"
    }

    private fun projectBuildCommand(dir: File): String = when {
        File(dir, "package.json").exists() -> "cd \"${dir.absolutePath}\" && npm run build"
        File(dir, "build.gradle").exists() || File(dir, "build.gradle.kts").exists() -> "cd \"${dir.absolutePath}\" && ./gradlew assembleDebug"
        File(dir, "Cargo.toml").exists() -> "cd \"${dir.absolutePath}\" && cargo build"
        File(dir, "go.mod").exists() -> "cd \"${dir.absolutePath}\" && go build ./..."
        else -> "cd \"${dir.absolutePath}\" && ls -la"
    }

    private fun projectRepairCommand(dir: File): String = when {
        File(dir, "package.json").exists() -> "cd \"${dir.absolutePath}\" && rm -rf node_modules package-lock.json && npm install"
        File(dir, "build.gradle").exists() || File(dir, "build.gradle.kts").exists() -> "cd \"${dir.absolutePath}\" && ./gradlew --stop; ./gradlew clean"
        File(dir, "requirements.txt").exists() -> "cd \"${dir.absolutePath}\" && python3 -m pip install -r requirements.txt --break-system-packages"
        File(dir, "pyproject.toml").exists() -> "cd \"${dir.absolutePath}\" && python3 -m pip install . --break-system-packages"
        File(dir, "Cargo.toml").exists() -> "cd \"${dir.absolutePath}\" && cargo clean && cargo fetch"
        File(dir, "go.mod").exists() -> "cd \"${dir.absolutePath}\" && go clean -cache && go mod tidy"
        else -> "cd \"${dir.absolutePath}\" && pwd && ls -la"
    }

    private fun projectHealthCommand(dir: File): String = when {
        File(dir, "package.json").exists() -> "cd \"${dir.absolutePath}\" && node -e \"const p=require('./package.json'); console.log('name:',p.name||'-'); console.log('scripts:', Object.keys(p.scripts||{}).join(','))\" && npm pkg get scripts"
        File(dir, "build.gradle").exists() || File(dir, "build.gradle.kts").exists() -> "cd \"${dir.absolutePath}\" && ./gradlew tasks --all | head -80"
        File(dir, "requirements.txt").exists() || File(dir, "pyproject.toml").exists() -> "cd \"${dir.absolutePath}\" && python3 --version && python3 -m pip --version && python3 -m pytest --collect-only"
        File(dir, "Cargo.toml").exists() -> "cd \"${dir.absolutePath}\" && cargo metadata --no-deps"
        File(dir, "go.mod").exists() -> "cd \"${dir.absolutePath}\" && go list ./..."
        else -> "cd \"${dir.absolutePath}\" && pwd && ls -la"
    }

    private fun runInTerminal(command: String) {
        val dir = selected()?.takeIf { it.isDirectory } ?: activeDir()
        rememberProjectAction(commandLabel(command), dir, command)
        if (activity is ShellActivity) {
            TerminalCommandBus.pending = command
            (activity as ShellActivity).switchTo(ShellActivity.TAB_TERMINAL)
        }
    }

    private fun commandLabel(command: String): String = when {
        command.contains("git status") -> "Git"
        command.contains("test") || command.contains("pytest") -> "测试"
        command.contains("build") || command.contains("assemble") -> "构建"
        command.contains("install") || command.contains("fetch") -> "依赖"
        command.contains("clean") || command.contains("tidy") -> "修复"
        command.contains("tasks --all") || command.contains("collect-only") -> "诊断"
        else -> "命令"
    }

    private fun rememberProjectAction(label: String, dir: File, command: String) {
        val prefs = activity.getSharedPreferences("aidev_ui", Activity.MODE_PRIVATE)
        val line = "${System.currentTimeMillis()}\t$label\t${dir.absolutePath}\t$command"
        val old = prefs.getString("project_action_history", "") ?: ""
        val next = (old.lines().filter { it.isNotBlank() } + line).takeLast(20).joinToString("\n")
        prefs.edit().putString("project_action_history", next).apply()
    }

    private fun showApkInfo(file: File) {
        val info = activity.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
        val body = if (info == null) {
            "无法解析 APK 信息。\n\n路径：${file.absolutePath}\n大小：${formatSize(file.length())}"
        } else {
            val versionName = info.versionName ?: "-"
            val versionCode = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode.toString() else info.versionCode.toString()
            "包名：${info.packageName}\n版本名：$versionName\n版本号：$versionCode\n大小：${formatSize(file.length())}\n路径：${file.absolutePath}"
        }
        AlertDialog.Builder(activity)
            .setTitle("APK 信息")
            .setMessage(body)
            .setPositiveButton("复制路径") { _, _ -> copySelectedPath() }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun showImageInfo(file: File) {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        AlertDialog.Builder(activity)
            .setTitle("图片信息")
            .setMessage("文件：${file.name}\n尺寸：${options.outWidth} × ${options.outHeight}\n类型：${options.outMimeType ?: file.extension}\n大小：${formatSize(file.length())}\n路径：${file.absolutePath}")
            .setPositiveButton("复制路径") { _, _ -> copySelectedPath() }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun showBinaryInfo(file: File) {
        AlertDialog.Builder(activity)
            .setTitle("文件信息")
            .setMessage("文件：${file.name}\n类型：${file.extension.ifBlank { "未知/二进制" }}\n大小：${formatSize(file.length())}\n路径：${file.absolutePath}\n\n该文件不适合直接作为文本预览。")
            .setPositiveButton("复制路径") { _, _ -> copySelectedPath() }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun isImageFile(file: File): Boolean =
        file.extension.lowercase() in setOf("png", "jpg", "jpeg", "webp", "gif", "bmp")

    private fun isLikelyText(file: File): Boolean =
        runCatching {
            file.inputStream().use { input ->
                val buffer = ByteArray(2048)
                val n = input.read(buffer)
                if (n <= 0) return@use true
                for (i in 0 until n) {
                    val b = buffer[i].toInt() and 0xFF
                    if (b == 0) return@use false
                }
                true
            }
        }.getOrDefault(false)

    private fun copySelectedPath() {
        val src = selected() ?: activeDir()
        copyText("AIDev 文件路径", src.absolutePath)
        toast("已复制路径")
    }

    private fun searchActiveDir() {
        inputAllowAny("搜索文件", "输入文件名关键词") { keyword ->
            val base = activeDir()
            val matches = runCatching {
                base.walkTopDown()
                    .maxDepth(4)
                    .filter { it.name.contains(keyword, ignoreCase = true) }
                    .take(60)
                    .toList()
            }.getOrElse { emptyList() }
            if (matches.isEmpty()) {
                toast("没有找到匹配文件")
                return@inputAllowAny
            }
            AlertDialog.Builder(activity)
                .setTitle("搜索结果")
                .setItems(matches.map { it.absolutePath.removePrefix(base.absolutePath).ifBlank { it.absolutePath } }.toTypedArray()) { _, which ->
                    val file = matches[which]
                    if (file.isDirectory) {
                        if (activeLeft) leftDir = file else rightDir = file
                    } else {
                        if (activeLeft) leftSelected = file else rightSelected = file
                    }
                    loadPane(activeLeft)
                }
                .show()
        }
    }

    private fun addFavorite() {
        val prefs = activity.getSharedPreferences("aidev_ui", Activity.MODE_PRIVATE)
        val set = prefs.getStringSet("file_favorites", emptySet())?.toMutableSet() ?: mutableSetOf()
        set.add(activeDir().absolutePath)
        prefs.edit().putStringSet("file_favorites", set).apply()
        toast("已收藏当前路径")
    }

    private fun showFavorites() {
        val prefs = activity.getSharedPreferences("aidev_ui", Activity.MODE_PRIVATE)
        val favorites = prefs.getStringSet("file_favorites", emptySet())?.toList()?.sorted().orEmpty()
        if (favorites.isEmpty()) {
            toast("暂无收藏路径")
            return
        }
        AlertDialog.Builder(activity)
            .setTitle("路径收藏")
            .setItems(favorites.toTypedArray()) { _, which ->
                val dir = File(favorites[which])
                if (!dir.isDirectory) {
                    toast("路径不可用")
                    return@setItems
                }
                if (activeLeft) leftDir = dir else rightDir = dir
                loadPane(activeLeft)
            }
            .setNegativeButton("清空收藏") { _, _ ->
                prefs.edit().remove("file_favorites").apply()
                toast("已清空收藏")
            }
            .show()
    }

    private fun showQuickDirs() {
        val dirs = listOf(
            "内部存储" to Environment.getExternalStorageDirectory(),
            "下载目录" to File(Environment.getExternalStorageDirectory(), "Download"),
            "AIDev Home" to File(activity.filesDir, "home"),
            "Ubuntu Root" to File(activity.filesDir, "home/ubuntu-rootfs"),
            "项目目录" to File(activity.filesDir, "home/ubuntu-rootfs/root/projects"),
            "任务日志" to File(activity.filesDir, "home/tasks")
        ).filter { it.second.exists() }
        if (dirs.isEmpty()) return toast("暂无可用常用目录")
        AlertDialog.Builder(activity)
            .setTitle("常用目录")
            .setItems(dirs.map { "${it.first}\n${it.second.absolutePath}" }.toTypedArray()) { _, which ->
                val dir = dirs[which].second
                if (activeLeft) leftDir = dir else rightDir = dir
                rememberRecentDir(dir)
                loadPane(activeLeft)
            }
            .show()
    }

    private fun showRecentProjects() {
        val prefs = activity.getSharedPreferences("aidev_ui", Activity.MODE_PRIVATE)
        val recent = prefs.getStringSet("file_recent_dirs", emptySet())?.toMutableSet() ?: mutableSetOf()
        val roots = listOf(
            File(activity.filesDir, "home/ubuntu-rootfs/root/projects"),
            File(activity.filesDir, "home/projects"),
            File(Environment.getExternalStorageDirectory(), "Download")
        )
        roots.filter { it.isDirectory }.flatMap { root ->
            root.listFiles()?.filter { it.isDirectory }?.take(20).orEmpty()
        }.forEach { recent.add(it.absolutePath) }
        val dirs = recent.map { File(it) }.filter { it.isDirectory }.sortedBy { it.name.lowercase() }.take(60)
        if (dirs.isEmpty()) return toast("暂无最近项目")
        AlertDialog.Builder(activity)
            .setTitle("最近项目")
            .setItems(dirs.map { "${it.name}\n${it.absolutePath}" }.toTypedArray()) { _, which ->
                val dir = dirs[which]
                if (activeLeft) leftDir = dir else rightDir = dir
                rememberRecentDir(dir)
                loadPane(activeLeft)
            }
            .show()
    }

    private fun installSelectedApk(host: ShellHost) {
        val file = selected() ?: return toast("请先选择 APK 文件")
        if (!file.isFile || !file.name.endsWith(".apk", ignoreCase = true)) {
            toast("当前选择的不是 APK")
            return
        }
        AlertDialog.Builder(activity)
            .setTitle("安装 APK")
            .setMessage("将通过终端执行：\npm install -r \"${file.absolutePath}\"")
            .setPositiveButton("执行安装") { _, _ ->
                host.openTerminal("pm install -r \"${file.absolutePath}\"")
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun rememberRecentDir(dir: File) {
        val prefs = activity.getSharedPreferences("aidev_ui", Activity.MODE_PRIVATE)
        val set = prefs.getStringSet("file_recent_dirs", emptySet())?.toMutableSet() ?: mutableSetOf()
        set.add(dir.absolutePath)
        while (set.size > 80) set.remove(set.first())
        prefs.edit().putStringSet("file_recent_dirs", set).apply()
    }

    private fun fileActions(file: File) {
        AlertDialog.Builder(activity)
            .setTitle(file.name)
            .setItems(arrayOf("预览", "编辑", "另存为", "标记当前项目", "跳到当前项目", "项目识别", "项目工作区", "复制路径", "复制到对侧", "移动到对侧", "重命名", "删除")) { _, which ->
                when (which) {
                    0 -> previewSelected()
                    1 -> editSelected()
                    2 -> saveSelectedAs()
                    3 -> markCurrentProject()
                    4 -> jumpCurrentProject()
                    5 -> inspectProject()
                    6 -> projectWorkspace()
                    7 -> copySelectedPath()
                    8 -> copyToOther(false)
                    9 -> copyToOther(true)
                    10 -> renameSelected()
                    11 -> deleteSelected()
                }
            }.show()
    }

    private fun openStorageSettings() {
        if (Build.VERSION.SDK_INT >= 30) {
            runCatching {
                activity.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply { data = Uri.parse("package:${activity.packageName}") })
            }.onFailure { activity.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
        } else {
            activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.parse("package:${activity.packageName}") })
        }
    }

    private fun input(title: String, hint: String, cb: (String) -> Unit) {
        val edit = EditText(activity).apply {
            setText(hint)
            selectAll()
        }
        AlertDialog.Builder(activity).setTitle(title).setView(edit).setPositiveButton("确定") { _, _ ->
            val text = edit.text.toString().trim()
            if (text.isNotEmpty() && !text.contains("/")) cb(text)
        }.setNegativeButton("取消", null).show()
    }

    private fun inputAllowAny(title: String, hint: String, cb: (String) -> Unit) {
        val edit = EditText(activity).apply {
            setHint(hint)
        }
        AlertDialog.Builder(activity).setTitle(title).setView(edit).setPositiveButton("确定") { _, _ ->
            val text = edit.text.toString().trim()
            if (text.isNotEmpty()) cb(text)
        }.setNegativeButton("取消", null).show()
    }

    private fun action(label: String, click: () -> Unit): TextView =
        ui.text(label, 12f, ui.palette.text).apply {
            gravity = Gravity.CENTER
            background = ui.subtleCommandButtonBackground()
            setOnClickListener {
                ui.pulse()
                click()
            }
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            includeFontPadding = false
            layoutParams = LinearLayout.LayoutParams(ui.dp(64), ui.dp(34)).apply { setMargins(ui.dp(2), ui.dp(2), ui.dp(2), ui.dp(4)) }
            setPadding(ui.dp(4), 0, ui.dp(4), 0)
        }

    private fun paneHeaderBg(): android.graphics.drawable.GradientDrawable =
        android.graphics.drawable.GradientDrawable().apply {
            setColor(0x221D4ED8)
            cornerRadius = ui.dp(10).toFloat()
            setStroke(ui.dp(1), ui.palette.outline)
        }

    private fun copyDir(src: File, dst: File) {
        dst.mkdirs()
        src.listFiles()?.forEach { if (it.isDirectory) copyDir(it, File(dst, it.name)) else it.copyTo(File(dst, it.name)) }
    }

    private fun clearSelection() {
        leftSelected = null
        rightSelected = null
    }
    private fun label(file: File): String = (if (file.isDirectory) "📁 " else "📄 ") + file.name + if (file.isFile) "  ${formatSize(file.length())}" else ""
    private fun formatSize(n: Long): String = when {
        n < 1024 -> "${n}B"
        n < 1024 * 1024 -> "%.1fK".format(n / 1024.0)
        n < 1024L * 1024L * 1024L -> "%.1fM".format(n / 1024.0 / 1024.0)
        else -> "%.1fG".format(n / 1024.0 / 1024.0 / 1024.0)
    }
    private fun info(text: String): TextView = ui.text(text, 12f, ui.palette.muted).apply { setPadding(ui.dp(8), ui.dp(10), ui.dp(8), ui.dp(10)) }
    private fun copyText(label: String, text: String) {
        (activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(label, text))
    }
    private fun toast(text: String) = Toast.makeText(activity, text, Toast.LENGTH_SHORT).show()
}

class EmbeddedTasksPage : ShellPage {
    private lateinit var activity: Activity
    private lateinit var ui: AIDevUi
    private lateinit var list: LinearLayout
    private lateinit var host: ShellHost

    override fun create(activity: Activity, ui: AIDevUi, host: ShellHost): View {
        this.activity = activity
        this.ui = ui
        this.host = host
        list = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(18), ui.dp(12), ui.dp(18), ui.dp(24))
        }
        reload()
        return ScrollView(activity).apply { addView(list) }
    }

    override fun onSelected(activity: Activity, view: View) {
        if (::list.isInitialized) reload()
    }

    private fun reload() {
        list.removeAllViews()
        val tasks = taskDir().listFiles { f -> f.name.endsWith(".meta") }?.mapNotNull { parseTask(it) }?.sortedByDescending { it.id } ?: emptyList()
        val running = tasks.count { isRunning(it.pid) }
        list.addView(ui.section("任务与服务", "后台任务、AI Agent、Web 服务和最近日志统一放在一个 Shell 内容层"))
        list.addView(ui.rowOf(ui.statusCard("运行中", "${running} 个", running > 0), ui.statusCard("任务记录", "${tasks.size} 个", tasks.isNotEmpty())))
        list.addView(ui.rowOf(
            ui.statusCard("OpenCode", if (opencodeInstalled()) "已检测到" else "未安装", opencodeInstalled()),
            ui.statusCard("AI 配置", if (providerConfigured()) "有配置" else "待配置", providerConfigured())
        ))
        list.addView(ui.rowOf(
            ui.statusCard("监听端口", "${listeningPorts().size} 个", listeningPorts().isNotEmpty()),
            ui.statusCard("电池优化", if (batteryIgnored()) "已忽略" else "受限制", batteryIgnored())
        ))
        list.addView(ui.section("AI 代理运行面板", "围绕 OpenCode 的启动、上下文、日志、端口和复盘集中处理"))
        list.addView(ui.rowOf(
            ui.actionCard("原生 OpenCode", "多会话、Prompt、SSE、TODO、Diff", "NATIVE") {
                com.aidev.terminal.opencode.OpencodeNativePanel.showHome(activity)
            },
            ui.actionCard("新建原生会话", "直接通过 HTTP API 提交任务", "API") {
                com.aidev.terminal.opencode.OpencodeNativePanel.createSessionAndPrompt(activity)
            }
        ))
        list.addView(ui.rowOf(
            ui.statusCard("当前项目", currentProject()?.name ?: "未标记", currentProject() != null),
            ui.statusCard("最近代理日志", recentAgentLog()?.name ?: "暂无", recentAgentLog() != null)
        ))
        currentProject()?.let { project ->
            list.addView(ui.rowOf(
                ui.actionCard("OpenCode 项目", "在当前项目启动 AI 代理", "AI") { host.openTerminal("cd \"${project.absolutePath}\" && aidev-opencode") },
                ui.actionCard("后台代理", "后台运行 OpenCode 并记录日志", "BG") { host.openTerminal("cd \"${project.absolutePath}\" && aidev-opencode-task") }
            ))
            list.addView(ui.rowOf(
                ui.actionCard("代理上下文", "输出项目/文件/Git/任务摘要", "CTX") { host.openTerminal("cd \"${project.absolutePath}\" && aidev-agent-context") },
                ui.actionCard("更多 AI", "日志、诊断、端口和上下文文件", "MORE") { showAgentPanelMore(project) }
            ))
        }
        list.addView(ui.rowOf(
            ui.actionCard("日志摘要", "抽取错误、命令和修改线索", "SUM") { showAgentLogSummary() },
            ui.actionCard("追踪代理", "tail 最近 AI/任务日志", "TAIL") { host.openTerminal("aidev-agent-tail") }
        ))
        list.addView(ui.rowOf(
            ui.actionCard("安装 OpenCode", "在终端执行 install-aitool", "AI") { host.openTerminal("install-aitool") },
            ui.actionCard("端口详情", "直接查看 LISTEN 端口", "PORT") { showPortDetails() }
        ))
        list.addView(ui.rowOf(
            ui.actionCard("任务模板", "常用后台任务一键生成", "TPL") { showTaskTemplates() },
            ui.actionCard("Logcat", "后台记录 Android 日志", "LOG") { host.openTerminal("task-run logcat 'logcat'") }
        ))
        list.addView(ui.rowOf(
            ui.actionCard("启动常驻", "启动前台服务与 WakeLock", "KEEP") {
                KeepAliveService.start(activity)
                Toast.makeText(activity, "已启动后台常驻", Toast.LENGTH_SHORT).show()
            },
            ui.actionCard("搜索任务", "按名称、命令或 ID 查找", "FIND") { searchTasks(tasks) }
        ))
        list.addView(ui.rowOf(
            ui.actionCard("环境检测", "检查开发与服务环境", "CHECK") { host.openTerminal("check-dev-env") },
            ui.actionCard("刷新列表", "重新读取任务与端口状态", "REF") { reload() }
        ))
        list.addView(ui.section("任务列表", "点击任务查看命令、PID 和最近 80 行日志"))
        if (tasks.isEmpty()) {
            list.addView(emptyCard())
        } else {
            tasks.forEach { list.addView(taskRow(it)) }
        }
    }

    private fun taskDir() = File(activity.filesDir, "home/tasks").apply { mkdirs() }

    private fun showAgentPanelMore(project: File) {
        val actions = listOf(
            "原生 · OpenCode 面板" to { com.aidev.terminal.opencode.OpencodeNativePanel.showHome(activity) },
            "原生 · 新建会话并提问" to { com.aidev.terminal.opencode.OpencodeNativePanel.createSessionAndPrompt(activity) },
            "原生 · 会话列表" to { com.aidev.terminal.opencode.OpencodeNativePanel.showSessions(activity) },
            "原生 · 当前 TODO" to { com.aidev.terminal.opencode.OpencodeNativePanel.showActiveTodo(activity) },
            "原生 · 当前 Diff" to { com.aidev.terminal.opencode.OpencodeNativePanel.showActiveDiff(activity) },
            "原生 · SSE 事件监听" to { com.aidev.terminal.opencode.OpencodeNativePanel.showEventConsole(activity) },
            "项目 · 当前项目目录" to { host.openTerminal("cd \"${project.absolutePath}\" && pwd && ls -la") },
            "项目 · 项目 Git" to { host.openTerminal("cd \"${project.absolutePath}\" && git status --short --branch") },
            "项目 · 项目诊断" to { host.openTerminal(currentProjectTask(projectHealthCommand(project))) },
            "日志 · 异常日志" to { searchErrorLogs() },
            "日志 · 代理日志" to { host.openTerminal("aidev-agent-log") },
            "上下文 · 导出上下文" to { host.openTerminal("cd \"${project.absolutePath}\" && aidev-agent-context-file") },
            "检查 · 启动检查" to { host.openTerminal("cd \"${project.absolutePath}\" && aidev-opencode-preflight") },
            "日志 · 日志摘要" to { showAgentLogSummary() },
            "日志 · 追踪代理" to { host.openTerminal("aidev-agent-tail") },
            "系统 · 端口详情" to { showPortDetails() },
            "安装 · 安装 OpenCode" to { host.openTerminal("install-aitool") },
            "任务 · 任务模板" to { showTaskTemplates() }
        )
        val recent = recentTaskMenuLabels().filter { label -> actions.any { it.first == label } }
        val display = recent.map { "最近 · ${it.substringAfter(" · ")}" to it } + actions.filterNot { recent.contains(it.first) }.map { it.first to it.first }
        AlertDialog.Builder(activity)
            .setTitle("AI 代理更多")
            .setItems(display.map { it.first }.toTypedArray()) { _, which ->
                val original = display[which].second
                rememberTaskMenuLabel(original)
                actions.firstOrNull { it.first == original }?.second?.invoke()
            }
            .setNeutralButton("搜索") { _, _ -> searchTaskMoreMenu(actions) }
            .show()
    }

    private fun searchTaskMoreMenu(actions: List<Pair<String, () -> Unit>>) {
        val edit = EditText(activity).apply { hint = "输入 日志、项目、上下文、端口" }
        AlertDialog.Builder(activity)
            .setTitle("搜索 AI 更多")
            .setView(edit)
            .setPositiveButton("搜索") { _, _ ->
                val keyword = edit.text.toString().trim()
                val matches = actions.filter { keyword.isBlank() || it.first.contains(keyword, true) }.take(30)
                if (matches.isEmpty()) return@setPositiveButton Toast.makeText(activity, "没有匹配项", Toast.LENGTH_SHORT).show()
                AlertDialog.Builder(activity)
                    .setTitle("搜索结果")
                    .setItems(matches.map { it.first }.toTypedArray()) { _, which ->
                        rememberTaskMenuLabel(matches[which].first)
                        matches[which].second.invoke()
                    }
                    .show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun recentTaskMenuLabels(): List<String> =
        activity.getSharedPreferences("aidev_ui", Activity.MODE_PRIVATE).getString("recent_task_more", "")?.lines()?.filter { it.isNotBlank() }.orEmpty().takeLast(3).reversed()

    private fun rememberTaskMenuLabel(label: String) {
        val prefs = activity.getSharedPreferences("aidev_ui", Activity.MODE_PRIVATE)
        val old = prefs.getString("recent_task_more", "")?.lines()?.filter { it.isNotBlank() && it != label }.orEmpty()
        prefs.edit().putString("recent_task_more", (old + label).takeLast(6).joinToString("\n")).apply()
    }

    private fun currentProject(): File? =
        activity.getSharedPreferences("aidev_ui", Activity.MODE_PRIVATE)
            .getString("current_project_path", "")
            ?.takeIf { it.isNotBlank() }
            ?.let { File(it) }
            ?.takeIf { it.isDirectory }

    private fun opencodeInstalled(): Boolean =
        File(activity.filesDir, "home/ubuntu-rootfs/root/.opencode/bin/opencode").exists() ||
            File(activity.filesDir, "home/.opencode/bin/opencode").exists() ||
            File(activity.filesDir, "home/ubuntu-rootfs/usr/local/bin/opencode").exists()

    private fun providerConfigured(): Boolean =
        File(activity.filesDir, "home/ubuntu-rootfs/root/.config/opencode").exists() ||
            File(activity.filesDir, "home/ubuntu-rootfs/root/.opencode").exists() ||
            File(activity.filesDir, "home/.config/opencode").exists()

    private fun recentAgentLog(): File? =
        taskDir().listFiles { f -> f.name.endsWith(".log") }
            ?.sortedByDescending { it.lastModified() }
            ?.firstOrNull { it.name.contains("opencode", true) || runCatching { it.readText().contains("opencode", true) }.getOrDefault(false) }
            ?: taskDir().listFiles { f -> f.name.endsWith(".log") }?.maxByOrNull { it.lastModified() }

    private fun showAgentLogSummary() {
        val log = recentAgentLog()
        if (log == null || !log.isFile) {
            Toast.makeText(activity, "暂无代理日志", Toast.LENGTH_SHORT).show()
            return
        }
        val summary = summarizeAgentLog(log)
        AlertDialog.Builder(activity)
            .setTitle("代理日志摘要")
            .setMessage(summary)
            .setPositiveButton("复制摘要") { _, _ ->
                copyText("AIDev 代理日志摘要", summary)
                Toast.makeText(activity, "已复制摘要", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("追踪日志") { _, _ -> host.openTerminal("tail -f \"${log.absolutePath}\"") }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun summarizeAgentLog(log: File): String {
        val lines = runCatching { log.readLines().takeLast(600) }.getOrDefault(emptyList())
        fun hits(vararg keys: String, limit: Int = 20): List<String> =
            lines.filter { line -> keys.any { line.contains(it, ignoreCase = true) } }.takeLast(limit)
        val commands = hits("command", "running", "exec", "npm ", "python", "gradle", "cargo", "go ", "git ", limit = 18)
        val errors = hits("error", "failed", "exception", "traceback", "cannot", "not found", "denied", limit = 30)
        val files = hits("modified", "created", "updated", "wrote", "write", "saved", "changed", limit = 18)
        return listOf(
            "日志：${log.absolutePath}",
            "",
            "最近命令：",
            commands.ifEmpty { listOf("未识别到命令线索") }.joinToString("\n"),
            "",
            "错误/失败：",
            errors.ifEmpty { listOf("未识别到明显错误") }.joinToString("\n"),
            "",
            "文件修改线索：",
            files.ifEmpty { listOf("未识别到文件修改线索") }.joinToString("\n")
        ).joinToString("\n")
    }

    private fun batteryIgnored(): Boolean {
        if (Build.VERSION.SDK_INT < 23) return true
        val pm = activity.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(activity.packageName)
    }

    private fun listeningPorts(): List<Int> =
        parseTcpPorts(File("/proc/net/tcp")) + parseTcpPorts(File("/proc/net/tcp6"))

    private fun parseTcpPorts(file: File): List<Int> =
        runCatching {
            file.readLines().drop(1).mapNotNull { line ->
                val columns = line.trim().split(Regex("\\s+"))
                val local = columns.getOrNull(1) ?: return@mapNotNull null
                val state = columns.getOrNull(3) ?: return@mapNotNull null
                if (state != "0A") return@mapNotNull null
                local.substringAfterLast(":").toIntOrNull(16)
            }
        }.getOrDefault(emptyList()).distinct().sorted()

    private fun showPortDetails() {
        val ports = listeningPorts().distinct().sorted()
        val body = if (ports.isEmpty()) {
            "当前没有检测到 LISTEN 端口。\n\n如果你启动了 Web 服务，请确认服务监听的是 127.0.0.1 或 0.0.0.0。"
        } else {
            ports.joinToString("\n") { port ->
                "端口 $port    本机：http://127.0.0.1:$port"
            } + "\n\n局域网访问需要服务监听 0.0.0.0，并确保 HyperOS 没有限制后台网络。"
        }
        AlertDialog.Builder(activity)
            .setTitle("监听端口详情")
            .setMessage(body)
            .setNeutralButton("复制地址") { _, _ ->
                val text = if (ports.isEmpty()) "" else ports.joinToString("\n") { "http://127.0.0.1:$it" }
                if (text.isNotEmpty()) {
                    copyText("AIDev 监听端口", text)
                    Toast.makeText(activity, "已复制端口地址", Toast.LENGTH_SHORT).show()
                }
            }
            .setPositiveButton("终端检查") { _, _ -> host.openTerminal("list-listen-ports") }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun showTaskTemplates() {
        val names = arrayOf(
            "OpenCode 服务",
            "Python HTTP 8000",
            "Node Dev",
            "Gradle Debug 构建",
            "Logcat 记录",
            "当前端口检查",
            "Git 状态",
            "Python 测试",
            "Node 测试",
            "Go 测试",
            "当前项目 Git",
            "当前项目测试",
            "当前项目构建",
            "当前项目诊断",
            "当前项目修复",
            "当前项目 OpenCode",
            "当前项目后台代理",
            "当前项目代理上下文",
            "AI 代理日志"
        )
        val commands = arrayOf(
            "task-run opencode 'opencode serve'",
            "task-run pyserver 'python3 -m http.server 8000'",
            "task-run npm-dev 'npm run dev'",
            "task-run gradle './gradlew assembleDebug'",
            "task-run logcat 'logcat'",
            "list-listen-ports",
            "task-run git-status 'git status --short --branch'",
            "task-run pytest 'python3 -m pytest'",
            "task-run npm-test 'npm test'",
            "task-run go-test 'go test ./...'",
            currentProjectTask("git status --short --branch"),
            currentProjectTask("if [ -f package.json ]; then npm test; elif [ -f build.gradle ] || [ -f build.gradle.kts ]; then ./gradlew test; elif [ -f go.mod ]; then go test ./...; else python3 -m pytest; fi"),
            currentProjectTask("if [ -f package.json ]; then npm run build; elif [ -f build.gradle ] || [ -f build.gradle.kts ]; then ./gradlew assembleDebug; elif [ -f go.mod ]; then go build ./...; elif [ -f Cargo.toml ]; then cargo build; else ls -la; fi"),
            currentProjectTask("if [ -f package.json ]; then npm pkg get scripts; elif [ -f build.gradle ] || [ -f build.gradle.kts ]; then ./gradlew tasks --all | head -80; elif [ -f go.mod ]; then go list ./...; elif [ -f Cargo.toml ]; then cargo metadata --no-deps; else python3 -m pytest --collect-only; fi"),
            currentProjectTask("if [ -f package.json ]; then rm -rf node_modules package-lock.json && npm install; elif [ -f build.gradle ] || [ -f build.gradle.kts ]; then ./gradlew --stop; ./gradlew clean; elif [ -f go.mod ]; then go clean -cache && go mod tidy; elif [ -f Cargo.toml ]; then cargo clean && cargo fetch; elif [ -f requirements.txt ]; then python3 -m pip install -r requirements.txt --break-system-packages; else ls -la; fi"),
            currentProjectTask("aidev-opencode"),
            currentProjectTask("aidev-opencode-task"),
            currentProjectTask("aidev-agent-context"),
            "aidev-agent-log"
        )
        AlertDialog.Builder(activity)
            .setTitle("任务模板")
            .setItems(names) { _, which ->
                executeTaskTemplate(names[which], commands[which])
            }
            .setNeutralButton("搜索") { _, _ -> searchTaskTemplates(names, commands) }
            .show()
    }

    private fun searchTaskTemplates(names: Array<String>, commands: Array<String>) {
        val edit = EditText(activity).apply { hint = "输入 git、test、build、fix、log、port" }
        AlertDialog.Builder(activity)
            .setTitle("搜索任务模板")
            .setView(edit)
            .setPositiveButton("搜索") { _, _ ->
                val keyword = edit.text.toString().trim()
                val matches = names.mapIndexed { i, name -> i to name }
                    .filter { keyword.isBlank() || templateMatches(it.second, commands[it.first], keyword) }
                    .take(30)
                if (matches.isEmpty()) {
                    Toast.makeText(activity, "没有匹配模板", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                AlertDialog.Builder(activity)
                    .setTitle("模板结果")
                    .setItems(matches.map { "${it.second}\n${commands[it.first]}" }.toTypedArray()) { _, which ->
                        val index = matches[which].first
                        executeTaskTemplate(names[index], commands[index])
                    }
                    .show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun templateMatches(name: String, command: String, keyword: String): Boolean {
        if (name.contains(keyword, true) || command.contains(keyword, true)) return true
        return when (keyword.lowercase()) {
            "test", "pytest" -> name.contains("测试")
            "build" -> name.contains("构建")
            "fix", "repair" -> name.contains("修复")
            "log" -> name.contains("日志") || name.contains("Logcat", true)
            "ai", "agent", "opencode" -> name.contains("AI", true) || name.contains("OpenCode", true) || name.contains("代理")
            "port" -> name.contains("端口")
            else -> false
        }
    }

    private fun executeTaskTemplate(name: String, command: String) {
        if (name.contains("修复")) {
            confirmTaskRepair(command)
        } else {
            host.openTerminal(command)
        }
    }

    private fun confirmTaskRepair(command: String) {
        AlertDialog.Builder(activity)
            .setTitle("确认执行修复模板")
            .setMessage("将执行：\n$command\n\n注意：修复模板可能删除缓存、依赖目录或锁文件。")
            .setPositiveButton("确认执行") { _, _ -> host.openTerminal(command) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun currentProjectTask(command: String): String {
        val path = activity.getSharedPreferences("aidev_ui", Activity.MODE_PRIVATE).getString("current_project_path", "").orEmpty()
        return if (path.isBlank()) "pwd && $command" else "cd \"$path\" && $command"
    }

    private fun projectHealthCommand(dir: File): String = when {
        File(dir, "package.json").exists() -> "node -e \"const p=require('./package.json'); console.log('name:',p.name||'-'); console.log('scripts:', Object.keys(p.scripts||{}).join(','))\" && npm pkg get scripts"
        File(dir, "build.gradle").exists() || File(dir, "build.gradle.kts").exists() -> "./gradlew tasks --all | head -80"
        File(dir, "requirements.txt").exists() || File(dir, "pyproject.toml").exists() -> "python3 --version && python3 -m pip --version && python3 -m pytest --collect-only"
        File(dir, "Cargo.toml").exists() -> "cargo metadata --no-deps"
        File(dir, "go.mod").exists() -> "go list ./..."
        else -> "pwd && ls -la"
    }

    private fun projectRepairCommand(dir: File): String = when {
        File(dir, "package.json").exists() -> "rm -rf node_modules package-lock.json && npm install"
        File(dir, "build.gradle").exists() || File(dir, "build.gradle.kts").exists() -> "./gradlew --stop; ./gradlew clean"
        File(dir, "requirements.txt").exists() -> "python3 -m pip install -r requirements.txt --break-system-packages"
        File(dir, "pyproject.toml").exists() -> "python3 -m pip install . --break-system-packages"
        File(dir, "Cargo.toml").exists() -> "cargo clean && cargo fetch"
        File(dir, "go.mod").exists() -> "go clean -cache && go mod tidy"
        else -> "pwd && ls -la"
    }

    private fun searchErrorLogs() {
        val matches = taskDir().listFiles { f -> f.name.endsWith(".log") }
            ?.mapNotNull { file ->
                val snippet = errorSnippet(file)
                if (snippet.isBlank()) null else file to snippet
            }.orEmpty()
        if (matches.isEmpty()) {
            Toast.makeText(activity, "最近日志未发现明显异常", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(activity)
            .setTitle("异常日志")
            .setItems(matches.map { "${it.first.name}\n${it.second.lineSequence().firstOrNull().orEmpty()}" }.toTypedArray()) { _, which ->
                showErrorLogDetail(matches[which].first, matches[which].second)
            }
            .show()
    }

    private fun errorSnippet(file: File): String {
        val lines = runCatching { file.readLines().takeLast(300) }.getOrDefault(emptyList())
        val index = lines.indexOfLast { it.contains("error", true) || it.contains("failed", true) || it.contains("exception", true) }
        if (index < 0) return ""
        val start = (index - 3).coerceAtLeast(0)
        val end = (index + 4).coerceAtMost(lines.size)
        return lines.subList(start, end).joinToString("\n")
    }

    private fun showErrorLogDetail(file: File, snippet: String) {
        AlertDialog.Builder(activity)
            .setTitle("异常片段：${file.name}")
            .setMessage(snippet)
            .setPositiveButton("复制片段") { _, _ ->
                copyText("AIDev 异常日志", snippet)
                Toast.makeText(activity, "已复制异常片段", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("追踪日志") { _, _ -> host.openTerminal("tail -f \"${file.absolutePath}\"") }
            .setNegativeButton("项目诊断") { _, _ ->
                currentProject()?.let { host.openTerminal(currentProjectTask(projectHealthCommand(it))) } ?: Toast.makeText(activity, "未标记当前项目", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun searchTasks(tasks: List<EmbeddedTaskInfo>) {
        val edit = EditText(activity).apply { setHint("输入任务名、命令、PID 或 ID") }
        AlertDialog.Builder(activity)
            .setTitle("搜索任务")
            .setView(edit)
            .setPositiveButton("搜索") { _, _ ->
                val keyword = edit.text.toString().trim()
                if (keyword.isEmpty()) return@setPositiveButton
                val matches = tasks.filter {
                    it.id.contains(keyword, ignoreCase = true) ||
                        it.name.contains(keyword, ignoreCase = true) ||
                        it.pid.contains(keyword, ignoreCase = true) ||
                        it.cmd.contains(keyword, ignoreCase = true)
                }
                if (matches.isEmpty()) {
                    Toast.makeText(activity, "没有匹配任务", Toast.LENGTH_SHORT).show()
                } else {
                    AlertDialog.Builder(activity)
                        .setTitle("搜索结果")
                        .setItems(matches.map { "${it.id}\n${it.cmd.ifBlank { it.name }}" }.toTypedArray()) { _, which ->
                            showTask(matches[which])
                        }
                        .show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun parseTask(meta: File): EmbeddedTaskInfo? {
        val map = meta.readLines().mapNotNull {
            val i = it.indexOf('=')
            if (i <= 0) null else it.substring(0, i) to it.substring(i + 1)
        }.toMap()
        val id = map["id"] ?: meta.name.removeSuffix(".meta")
        val log = map["log"] ?: File(taskDir(), "$id.log").absolutePath
        return EmbeddedTaskInfo(id, map["name"] ?: "", map["pid"] ?: "", map["started"] ?: "", map["cmd"] ?: "", log, File(log))
    }

    private fun taskRow(task: EmbeddedTaskInfo): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12))
            background = ui.subtleCommandButtonBackground()
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, ui.dp(8)) }
            val running = isRunning(task.pid)
            addView(ui.text("${task.id}  ${if (running) "运行中" else "已结束"}", 13f, if (running) ui.palette.primary else ui.palette.text, bold = true))
            addView(ui.muted("PID：${task.pid.ifBlank { "-" }}    开始：${task.started.ifBlank { "-" }}"))
            addView(ui.muted("命令：${task.cmd.ifBlank { "-" }}").apply { maxLines = 2 })
            setOnClickListener { showTask(task) }
        }

    private fun emptyCard(): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12))
            background = ui.infoPanelBackground()
            addView(ui.text("暂无后台任务", 16f, ui.palette.text, bold = true))
            addView(ui.muted("可以在终端中运行：task-run opencode 'opencode serve'"))
        }

    private fun showTask(task: EmbeddedTaskInfo) {
        val log = if (task.logFile.exists()) task.logFile.readLines().takeLast(80).joinToString("\n") else "日志不存在：${task.logPath}"
        AlertDialog.Builder(activity)
            .setTitle(task.id)
            .setMessage("状态：${if (isRunning(task.pid)) "运行中" else "已结束"}\n名称：${task.name}\nPID：${task.pid}\n命令：${task.cmd}\n日志：${task.logPath}\n\n最近日志：\n$log")
            .setPositiveButton("刷新") { _, _ -> reload() }
            .setNegativeButton(if (isRunning(task.pid)) "停止任务" else "关闭") { _, _ -> if (isRunning(task.pid)) stopTask(task) }
            .setNeutralButton("更多") { _, _ -> showTaskMore(task, log) }
            .show()
    }

    private fun showTaskMore(task: EmbeddedTaskInfo, log: String) {
        AlertDialog.Builder(activity)
            .setTitle("任务操作")
            .setItems(arrayOf("日志查看器", "复制日志", "复制日志路径", "终端追踪日志", "导出日志副本", "复制异常片段", "刷新任务页")) { _, which ->
                when (which) {
                    0 -> showLogViewer(task)
                    1 -> {
                        copyText("AIDev 任务日志", log)
                        Toast.makeText(activity, "已复制日志", Toast.LENGTH_SHORT).show()
                    }
                    2 -> {
                        copyText("AIDev 日志路径", task.logPath)
                        Toast.makeText(activity, "已复制日志路径", Toast.LENGTH_SHORT).show()
                    }
                    3 -> host.openTerminal("tail -f \"${task.logPath}\"")
                    4 -> exportTaskLog(task)
                    5 -> copyTaskErrorSnippet(task)
                    6 -> reload()
                }
            }
            .show()
    }

    private fun exportTaskLog(task: EmbeddedTaskInfo) {
        if (!task.logFile.isFile) return Toast.makeText(activity, "日志不存在", Toast.LENGTH_SHORT).show()
        val dst = File(taskDir(), "${task.id}-export.log")
        runCatching { task.logFile.copyTo(dst, overwrite = true) }
            .onSuccess {
                copyText("AIDev 导出日志路径", dst.absolutePath)
                Toast.makeText(activity, "已导出日志并复制路径", Toast.LENGTH_SHORT).show()
            }
            .onFailure { Toast.makeText(activity, "导出失败：${it.message}", Toast.LENGTH_SHORT).show() }
    }

    private fun copyTaskErrorSnippet(task: EmbeddedTaskInfo) {
        val snippet = errorSnippet(task.logFile)
        if (snippet.isBlank()) return Toast.makeText(activity, "未发现明显异常片段", Toast.LENGTH_SHORT).show()
        copyText("AIDev 异常日志", snippet)
        Toast.makeText(activity, "已复制异常片段", Toast.LENGTH_SHORT).show()
    }

    private fun showLogViewer(task: EmbeddedTaskInfo) {
        val latest = if (task.logFile.exists()) task.logFile.readLines().takeLast(180).joinToString("\n") else "日志不存在：${task.logPath}"
        AlertDialog.Builder(activity)
            .setTitle("日志：${task.id}")
            .setMessage(latest)
            .setPositiveButton("刷新") { _, _ -> showLogViewer(task) }
            .setNeutralButton("复制") { _, _ ->
                copyText("AIDev 任务日志", latest)
                Toast.makeText(activity, "已复制日志", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun isRunning(pid: String): Boolean = pid.toIntOrNull()?.let {
        runCatching {
            val p = Runtime.getRuntime().exec(arrayOf("kill", "-0", it.toString()))
            p.waitFor() == 0
        }.getOrDefault(false)
    } ?: false
    private fun stopTask(task: EmbeddedTaskInfo) {
        task.pid.toIntOrNull()?.let { Runtime.getRuntime().exec(arrayOf("kill", it.toString())).waitFor() }
        reload()
    }
    private fun copyText(label: String, text: String) {
        (activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(label, text))
    }
}

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
        content.addView(row("开发环境", "Ubuntu、基础工具链、Android 调试桥接、修复工具") { devMenu() })
        content.addView(row("AI 与服务器", "OpenCode、后台常驻、端口诊断、任务日志") { aiServerMenu() })
        content.addView(row("文件与权限", "存储访问、安装权限、Shizuku、应用详情") { permissionMenu() })
        content.addView(row("权限与环境诊断", "存储、电池、Ubuntu、OpenCode、SDK、Shizuku 状态") { diagnosticsCenter() })
        content.addView(row("系统与高级", "命令速查、命令面板、环境诊断") { advancedMenu() })
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
        AlertDialog.Builder(activity).setTitle("终端设置").setItems(arrayOf("终端字号", "新增快捷键", "管理快捷键", "清除快捷键", "打开终端 Tab", "快捷键说明", "会话说明")) { _, which ->
            when (which) {
                0 -> terminalFontDialog()
                1 -> customKeyDialog()
                2 -> manageCustomKeys()
                3 -> {
                    activity.getSharedPreferences("aidev_ui", Activity.MODE_PRIVATE).edit()
                        .remove("terminal_custom_key_label")
                        .remove("terminal_custom_key_input")
                        .remove("terminal_custom_keys")
                        .apply()
                    toast("已清除自定义快捷键")
                }
                4 -> host.switchTab(ShellActivity.TAB_TERMINAL)
                5 -> detail("快捷键说明", "内嵌终端已提供 ESC、TAB、CTRL-C、方向键、HOME、END、/、-、|、~、清屏。\n\n现在可配置最多 8 个自定义快捷键；输入内容支持 \\n、\\t 和 \\e 转义。")
                6 -> detail("会话说明", "终端 Tab 支持多会话标签、新建会话、关闭当前会话、点击标签切换、长按标签重命名。关闭最后一个会话时会自动创建新会话。")
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
        box.addView(label)
        box.addView(input)
        AlertDialog.Builder(activity)
            .setTitle("自定义快捷键")
            .setView(box)
            .setPositiveButton("保存") { _, _ ->
                val name = label.text.toString().trim().take(8)
                val value = input.text.toString()
                if (name.isNotEmpty() && value.isNotEmpty()) {
                    val old = prefs.getString("terminal_custom_keys", "") ?: ""
                    val lines = old.lines().filter { it.isNotBlank() }.toMutableList()
                    lines.add("$name\t$value")
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
                    .setMessage(lines[which].substringAfter("\t", ""))
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
        val current = prefs.getFloat("font_sp", 16f).toInt().coerceIn(8, 24)
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(20), ui.dp(10), ui.dp(20), 0)
        }
        val value = ui.text("${current}sp", 18f, ui.palette.text, bold = true)
        val seek = SeekBar(activity).apply {
            max = 16
            progress = current - 8
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    value.text = "${8 + progress}sp"
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
                prefs.edit().putFloat("font_sp", (8 + seek.progress).toFloat()).apply()
                toast("终端字号已更新，终端页可用字号按钮立即刷新")
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun devMenu() {
        AlertDialog.Builder(activity).setTitle("开发环境").setItems(arrayOf("进入 Ubuntu", "检测开发环境", "部署 Android 工具链", "修复环境", "Android 调试桥接")) { _, which ->
            when (which) {
                0 -> host.openTerminal("ubuntu")
                1 -> host.openTerminal("check-dev-env")
                2 -> host.openTerminal("deploy-android-dev")
                3 -> host.openTerminal("repair-dev-env")
                4 -> detail("Android 调试桥接", "在 Ubuntu/终端中优先使用 pmx、amx、getpropx、logcatx，避免直接执行 /system/bin/pm 造成 PRoot 链接器错误。")
            }
        }.show()
    }

    private fun aiServerMenu() {
        AlertDialog.Builder(activity).setTitle("AI 与服务器").setItems(arrayOf("安装 OpenCode", "启动 AI 服务示例", "监听端口", "后台常驻", "任务页")) { _, which ->
            when (which) {
                0 -> host.openTerminal("install-aitool")
                1 -> host.openTerminal("task-run opencode 'opencode --help'")
                2 -> host.openTerminal("list-listen-ports")
                3 -> {
                    KeepAliveService.start(activity)
                    activity.getSharedPreferences("aidev_ui", Activity.MODE_PRIVATE).edit().putBoolean("keepalive_auto", true).apply()
                    toast("后台常驻已启动")
                }
                4 -> host.switchTab(ShellActivity.TAB_TASKS)
            }
        }.show()
    }

    private fun permissionMenu() {
        AlertDialog.Builder(activity).setTitle("文件与权限").setItems(arrayOf("打开文件 Tab", "存储权限", "应用详情", "Shizuku 状态")) { _, which ->
            when (which) {
                0 -> host.switchTab(ShellActivity.TAB_FILES)
                1 -> openStorageSettings()
                2 -> openAppSettings()
                3 -> detail("Shizuku 状态", "如果 Shizuku 未运行，请先打开 Shizuku 应用并启动服务。应用已声明 Shizuku Provider，用于后续更高权限能力。")
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
        AlertDialog.Builder(activity).setTitle("系统与高级").setItems(arrayOf("命令速查", "命令面板", "环境诊断")) { _, which ->
            when (which) {
                0 -> detail("命令速查", "ubuntu\npmx list packages\namx start ...\ngetpropx ro.product.model\nlogcatx -d\ntask-list\ncheck-dev-env")
                1 -> host.showCommandPalette()
                2 -> diagnosticsCenter()
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
