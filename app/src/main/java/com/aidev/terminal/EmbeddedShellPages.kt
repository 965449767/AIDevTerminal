package com.aidev.terminal
import com.google.android.material.dialog.MaterialAlertDialogBuilder

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import kotlin.math.abs
import java.io.File

/**
 * 内嵌终端页面：终端会话管理、虚拟键盘、自动补全、TUI 模式。
 *
 * TODO: 未来拆分建议（当前因内部类交叉引用和共享状态过多，拆分风险较高）：
 *   1. TerminalImeProxyEditText (L63-191) -> 独立文件，通过接口回调解耦
 *   2. 自动补全相关方法 (L799-1035) -> CompletionEngine 辅助类
 *   3. 虚拟键盘相关方法 (L1088-1341) -> VirtualKeyboardBuilder 辅助类
 *   4. 会话管理相关方法 (L1416-1640) -> SessionManager 辅助类
 *   5. TerminalSessionClient / TerminalViewClient (L1663-1753) -> 独立 Client 类
 */
class EmbeddedTerminalPage : ShellPage, CompletionHost {
    private companion object {
        const val DEFAULT_FONT_SP = 10f
        const val MIN_FONT_SP = 10f
        const val MAX_FONT_SP = 24f
        const val MAX_SESSIONS = 8
    }

    private var activity: Activity? = null
    private var ui: AIDevUi? = null
    private var terminalView: TerminalView? = null
    private var inputProxy: com.aidev.terminal.TerminalImeProxyEditText? = null
    private lateinit var tabBar: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var completionRow: LinearLayout
    private var completionBarView: View? = null
    private var session: TerminalSession? = null
    private var current: EmbeddedTermSession? = null
    private var homeDir: File? = null
    private val sessions = mutableListOf<EmbeddedTermSession>()

    private fun Activity.spToPx(sp: Float): Int {
        val config = resources.configuration
        return (sp * config.fontScale * config.densityDpi / 160f).toInt()
    }
    private var ctrlLatched = false
    private var _autoBootstrapDone = false
    val autoBootstrapDone: Boolean get() = _autoBootstrapDone
    private var pendingFontSp = DEFAULT_FONT_SP
    private var fontApplyScheduled = false
    private var inputBuffer = ""
    private var composingBuffer = ""
    private var pwdObserver: PwdFileObserver? = null
    private val pendingRunnables = mutableListOf<Pair<View, Runnable>>()
    private var lastSyncedPwd = ""
    private var tuiIndicator: TextView? = null
    private var keyboardIndicator: TextView? = null
    private var root: View? = null
    private var tuiActive = false
    private var cachedCompletionPwd = ""
    private val sharedHandler = Handler(Looper.getMainLooper())
    private val keyEditor = VirtualKeyEditor {
        val act = activity ?: return@VirtualKeyEditor
        ui?.let { buildKeyboardRows(act, it, getOrderedKeys(act)) }
    }
    private lateinit var completionEngine: CompletionEngine

    private var manualTuiOverride = false

    private val pm by lazy { PreferencesManager(checkNotNull(activity) { "activity must be set before pm access" }) }

    private var isRearranging = false
    private var keyboardView: LinearLayout? = null
    private var currentKeyOrder = mutableListOf<String>()
    private var selectedKeyView: View? = null
    private var wiggleAnimator: android.animation.ValueAnimator? = null
    private val SWIPE_THRESHOLD_DP = 72

    // ── CompletionHost ──
    override val completionInputBuffer: String get() = inputBuffer
    override val completionComposingBuffer: String get() = composingBuffer
    override val completionHomeDir: File? get() = homeDir
    override val completionCachedPwd: String get() = cachedCompletionPwd
    override fun completionWriteSession(text: String) { session?.write(text) }
    override fun completionClearComposing() { clearComposingInput() }
    override fun completionSetInputBuffer(value: String) { inputBuffer = value }
    override fun completionFocusInput() { val act = activity ?: return; focusTerminalInput(act) }
    override fun completionRefresh() { val act = activity ?: return; completionEngine.refresh(act, ui ?: return, completionRow) }

    private fun updateTuiMode() {
        val s = session ?: return
        val isTui = runCatching {
            val emulator = s.javaClass.getMethod("getEmulator").invoke(s)
            emulator.javaClass.getMethod("isAlternateScreenActive").invoke(emulator) as? Boolean
        }.onFailure { e ->
            android.util.Log.w("AIDEV_TUI", "Failed to detect alternate screen: ${e.message}")
        }.getOrDefault(false)
        // 如果用户手动切换了 TUI 模式，不覆盖
        if (manualTuiOverride) return
        inputProxy?.tuiMode = isTui ?: false
        if (isTui != tuiActive) {
            tuiActive = isTui ?: false
            val act = activity ?: return
            if (isTui != true) {
                inputProxy?.requestFocus()
            }
        }
    }

    override fun create(activity: Activity, ui: AIDevUi, host: ShellHost): View {
        this.activity = activity
        this.ui = ui
        completionEngine = CompletionEngine(this)
        val rootView = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }
        root = rootView
        rootView.addView(topBar(activity, ui, host), LinearLayout.LayoutParams(-1, ui.dp(40)))
        tabBar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(ui.dp(6), ui.dp(2), ui.dp(6), ui.dp(2))
        }
        rootView.addView(HorizontalScrollView(activity).apply {
            isHorizontalScrollBarEnabled = false
            setBackgroundColor(0xFF0B0E12.toInt())
            addView(tabBar)
        }, LinearLayout.LayoutParams(-1, ui.dp(32)))
        rootView.addView(statusBar(activity, ui), LinearLayout.LayoutParams(-1, ui.dp(24)))
        terminalView = TerminalView(activity, null).apply {
            setBackgroundColor(Color.BLACK)
            isFocusable = true
            isFocusableInTouchMode = true
            pendingFontSp = currentFontSp(activity)
            setTextSize(fontPx(activity))
            setTerminalViewClient(viewClient(activity))
        }
        inputProxy = com.aidev.terminal.TerminalImeProxyEditText(activity).apply {
            onComposingChanged = { text ->
                composingBuffer = text
                refreshCompletions(activity)
            }
            onCommittedText = { text ->
                if (text == "\n") {
                    // 回车键：发送 CR (0x0D) 而不是 LF (0x0A)
                    session?.write("\r")
                } else if (tuiActive && ctrlLatched && text.length == 1) {
                    // TUI 模式 + Ctrl 按下：发送组合键转义序列
                    val codePoint = text[0].code
                    val ctrlCode = when {
                        codePoint in 0x41..0x5A -> codePoint - 0x40 // A-Z -> Ctrl+A-Z (0x01-0x1A)
                        codePoint in 0x61..0x7A -> codePoint - 0x60 // a-z -> Ctrl+A-Z (0x01-0x1A)
                        else -> codePoint
                    }
                    if (ctrlCode in 0x01..0x1A) {
                        session?.write(String(byteArrayOf(ctrlCode.toByte())))
                    } else {
                        session?.write(text)
                    }
                } else {
                    session?.write(text)
                    updateInputBuffer(text)
                }
            }
            onBackspace = {
                session?.write("\u007F")
                updateInputBuffer("\b")
            }
            onEnter = {
                session?.write("\r")
                updateInputBuffer("\n")
            }
        }
        // TUI 模式下按键转发给 TerminalView
        inputProxy?.tuiKeyHandler = { event ->
            terminalView?.onKeyDown(event.keyCode, event) ?: false
        }
        rootView.addView(terminalView, LinearLayout.LayoutParams(-1, 0, 1f))
        rootView.addView(inputProxy, LinearLayout.LayoutParams(1, 1))
        rootView.addView(completionBar(activity, ui), LinearLayout.LayoutParams(-1, ui.dp(32)))
        // 键盘可见性监听器：自动同步 ⌨ 指示器状态（WindowInsetsCompat 不受 softInputMode 影响）
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, insets ->
            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            val autoShow = pm.autoShowKeyboard
            val active = imeVisible || autoShow
            keyboardIndicator?.setTextColor(if (active) DesignTokens.ACCENT else 0xFF9CA3AF.toInt())
            insets
        }
        keyboardView = keys(activity, ui)
        rootView.addView(keyboardView, LinearLayout.LayoutParams(-1, ui.dp(70)))
        ensureSession(activity)
        initPwdObserver(activity)
        startShizukuBridge(activity)
        trackPostDelayed(rootView, 250) { focusTerminalInput(activity) }
        terminalView?.postDelayed({
            consumePendingCommand()
            maybeAutoBootstrapUbuntu(activity)
            focusTerminalInput(activity)
        }, 600)
        return rootView
    }

    private fun statusBar(activity: Activity, ui: AIDevUi): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0xFF0A0D12.toInt())
            setPadding(ui.dp(12), 0, ui.dp(6), 0)
            val textView = TextView(activity).apply {
                statusText = this
                text = terminalStatus(activity)
                textSize = 11f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.CENTER_VERTICAL
                setTextColor(0xFF9CA3AF.toInt())
                includeFontPadding = false
                setOnLongClickListener {
                    showFontDialog(activity)
                    true
                }
            }
            addView(textView, LinearLayout.LayoutParams(0, -1, 1f))
            tuiIndicator = tuiIndicatorView(activity, ui)
            addView(tuiIndicator, LinearLayout.LayoutParams(ui.dp(28), -1))
        }

    private fun terminalStatus(activity: Activity): String =
        "${currentFontSp(activity).toInt()}sp"

    private fun refreshStatus(activity: Activity) {
        if (::statusText.isInitialized) statusText.text = terminalStatus(activity)
    }

    private fun completionBar(activity: Activity, ui: AIDevUi): View {
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setBackgroundColor(0xFF0D1117.toInt())
        }
        completionBarView = completionEngine.buildBar(activity, ui).apply {
            completionRow = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(ui.dp(6), ui.dp(3), ui.dp(6), ui.dp(3))
            }
            addView(completionRow)
            post { completionEngine.refresh(activity, ui, completionRow) }
        }
        container.addView(completionBarView, LinearLayout.LayoutParams(0, -1, 1f))
        keyboardIndicator = keyboardIndicatorView(activity, ui)
        // FrameLayout 包裹键盘指示器，固定贴右边，TUI 模式下不会跳到左边
        val indicatorWrapper = android.widget.FrameLayout(activity).apply {
            addView(keyboardIndicator, android.widget.FrameLayout.LayoutParams(
                ui.dp(30), -1, android.view.Gravity.END
            ))
        }
        container.addView(indicatorWrapper, LinearLayout.LayoutParams(ui.dp(30), -1))
        return container
    }

    private fun refreshCompletions(activity: Activity) {
        if (!::completionRow.isInitialized || !::completionEngine.isInitialized) return
        completionEngine.refresh(activity, ui ?: return, completionRow)
    }


    override fun onSelected(activity: Activity, view: View) {
        this.activity = activity
        ensureSession(activity)
        initPwdObserver(activity)
        focusTerminalInput(activity)
        consumePendingCommand()
        terminalView?.postDelayed({
            maybeAutoBootstrapUbuntu(activity)
            focusTerminalInput(activity)
        }, 600)
    }

    override fun onDestroy(activity: Activity) {
        // 移除键盘可见性监听器
        root?.let { ViewCompat.setOnApplyWindowInsetsListener(it, null) }
        // 停止 pwd 观察者
        pwdObserver?.stop()
        pwdObserver = null
        // 停止 Shizuku 桥服务
        ShizukuBridgeService.stop()
        // 清理追踪的延迟任务
        for ((view, runnable) in pendingRunnables) {
            view.removeCallbacks(runnable)
        }
        pendingRunnables.clear()
        // 重置状态
        this.activity = null
    }

    private fun startShizukuBridge(activity: Activity) {
        val home = homeDir ?: return
        if (ShizukuLogcat.isAvailable()) {
            ShizukuBridgeService.start(home)
        }
    }

    private fun initPwdObserver(activity: Activity) {
        if (this.activity == null) return
        val home = homeDir ?: run {
            val act = this.activity ?: return
            val contentView = act.findViewById<View>(android.R.id.content) ?: return
            trackPostDelayed(contentView, 3000) { initPwdObserver(act) }
            return
        }
        val pwdFile = File(home, ".aidev-current-pwd")
        pwdObserver?.stop()
        if (!pwdFile.isFile) {
            // Ubuntu 还没引导，延迟 3 秒后重试
            val act = this.activity ?: return
            val contentView = act.findViewById<View>(android.R.id.content) ?: return
            trackPostDelayed(contentView, 3000) {
                if (pwdFile.isFile) initPwdObserver(act)
            }
            return
        }
        val syncLog = File(home, "aidev-sync.log")
        pwdObserver = PwdFileObserver(pwdFile) { ubuntuPwd ->
            if (ubuntuPwd == lastSyncedPwd) return@PwdFileObserver
            lastSyncedPwd = ubuntuPwd
            cachedCompletionPwd = ubuntuPwd
            val act = this.activity ?: return@PwdFileObserver
            val sb = StringBuilder()
            sb.appendLine("[${java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date())}] PWD: $ubuntuPwd")
            sb.appendLine("  home: ${home.absolutePath}")
            sb.appendLine("  home.exists: ${home.exists()}")
            val targetDir = SyncCoordinator.toAndroidDir(ubuntuPwd, home)
            if (targetDir != null) {
                sb.appendLine("  target: ${targetDir.absolutePath}")
                sb.appendLine("  target.exists: ${targetDir.exists()}")
                sb.appendLine("  target.isDir: ${targetDir.isDirectory}")
                sb.appendLine("  target.canRead: ${targetDir.canRead()}")
                sb.appendLine("  target.listFiles: ${targetDir.listFiles()?.size}")
                android.util.Log.d("AIDEV_SYNC", "terminal sync: $ubuntuPwd -> ${targetDir.absolutePath}")
                try {
                    if (act is ShellActivity) act.syncBrowserToDir(targetDir)
                    sb.appendLine("  -> syncBrowserToDir OK")
                } catch (e: Exception) {
                    sb.appendLine("  -> syncBrowserToDir ERROR: ${e.message}")
                    android.util.Log.e("AIDEV_SYNC", "syncBrowserToDir error", e)
                }
            } else {
                val mapped = PathBridge.ubuntuToAndroid(home, ubuntuPwd)
                sb.appendLine("  ubuntuToAndroid: ${mapped?.absolutePath}")
                sb.appendLine("  mapped.exists: ${mapped?.exists()}")
                sb.appendLine("  mapped.isDir: ${mapped?.isDirectory}")
                sb.appendLine("  isBrowsable: ${PathBridge.isBrowsable(ubuntuPwd)}")
                sb.appendLine("  -> target is null, sync SKIPPED")
            }
            try {
                syncLog.appendText(sb.toString())
                android.util.Log.d("AIDEV_SYNC_DIAG", sb.toString())
            } catch (_: Exception) {}
        }
        pwdObserver?.start()
    }

    fun silentCd(ubuntuPath: String) {
        session?.write("cd $ubuntuPath\r")
    }

    private fun toggleTuiMode(activity: Activity) {
        tuiActive = !tuiActive
        manualTuiOverride = tuiActive
        inputProxy?.tuiMode = tuiActive
        completionBarView?.visibility = if (tuiActive) View.GONE else View.VISIBLE
        tuiIndicator?.let {
            it.setTextColor(if (tuiActive) DesignTokens.ACCENT else 0xFF9CA3AF.toInt())
        }
        Toast.makeText(activity, if (tuiActive) "TUI 模式已开启" else "TUI 模式已关闭", Toast.LENGTH_SHORT).show()
        if (!tuiActive) {
            inputProxy?.requestFocus()
            refreshCompletions(activity)
        }
    }

    private fun tuiIndicatorView(activity: Activity, ui: AIDevUi): TextView =
        TextView(activity).apply {
            gravity = Gravity.CENTER
            textSize = 11f
            includeFontPadding = false
            text = "T"
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(if (tuiActive) DesignTokens.ACCENT else 0xFF9CA3AF.toInt())
            setOnClickListener { toggleTuiMode(activity) }
            setOnLongClickListener {
                Toast.makeText(activity, if (tuiActive) "TUI 模式：命令建议栏已隐藏" else "点击开启 TUI 模式（隐藏命令建议栏）", Toast.LENGTH_SHORT).show()
                true
            }
        }

    private fun keyboardIndicatorView(activity: Activity, ui: AIDevUi): TextView {
        val enabled = pm.autoShowKeyboard
        return TextView(activity).apply {
            gravity = Gravity.CENTER
            textSize = 11f
            includeFontPadding = false
            maxWidth = ui.dp(30)
            text = "\u2328"
            setTextColor(if (enabled) DesignTokens.ACCENT else 0xFF9CA3AF.toInt())
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(0x000D1117.toInt(), 0xFF0D1117.toInt())
            )
            setOnClickListener {
                val newValue = !pm.autoShowKeyboard
                pm.autoShowKeyboard = newValue
                setTextColor(if (newValue) DesignTokens.ACCENT else 0xFF9CA3AF.toInt())
                val imm = activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                val target = inputProxy ?: terminalView
                if (newValue) {
                    target?.requestFocus()
                    target?.let { v -> imm?.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT) }
                } else {
                    target?.windowToken?.let { t -> imm?.hideSoftInputFromWindow(t, 0) }
                }
            }
            setOnLongClickListener {
                Toast.makeText(activity, if (pm.autoShowKeyboard) "键盘已开启" else "键盘已关闭", Toast.LENGTH_SHORT).show()
                true
            }
        }
    }

    fun prefillCdCommand(ubuntuPath: String) {
        val cmd = "cd $ubuntuPath"
        inputBuffer = ""
        composingBuffer = ""
        session?.write("\u0015$cmd")
        inputBuffer = cmd
        val act = activity ?: return
        refreshCompletions(act)
        focusTerminalInput(act)
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
            addView(button(activity, ui, "粘贴") {
                val text = ClipboardHelper.paste(activity)
                if (text != null) session?.write(text)
                else Toast.makeText(activity, "剪贴板为空", Toast.LENGTH_SHORT).show()
            }, LinearLayout.LayoutParams(ui.dp(54), ui.dp(30)).apply {
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

            addView(button(activity, ui, "上下文") { sendAgentCommand("aidev-agent-context") }, LinearLayout.LayoutParams(0, ui.dp(30), 1f).apply { setMargins(ui.dp(3), 0, ui.dp(3), 0) })
            addView(button(activity, ui, "任务") { send("task-list") }.apply {
                setOnLongClickListener {
                    send("task-list")
                    true
                }
            }, LinearLayout.LayoutParams(0, ui.dp(30), 1f).apply { setMargins(ui.dp(3), 0, ui.dp(3), 0) })
            addView(button(activity, ui, "日志") { send("ls -lt \"${'$'}AIDEV_HOME/tasks\"/*.log 2>/dev/null | head -20") }.apply {
                setOnLongClickListener {
                    send("ls -lt \"${'$'}AIDEV_HOME/tasks\"/*.log 2>/dev/null | head -20")
                    true
                }
            }, LinearLayout.LayoutParams(0, ui.dp(30), 1f).apply { setMargins(ui.dp(3), 0, ui.dp(3), 0) })
            addView(button(activity, ui, "更多") { showAgentMenu(activity) }, LinearLayout.LayoutParams(0, ui.dp(30), 1f).apply { setMargins(ui.dp(3), 0, 0, 0) })
        }

    private fun showTerminalTopMore(activity: Activity, host: ShellHost) {
        showGroupedActionMenu(activity, "更多设置", "recent_terminal_more", listOf(
            "终端 · 诊断 Doctor" to { send("aidev-doctor") },
            "终端 · Shell 增强" to { showShellEnhancements(activity) },
            "SSH · 连接管理" to { showSshBookmarks(activity, host) }
        ))
    }

    private fun showAgentMenu(activity: Activity) {
        showGroupedActionMenu(activity, "AI 代理终端", "recent_agent_more", listOf(
            "上下文 · 导出上下文文件" to { sendAgentCommand("aidev-agent-context-file") },
            "日志 · 代理日志摘要" to { send("aidev-agent-summary") },
            "项目 · 当前项目目录" to { sendAgentCommand("pwd && git status --short --branch 2>/dev/null || true && ls -la") },
        ))
    }

    private fun showGroupedActionMenu(activity: Activity, title: String, prefKey: String, actions: List<Pair<String, () -> Unit>>) {
        val recent = recentMenuLabels(activity, prefKey).filter { label -> actions.any { it.first == label } }
        val display = (recent.map { "最近 · ${it.substringAfter(" · ")}" to it } + actions.filterNot { recent.contains(it.first) }.map { it.first to it.first })
        val ui = AIDevUi(activity, pm.sharedPreferences)
        val items = display.map { (showLabel, original) ->
            MenuBottomSheet.MenuItem(showLabel, "") {
                rememberMenuLabel(activity, prefKey, original)
                actions.firstOrNull { it.first == original }?.second?.invoke()
            }
        } + MenuBottomSheet.MenuItem("搜索", "关键词搜索菜单项") {
            searchGroupedActionMenu(activity, title, prefKey, actions)
        }
        MenuBottomSheet(activity, ui).show(title, items)
    }

    private fun searchGroupedActionMenu(activity: Activity, title: String, prefKey: String, actions: List<Pair<String, () -> Unit>>) {
        val edit = EditText(activity).apply { hint = "输入 ai、log、端口、会话、显示" }
        MaterialAlertDialogBuilder(activity)
            .setTitle("搜索$title")
            .setView(edit)
            .setPositiveButton("搜索") { _, _ ->
                val keyword = edit.text.toString().trim()
                val matches = actions.filter { keyword.isBlank() || it.first.contains(keyword, true) }.take(30)
                if (matches.isEmpty()) return@setPositiveButton Toast.makeText(activity, "没有匹配项", Toast.LENGTH_SHORT).show()
        val ui = AIDevUi(activity, pm.sharedPreferences)
                val items = matches.map { (label, action) ->
                    MenuBottomSheet.MenuItem(label, "") {
                        rememberMenuLabel(activity, prefKey, label)
                        action.invoke()
                    }
                }
                MenuBottomSheet(activity, ui).show("搜索结果", items)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun recentMenuLabels(activity: Activity, key: String): List<String> =
        pm.sharedPreferences.getString(key, "")?.lines()?.filter { it.isNotBlank() }.orEmpty().takeLast(3).reversed()

    private fun rememberMenuLabel(activity: Activity, key: String, label: String) {
        val old = pm.sharedPreferences.getString(key, "")?.lines()?.filter { it.isNotBlank() && it != label }.orEmpty()
        pm.sharedPreferences.edit().putString(key, (old + label).takeLast(6).joinToString("\n")).apply()
    }

    private fun sendAgentCommand(command: String) {
        val dir = currentProjectDir()
        if (dir != null) send("cd \"${dir.absolutePath}\" && $command") else send(command)
    }

    /** 打开 Shell 增强页面（命令历史统计、别名管理等） */
    private fun showShellEnhancements(activity: Activity) {
        val page = ShellEnhancementsPage()
        val view = page.create(activity, ui ?: AIDevUi(activity, pm.sharedPreferences), ShellHost(activity, pm.sharedPreferences, ui ?: AIDevUi(activity, pm.sharedPreferences)))
        MaterialAlertDialogBuilder(activity)
            .setTitle("Shell 增强")
            .setView(view)
            .setNegativeButton("关闭") { _, _ -> page.onSelected(activity, view) }
            .show()
    }

    private fun showSshBookmarks(activity: Activity, host: ShellHost) {
        val ui = ui ?: AIDevUi(activity, pm.sharedPreferences)
        val page = SshBookmarksPage()
        val view = page.create(activity, ui, host)
        val dialog = ui.showAsDialog(view)
        page.dismiss = { dialog.dismiss() }
    }


    private fun updateInputBuffer(text: String) {
        text.forEach { ch ->
            when (ch) {
                '\r', '\n' -> {
                    inputBuffer = ""
                    composingBuffer = ""
                }
                '\b', '\u007F' -> inputBuffer = inputBuffer.dropLast(1)
                else -> if (!ch.isISOControl()) inputBuffer += ch
            }
        }
        activity?.let { refreshCompletions(it) }
    }

    private fun clearComposingInput() {
        composingBuffer = ""
        inputProxy?.clearProxyText()
        inputProxy?.let { proxy ->
            (activity?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.restartInput(proxy)
        }
    }

    private fun focusTerminalInput(activity: Activity) {
        val proxy = inputProxy
        if (proxy != null) {
            proxy.requestFocus()
        } else {
            terminalView?.requestFocus()
        }
    }

    private fun currentProjectDir(): File? =
        activity?.let { pm.sharedPreferences.getString("current_project_path", "") }
            ?.takeIf { it.isNotBlank() }
            ?.let { File(it) }
            ?.takeIf { it.isDirectory }

    private fun applyFontPreset(activity: Activity, sp: Float) {
        val value = sp.coerceIn(MIN_FONT_SP, MAX_FONT_SP)
        pm.fontSp = value
        pendingFontSp = value
        terminalView?.setTextSize(activity!!.spToPx(value))
        terminalView?.onScreenUpdated()
        refreshStatus(activity)
    }

    private fun keys(activity: Activity, ui: AIDevUi): LinearLayout =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF111418.toInt())
            setPadding(ui.dp(4), ui.dp(3), ui.dp(4), ui.dp(3))
            keyboardView = this
            currentKeyOrder = loadKeyOrder(activity)
            buildKeyboardRows(activity, ui, getOrderedKeys(activity))
        }

    private fun buildKeyboardRows(activity: Activity, ui: AIDevUi, keys: List<EmbeddedVirtualKey>) {
        keyboardView?.removeAllViews()
        keys.chunked(6).forEach { rowKeys ->
            keyboardView?.addView(LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                rowKeys.forEachIndexed { index, key ->
                    addView(shortcutKey(activity, ui, key), LinearLayout.LayoutParams(0, ui.dp(30), 1f).apply {
                        setMargins(if (index == 0) 0 else ui.dp(2), ui.dp(1), ui.dp(2), ui.dp(1))
                    })
                }
            }, LinearLayout.LayoutParams(-1, 0, 1f))
        }
    }

    private fun getOrderedKeys(activity: Activity): List<EmbeddedVirtualKey> {
        val all = embeddedKeys(activity)
        val keyMap = all.associateBy { it.id }
        val ordered = currentKeyOrder.mapNotNull { keyMap[it] }
        val rest = all.filter { it.id !in currentKeyOrder }
        return ordered + rest
    }

    private fun loadKeyOrder(activity: Activity): MutableList<String> {
        val raw = pm.terminalKeyOrder
        if (raw.isBlank()) return embeddedKeys(activity).map { it.id }.toMutableList()
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
    }

    private fun saveKeyOrder(activity: Activity) {
        val raw = currentKeyOrder.joinToString(",")
        pm.terminalKeyOrder = raw
    }

    private fun enterRearrangeMode(activity: Activity, ui: AIDevUi) {
        isRearranging = true
        hapticTap(activity)
        setButtonsRearrangeStyle(true)
    }

    private fun exitRearrangeMode(activity: Activity, ui: AIDevUi, save: Boolean) {
        if (save) saveKeyOrder(activity)
        isRearranging = false
        ctrlLatched = false
        hapticTap(activity)
        setButtonsRearrangeStyle(false)
        refreshKeyboard(activity)
    }

    private fun setButtonsRearrangeStyle(rearranging: Boolean) {
        keyboardView?.let { kb ->
            if (rearranging) {
                kb.setBackgroundColor(0xFF1A2240.toInt())
            } else {
                kb.setBackgroundColor(0xFF111418.toInt())
                selectedKeyView = null
                stopWiggle()
            }
            val strokeWidth = ui?.dp(1) ?: 1
            val accent = ui?.palette?.accent ?: 0xFF7C3AED.toInt()
            for (ri in 0 until kb.childCount) {
                val row = kb.getChildAt(ri) as? ViewGroup ?: continue
                for (ci in 0 until row.childCount) {
                    val btn = row.getChildAt(ci)
                    val gd = btn.background as? android.graphics.drawable.GradientDrawable
                    if (rearranging) {
                        gd?.setColor(0xFF2D3748.toInt())
                        gd?.setStroke(strokeWidth, 0xFF5A6A8A.toInt())
                    } else {
                        gd?.setStroke(0, 0)
                        val id = btn.tag as? String ?: continue
                        val isC = id == "ctrl"
                        gd?.setColor(if (isC && ctrlLatched) 0xFF374151.toInt() else 0xFF1F2937.toInt())
                    }
                }
            }
            if (rearranging) startWiggle()
        }
    }

    private fun startWiggle() {
        val kb = keyboardView ?: return
        val buttons = mutableListOf<View>()
        for (ri in 0 until kb.childCount) {
            val row = kb.getChildAt(ri) as? ViewGroup ?: continue
            for (ci in 0 until row.childCount) buttons.add(row.getChildAt(ci))
        }
        wiggleAnimator?.cancel()
        wiggleAnimator = android.animation.ValueAnimator.ofFloat(-2.5f, 2.5f).apply {
            duration = 300
            repeatMode = android.animation.ValueAnimator.REVERSE
            repeatCount = android.animation.ValueAnimator.INFINITE
            addUpdateListener { anim ->
                val a = anim.animatedValue as Float
                buttons.forEach { it.rotation = a }
            }
            start()
        }
    }

    private fun stopWiggle(animate: Boolean = true) {
        wiggleAnimator?.cancel()
        wiggleAnimator = null
        val kb = keyboardView ?: return
        val buttons = mutableListOf<View>()
        for (ri in 0 until kb.childCount) {
            val row = kb.getChildAt(ri) as? ViewGroup ?: continue
            for (ci in 0 until row.childCount) buttons.add(row.getChildAt(ci))
        }
        if (animate && buttons.isNotEmpty()) {
            val startRots = buttons.map { it.rotation }
            android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 200
                addUpdateListener { anim ->
                    val t = anim.animatedFraction
                    val ease = if (t < 0.5f) 2f * t * t else 1f - (-2f * t * t + 4f * t - 1f) / 2f
                    buttons.forEachIndexed { i, btn ->
                        btn.rotation = startRots[i] * (1f - ease)
                    }
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) { buttons.forEach { it.rotation = 0f } }
                })
                start()
            }
        } else {
            buttons.forEach { it.rotation = 0f }
        }
    }

    private fun applySelectedVisual(btn: View) {
        val gd = btn.background as? android.graphics.drawable.GradientDrawable
        gd?.setStroke(ui?.dp(2) ?: 2, ui?.palette?.accent ?: 0xFF7C3AED.toInt())
        btn.scaleX = 1.06f
        btn.scaleY = 1.06f
        btn.alpha = 0.7f
    }

    private fun applyDefaultVisual(btn: View) {
        val gd = btn.background as? android.graphics.drawable.GradientDrawable
        gd?.setStroke(ui?.dp(1) ?: 1, 0xFF5A6A8A.toInt())
        btn.scaleX = 1f
        btn.scaleY = 1f
        btn.alpha = 1f
    }

    private fun animateSwap(v1: View, v2: View, ui: AIDevUi) {
        val loc1 = IntArray(2)
        val loc2 = IntArray(2)
        v1.getLocationOnScreen(loc1)
        v2.getLocationOnScreen(loc2)
        val dx = loc2[0] - loc1[0]
        val dy = loc2[1] - loc1[1]
        android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 160
            addUpdateListener { anim ->
                val t = anim.animatedFraction
                v1.translationX = dx * t
                v1.translationY = dy * t
                v2.translationX = -dx * t
                v2.translationY = -dy * t
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    v1.translationX = 0f; v1.translationY = 0f
                    v2.translationX = 0f; v2.translationY = 0f
                    swapButtonViews(v1, v2, ui)
                }
            })
            start()
        }
    }

    private fun swapButtonViews(v1: View, v2: View, ui: AIDevUi) {
        val row1 = v1.parent as ViewGroup
        val row2 = v2.parent as ViewGroup
        val idx1 = row1.indexOfChild(v1)
        val idx2 = row2.indexOfChild(v2)
        row1.removeView(v1)
        row2.removeView(v2)
        row1.addView(v2, idx1)
        row2.addView(v1, idx2)
        fun fixMargin(v: View, idx: Int) {
            val lp = v.layoutParams as? LinearLayout.LayoutParams ?: return
            lp.setMargins(if (idx == 0) 0 else ui.dp(2), ui.dp(1), ui.dp(2), ui.dp(1))
            v.layoutParams = lp
        }
        fixMargin(v2, idx1)
        fixMargin(v1, idx2)
        val id1 = v1.tag as? String
        val id2 = v2.tag as? String
        if (id1 != null && id2 != null) {
            val i1 = currentKeyOrder.indexOf(id1)
            val i2 = currentKeyOrder.indexOf(id2)
            if (i1 >= 0 && i2 >= 0) {
                currentKeyOrder[i1] = id2
                currentKeyOrder[i2] = id1
            }
        }
        // 闪烁确认
        android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 250
            addUpdateListener { anim ->
                val t = anim.animatedFraction
                val blink = if (t < 0.5f) 1f - t * 2f else (t - 0.5f) * 2f
                v1.alpha = 1f - blink * 0.4f
                v2.alpha = 1f - blink * 0.4f
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) { v1.alpha = 1f; v2.alpha = 1f; applyDefaultVisual(v1); applyDefaultVisual(v2) }
            })
            start()
        }
        // 重新开始抖动
        stopWiggle(false)
        startWiggle()
    }

    private fun embeddedKeys(activity: Activity): List<EmbeddedVirtualKey> {
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
        val overrides = parseKeyOverrides(pm.terminalKeyOverrides)
        val customizedDefaults = defaults.map { key -> overrides[key.id] ?: key }
        val custom = parseCustomKeys(pm.terminalCustomKeys)
        return (customizedDefaults + custom).take(12)
    }

    private fun shortcutKey(activity: Activity, ui: AIDevUi, key: EmbeddedVirtualKey): TextView =
        TextView(activity).apply {
            text = key.label
            textSize = 11f
            setTextColor(0xFFD1D5DB.toInt())
            gravity = Gravity.CENTER
            includeFontPadding = false
            tag = key.id
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(if (key.input == "__CTRL__" && ctrlLatched) 0xFF374151.toInt() else 0xFF1F2937.toInt())
                cornerRadius = ui.dp(8).toFloat()
            }
            var downX = 0f
            var downY = 0f
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX
                        downY = event.rawY
                        if (isRearranging) {
                            wiggleAnimator?.pause()
                            applySelectedVisual(this@apply)
                            true
                        } else false
                    }
                    MotionEvent.ACTION_UP -> {
                        if (isRearranging) {
                            val dx = event.rawX - downX
                            if (dx > ui.dp(SWIPE_THRESHOLD_DP) && abs(event.rawY - downY) < ui.dp(32)) {
                                applyDefaultVisual(this@apply)
                                selectedKeyView = null
                                wiggleAnimator?.resume()
                                exitRearrangeMode(activity, ui, true)
                                true
                            } else {
                                val tapped = this@apply
                                if (tapped == selectedKeyView) {
                                    applyDefaultVisual(tapped)
                                    selectedKeyView = null
                                    wiggleAnimator?.resume()
                                } else if (selectedKeyView == null) {
                                    selectedKeyView = tapped
                                } else {
                                    val source = selectedKeyView!!
                                    selectedKeyView = null
                                    applyDefaultVisual(source)
                                    applyDefaultVisual(tapped)
                                    hapticTap(activity)
                                    animateSwap(source, tapped, ui)
                                }
                                true
                            }
                        } else {
                            val dx = event.rawX - downX
                            val dy = downY - event.rawY
                            if (dx < -ui.dp(SWIPE_THRESHOLD_DP) && abs(dy) < ui.dp(32)) {
                                enterRearrangeMode(activity, ui)
                                true
                            } else if (dy > ui.dp(24) && key.swipeCommand.isNotBlank()) {
                                hapticTap(activity)
                                sendSwipeAction(key.swipeCommand)
                                true
                            } else false
                        }
                    }
                    else -> false
                }
            }
            setOnClickListener {
                if (!isRearranging) handleVirtualKeyTap(activity, key)
            }
            setOnLongClickListener {
                if (!isRearranging) {
                    showVirtualKeyMenu(activity, key)
                    true
                } else true
            }
        }

    private fun handleVirtualKeyTap(activity: Activity, key: EmbeddedVirtualKey) {
        hapticTap(activity)
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
        updateInputBuffer(input)
        if (ctrlLatched) {
            ctrlLatched = false
            refreshKeyboard(activity)
        }
    }

    private fun trackPostDelayed(view: View, delayMs: Long, action: () -> Unit) {
        val r = Runnable { action() }
        pendingRunnables.add(view to r)
        view.postDelayed(r, delayMs)
    }

    private fun hapticTap(activity: Activity) {
        if (!pm.hapticTap) return
        val view = terminalView ?: return
        view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP, android.view.HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
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
        val kb = keyboardView ?: return
        for (i in 0 until kb.childCount) {
            val row = kb.getChildAt(i) as? ViewGroup ?: continue
            for (j in 0 until row.childCount) {
                val key = row.getChildAt(j)
                if (key.tag == "ctrl") {
                    key.background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(if (ctrlLatched) 0xFF374151.toInt() else 0xFF1F2937.toInt())
                        cornerRadius = (ui?.dp(8) ?: 8).toFloat()
                    }
                    return
                }
            }
        }
    }

    private fun showExtraKeysMenu(activity: Activity) {
        val keys = listOf(
            EmbeddedVirtualKey("HOME", "\u001b[H"),
            EmbeddedVirtualKey("END", "\u001b[F"),
            EmbeddedVirtualKey("PGUP", "\u001b[5~"),
            EmbeddedVirtualKey("PGDN", "\u001b[6~"),
            EmbeddedVirtualKey("~", "~"),
            EmbeddedVirtualKey("清屏", "clear\n"),
            EmbeddedVirtualKey("Ubuntu", "ubuntu\n"),
            EmbeddedVirtualKey("任务", "task-list\n")
        ).toMutableList()
        keys.addAll(parseCustomKeys(pm.terminalCustomKeys))
        MaterialAlertDialogBuilder(activity)
            .setTitle("扩展键盘更多")
            .setItems(keys.map { it.label }.toTypedArray()) { _, which -> session?.write(keys[which].input) }
            .show()
    }

    private fun showVirtualKeyMenu(activity: Activity, key: EmbeddedVirtualKey) {
        MaterialAlertDialogBuilder(activity)
            .setTitle("${key.label} 键")
            .setItems(arrayOf("编辑此键", "更多快捷键")) { _, which ->
                when (which) {
                    0 -> keyEditor.show(activity, key)
                    1 -> showExtraKeysMenu(activity)
                }
            }
            .show()
    }

    private fun fontPx(activity: Activity): Int =
        activity!!.spToPx(currentFontSp(activity))

    private fun currentFontSp(activity: Activity): Float =
        pm.fontSp.coerceIn(MIN_FONT_SP, MAX_FONT_SP)

    private fun showFontDialog(activity: Activity) {
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
        MaterialAlertDialogBuilder(activity)
            .setTitle("终端字号")
            .setView(box)
            .setPositiveButton("应用") { _, _ ->
                val sp = (MIN_FONT_SP.toInt() + seek.progress).toFloat()
                pm.fontSp = sp
                pendingFontSp = sp
                terminalView?.setTextSize(activity!!.spToPx(sp))
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
        // 异步初始化文件资源，避免主线程 IO 卡顿
        Thread {
            val shellAssets = runCatching { TerminalShellAssets.ensure(activity) }.getOrElse {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(activity, "终端环境初始化失败：${it.message}", Toast.LENGTH_LONG).show()
                }
                return@Thread
            }
            Handler(Looper.getMainLooper()).post {
                if (this.activity == null || this.activity !== activity) return@post
                homeDir = shellAssets.home
                initPwdObserver(activity)
                val entry = shellAssets.entry
                if (current != null) {
                    session = current?.session
                    terminalView?.attachSession(session)
                    terminalView?.requestFocus()
                    refreshTabs(activity)
                    return@post
                }
                runCatching { newSession(activity) }.onFailure { e ->
                    Toast.makeText(activity, "会话创建失败：${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.apply { isDaemon = true; start() }
    }

    private fun createTerminalSession(activity: Activity, id: Int): TerminalSession {
        val home = homeDir ?: throw IllegalStateException("homeDir is null")
        val rc = File(home, ".aidevrc")
        val entry = File(home, ".aidev_shell_entry")
        val nativeDir = activity.applicationInfo.nativeLibraryDir
        val aidevBin = File(home, "dev-env/bin").absolutePath
        val prootLibDir = File(home, "proot-lib").absolutePath
        val env = arrayOf(
            "TERM=xterm-256color",
            "COLORTERM=truecolor",
            "HOME=${home.absolutePath}",
            "PWD=${home.absolutePath}",
            "TMPDIR=${File(activity.cacheDir, "tmp").apply { mkdirs() }.absolutePath}",
            "AIDEV_HOME=${home.absolutePath}",
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
        return TerminalSession("/system/bin/sh", homeDir?.absolutePath ?: "/data/data/com.aidev.terminal/files/home", arrayOf("sh", entry.absolutePath), env, 5000, sessionClient(activity)).apply {
            mSessionName = "AIDev-Shell-$id"
        }
    }

    private fun newSession(activity: Activity) {
        if (sessions.size >= MAX_SESSIONS) {
            val oldest = sessions.firstOrNull()
            MaterialAlertDialogBuilder(activity)
                .setTitle("会话上限")
                .setMessage("已达到最大会话数（$MAX_SESSIONS 个）。关闭最早的「${oldest?.title ?: "会话"}」后创建新会话吗？")
                .setPositiveButton("确认") { _, _ ->
                    oldest?.let { closeSessionItem(activity, it) }
                    homeDir = File(activity.filesDir, "home").apply { mkdirs() }
                    val id = (sessions.maxOfOrNull { it.id } ?: 0) + 1
                    val item = EmbeddedTermSession(id, "会话$id", createTerminalSession(activity, id))
                    sessions.add(item)
                    switchSession(activity, item)
                }
                .setNegativeButton("取消", null)
                .show()
            return
        }
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

    private fun newAiSession(activity: Activity) {
        if (sessions.size >= MAX_SESSIONS) {
            val oldest = sessions.firstOrNull()
            MaterialAlertDialogBuilder(activity)
                .setTitle("会话上限")
                .setMessage("已达到最大会话数（$MAX_SESSIONS 个）。关闭最早的「${oldest?.title ?: "会话"}」后创建新 AI 会话吗？")
                .setPositiveButton("确认") { _, _ ->
                    oldest?.let { closeSessionItem(activity, it) }
                    homeDir = File(activity.filesDir, "home").apply { mkdirs() }
                    val id = (sessions.maxOfOrNull { it.id } ?: 0) + 1
                    val item = EmbeddedTermSession(id, "AI-会话$id", createTerminalSession(activity, id), aiSession = true)
                    sessions.add(item)
                    switchSession(activity, item)
                }
                .setNegativeButton("取消", null)
                .show()
            return
        }
        homeDir = File(activity.filesDir, "home").apply { mkdirs() }
        val id = (sessions.maxOfOrNull { it.id } ?: 0) + 1
        val item = EmbeddedTermSession(id, "AI-会话$id", createTerminalSession(activity, id), aiSession = true)
        sessions.add(item)
        switchSession(activity, item)
    }

    private fun renameCurrentSession(activity: Activity) {
        val item = current ?: return
        val edit = EditText(activity).apply {
            setText(item.title)
            selectAll()
        }
        MaterialAlertDialogBuilder(activity)
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
        }
        attachSwipeDownToClose(
            chip,
            onClick = { switchSession(activity, item) },
            onClose = { closeSessionItem(activity, item) },
            onLongPress = {
                switchSession(activity, item)
                renameCurrentSession(activity)
            }
        )
        return chip
    }

    private fun attachSwipeDownToClose(view: View, onClick: () -> Unit, onClose: () -> Unit, onLongPress: (() -> Unit)? = null) {
        val threshold = (ui?.dp(18) ?: 18).toFloat()
        val touchSlop = android.view.ViewConfiguration.get(view.context).scaledTouchSlop
        val longPressTimeout = android.view.ViewConfiguration.getLongPressTimeout().toLong()
        var startY = 0f
        var startX = 0f
        var moved = false
        var longPressed = false
        // View 被移除时清理 pending 回调
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) {
                (v.tag as? Runnable)?.let { sharedHandler.removeCallbacks(it) }
            }
        })
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                    startY = event.rawY
                    startX = event.rawX
                    moved = false
                    longPressed = false
                    v.alpha = 1f
                    v.translationY = 0f
                    if (onLongPress != null) {
                        val longPressRunnable = Runnable {
                            longPressed = true
                            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            onLongPress()
                        }
                        sharedHandler.postDelayed(longPressRunnable, longPressTimeout)
                        v.tag = longPressRunnable
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = event.rawY - startY
                    val dx = kotlin.math.abs(event.rawX - startX)
                    if (kotlin.math.abs(dy) > touchSlop || dx > touchSlop) {
                        (v.tag as? Runnable)?.let { sharedHandler.removeCallbacks(it) }
                    }
                    if (dy > touchSlop * 0.5f && dy > dx * 0.4f) {
                        moved = true
                        v.translationY = dy * 0.75f
                        v.alpha = (1f - (dy / (threshold * 2f))).coerceIn(0.25f, 1f)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    v.parent?.requestDisallowInterceptTouchEvent(false)
                    (v.tag as? Runnable)?.let { sharedHandler.removeCallbacks(it) }
                    v.tag = null
                    val dy = event.rawY - startY
                    if (dy >= threshold) {
                        onClose()
                    } else {
                        v.animate().translationY(0f).alpha(1f).setDuration(120).start()
                        if (!moved && !longPressed) onClick()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    v.parent?.requestDisallowInterceptTouchEvent(false)
                    (v.tag as? Runnable)?.let { sharedHandler.removeCallbacks(it) }
                    v.tag = null
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

    private fun send(command: String, remember: Boolean = true) {
        ensureSession(activity ?: return)
        val finalCommand = command.trimEnd()
        session?.write(finalCommand + "\r")
        inputBuffer = ""
        activity?.let { refreshCompletions(it) }
        activity?.let { focusTerminalInput(it) }
    }

    private fun consumePendingCommand() {
        val command = TerminalCommandBus.consume() ?: return
        if (command == "aidev-auto-bootstrap") {
            if (_autoBootstrapDone) return
            _autoBootstrapDone = true
        }
        send(command, remember = false)
    }

    private fun maybeAutoBootstrapUbuntu(activity: Activity) {
        if (_autoBootstrapDone) return
        _autoBootstrapDone = true
        session?.write("aidev-auto-bootstrap\r")
        focusTerminalInput(activity)
    }

    private fun sessionClient(activity: Activity): TerminalSessionClient =
        object : TerminalSessionClient {
            private val mainHandler = Handler(Looper.getMainLooper())
            override fun onTextChanged(changedSession: TerminalSession) {
                mainHandler.post {
                    terminalView?.onScreenUpdated()
                    updateTuiMode()
                }
            }
            override fun onTitleChanged(changedSession: TerminalSession) {}
            override fun onSessionFinished(finishedSession: TerminalSession) {
                mainHandler.post { terminalView?.onScreenUpdated() }
            }
            override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
                mainHandler.post {
                    (activity.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(ClipData.newPlainText("AIDev Terminal", text))
                    val preview = text.replace("\n", " ").take(40)
                    Toast.makeText(activity, "已复制: $preview${if (text.length > 40) "..." else ""}", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onPasteTextFromClipboard(session: TerminalSession) {
                val text = (activity.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(activity)?.toString()
                if (!text.isNullOrEmpty()) {
                    session.write(text)
                    mainHandler.post { updateInputBuffer(text) }
                }
            }
            override fun onBell(session: TerminalSession) {
                mainHandler.post { terminalView?.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP) }
            }
            override fun onColorsChanged(session: TerminalSession) {
                mainHandler.post { terminalView?.invalidate() }
            }
            override fun onTerminalCursorStateChange(state: Boolean) {
                mainHandler.post { terminalView?.invalidate() }
            }
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
                            pm.fontSp = next
                            terminalView?.setTextSize(activity!!.spToPx(next))
                            terminalView?.onScreenUpdated()
                            refreshStatus(activity)
                        }
                    }
                }
                return 1f
            }
            override fun onSingleTapUp(e: MotionEvent) {
                focusTerminalInput(activity)
                val imm = activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                (inputProxy ?: terminalView)?.let { v -> imm?.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT) }
            }
            override fun shouldBackButtonBeMappedToEscape(): Boolean = true
            override fun shouldEnforceCharBasedInput(): Boolean = false
            override fun shouldUseCtrlSpaceWorkaround(): Boolean = false
            override fun isTerminalViewSelected(): Boolean = terminalView?.hasFocus() == true || inputProxy?.hasFocus() == true
            override fun copyModeChanged(copyMode: Boolean) {}
            override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean {
                when (keyCode) {
                    KeyEvent.KEYCODE_ENTER -> updateInputBuffer("\n")
                    KeyEvent.KEYCODE_DEL -> updateInputBuffer("\b")
                }
                return false
            }
            override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean = false
            override fun onLongPress(event: MotionEvent): Boolean = false
            override fun readControlKey(): Boolean = false
            override fun readAltKey(): Boolean = false
            override fun readShiftKey(): Boolean = false
            override fun readFnKey(): Boolean = false
            override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean {
                if (!ctrlDown && codePoint > 0) {
                    updateInputBuffer(String(Character.toChars(codePoint)))
                }
                return false
            }
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
