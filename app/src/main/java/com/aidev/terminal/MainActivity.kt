package com.aidev.terminal

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import rikka.shizuku.Shizuku
import java.io.File

private data class AidTermSession(
    val id: Int,
    val title: String,
    val session: TerminalSession
)

private data class VirtualKey(
    val label: String,
    val input: String,
    val swipeCommand: String = ""
)

class MainActivity : Activity() {

    private companion object {
        const val CLOSE_MODE_SWIPE = 0
        const val CLOSE_MODE_TAP = 1
        const val REQ_SETTINGS = 2001
    }

    private lateinit var homeDir: File
    private lateinit var rcFile: File
    private lateinit var entryFile: File
    private lateinit var terminalView: TerminalView
    private lateinit var topBar: LinearLayout
    private lateinit var debugButton: TextView
    private lateinit var tabBar: LinearLayout
    private lateinit var tabBarScroll: HorizontalScrollView
    private lateinit var terminalFrame: LinearLayout
    private lateinit var keyboardGrid: LinearLayout
    private lateinit var prefs: SharedPreferences
    private val sessions = mutableListOf<AidTermSession>()
    private var current: AidTermSession? = null
    // 终端字号以 sp 为单位存储，渲染时转 px。默认 16sp，初次启动时阅读舒适。
    private var fontSizeSp = 16f
    private var pendingFontSizeSp = 16f
    private var fontApplyScheduled = false
    private var ctrlLatched = false
    private var lastStreamHapticAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        prefs = getSharedPreferences("aidev_ui", MODE_PRIVATE)
        fontSizeSp = prefs.getFloat("font_sp", 16f).coerceIn(8f, 24f)
        pendingFontSizeSp = fontSizeSp
        homeDir = File(filesDir, "home").apply { mkdirs() }
        rcFile = File(homeDir, ".aidevrc")
        entryFile = File(homeDir, ".aidev_entry")
        installProotSupportLibraries()
        writeShellAssets()
        requestNotificationPermission()
        if (prefs.getBoolean("keepalive_auto", true)) {
            runCatching { KeepAliveService.start(this) }
        }
        buildUi()
        newSession()
        intent.getStringExtra("initial_cmd")?.takeIf { it.isNotBlank() }?.let { cmd ->
            terminalView.postDelayed({ sendCommand(cmd) }, 500)
        }
    }

    private fun installProotSupportLibraries() {
        val outDir = File(homeDir, "proot-lib").apply { mkdirs() }
        val files = listOf("libtalloc.so.2", "libandroid-shmem.so")
        files.forEach { name ->
            val out = File(outDir, name)
            val marker = File(outDir, "$name.v2")
            if (out.exists() && marker.exists()) return@forEach
            assets.open("proot-libs/arm64-v8a/$name").use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            }
            out.setReadable(true, false)
            out.setExecutable(false, false)
            marker.writeText("ok\n")
        }
    }

    override fun onDestroy() {
        sessions.forEach { it.session.finishIfRunning() }
        super.onDestroy()
    }

    // ---------- UI ----------

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF000000.toInt())
        }
        AIDevUi(this, prefs).attachSwipeNavigation(
            root,
            previous = { ShellActivity.open(this, ShellActivity.TAB_DASHBOARD) },
            next = { ShellActivity.open(this, ShellActivity.TAB_FILES) }
        )

        // 顶栏：标题 + 菜单按钮，参数可在临时 UI 调试模式中微调。
        topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(0), dp(uiInt("top_right_pad", 16)), dp(0))
            background = panelBg(0xFF111418.toInt())
            layoutParams = LinearLayout.LayoutParams(-1, dp(uiInt("top_h", 40)))
        }
        topBar.addView(TextView(this).apply {
            text = "AIDev"
            setTextColor(0xFFE5E7EB.toInt())
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, -2, 1f))
        debugButton = iconButton("UI") { showUiDebugPanel() }.apply {
            visibility = if (uiBool("debug_mode", false)) View.VISIBLE else View.GONE
            textSize = 11f
        }
        topBar.addView(debugButton)
        topBar.addView(iconButton("退出") { _ -> ShellActivity.open(this, ShellActivity.TAB_DASHBOARD) }.apply { textSize = 11f })
        topBar.addView(iconButton("+") { newSession() })
        topBar.addView(iconButton("⋮") { v -> showMenu(v) })
        root.addView(topBar)

        // 会话标签：常驻显示，横向滑动，自适应文本宽度。
        tabBarScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            background = panelBg(0xFF0B0E12.toInt())
            layoutParams = LinearLayout.LayoutParams(-1, dp(uiInt("tab_h", 33)))
        }
        tabBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(6), dp(2), dp(6), dp(2))
        }
        tabBarScroll.addView(tabBar)
        root.addView(tabBarScroll)

        terminalFrame = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = panelBg(Color.BLACK)
            setPadding(if (wireframeEnabled()) dp(1) else 0, if (wireframeEnabled()) dp(1) else 0, if (wireframeEnabled()) dp(1) else 0, if (wireframeEnabled()) dp(1) else 0)
        }
        terminalView = TerminalView(this, null).apply {
            setBackgroundColor(Color.BLACK)
            isFocusable = true
            isFocusableInTouchMode = true
            setTerminalViewClient(createViewClient())
            setTextSize(spToPx(fontSizeSp))
        }
        terminalFrame.addView(terminalView, LinearLayout.LayoutParams(-1, -1))
        root.addView(terminalFrame, LinearLayout.LayoutParams(-1, 0, 1f))

        // 扩展键盘：固定网格，默认 2 行 × 6 列；不横向滚动。
        keyboardGrid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = panelBg(0xFF111418.toInt())
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        rebuildKeyboard()
        root.addView(keyboardGrid)
        setContentView(root)
    }

    private fun showMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 100, 0, "设置中心")
        popup.menu.add(0, 101, 0, "返回工作台")
        popup.menu.add(0, 7, 0, "关闭当前会话")
        popup.menu.add(0, 8, 0, "键盘布局")
        popup.menu.add(0, 9, 0, "重置键盘")
        popup.menu.add(0, 10, 0, "键盘说明")
        popup.menu.add(0, 11, 0, "字号设置")
        popup.menu.add(0, 13, 0, "标签关闭方式")
        popup.menu.add(0, 12, 0, if (uiBool("debug_mode", false)) "关闭调试模式" else "打开调试模式")
        popup.menu.add(0, 1, 0, "快速进入 Ubuntu")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> sendCommand("ubuntu")
                7 -> closeCurrent()
                8 -> showKeyboardLayoutDialog()
                9 -> resetKeyboard()
                10 -> showKeyboardHelp()
                11 -> showFontSizeDialog()
                12 -> toggleDebugMode()
                13 -> showTabCloseModeDialog()
                100 -> AppNav.open(this, SettingsActivity::class.java)
                101 -> ShellActivity.open(this, ShellActivity.TAB_DASHBOARD)
            }
            true
        }
        popup.show()
    }

    @Deprecated("使用传统回调保持低复杂度")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_SETTINGS || resultCode != RESULT_OK || data == null) return
        when (data.getStringExtra("action")) {
            "cmd" -> data.getStringExtra("cmd")?.let { sendCommand(it) }
            "new_session" -> newSession()
            "close_session" -> closeCurrent()
            "keyboard_layout" -> showKeyboardLayoutDialog()
            "keyboard_reset" -> resetKeyboard()
            "keyboard_help" -> showKeyboardHelp()
            "font_size" -> showFontSizeDialog()
            "tab_close_mode" -> showTabCloseModeDialog()
            "debug_toggle" -> toggleDebugMode()
            "shizuku" -> checkShizuku()
        }
    }

    private fun showTabCloseModeDialog() {
        val current = closeMode()
        val labels = arrayOf("下滑关闭（默认）", "点击 × 关闭")
        val values = intArrayOf(CLOSE_MODE_SWIPE, CLOSE_MODE_TAP)
        val checked = values.indexOf(current).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("会话标签关闭方式")
            .setSingleChoiceItems(labels, checked) { d, which ->
                prefs.edit().putInt("tab_close_mode", values[which]).apply()
                refreshTabs()
                d.dismiss()
                toast(if (values[which] == CLOSE_MODE_SWIPE) "下滑关闭已生效" else "点击关闭已生效")
            }
            .show()
    }

    private fun closeMode(): Int = prefs.getInt("tab_close_mode", CLOSE_MODE_SWIPE)

    private fun toggleDebugMode() {
        val enabled = !uiBool("debug_mode", false)
        prefs.edit()
            .putBoolean("debug_mode", enabled)
            .putBoolean("ui_show_bounds", enabled)
            .apply()
        debugButton.visibility = if (enabled) View.VISIBLE else View.GONE
        applyUiTuning()
        if (enabled) showUiDebugPanel()
    }

    private fun showFontSizeDialog() {
        val opts = arrayOf("12 sp", "14 sp", "16 sp", "18 sp", "20 sp", "24 sp")
        val vals = floatArrayOf(12f, 14f, 16f, 18f, 20f, 24f)
        val current = vals.indexOfFirst { kotlin.math.abs(it - fontSizeSp) < 0.5f }.coerceAtLeast(0)
        android.app.AlertDialog.Builder(this)
            .setTitle("字号  ·  也可双指捏合实时缩放")
            .setSingleChoiceItems(opts, current) { d, which ->
                fontSizeSp = vals[which]
                pendingFontSizeSp = fontSizeSp
                applyTerminalFontSize()
                prefs.edit().putFloat("font_sp", fontSizeSp).apply()
                d.dismiss()
            }
            .show()
    }

    private fun iconButton(text: String, onClick: (View) -> Unit): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 16f
            setTextColor(0xFFE5E7EB.toInt())
            background = if (wireframeEnabled()) roundedBg(Color.TRANSPARENT, 0, 0xFF64748B.toInt()) else null
            gravity = Gravity.CENTER
            includeFontPadding = false
            val lp = LinearLayout.LayoutParams(dp(uiInt("icon_w", 40)), dp(uiInt("top_h", 40).coerceAtLeast(32)))
            lp.setMargins(0, 0, 0, 0)
            layoutParams = lp
            setPadding(0, 0, 0, 0)
            setOnClickListener(onClick)
        }
    }

    private fun shortcutButton(index: Int, key: VirtualKey): Button {
        return Button(this).apply {
            this.text = key.label
            textSize = 11f
            setAllCaps(false)
            minHeight = dp(32)
            setTextColor(0xFFD1D5DB.toInt())
            background = roundedBg(
                if (key.input == "__CTRL__" && ctrlLatched) 0xFF374151.toInt() else 0xFF1F2937.toInt(),
                dp(uiInt("key_radius", 8)),
                if (wireframeEnabled()) 0xFF64748B.toInt() else 0
            )
            val lp = LinearLayout.LayoutParams(0, dp(uiInt("key_h", 31)), 1f)
            val keyGap = dp(uiInt("key_gap", 1))
            lp.setMargins(keyGap, 0, keyGap, 0)
            layoutParams = lp
            setPadding(0, 0, 0, 0)
            var downY = 0f
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downY = event.rawY
                        false
                    }
                    MotionEvent.ACTION_UP -> {
                        val swipeUp = downY - event.rawY > dp(24)
                        if (swipeUp && key.swipeCommand.isNotBlank()) {
                            sendCommand(key.swipeCommand)
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            }
            setOnClickListener {
                handleVirtualKeyTap(key)
            }
            setOnLongClickListener {
                showKeyEditor(index)
                true
            }
        }
    }

    private fun tabChip(label: String, active: Boolean, onClick: () -> Unit, onClose: () -> Unit): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedBg(
                if (active) 0xFF1F2937.toInt() else 0xFF14181E.toInt(),
                dp(6), if (wireframeEnabled()) 0xFF64748B.toInt() else 0
            )
            val lp = LinearLayout.LayoutParams(-2, dp((uiInt("tab_h", 33) - 4).coerceAtLeast(24)))
            lp.setMargins(dp(3), dp(3), dp(3), dp(3))
            layoutParams = lp
            setPadding(dp(uiInt("tab_pad", 9)), 0, dp(uiInt("tab_pad", 9)), 0)
        }
        val labelView = TextView(this).apply {
            text = label
            textSize = 12f
            setTextColor(if (active) 0xFFE5E7EB.toInt() else 0xFF9CA3AF.toInt())
        }
        container.addView(labelView)

        val mode = closeMode()
        if (mode == CLOSE_MODE_TAP) {
            // 点击关闭：显示 × 按钮，紧贴标签文本
            container.addView(TextView(this).apply {
                text = "×"
                textSize = 13f
                setTextColor(0xFF9CA3AF.toInt())
                gravity = Gravity.CENTER
                includeFontPadding = false
                setPadding(dp(6), 0, 0, 0)
                setOnClickListener { onClose() }
            })
            container.setOnClickListener { onClick() }
        } else {
            // 下滑关闭：监听竖向手势，达到阈值则关闭
            attachSwipeDownToClose(container, onClick, onClose)
        }
        return container
    }

    private fun attachSwipeDownToClose(view: View, onClick: () -> Unit, onClose: () -> Unit) {
        val threshold = maxOf(dp(14).toFloat(), dp(uiInt("tab_h", 33)).toFloat() * 0.45f)
        val touchSlop = android.view.ViewConfiguration.get(this).scaledTouchSlop
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
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                        moved = true
                        v.translationY = dy * 0.75f
                        v.alpha = (1f - (dy / (threshold * 2f))).coerceIn(0.2f, 1f)
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

    private fun roundedBg(fill: Int, radius: Int, stroke: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(fill)
            cornerRadius = radius.toFloat()
            if (stroke != 0) setStroke(dp(1), stroke)
        }
    }

    private fun panelBg(fill: Int): GradientDrawable =
        roundedBg(fill, 0, if (wireframeEnabled()) 0xFF64748B.toInt() else 0)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun uiInt(key: String, default: Int): Int = prefs.getInt("ui_$key", default)

    private fun uiBool(key: String, default: Boolean): Boolean = prefs.getBoolean("ui_$key", default)

    private fun wireframeEnabled(): Boolean =
        uiBool("debug_mode", false) && uiBool("show_bounds", false)

    private fun applyUiTuning() {
        if (::topBar.isInitialized) {
            topBar.setPadding(dp(14), 0, dp(uiInt("top_right_pad", 16)), 0)
            topBar.background = panelBg(0xFF111418.toInt())
            topBar.layoutParams = LinearLayout.LayoutParams(-1, dp(uiInt("top_h", 40)))
            topBar.getChildAt(0)?.background = if (wireframeEnabled()) roundedBg(Color.TRANSPARENT, 0, 0xFF64748B.toInt()) else null
            for (i in 1 until topBar.childCount) {
                val child = topBar.getChildAt(i)
                child.layoutParams = LinearLayout.LayoutParams(
                    dp(uiInt("icon_w", 40)),
                    dp(uiInt("top_h", 40).coerceAtLeast(32))
                )
                child.background = if (wireframeEnabled()) roundedBg(Color.TRANSPARENT, 0, 0xFF64748B.toInt()) else null
            }
        }
        if (::tabBarScroll.isInitialized) {
            tabBarScroll.background = panelBg(0xFF0B0E12.toInt())
            tabBarScroll.layoutParams = LinearLayout.LayoutParams(-1, dp(uiInt("tab_h", 33)))
            refreshTabs()
        }
        if (::keyboardGrid.isInitialized) {
            keyboardGrid.background = panelBg(0xFF111418.toInt())
            rebuildKeyboard()
        }
        if (::terminalFrame.isInitialized) {
            terminalFrame.background = panelBg(Color.BLACK)
            val p = if (wireframeEnabled()) dp(1) else 0
            terminalFrame.setPadding(p, p, p, p)
        }
        if (::debugButton.isInitialized) debugButton.visibility = if (uiBool("debug_mode", false)) View.VISIBLE else View.GONE
        if (::terminalView.isInitialized) applyTerminalFontSize()
    }

    private fun spToPx(sp: Float): Int =
        (sp * resources.displayMetrics.scaledDensity + 0.5f).toInt()

    private fun maxSafeFontSp(): Float {
        if (!::terminalView.isInitialized || terminalView.width <= 0 || terminalView.height <= 0) return 24f
        val density = resources.displayMetrics.scaledDensity
        val minColumns = 18f
        val minRows = 8f
        val estimatedFontWidthRatio = 0.62f
        val estimatedLineHeightRatio = 1.35f
        val byWidth = terminalView.width / (minColumns * estimatedFontWidthRatio * density)
        val byHeight = terminalView.height / (minRows * estimatedLineHeightRatio * density)
        return minOf(24f, byWidth, byHeight).coerceAtLeast(12f)
    }

    private fun applyTerminalFontSize() {
        fontSizeSp = fontSizeSp.coerceIn(8f, maxSafeFontSp())
        pendingFontSizeSp = fontSizeSp
        terminalView.setTextSize(spToPx(fontSizeSp))
        terminalView.updateSize()
        terminalView.onScreenUpdated()
        terminalView.requestLayout()
        terminalView.invalidate()
    }

    private fun keyboardRows(): Int = prefs.getInt("keyboard_rows", 2).coerceIn(2, 3)

    private fun keyboardCols(): Int = prefs.getInt("keyboard_cols", 6).coerceIn(6, 8)

    private fun defaultKeys(): List<VirtualKey> = listOf(
        VirtualKey("ESC", "\u001B", "clear"),
        VirtualKey("CTRL", "__CTRL__", ""),
        VirtualKey("↑", "\u001B[A", "history"),
        VirtualKey("TAB", "\t", "help"),
        VirtualKey("/", "/", "cd /"),
        VirtualKey("-", "-", "cd -"),
        VirtualKey("C", "c", ""),
        VirtualKey("←", "\u001B[D", ""),
        VirtualKey("↓", "\u001B[B", ""),
        VirtualKey("→", "\u001B[C", ""),
        VirtualKey("|", "|", ""),
        VirtualKey("SPC", " ", "pwd"),
        VirtualKey("HOME", "\u001B[H", "cd"),
        VirtualKey("END", "\u001B[F", "pwd"),
        VirtualKey("PGUP", "\u001B[5~", ""),
        VirtualKey("PGDN", "\u001B[6~", ""),
        VirtualKey("AI", "", "install-aitool"),
        VirtualKey("UBU", "", "ubuntu"),
        VirtualKey("DEV", "", "deploy-dev-env"),
        VirtualKey("INFO", "", "sysinfo"),
        VirtualKey("LS", "", "ls"),
        VirtualKey("PWD", "", "pwd"),
        VirtualKey("CP", "cp ", ""),
        VirtualKey("MV", "mv ", ""),
        VirtualKey("RM", "rm ", ""),
        VirtualKey("MKD", "mkdir ", "")
    )

    private fun keyAt(index: Int): VirtualKey {
        val def = defaultKeys().getOrElse(index) { VirtualKey("", "", "") }
        return VirtualKey(
            prefs.getString("key_${index}_label", def.label) ?: def.label,
            prefs.getString("key_${index}_input", def.input) ?: def.input,
            prefs.getString("key_${index}_swipe", def.swipeCommand) ?: def.swipeCommand
        )
    }

    private fun rebuildKeyboard() {
        if (!::keyboardGrid.isInitialized) return
        keyboardGrid.removeAllViews()
        val rows = keyboardRows()
        val cols = keyboardCols()
        var index = 0
        repeat(rows) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                background = if (wireframeEnabled()) roundedBg(Color.TRANSPARENT, 0, 0xFF334155.toInt()) else null
                val rowGap = dp(uiInt("key_gap", 1))
                val lp = LinearLayout.LayoutParams(-1, dp(uiInt("key_h", 31) + uiInt("key_gap", 1) * 2))
                lp.setMargins(0, rowGap, 0, rowGap)
                layoutParams = lp
            }
            repeat(cols) {
                row.addView(shortcutButton(index, keyAt(index)))
                index++
            }
            keyboardGrid.addView(row)
        }
    }

    private fun handleVirtualKeyTap(key: VirtualKey) {
        if (key.input == "__CTRL__") {
            ctrlLatched = !ctrlLatched
            rebuildKeyboard()
            return
        }
        val input = key.input
        if (input.isEmpty()) return
        if (ctrlLatched && input.length == 1) {
            val ch = input[0].uppercaseChar()
            if (ch in '@'..'_') {
                val b = byteArrayOf((ch.code and 0x1F).toByte())
                current?.session?.write(b, 0, 1)
                ctrlLatched = false
                rebuildKeyboard()
                return
            }
        }
        current?.session?.write(input)
        if (ctrlLatched) {
            ctrlLatched = false
            rebuildKeyboard()
        }
    }

    private fun showKeyboardLayoutDialog() {
        val options = arrayOf("2 × 6", "2 × 7", "2 × 8", "3 × 6", "3 × 7", "3 × 8")
        AlertDialog.Builder(this)
            .setTitle("键盘布局")
            .setItems(options) { _, which ->
                val value = options[which].split(" × ")
                prefs.edit()
                    .putInt("keyboard_rows", value[0].toInt())
                    .putInt("keyboard_cols", value[1].toInt())
                    .apply()
                rebuildKeyboard()
            }
            .show()
    }

    private fun showKeyEditor(index: Int) {
        val key = keyAt(index)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }
        panel.addView(TextView(this).apply {
            text = "点击：向终端输入内容。上滑：执行一条命令。长按：编辑本按键。Ctrl 请用独立按键，再点 C 等字符。"
            textSize = 12f
            setTextColor(0xFF6B7280.toInt())
            setPadding(0, 0, 0, dp(8))
        })
        val labelInput = EditText(this).apply {
            hint = "按键显示文本"
            setText(key.label)
            setSingleLine(true)
        }
        val tapInput = EditText(this).apply {
            hint = "点击输入内容，例如 ls 或方向键转义序列"
            setText(key.input)
            setSingleLine(true)
        }
        val swipeInput = EditText(this).apply {
            hint = "上滑执行命令，例如 install-ubuntu"
            setText(key.swipeCommand)
            setSingleLine(true)
        }
        panel.addView(labelInput)
        panel.addView(tapInput)
        panel.addView(swipeInput)
        AlertDialog.Builder(this)
            .setTitle("编辑虚拟按键")
            .setView(panel)
            .setPositiveButton("保存") { _, _ ->
                prefs.edit()
                    .putString("key_${index}_label", labelInput.text.toString())
                    .putString("key_${index}_input", tapInput.text.toString())
                    .putString("key_${index}_swipe", swipeInput.text.toString())
                    .apply()
                rebuildKeyboard()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showKeyboardHelp() {
        AlertDialog.Builder(this)
            .setTitle("扩展键盘")
            .setMessage(
                "点击按键：输入字符或控制键。\n" +
                    "上滑按键：执行该键绑定的命令。\n" +
                    "长按按键：编辑显示文本、点击输入、上滑命令。\n" +
                    "Ctrl 是独立按键：先点 Ctrl，再点 C，可发送 Ctrl+C。"
            )
            .setPositiveButton("知道了", null)
            .show()
    }

    private fun showUiDebugPanel() {
        lateinit var dialog: AlertDialog
        val fadePanel = { dim: Boolean ->
            dialog.window?.decorView?.alpha = if (dim) 0.22f else 1.0f
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(10), dp(18), dp(4))
        }
        panel.addView(TextView(this).apply {
            text = "拖动滑块时面板会自动半透明，便于观察界面变化。调好后点“导出”，把参数反馈给我。"
            textSize = 12f
            setTextColor(0xFF6B7280.toInt())
            setPadding(0, 0, 0, dp(8))
        })
        addSlider(panel, "顶栏高度", "top_h", 28, 48, 40, fadePanel)
        addSlider(panel, "右侧按钮宽度", "icon_w", 22, 40, 40, fadePanel)
        addSlider(panel, "顶栏右边距", "top_right_pad", 0, 16, 16, fadePanel)
        addSlider(panel, "会话栏高度", "tab_h", 24, 42, 33, fadePanel)
        addSlider(panel, "会话标签内边距", "tab_pad", 4, 16, 9, fadePanel)
        addSlider(panel, "键盘按键高度", "key_h", 28, 48, 31, fadePanel)
        addSlider(panel, "键盘按键间距", "key_gap", 0, 6, 1, fadePanel)
        addSlider(panel, "键盘圆角", "key_radius", 0, 14, 8, fadePanel)
        panel.addView(TextView(this).apply {
            text = if (uiBool("show_bounds", false)) "布局边界：已显示" else "布局边界：未显示"
            textSize = 13f
            setTextColor(0xFFE5E7EB.toInt())
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, dp(10))
            setOnClickListener {
                prefs.edit().putBoolean("ui_show_bounds", !uiBool("show_bounds", false)).apply()
                applyUiTuning()
                showUiDebugPanel()
            }
        })
        val scroll = ScrollView(this).apply { addView(panel) }
        dialog = AlertDialog.Builder(this)
            .setTitle("UI 调试")
            .setView(scroll)
            .setPositiveButton("完成", null)
            .setNeutralButton("重置 UI") { _, _ -> resetUiTuning() }
            .setNegativeButton("导出", null)
            .show()
        dialog.setOnDismissListener { dialog.window?.decorView?.alpha = 1.0f }
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener { exportUiTuning() }
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            resetUiTuning()
            dialog.dismiss()
            showUiDebugPanel()
        }
    }

    private fun addSlider(
        panel: LinearLayout,
        title: String,
        key: String,
        min: Int,
        max: Int,
        default: Int,
        fadePanel: (Boolean) -> Unit
    ) {
        val label = TextView(this).apply {
            text = "$title：${uiInt(key, default)}"
            textSize = 12f
            setTextColor(0xFFD1D5DB.toInt())
            setPadding(0, dp(6), 0, 0)
        }
        val seek = SeekBar(this).apply {
            this.max = max - min
            progress = uiInt(key, default).coerceIn(min, max) - min
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val value = min + progress
                    label.text = "$title：$value"
                    prefs.edit().putInt("ui_$key", value).apply()
                    applyUiTuning()
                }
                override fun onStartTrackingTouch(bar: SeekBar?) {
                    fadePanel(true)
                }
                override fun onStopTrackingTouch(bar: SeekBar?) {
                    fadePanel(false)
                }
            })
        }
        panel.addView(label)
        panel.addView(seek)
    }

    private fun exportUiTuning() {
        val text = buildString {
            appendLine("AIDev Terminal UI 参数")
            appendLine("top_h=${uiInt("top_h", 40)}")
            appendLine("icon_w=${uiInt("icon_w", 40)}")
            appendLine("top_right_pad=${uiInt("top_right_pad", 16)}")
            appendLine("tab_h=${uiInt("tab_h", 33)}")
            appendLine("tab_pad=${uiInt("tab_pad", 9)}")
            appendLine("key_h=${uiInt("key_h", 31)}")
            appendLine("key_gap=${uiInt("key_gap", 1)}")
            appendLine("key_radius=${uiInt("key_radius", 8)}")
            appendLine("show_bounds=${uiBool("show_bounds", false)}")
            appendLine()
            appendLine("建议默认值：")
            appendLine("top_h=${uiInt("top_h", 40)}, icon_w=${uiInt("icon_w", 40)}, top_right_pad=${uiInt("top_right_pad", 16)}, tab_h=${uiInt("tab_h", 33)}, tab_pad=${uiInt("tab_pad", 9)}, key_h=${uiInt("key_h", 31)}, key_gap=${uiInt("key_gap", 1)}, key_radius=${uiInt("key_radius", 8)}")
        }
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("AIDev UI 参数", text))
        toast("UI 参数已复制，可直接反馈给我")
    }

    private fun resetUiTuning() {
        prefs.edit()
            .remove("ui_top_h")
            .remove("ui_icon_w")
            .remove("ui_top_right_pad")
            .remove("ui_tab_h")
            .remove("ui_tab_pad")
            .remove("ui_key_h")
            .remove("ui_key_gap")
            .remove("ui_key_radius")
            .remove("ui_show_bounds")
            .apply()
        applyUiTuning()
    }

    private fun resetKeyboard() {
        val editor = prefs.edit()
        repeat(24) {
            editor.remove("key_${it}_label")
            editor.remove("key_${it}_input")
            editor.remove("key_${it}_swipe")
        }
        editor.remove("keyboard_rows").remove("keyboard_cols").apply()
        rebuildKeyboard()
    }

    // ---------- Sessions ----------

    private fun newSession() {
        val id = (sessions.maxOfOrNull { it.id } ?: 0) + 1
        val session = createTerminalSession(id)
        val item = AidTermSession(id, "会话${toChineseNumber(id)}", session)
        sessions.add(item)
        switchSession(item)
    }

    private fun closeCurrent() {
        val cur = current ?: return
        cur.session.finishIfRunning()
        sessions.remove(cur)
        if (sessions.isEmpty()) {
            newSession()
        } else {
            switchSession(sessions.last())
        }
    }

    private fun switchSession(item: AidTermSession) {
        current = item
        terminalView.attachSession(item.session)
        terminalView.requestFocus()
        terminalView.onScreenUpdated()
        refreshTabs()
    }

    private fun refreshTabs() {
        tabBar.removeAllViews()
        sessions.forEach { item ->
            tabBar.addView(tabChip(
                item.title,
                item == current,
                onClick = { switchSession(item) },
                onClose = {
                    item.session.finishIfRunning()
                    sessions.remove(item)
                    if (sessions.isEmpty()) newSession()
                    else if (current == item) switchSession(sessions.last())
                    else refreshTabs()
                }
            ))
        }
    }

    private fun toChineseNumber(value: Int): String {
        val nums = listOf("零", "一", "二", "三", "四", "五", "六", "七", "八", "九", "十")
        return when {
            value in 1..10 -> nums[value]
            value < 20 -> "十${nums[value - 10]}"
            value < 100 -> {
                val ten = value / 10
                val one = value % 10
                "${nums[ten]}十${if (one == 0) "" else nums[one]}"
            }
            else -> value.toString()
        }
    }

    private fun createTerminalSession(id: Int): TerminalSession {
        val shell = "/system/bin/sh"
        val nativeDir = applicationInfo.nativeLibraryDir
        val prootLibDir = File(homeDir, "proot-lib").absolutePath
        val env = arrayOf(
            "TERM=xterm-256color",
            "COLORTERM=truecolor",
            "HOME=${homeDir.absolutePath}",
            "PWD=${homeDir.absolutePath}",
            "TMPDIR=${File(cacheDir, "tmp").apply { mkdirs() }.absolutePath}",
            "AIDEV_HOME=${homeDir.absolutePath}",
            "AIDEV_NATIVE=$nativeDir",
            "AIDEV_PROOT_LIB=$prootLibDir",
            "AIDEV_PROOT=$nativeDir/libproot.so",
            "AIDEV_PROOT_LOADER=$nativeDir/libproot_loader.so",
            "AIDEV_PROOT_LOADER_32=$nativeDir/libproot_loader32.so",
            "PROOT_LOADER=$nativeDir/libproot_loader.so",
            "PROOT_LOADER_32=$nativeDir/libproot_loader32.so",
            "PROOT_TMP_DIR=${File(cacheDir, "proot_tmp").apply { mkdirs() }.absolutePath}",
            "LD_LIBRARY_PATH=$prootLibDir:$nativeDir",
            "ENV=${rcFile.absolutePath}",
            "AIDEV_RC=${rcFile.absolutePath}",
            "PATH=/system/bin:/system/xbin"
        )
        return TerminalSession(
            shell,
            homeDir.absolutePath,
            arrayOf("sh", entryFile.absolutePath),
            env,
            5000,
            createSessionClient()
        ).apply {
            mSessionName = "AIDev-$id"
        }
    }

    private fun sendCommand(command: String) {
        val session = current?.session ?: return
        if (!session.isRunning) {
            toast("当前会话已结束")
            return
        }
        session.write(command + "\n")
        terminalView.requestFocus()
    }

    private fun createSessionClient(): TerminalSessionClient {
        return object : TerminalSessionClient {
            override fun onTextChanged(changedSession: TerminalSession) {
                if (changedSession == current?.session) terminalView.onScreenUpdated()
            }

            override fun onTitleChanged(changedSession: TerminalSession) {}

            override fun onSessionFinished(finishedSession: TerminalSession) {
                if (finishedSession == current?.session) terminalView.onScreenUpdated()
            }

            override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("AIDev Terminal", text))
            }

            override fun onPasteTextFromClipboard(session: TerminalSession) {
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                val text = clipboard.primaryClip
                    ?.takeIf { it.itemCount > 0 }
                    ?.getItemAt(0)
                    ?.coerceToText(this@MainActivity)
                    ?.toString()
                if (!text.isNullOrEmpty()) session.write(text)
            }

            override fun onBell(session: TerminalSession) {
                terminalView.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            }

            override fun onColorsChanged(session: TerminalSession) {
                if (session == current?.session) terminalView.invalidate()
            }

            override fun onTerminalCursorStateChange(state: Boolean) {
                terminalView.invalidate()
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
    }

    private fun createViewClient(): TerminalViewClient {
        return object : TerminalViewClient {
            override fun onScale(scale: Float): Float {
                // 浮点累计 + 阻尼，避免双指缩放整数级跳跃造成的卡顿感。
                val damped = 1f + (scale - 1f) * 0.55f
                pendingFontSizeSp = (pendingFontSizeSp * damped).coerceIn(8f, maxSafeFontSp())
                if (!fontApplyScheduled) {
                    fontApplyScheduled = true
                    terminalView.post {
                        fontApplyScheduled = false
                        pendingFontSizeSp = pendingFontSizeSp.coerceIn(8f, maxSafeFontSp())
                        if (kotlin.math.abs(pendingFontSizeSp - fontSizeSp) >= 0.25f) {
                            fontSizeSp = pendingFontSizeSp
                            applyTerminalFontSize()
                            prefs.edit().putFloat("font_sp", fontSizeSp).apply()
                        }
                    }
                }
                return 1.0f
            }

            override fun onSingleTapUp(e: MotionEvent) {
                terminalView.requestFocus()
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(terminalView, InputMethodManager.SHOW_IMPLICIT)
            }

            override fun shouldBackButtonBeMappedToEscape(): Boolean = true
            override fun shouldEnforceCharBasedInput(): Boolean = false
            override fun shouldUseCtrlSpaceWorkaround(): Boolean = false
            override fun isTerminalViewSelected(): Boolean = terminalView.hasFocus()
            override fun copyModeChanged(copyMode: Boolean) {}
            override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean = false
            override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean = false
            override fun onLongPress(event: MotionEvent): Boolean = false
            override fun readControlKey(): Boolean = false
            override fun readAltKey(): Boolean = false
            override fun readShiftKey(): Boolean = false
            override fun readFnKey(): Boolean = false
            override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean = false
            override fun onEmulatorSet() {
                terminalView.onScreenUpdated()
                streamHaptic()
            }
            override fun logError(tag: String, message: String) { Log.e(tag, message) }
            override fun logWarn(tag: String, message: String) { Log.w(tag, message) }
            override fun logInfo(tag: String, message: String) { Log.i(tag, message) }
            override fun logDebug(tag: String, message: String) { Log.d(tag, message) }
            override fun logVerbose(tag: String, message: String) { Log.v(tag, message) }
            override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) { Log.e(tag, message, e) }
            override fun logStackTrace(tag: String, e: Exception) { Log.e(tag, "TerminalView exception", e) }
        }
    }

    // ---------- Shell assets (no +x dependency) ----------
    //
    // Android 11+ 禁止从 /data/data/<pkg>/files 直接 exec 文件。
    // 解决方案：所有逻辑写成 shell 函数，由 mksh 通过 `sh script` 或 `$ENV` 加载，
    // 不依赖任何文件的可执行位。

    private fun writeShellAssets() {
        val home = homeDir.absolutePath

        // 入口脚本：由 `sh /path/to/.aidev_entry` 调用，不需要 +x。
        // 行为：尝试进入 ubuntu，否则进入带函数命令的交互式 sh。
        entryFile.writeText("""
            # AIDev entry; sourced by `sh <file>`.
            export AIDEV_HOME="$home"
            export ENV="${rcFile.absolutePath}"
            . "${'$'}ENV"
            if _aidev_has_ubuntu; then
                _aidev_enter_ubuntu
                exit ${'$'}?
            fi
            exec sh -i
        """.trimIndent() + "\n")

        // rc 文件：所有命令以 mksh 函数形式定义。
        // mksh（Android sh）支持带连字符的函数名，无需 +x 即可作为命令使用。
        rcFile.writeText(buildRc(home))
    }

    private fun buildRc(home: String): String = """
        # AIDev shell rc — loaded via ${'$'}ENV in interactive sh.
        AIDEV_VERSION="0.12.9-oobe-final-debug"
        AIDEV_HOME="$home"
        AIDEV_BIN="${'$'}AIDEV_HOME/dev-env/bin"
        AIDEV_ROOTFS="${'$'}AIDEV_HOME/ubuntu-rootfs"
        export AIDEV_VERSION AIDEV_HOME AIDEV_BIN AIDEV_ROOTFS
        # 彩色提示符：青色 aidev / 黄色路径 / 绿色 #
        export PS1='${'$'}(printf "\033[36maidev\033[0m:\033[33m%s\033[32m# \033[0m" "${'$'}{PWD##*/}")'
        # 让常用命令默认带颜色（ANSI 转义会被 TerminalView 渲染）
        alias ls='ls --color=auto'
        alias ll='ls -lah --color=auto'
        alias grep='grep --color=auto'
        alias egrep='egrep --color=auto'
        alias fgrep='fgrep --color=auto'
        alias diff='diff --color=auto'
        export GREP_COLORS='ms=01;31:mc=01;31:sl=:cx=:fn=35:ln=32:bn=32:se=36'
        export LS_COLORS='di=1;34:ln=1;36:so=35:pi=33:ex=1;32:bd=1;33;40:cd=1;33;40:su=37;41:sg=30;43:tw=30;42:ow=34;42'

        # hl <file>：把常见编程语言关键字 / 字符串 / 数字 / 注释用 ANSI 颜色高亮。
        # 依赖 awk（Android proot/ubuntu/busybox 均自带）；行内分词，不解析多行结构。
        # 覆盖 sh / py / js / ts / kt / java / c / cpp / go / rs。
        hl() {
            f="${'$'}1"
            [ -z "${'$'}f" ] && { echo "usage: hl <file>   # 关键字高亮显示文件"; return 1; }
            [ ! -f "${'$'}f" ] && { echo "hl: ${'$'}f not found"; return 1; }
            command -v awk >/dev/null 2>&1 || { echo "hl: awk not found（请在 Ubuntu 容器内使用）"; return 1; }
            awk '
            BEGIN {
                K="\033[35m"; S="\033[33m"; N="\033[36m"; C="\033[90m"; R="\033[0m"
                kw="^(if|else|elif|fi|then|do|done|while|for|in|case|esac|return|function|local|export|alias|break|continue|exit|set|unset|read|true|false|null|None|True|False|def|class|import|from|as|with|try|except|finally|raise|lambda|yield|pass|var|val|let|const|new|public|private|protected|static|void|int|long|float|double|char|bool|string|String|interface|abstract|override|final|package|fun|when|object|sealed|enum|struct|impl|trait|use|mod|pub|match|loop|where|fn|extern|unsafe|self|Self|nil|switch|default|goto|namespace|template|typename|virtual|inline|extends|implements|throws|throw|catch|this|super|async|await)$"
            }
            {
                line=${'$'}0
                tail=""
                if (match(line, /(#|\/\/).*$/)) {
                    pre=substr(line,1,RSTART-1)
                    cm=substr(line,RSTART)
                    line=pre
                    tail=C cm R
                }
                gsub(/"[^"]*"/, S "&" R, line)
                gsub(/'\''[^'\'']*'\''/, S "&" R, line)
                gsub(/[0-9]+(\.[0-9]+)?/, N "&" R, line)
                out=""; rest=line
                while (match(rest, /[A-Za-z_][A-Za-z0-9_]*/)) {
                    pre=substr(rest,1,RSTART-1)
                    word=substr(rest,RSTART,RLENGTH)
                    rest=substr(rest,RSTART+RLENGTH)
                    if (word ~ kw) out=out pre K word R
                    else out=out pre word
                }
                print out rest tail
            }' "${'$'}f"
        }
        # less 也启用 ANSI 颜色
        export LESS='-R'

        aidev_version() {
            cat <<EOF
AIDev Terminal ${'$'}AIDEV_VERSION
能力：
  Ubuntu PRoot 容器
  开发环境检测/修复/部署
  opencode 官方安装入口
  fast-download 下载封装
  task-run 后台任务管理
EOF
        }

        _aidev_task_dir() {
            mkdir -p "${'$'}AIDEV_HOME/tasks"
            printf '%s\n' "${'$'}AIDEV_HOME/tasks"
        }

        aidev_task_run() {
            name="${'$'}1"; shift
            [ -z "${'$'}name" ] || [ "${'$'}#" -eq 0 ] && {
                echo "usage: task-run <name> <command...>"
                return 1
            }
            dir="${'$'}(_aidev_task_dir)"
            safe="${'$'}(printf '%s' "${'$'}name" | tr -cs 'A-Za-z0-9_.-' '_' | sed 's/^_*//;s/_*$//')"
            [ -z "${'$'}safe" ] && safe="task"
            id="${'$'}(date +%Y%m%d-%H%M%S)-${'$'}safe"
            log="${'$'}dir/${'$'}id.log"
            pidfile="${'$'}dir/${'$'}id.pid"
            meta="${'$'}dir/${'$'}id.meta"
            cmd="${'$'}*"
            nohup sh -lc "${'$'}cmd" > "${'$'}log" 2>&1 &
            pid="${'$'}!"
            printf '%s\n' "${'$'}pid" > "${'$'}pidfile"
            {
                echo "id=${'$'}id"
                echo "name=${'$'}name"
                echo "pid=${'$'}pid"
                echo "started=${'$'}(date '+%F %T')"
                echo "cmd=${'$'}cmd"
                echo "log=${'$'}log"
            } > "${'$'}meta"
            echo "任务已启动：${'$'}id"
            echo "PID：${'$'}pid"
            echo "日志：${'$'}log"
        }

        aidev_task_list() {
            dir="${'$'}(_aidev_task_dir)"
            found=0
            for meta in "${'$'}dir"/*.meta; do
                [ -e "${'$'}meta" ] || continue
                found=1
                id="${'$'}(grep '^id=' "${'$'}meta" | cut -d= -f2-)"
                name="${'$'}(grep '^name=' "${'$'}meta" | cut -d= -f2-)"
                pid="${'$'}(grep '^pid=' "${'$'}meta" | cut -d= -f2-)"
                started="${'$'}(grep '^started=' "${'$'}meta" | cut -d= -f2-)"
                if kill -0 "${'$'}pid" >/dev/null 2>&1; then status="running"; else status="done"; fi
                printf '%-32s %-8s pid=%s  %s  %s\n' "${'$'}id" "${'$'}status" "${'$'}pid" "${'$'}started" "${'$'}name"
            done
            [ "${'$'}found" -eq 1 ] || echo "暂无后台任务。"
        }

        aidev_task_log() {
            id="${'$'}1"
            [ -z "${'$'}id" ] && { echo "usage: task-log <task-id>"; return 1; }
            log="${'$'}AIDEV_HOME/tasks/${'$'}id.log"
            [ -f "${'$'}log" ] || { echo "日志不存在：${'$'}log"; return 1; }
            tail -120 "${'$'}log"
        }

        aidev_task_tail() {
            id="${'$'}1"
            [ -z "${'$'}id" ] && { echo "usage: task-tail <task-id>"; return 1; }
            log="${'$'}AIDEV_HOME/tasks/${'$'}id.log"
            [ -f "${'$'}log" ] || { echo "日志不存在：${'$'}log"; return 1; }
            tail -f "${'$'}log"
        }

        aidev_task_stop() {
            id="${'$'}1"
            [ -z "${'$'}id" ] && { echo "usage: task-stop <task-id>"; return 1; }
            pidfile="${'$'}AIDEV_HOME/tasks/${'$'}id.pid"
            [ -f "${'$'}pidfile" ] || { echo "PID 文件不存在：${'$'}pidfile"; return 1; }
            pid="${'$'}(cat "${'$'}pidfile")"
            kill "${'$'}pid" >/dev/null 2>&1 && echo "已停止：${'$'}id" || echo "任务可能已结束：${'$'}id"
        }

        _aidev_has_ubuntu() {
            [ -x "${'$'}AIDEV_PROOT" ] && [ -f "${'$'}AIDEV_ROOTFS/etc/os-release" ]
        }

        _aidev_prepare_ubuntu() {
            mkdir -p "${'$'}AIDEV_ROOTFS/etc"
            [ -s "${'$'}AIDEV_ROOTFS/etc/resolv.conf" ] || \
                printf 'nameserver 223.5.5.5\nnameserver 8.8.8.8\n' > "${'$'}AIDEV_ROOTFS/etc/resolv.conf"
            [ -s "${'$'}AIDEV_ROOTFS/etc/hosts" ] || \
                printf '127.0.0.1 localhost\n::1 localhost ip6-localhost ip6-loopback\n' > "${'$'}AIDEV_ROOTFS/etc/hosts"

            touch "${'$'}AIDEV_ROOTFS/etc/group"
            for gid in ${'$'}(id -G 2>/dev/null); do
                case "${'$'}gid" in
                    ''|*[!0-9]*) continue ;;
                esac
                if ! grep -q "^[^:]*:[^:]*:${'$'}gid:" "${'$'}AIDEV_ROOTFS/etc/group" 2>/dev/null; then
                    printf 'android_%s:x:%s:\n' "${'$'}gid" "${'$'}gid" >> "${'$'}AIDEV_ROOTFS/etc/group"
                fi
            done

            mkdir -p "${'$'}AIDEV_ROOTFS/etc/apt" "${'$'}AIDEV_ROOTFS/etc/apt/sources.list.d" "${'$'}AIDEV_ROOTFS/usr/local/bin" "${'$'}AIDEV_ROOTFS/root"
            for src in "${'$'}AIDEV_ROOTFS"/etc/apt/sources.list.d/*.sources; do
                [ -e "${'$'}src" ] || continue
                case "${'$'}src" in
                    *.disabled) : ;;
                    *) mv "${'$'}src" "${'$'}src.disabled" 2>/dev/null || true ;;
                esac
            done
            cat > "${'$'}AIDEV_ROOTFS/etc/apt/sources.list" <<'AIDEV_APT_EOF'
deb http://mirrors.tuna.tsinghua.edu.cn/ubuntu-ports/ noble main universe multiverse restricted
deb http://mirrors.tuna.tsinghua.edu.cn/ubuntu-ports/ noble-updates main universe multiverse restricted
deb http://mirrors.tuna.tsinghua.edu.cn/ubuntu-ports/ noble-security main universe multiverse restricted
AIDEV_APT_EOF

            cat > "${'$'}AIDEV_ROOTFS/usr/local/bin/env-explain" <<'AIDEV_EXPLAIN_EOF'
#!/bin/bash
cat <<'EOF'
AIDev 开发环境说明

基础层：
  ca-certificates  HTTPS 证书，curl/git/npm 访问 https 必需
  curl/wget        下载工具
  git              代码拉取
  unzip/zip/tar/xz 解压缩工具
  nano/vim/less    基础编辑和查看
  procps/coreutils/findutils 常用 Linux 命令

编译层：
  build-essential  gcc/g++/make 等基础编译工具
  python3/pip      Python 与包管理
  openjdk-17-jdk   Java/Gradle/Android 工具链基础

JS/AI 层：
  nodejs/npm       Node.js 工具链
  npm registry     默认配置为 npmmirror，提升国内访问成功率
  opencode         通过 install-aitool 安装到 ~/.opencode/bin

推荐顺序：
  check-dev-env    先检测
  deploy-dev-env   缺什么装什么
  check-dev-env    再确认
  install-aitool   安装 opencode
EOF
AIDEV_EXPLAIN_EOF
            chmod 755 "${'$'}AIDEV_ROOTFS/usr/local/bin/env-explain"

            cat > "${'$'}AIDEV_ROOTFS/usr/local/bin/check-dev-env" <<'AIDEV_CHECK_EOF'
#!/bin/bash
set +e
echo "AIDev 环境检测"
echo
echo "== 系统 =="
grep PRETTY_NAME /etc/os-release 2>/dev/null | cut -d= -f2- | tr -d '"' || true
uname -m
echo
echo "== 命令检测 =="
for c in curl wget git unzip zip tar xz node npm python3 pip3 java gcc g++ make opencode; do
  if command -v "${'$'}c" >/dev/null 2>&1; then
    v="${'$'}(${'$'}c --version 2>/dev/null | head -1)"
    [ -z "${'$'}v" ] && v="${'$'}(command -v "${'$'}c")"
    printf "  %-10s ✓ %s\n" "${'$'}c" "${'$'}v"
  else
    printf "  %-10s ✗ 缺失\n" "${'$'}c"
  fi
done
echo
echo "== dpkg 包检测 =="
for p in ca-certificates curl wget git unzip zip tar xz-utils nano vim less procps coreutils findutils build-essential python3 python3-pip openjdk-17-jdk nodejs npm; do
  if dpkg-query -W -f='${'$'}{Status}' "${'$'}p" 2>/dev/null | grep -q "install ok installed"; then
    printf "  %-22s ✓ 已安装\n" "${'$'}p"
  else
    printf "  %-22s ✗ 未安装\n" "${'$'}p"
  fi
done
echo
echo "== dpkg 状态 =="
dpkg --audit 2>/dev/null || true
echo
echo "== 证书 =="
if [ -s /etc/ssl/certs/ca-certificates.crt ]; then
  echo "  ca-certificates ✓"
else
  echo "  ca-certificates ✗"
fi
echo
echo "== 网络 =="
getent hosts github.com >/dev/null 2>&1 && echo "  DNS github.com ✓" || echo "  DNS github.com ✗"
if command -v curl >/dev/null 2>&1; then
  curl -I -L --connect-timeout 10 https://github.com 2>&1 | head -8
else
  echo "  curl 缺失，无法测试 HTTPS。"
fi
AIDEV_CHECK_EOF
            chmod 755 "${'$'}AIDEV_ROOTFS/usr/local/bin/check-dev-env"

            cat > "${'$'}AIDEV_ROOTFS/usr/local/bin/repair-dev-env" <<'AIDEV_REPAIR_EOF'
#!/bin/bash
set +e
export DEBIAN_FRONTEND=noninteractive
echo "AIDev 环境修复"
echo "[1/4] 清理 apt/dpkg 锁残留（不会终止正在运行的 apt）..."
rm -f /var/cache/apt/archives/lock /var/lib/apt/lists/lock /var/lib/dpkg/lock-frontend /var/lib/dpkg/lock 2>/dev/null
echo "[2/4] 配置未完成的包..."
dpkg --configure -a
echo "[3/4] 修复破损依赖..."
apt-get -f install -y
echo "[4/4] 再次配置确认..."
dpkg --configure -a
echo
echo "修复完成。建议运行：check-dev-env"
AIDEV_REPAIR_EOF
            chmod 755 "${'$'}AIDEV_ROOTFS/usr/local/bin/repair-dev-env"

            cat > "${'$'}AIDEV_ROOTFS/usr/local/bin/deploy-dev-env" <<'AIDEV_DEV_EOF'
#!/bin/bash
set +e
export DEBIAN_FRONTEND=noninteractive

echo "AIDev 开发环境配置"
echo "原则：先检测，缺什么装什么；基础层优先成功；Node/npm 作为 JS 层单独处理。"
echo

base_packages=(
  ca-certificates curl wget git unzip zip tar xz-utils
  nano vim less procps coreutils findutils
  build-essential python3 python3-pip openjdk-17-jdk
)
js_packages=(nodejs npm)

missing_base=()
for p in "${'$'}{base_packages[@]}"; do
  if ! dpkg-query -W -f='${'$'}{Status}' "${'$'}p" 2>/dev/null | grep -q "install ok installed"; then
    missing_base+=("${'$'}p")
  fi
done

missing_js=()
for p in "${'$'}{js_packages[@]}"; do
  if ! dpkg-query -W -f='${'$'}{Status}' "${'$'}p" 2>/dev/null | grep -q "install ok installed"; then
    missing_js+=("${'$'}p")
  fi
done

echo "[1/6] 当前检测结果"
if [ "${'$'}{#missing_base[@]}" -eq 0 ]; then
  echo "  基础层：已全部安装。"
else
  echo "  基础层缺失：${'$'}{missing_base[*]}"
fi
if [ "${'$'}{#missing_js[@]}" -eq 0 ]; then
  echo "  JS 层：已全部安装。"
else
  echo "  JS 层缺失：${'$'}{missing_js[*]}"
fi

echo "[2/6] 检查并修复 dpkg 中断状态..."
repair-dev-env

need_update=0
[ "${'$'}{#missing_base[@]}" -gt 0 ] && need_update=1
[ "${'$'}{#missing_js[@]}" -gt 0 ] && need_update=1
if [ "${'$'}need_update" -eq 1 ]; then
  echo "[3/6] 更新 apt 索引..."
  apt-get update
else
  echo "[3/6] 跳过 apt update"
fi

if [ "${'$'}{#missing_base[@]}" -gt 0 ]; then
  echo "[4/6] 安装基础层缺失包..."
  if ! apt-get install -y --no-install-recommends "${'$'}{missing_base[@]}"; then
    echo "基础层安装遇到 apt/dpkg 问题，自动修复后重试一次..."
    repair-dev-env
    apt-get install -y --no-install-recommends "${'$'}{missing_base[@]}" || {
      echo "基础层安装失败。请运行 check-dev-env 查看状态。"
      exit 1
    }
  fi
else
  echo "[4/6] 基础层无需安装"
fi

if [ "${'$'}{#missing_js[@]}" -gt 0 ]; then
  echo "[5/6] 安装 JS 层 nodejs/npm..."
  if ! apt-get install -y --no-install-recommends "${'$'}{missing_js[@]}"; then
    echo "JS 层安装遇到问题，自动修复后重试一次..."
    repair-dev-env
    if ! apt-get install -y --no-install-recommends "${'$'}{missing_js[@]}"; then
      echo "JS 层 nodejs/npm 仍未完全配置。基础开发环境可继续使用。"
      echo "稍后可再次运行：repair-dev-env && deploy-dev-env"
    fi
  fi
else
  echo "[5/6] JS 层无需安装"
fi

echo "[6/6] 配置工具默认项..."
if command -v npm >/dev/null 2>&1; then
  current_registry="${'$'}(npm config get registry 2>/dev/null || true)"
  if [ "${'$'}current_registry" != "https://registry.npmmirror.com/" ]; then
    npm config set registry https://registry.npmmirror.com || true
    echo "  npm registry 已设置为 npmmirror"
  else
    echo "  npm registry 已是 npmmirror"
  fi
fi

echo
echo "配置完成。建议运行：check-dev-env"
AIDEV_DEV_EOF
            chmod 755 "${'$'}AIDEV_ROOTFS/usr/local/bin/deploy-dev-env"

            cat > "${'$'}AIDEV_ROOTFS/usr/local/bin/env-menu" <<'AIDEV_MENU_EOF'
#!/bin/bash
cat <<'EOF'
AIDev 环境菜单

基础：
  aidev-version        显示 AIDev Terminal 版本和能力
  aidev-help           显示 AIDev 命令帮助
  aidev-verify         批量执行最小验证

检测：
  check-dev-env        检测 curl/git/node/npm/jdk/opencode/证书/网络
  env-explain          查看一键配置到底会配置什么
  repair-dev-env       修复 dpkg/apt 中断或破损依赖
  keepalive-explain    查看后台常驻与 HyperOS 设置建议
  check-keepalive      检测后台常驻相关系统状态

配置：
  deploy-dev-env       先检测，缺什么装什么
  install-aitool       安装 opencode；已安装时会提示，不盲目覆盖

Android 开发：
  android-dev-explain  查看移动端 Android 开发环境说明
  check-android-dev    检测 JDK/Gradle/Android 工具/系统调试命令
  deploy-android-dev   部署 Android 开发工具链基础层
  android-debug-tools  查看 Android 调试命令入口

服务通信：
  aidev-net-explain    查看 AI/Web 服务端口通信说明
  list-listen-ports    查看当前监听端口
  check-local-server <port|url>  检测本机 Web 服务访问

后台任务：
  task-run <name> <cmd>  后台执行长任务
  task-list              查看任务列表
  task-log <id>          查看最近日志
  task-tail <id>         实时跟踪日志
  task-stop <id>         停止任务

Ubuntu：
  ubuntu               当前已在 Ubuntu 中时只提示状态
  install-ubuntu       当前已在 Ubuntu 中时只提示如何重装
  exit                 退出 Ubuntu，回到 Android shell

诊断建议：
  如果 curl 报错，先运行 check-dev-env，把输出贴给开发者。
EOF
AIDEV_MENU_EOF
            chmod 755 "${'$'}AIDEV_ROOTFS/usr/local/bin/env-menu"

            cat > "${'$'}AIDEV_ROOTFS/usr/local/bin/aidev-version" <<'AIDEV_VERSION_EOF'
#!/bin/bash
cat <<'EOF'
AIDev Terminal 0.12.9-oobe-final-debug
能力：
  Ubuntu PRoot 容器
  开发环境检测/修复/部署
  Android 开发工具链检测/部署
  Android 系统调试命令桥接
  opencode 官方安装入口
  fast-download 下载封装
  task-run 后台任务管理
  aidev-verify 批量验证
EOF
AIDEV_VERSION_EOF
            chmod 755 "${'$'}AIDEV_ROOTFS/usr/local/bin/aidev-version"

            cat > "${'$'}AIDEV_ROOTFS/usr/local/bin/aidev-help" <<'AIDEV_HELP_EOF'
#!/bin/bash
cat <<'EOF'
AIDev 命令帮助

基础：
  aidev-version        显示版本和能力
  aidev-help           显示本帮助
  aidev-verify         批量执行最小验证

Ubuntu：
  ubuntu               当前在 Ubuntu 内时只提示状态
  install-ubuntu       当前在 Ubuntu 内时提示如何回到外层重装

环境：
  env-menu             查看环境菜单
  env-explain          查看一键配置内容说明
  check-dev-env        检测工具、包、证书、网络和 dpkg 状态
  repair-dev-env       修复 dpkg/apt 中断或破损依赖
  keepalive-explain    查看后台常驻与 HyperOS 设置建议
  check-keepalive      检测后台常驻相关系统状态
  deploy-dev-env       先检测，缺什么装什么
  android-dev-explain  查看移动端 Android 开发环境说明
  check-android-dev    检测 Android 开发工具链
  deploy-android-dev   部署 Android 开发工具链基础层
  android-debug-tools  查看 Android 调试命令入口
  aidev-net-explain    查看 AI/Web 服务端口通信说明
  list-listen-ports    查看当前监听端口
  check-local-server <port|url> 检测本机 Web 服务访问
  install-aitool       安装 opencode 官方 CLI/代理客户端

后台任务：
  task-run <name> <cmd> 后台执行长任务
  task-list             查看任务列表
  task-log <id>         查看最近日志
  task-tail <id>        实时跟踪日志
  task-stop <id>        停止任务

说明：
  bash 自带 help 内建命令，所以 AIDev 帮助使用 aidev-help。
EOF
AIDEV_HELP_EOF
            chmod 755 "${'$'}AIDEV_ROOTFS/usr/local/bin/aidev-help"

            cat > "${'$'}AIDEV_ROOTFS/usr/local/bin/aidev-verify" <<'AIDEV_VERIFY_EOF'
#!/bin/bash
set +e
pass=0
fail=0

ok() {
  echo "✓ ${'$'}1"
  pass=${'$'}((pass + 1))
}

bad() {
  echo "✗ ${'$'}1"
  fail=${'$'}((fail + 1))
}

check_cmd() {
  if command -v "${'$'}1" >/dev/null 2>&1; then ok "命令存在：${'$'}1"; else bad "命令缺失：${'$'}1"; fi
}

echo "AIDev 最小验证"
echo
check_cmd aidev-version
check_cmd aidev-help
check_cmd env-menu
check_cmd check-dev-env
check_cmd deploy-dev-env
check_cmd repair-dev-env
check_cmd keepalive-explain
check_cmd check-keepalive
check_cmd install-aitool
check_cmd android-dev-explain
check_cmd check-android-dev
check_cmd deploy-android-dev
check_cmd android-debug-tools
check_cmd aidev-net-explain
check_cmd list-listen-ports
check_cmd check-local-server
check_cmd task-run
check_cmd task-list
check_cmd task-log
check_cmd task-tail
check_cmd task-stop

echo
echo "后台任务验证..."
task_id="${'$'}(task-run verify "echo hello && sleep 1 && echo done" 2>/dev/null | awk -F'：' '/任务已启动/ {print ${'$'}2}')"
if [ -n "${'$'}task_id" ]; then
  sleep 2
  if task-log "${'$'}task_id" 2>/dev/null | grep -q "done"; then
    ok "后台任务日志正常"
  else
    bad "后台任务日志未出现 done"
  fi
else
  bad "后台任务启动失败"
fi

echo
echo "网络粗检..."
getent hosts github.com >/dev/null 2>&1 && ok "DNS github.com" || bad "DNS github.com"
if command -v curl >/dev/null 2>&1; then
  curl -fsSI --connect-timeout 8 https://github.com >/dev/null 2>&1 && ok "HTTPS github.com" || bad "HTTPS github.com"
else
  bad "curl 缺失，无法测试 HTTPS"
fi

echo
echo "结果：通过 ${'$'}pass，失败 ${'$'}fail"
[ "${'$'}fail" -eq 0 ]
AIDEV_VERIFY_EOF
            chmod 755 "${'$'}AIDEV_ROOTFS/usr/local/bin/aidev-verify"

            cat > "${'$'}AIDEV_ROOTFS/usr/local/bin/task-run" <<'AIDEV_TASK_RUN_EOF'
#!/bin/bash
name="${'$'}1"; shift
[ -z "${'$'}name" ] || [ "${'$'}#" -eq 0 ] && { echo "usage: task-run <name> <command...>"; exit 1; }
dir="/aidev/tasks"
mkdir -p "${'$'}dir"
safe="${'$'}(printf '%s' "${'$'}name" | tr -cs 'A-Za-z0-9_.-' '_' | sed 's/^_*//;s/_*$//')"
[ -z "${'$'}safe" ] && safe="task"
id="${'$'}(date +%Y%m%d-%H%M%S)-${'$'}safe"
log="${'$'}dir/${'$'}id.log"
pidfile="${'$'}dir/${'$'}id.pid"
meta="${'$'}dir/${'$'}id.meta"
cmd="${'$'}*"
nohup bash -lc "${'$'}cmd" > "${'$'}log" 2>&1 &
pid="${'$'}!"
printf '%s\n' "${'$'}pid" > "${'$'}pidfile"
{
  echo "id=${'$'}id"
  echo "name=${'$'}name"
  echo "pid=${'$'}pid"
  echo "started=${'$'}(date '+%F %T')"
  echo "cmd=${'$'}cmd"
  echo "log=${'$'}log"
} > "${'$'}meta"
echo "任务已启动：${'$'}id"
echo "PID：${'$'}pid"
echo "日志：${'$'}log"
AIDEV_TASK_RUN_EOF
            chmod 755 "${'$'}AIDEV_ROOTFS/usr/local/bin/task-run"

            cat > "${'$'}AIDEV_ROOTFS/usr/local/bin/task-list" <<'AIDEV_TASK_LIST_EOF'
#!/bin/bash
dir="/aidev/tasks"
mkdir -p "${'$'}dir"
found=0
for meta in "${'$'}dir"/*.meta; do
  [ -e "${'$'}meta" ] || continue
  found=1
  id="${'$'}(grep '^id=' "${'$'}meta" | cut -d= -f2-)"
  name="${'$'}(grep '^name=' "${'$'}meta" | cut -d= -f2-)"
  pid="${'$'}(grep '^pid=' "${'$'}meta" | cut -d= -f2-)"
  started="${'$'}(grep '^started=' "${'$'}meta" | cut -d= -f2-)"
  if kill -0 "${'$'}pid" >/dev/null 2>&1; then status="running"; else status="done"; fi
  printf '%-32s %-8s pid=%s  %s  %s\n' "${'$'}id" "${'$'}status" "${'$'}pid" "${'$'}started" "${'$'}name"
done
[ "${'$'}found" -eq 1 ] || echo "暂无后台任务。"
AIDEV_TASK_LIST_EOF
            chmod 755 "${'$'}AIDEV_ROOTFS/usr/local/bin/task-list"

            cat > "${'$'}AIDEV_ROOTFS/usr/local/bin/task-log" <<'AIDEV_TASK_LOG_EOF'
#!/bin/bash
id="${'$'}1"
[ -z "${'$'}id" ] && { echo "usage: task-log <task-id>"; exit 1; }
log="/aidev/tasks/${'$'}id.log"
[ -f "${'$'}log" ] || { echo "日志不存在：${'$'}log"; exit 1; }
tail -120 "${'$'}log"
AIDEV_TASK_LOG_EOF
            chmod 755 "${'$'}AIDEV_ROOTFS/usr/local/bin/task-log"

            cat > "${'$'}AIDEV_ROOTFS/usr/local/bin/task-tail" <<'AIDEV_TASK_TAIL_EOF'
#!/bin/bash
id="${'$'}1"
[ -z "${'$'}id" ] && { echo "usage: task-tail <task-id>"; exit 1; }
log="/aidev/tasks/${'$'}id.log"
[ -f "${'$'}log" ] || { echo "日志不存在：${'$'}log"; exit 1; }
tail -f "${'$'}log"
AIDEV_TASK_TAIL_EOF
            chmod 755 "${'$'}AIDEV_ROOTFS/usr/local/bin/task-tail"

            cat > "${'$'}AIDEV_ROOTFS/usr/local/bin/task-stop" <<'AIDEV_TASK_STOP_EOF'
#!/bin/bash
id="${'$'}1"
[ -z "${'$'}id" ] && { echo "usage: task-stop <task-id>"; exit 1; }
pidfile="/aidev/tasks/${'$'}id.pid"
[ -f "${'$'}pidfile" ] || { echo "PID 文件不存在：${'$'}pidfile"; exit 1; }
pid="${'$'}(cat "${'$'}pidfile")"
kill "${'$'}pid" >/dev/null 2>&1 && echo "已停止：${'$'}id" || echo "任务可能已结束：${'$'}id"
AIDEV_TASK_STOP_EOF
            chmod 755 "${'$'}AIDEV_ROOTFS/usr/local/bin/task-stop"

            cat > "${'$'}AIDEV_ROOTFS/usr/local/bin/ubuntu" <<'AIDEV_UBUNTU_INNER_EOF'
#!/bin/bash
echo "你已经在 Ubuntu 容器内了。"
echo "当前提示符 root@localhost:~# 就是 Ubuntu root 环境。"
echo "如需回到 Android shell，请输入：exit"
AIDEV_UBUNTU_INNER_EOF
            chmod 755 "${'$'}AIDEV_ROOTFS/usr/local/bin/ubuntu"

            cat > "${'$'}AIDEV_ROOTFS/usr/local/bin/install-ubuntu" <<'AIDEV_INSTALL_INNER_EOF'
#!/bin/bash
echo "你已经在 Ubuntu 容器内，不需要再次 install-ubuntu。"
echo "如果要重试或重装 Ubuntu rootfs，请先输入 exit 回到 Android shell，然后执行："
echo "  install-ubuntu --retry"
echo "或完全清理后重装："
echo "  install-ubuntu --clean"
echo "  install-ubuntu --fast"
AIDEV_INSTALL_INNER_EOF
            chmod 755 "${'$'}AIDEV_ROOTFS/usr/local/bin/install-ubuntu"

            cat > "${'$'}AIDEV_ROOTFS/usr/local/bin/install-aitool" <<'AIDEV_AI_EOF'
#!/bin/bash
set -e
echo "AIDev 将安装：opencode"
echo "来源：opencode 官方安装脚本 https://opencode.ai/install"
echo "目标目录：${'$'}HOME/.opencode/bin"
echo
if ! command -v curl >/dev/null 2>&1 || ! command -v unzip >/dev/null 2>&1; then
  echo "缺少 curl/unzip，先执行 deploy-dev-env。"
  deploy-dev-env
fi
if ! command -v curl >/dev/null 2>&1; then
  echo "curl 仍然不可用，请运行 check-dev-env 查看原因。"
  exit 1
fi

if command -v opencode >/dev/null 2>&1; then
  echo "检测到 opencode 已安装：$(command -v opencode)"
  opencode --version 2>/dev/null || true
  echo "如需强制重装，请先删除：rm -f ~/.opencode/bin/opencode"
  exit 0
fi

install_dir="${'$'}HOME/.opencode/bin"
export OPENCODE_INSTALL_DIR="${'$'}install_dir"
export SHELL="${'$'}{SHELL:-/bin/bash}"

aidev_fix_dns() {
  cat > /etc/resolv.conf <<'EOF'
nameserver 223.5.5.5
nameserver 119.29.29.29
nameserver 8.8.8.8
EOF
}

aidev_net_precheck() {
  echo "安装前网络预检..."
  aidev_fix_dns
  for host in github.com api.github.com objects.githubusercontent.com release-assets.githubusercontent.com opencode.ai; do
    if getent hosts "${'$'}host" >/dev/null 2>&1; then
      echo "  DNS ${'$'}host ✓"
    else
      echo "  DNS ${'$'}host ✗，重写 resolv.conf 后重试"
      aidev_fix_dns
      sleep 1
      getent hosts "${'$'}host" >/dev/null 2>&1 || {
        echo "  仍无法解析 ${'$'}host。请稍后重试或切换网络。"
        return 1
      }
    fi
  done
  curl -fsSI --connect-timeout 12 https://github.com >/dev/null 2>&1 || {
    echo "  GitHub HTTPS 预检失败。请稍后重试或切换网络。"
    return 1
  }
  echo "网络预检通过。"
}

echo "开始执行官方安装脚本。官方脚本会负责选择版本、下载、解包和写入 PATH。"
echo "如果要看官方脚本内容，可运行：curl -fsSL https://opencode.ai/install"
aidev_net_precheck || exit 1

ok=0
for attempt in 1 2 3; do
  echo "官方安装尝试 ${'$'}attempt/3 ..."
  if curl -fsSL --retry 3 --retry-delay 2 --connect-timeout 20 https://opencode.ai/install | bash; then
    ok=1
    break
  fi
  echo "本次安装失败，刷新 DNS 并等待后重试。"
  aidev_fix_dns
  sleep 2
done

if [ "${'$'}ok" -ne 1 ]; then
  echo "opencode 官方安装脚本连续失败。"
  echo "建议运行：check-dev-env"
  echo "也可以手动执行：curl -fsSL https://opencode.ai/install | bash"
  exit 1
fi

echo "确认 PATH 配置..."
if ! grep -q '.opencode/bin' "${'$'}HOME/.bashrc" 2>/dev/null; then
  printf '\n# opencode\nexport PATH="${'$'}HOME/.opencode/bin:${'$'}PATH"\n' >> "${'$'}HOME/.bashrc"
fi
export PATH="${'$'}install_dir:${'$'}PATH"
echo "安装完成：${'$'}("${'$'}install_dir/opencode" --version 2>/dev/null || echo "${'$'}install_dir/opencode")"
echo "如果当前 shell 还找不到 opencode，请执行：source ~/.bashrc"
AIDEV_AI_EOF
            chmod 755 "${'$'}AIDEV_ROOTFS/usr/local/bin/install-aitool"

            cat > "${'$'}AIDEV_ROOTFS/usr/local/bin/android-dev-explain" <<'AIDEV_ANDROID_EXPLAIN_EOF'
#!/bin/bash
cat <<'EOF'
AIDev Android 开发环境说明

目标：
  让手机本身成为 Android 开发终端，支持代码获取、编辑、依赖下载、Gradle 构建、签名、安装、日志和设备调试。

基础工具链：
  openjdk-17-jdk、git、curl、wget、unzip、zip、build-essential、python3。

构建工具：
  Gradle 用于 Android/Java/Kotlin 项目构建。
  Android SDK Platform 用于编译 API。
  aapt/aapt2/apksigner/zipalign/adb/fastboot 会优先使用 Ubuntu 仓库中可用的 ARM64 包。

移动端限制：
  Google 官方 Android SDK Build-Tools 的 Linux 包主要面向桌面 Linux；在手机 ARM64 Ubuntu/PRoot 内可能出现二进制架构不匹配。
  AIDev 会优先安装 Ubuntu 仓库中可运行的 ARM64 Android 工具，并明确检测哪些可用、哪些不可用。

Android 调试能力：
  容器已挂载 /system，但 Android 系统命令应通过 android-sh 桥接执行。
  示例：android-sh pm list packages，或使用快捷命令 pmx list packages。

建议流程：
  1. deploy-dev-env
  2. deploy-android-dev
  3. check-android-dev
  4. task-run build './gradlew assembleDebug'
EOF
AIDEV_ANDROID_EXPLAIN_EOF
            chmod 755 "${'$'}AIDEV_ROOTFS/usr/local/bin/android-dev-explain"

            cat > "${'$'}AIDEV_ROOTFS/usr/local/bin/check-android-dev" <<'AIDEV_ANDROID_CHECK_EOF'
#!/bin/bash
set +e
echo "AIDev Android 开发环境检测"
echo
echo "== 系统 =="
uname -a
[ -f /etc/os-release ] && . /etc/os-release && echo "Ubuntu: ${'$'}{PRETTY_NAME:-unknown}"
echo "Arch: ${'$'}(uname -m)"
echo
echo "== Java / Gradle =="
for c in java javac gradle; do
  if command -v "${'$'}c" >/dev/null 2>&1; then
    printf '  %-10s %s\n' "${'$'}c" "${'$'}(command -v "${'$'}c")"
    "${'$'}c" --version 2>/dev/null | head -2 || "${'$'}c" -version 2>&1 | head -2
  else
    printf '  %-10s missing\n' "${'$'}c"
  fi
done
echo
echo "== Android SDK 环境变量 =="
echo "ANDROID_HOME=${'$'}{ANDROID_HOME:-未设置}"
echo "ANDROID_SDK_ROOT=${'$'}{ANDROID_SDK_ROOT:-未设置}"
for d in "${'$'}ANDROID_HOME" "${'$'}ANDROID_SDK_ROOT" "${'$'}HOME/android-sdk" "${'$'}HOME/.android-sdk"; do
  [ -n "${'$'}d" ] && [ -d "${'$'}d" ] && echo "  SDK 目录存在：${'$'}d"
done
echo
echo "== Android 构建/签名/平台工具 =="
for c in adb fastboot aapt aapt2 apksigner zipalign dx d8; do
  if command -v "${'$'}c" >/dev/null 2>&1; then
    printf '  %-10s %s\n' "${'$'}c" "${'$'}(command -v "${'$'}c")"
  else
    printf '  %-10s missing\n' "${'$'}c"
  fi
done
echo
echo "== Android 系统调试命令 =="
for c in android-sh pmx amx getpropx logcatx; do
  if command -v "${'$'}c" >/dev/null 2>&1; then
    printf '  %-12s %s\n' "${'$'}c" "${'$'}(command -v "${'$'}c")"
  else
    printf '  %-12s missing\n' "${'$'}c"
  fi
done
echo
echo "== 设备信息 =="
if command -v android-sh >/dev/null 2>&1; then
  echo "ro.product.model=${'$'}(android-sh getprop ro.product.model 2>/dev/null)"
  echo "ro.build.version.release=${'$'}(android-sh getprop ro.build.version.release 2>/dev/null)"
  echo "ro.product.cpu.abi=${'$'}(android-sh getprop ro.product.cpu.abi 2>/dev/null)"
else
  echo "android-sh 不可用。请确认已安装新版 AIDev，并重新进入 Ubuntu。"
fi
echo
echo "建议：如果缺少基础工具，运行 deploy-android-dev。"
AIDEV_ANDROID_CHECK_EOF
            chmod 755 "${'$'}AIDEV_ROOTFS/usr/local/bin/check-android-dev"

            cat > "${'$'}AIDEV_ROOTFS/usr/local/bin/deploy-android-dev" <<'AIDEV_ANDROID_DEPLOY_EOF'
#!/bin/bash
set +e
export DEBIAN_FRONTEND=noninteractive
echo "AIDev Android 开发工具链部署"
echo "原则：先安装可在 ARM64 Ubuntu/手机环境运行的工具；官方桌面 SDK 二进制不盲目强装。"
echo

repair-dev-env >/dev/null 2>&1 || true
apt-get update

base=(ca-certificates curl wget git unzip zip tar xz-utils python3 openjdk-17-jdk gradle)
echo "[1/3] 安装基础 Android 开发层..."
apt-get install -y --no-install-recommends "${'$'}{base[@]}"

echo "[2/3] 安装 Ubuntu 仓库中可用的 Android 工具..."
candidates=(adb fastboot android-sdk-platform-tools aapt aapt2 apksigner zipalign)
available=()
missing=()
for p in "${'$'}{candidates[@]}"; do
  if apt-cache show "${'$'}p" >/dev/null 2>&1; then
    available+=("${'$'}p")
  else
    missing+=("${'$'}p")
  fi
done
if [ "${'$'}{#available[@]}" -gt 0 ]; then
  apt-get install -y --no-install-recommends "${'$'}{available[@]}"
fi
if [ "${'$'}{#missing[@]}" -gt 0 ]; then
  echo "仓库中暂未找到这些包：${'$'}{missing[*]}"
fi

echo "[3/3] 写入 Android 开发环境变量..."
mkdir -p "${'$'}HOME/android-sdk" "${'$'}HOME/.gradle"
if ! grep -q 'AIDev Android Dev' "${'$'}HOME/.bashrc" 2>/dev/null; then
  cat >> "${'$'}HOME/.bashrc" <<'EOF'

# AIDev Android Dev
export ANDROID_HOME="${'$'}HOME/android-sdk"
export ANDROID_SDK_ROOT="${'$'}ANDROID_HOME"
export PATH="${'$'}ANDROID_HOME/platform-tools:${'$'}ANDROID_HOME/cmdline-tools/latest/bin:${'$'}PATH"
EOF
fi

echo
echo "部署完成。请运行：check-android-dev"
echo "说明：如需 Google 官方 SDK Platform，可后续接入 sdkmanager；但 build-tools 在 ARM64 手机环境可能存在架构兼容限制。"
AIDEV_ANDROID_DEPLOY_EOF
            chmod 755 "${'$'}AIDEV_ROOTFS/usr/local/bin/deploy-android-dev"

            cat > "${'$'}AIDEV_ROOTFS/usr/local/bin/android-debug-tools" <<'AIDEV_ANDROID_DEBUG_EOF'
#!/bin/bash
cat <<'EOF'
AIDev Android 调试工具入口

设备信息：
  android-sh getprop ro.product.model
  android-sh getprop ro.build.version.release
  android-sh getprop ro.product.cpu.abi

日志：
  android-sh logcat
  android-sh logcat -d
  android-sh logcat -c

应用管理：
  android-sh pm list packages
  android-sh pm path <package>
  android-sh pm dump <package>

Activity / Intent：
  android-sh am start -n <package>/<activity>
  android-sh am force-stop <package>
  android-sh am broadcast -a <action>

系统状态：
  android-sh dumpsys activity top
  android-sh dumpsys package <package>
  android-sh dumpsys battery

输入与截图：
  android-sh input text hello
  android-sh input keyevent 3
  android-sh screencap -p /sdcard/aidev-screen.png
  android-sh screenrecord /sdcard/aidev-record.mp4

权限说明：
  在 PRoot Ubuntu 内直接执行 /system/bin/pm 可能报 required file not found。
  请使用 android-sh 桥接到 Android shell 执行系统命令。
  普通应用上下文下部分 pm/am/dumpsys 能力会受 Android 权限限制。
  后续会通过“权限管理 / Shizuku”继续增强高权限调试入口。
EOF
AIDEV_ANDROID_DEBUG_EOF
            chmod 755 "${'$'}AIDEV_ROOTFS/usr/local/bin/android-debug-tools"

            cat > "${'$'}AIDEV_ROOTFS/usr/local/bin/android-sh" <<'AIDEV_ANDROID_SH_EOF'
#!/bin/bash
if [ "${'$'}#" -eq 0 ]; then
  echo "usage: android-sh <android-system-command...>"
  echo "example: android-sh pm list packages"
  echo "example: android-sh getprop ro.product.model"
  exit 1
fi
if [ -x /system/bin/sh ]; then
  /system/bin/sh -c "${'$'}*"
else
  echo "/system/bin/sh 不可用。请确认已重新进入 Ubuntu，并且 AIDev 已挂载 /system。"
  exit 1
fi
AIDEV_ANDROID_SH_EOF
            chmod 755 "${'$'}AIDEV_ROOTFS/usr/local/bin/android-sh"

            cat > "${'$'}AIDEV_ROOTFS/usr/local/bin/pmx" <<'AIDEV_PMX_EOF'
#!/bin/bash
android-sh pm "${'$'}@"
AIDEV_PMX_EOF
            chmod 755 "${'$'}AIDEV_ROOTFS/usr/local/bin/pmx"

            cat > "${'$'}AIDEV_ROOTFS/usr/local/bin/amx" <<'AIDEV_AMX_EOF'
#!/bin/bash
android-sh am "${'$'}@"
AIDEV_AMX_EOF
            chmod 755 "${'$'}AIDEV_ROOTFS/usr/local/bin/amx"

            cat > "${'$'}AIDEV_ROOTFS/usr/local/bin/getpropx" <<'AIDEV_GETPROPX_EOF'
#!/bin/bash
android-sh getprop "${'$'}@"
AIDEV_GETPROPX_EOF
            chmod 755 "${'$'}AIDEV_ROOTFS/usr/local/bin/getpropx"

            cat > "${'$'}AIDEV_ROOTFS/usr/local/bin/logcatx" <<'AIDEV_LOGCATX_EOF'
#!/bin/bash
android-sh logcat "${'$'}@"
AIDEV_LOGCATX_EOF
            chmod 755 "${'$'}AIDEV_ROOTFS/usr/local/bin/logcatx"

            cat > "${'$'}AIDEV_ROOTFS/usr/local/bin/keepalive-explain" <<'AIDEV_KEEPALIVE_EXPLAIN_EOF'
#!/bin/bash
cat <<'EOF'
AIDev 后台常驻说明

目标：
  把 AIDev Terminal 当作移动端 Linux 服务器使用，尽可能保证下载、编译、OpenCode、Web 前端、本机 API 服务在息屏和切后台后继续运行。

AIDev 已实现：
  1. Android 前台服务：显示常驻通知，降低被系统回收概率。
  2. CPU Partial WakeLock：尽量防止息屏后 CPU 休眠导致任务中断。
  3. Wi-Fi High Performance Lock：尽量保持 Wi-Fi 网络链路稳定。
  4. 开机/应用更新后可恢复常驻服务。
  5. 设置中心提供电池优化、应用详情、小米/HyperOS 后台设置入口。

仍需用户手动设置：
  小米 14 / HyperOS 建议：
  - 电池：无限制
  - 省电策略：无限制
  - 后台运行：允许
  - 自启动：允许
  - 锁定最近任务卡片
  - 关闭“暂停未使用应用活动”
  - 通知权限：允许，避免前台服务通知被隐藏或降级

重要限制：
  Android 厂商系统仍可能在极端省电、内存压力、系统清理、用户一键清理时杀进程。
  要尽量稳定，请保持 AIDev 的常驻通知存在，并完成系统后台权限设置。
EOF
AIDEV_KEEPALIVE_EXPLAIN_EOF
            chmod 755 "${'$'}AIDEV_ROOTFS/usr/local/bin/keepalive-explain"

            cat > "${'$'}AIDEV_ROOTFS/usr/local/bin/check-keepalive" <<'AIDEV_KEEPALIVE_CHECK_EOF'
#!/bin/bash
set +e
echo "AIDev 后台常驻检测"
echo
echo "== Android 设备 =="
if command -v getprop >/dev/null 2>&1; then
  echo "model=${'$'}(getprop ro.product.model 2>/dev/null)"
  echo "brand=${'$'}(getprop ro.product.brand 2>/dev/null)"
  echo "manufacturer=${'$'}(getprop ro.product.manufacturer 2>/dev/null)"
  echo "android=${'$'}(getprop ro.build.version.release 2>/dev/null)"
  echo "hyperos=${'$'}(getprop ro.mi.os.version.name 2>/dev/null)"
else
  echo "getprop 不可用"
fi
echo
echo "== 进程/任务 =="
echo "当前 shell PID: $$"
echo "后台任务目录：/aidev/tasks"
task-list 2>/dev/null || echo "task-list 不可用"
echo
echo "== 网络监听 =="
list-listen-ports 2>/dev/null | sed -n '1,40p' || echo "list-listen-ports 不可用"
echo
echo "== 建议 =="
echo "如果长任务切后台中断，请在设置中心打开“后台常驻”，依次启动常驻服务、忽略电池优化、打开 HyperOS 后台设置并设为无限制/允许自启动。"
AIDEV_KEEPALIVE_CHECK_EOF
            chmod 755 "${'$'}AIDEV_ROOTFS/usr/local/bin/check-keepalive"

            cat > "${'$'}AIDEV_ROOTFS/usr/local/bin/aidev-net-explain" <<'AIDEV_NET_EXPLAIN_EOF'
#!/bin/bash
cat <<'EOF'
AIDev AI/Web 服务通信说明

典型场景：
  在 Ubuntu 内启动 OpenCode 或其他 AI 助手服务，再启动 Web 前端服务，然后用手机浏览器访问 http://127.0.0.1:<port>。

常见卡住原因：
  1. 服务只绑定到 127.0.0.1，其他设备无法访问；同一手机浏览器通常可以访问。
  2. 服务绑定到 0.0.0.0，局域网其他设备可通过手机 IP 访问，但受系统网络/防火墙/热点隔离影响。
  3. 前端页面加载了 WebSocket，但 WebSocket 地址仍指向错误 host/port。
  4. 服务启动在后台后被系统省电策略杀掉。
  5. OpenCode 前端和后端端口不同，只打开了前端端口但后端 API 不通。
  6. 页面依赖外部 CDN，DNS 或 HTTPS 不稳定导致页面资源卡住。

建议检查：
  list-listen-ports
  check-local-server 3000
  check-local-server http://127.0.0.1:3000

访问建议：
  同一手机浏览器： http://127.0.0.1:<port>
  局域网其他设备： http://<手机局域网IP>:<port>，服务必须绑定 0.0.0.0

启动服务建议：
  优先让服务监听 0.0.0.0，例如：
  some-server --host 0.0.0.0 --port 3000

说明：
  AIDev 的 Ubuntu/PRoot 与 Android 应用共享同一 Android 网络栈，通常可以被同机浏览器访问。
  是否能被局域网其他设备访问，取决于服务绑定地址、手机网络、热点隔离和系统限制。
EOF
AIDEV_NET_EXPLAIN_EOF
            chmod 755 "${'$'}AIDEV_ROOTFS/usr/local/bin/aidev-net-explain"

            cat > "${'$'}AIDEV_ROOTFS/usr/local/bin/list-listen-ports" <<'AIDEV_PORTS_EOF'
#!/bin/bash
set +e
echo "AIDev 监听端口检测"
echo
if command -v ss >/dev/null 2>&1; then
  ss -ltnp 2>/dev/null || ss -ltn
  exit 0
fi
if command -v netstat >/dev/null 2>&1; then
  netstat -ltnp 2>/dev/null || netstat -ltn
  exit 0
fi
python3 - <<'PY' 2>/dev/null || {
  echo "缺少 ss/netstat/python3，无法解析监听端口。"
  exit 1
}
import socket
def ip4(hex_ip):
    raw=bytes.fromhex(hex_ip)
    return socket.inet_ntoa(raw[::-1])
def parse(path, v6=False):
    try:
        lines=open(path).read().splitlines()[1:]
    except Exception:
        return
    for line in lines:
        parts=line.split()
        local=parts[1]
        state=parts[3]
        if state!='0A':
            continue
        host,port_hex=local.split(':')
        port=int(port_hex,16)
        if v6:
            ip='::' if host=='0'*32 else host
        else:
            ip=ip4(host)
        print(f"LISTEN {ip}:{port}")
parse('/proc/net/tcp')
parse('/proc/net/tcp6', True)
PY
AIDEV_PORTS_EOF
            chmod 755 "${'$'}AIDEV_ROOTFS/usr/local/bin/list-listen-ports"

            cat > "${'$'}AIDEV_ROOTFS/usr/local/bin/check-local-server" <<'AIDEV_SERVER_CHECK_EOF'
#!/bin/bash
set +e
target="${'$'}1"
if [ -z "${'$'}target" ]; then
  echo "usage: check-local-server <port|url>"
  echo "example: check-local-server 3000"
  echo "example: check-local-server http://127.0.0.1:3000"
  exit 1
fi
case "${'$'}target" in
  http://*|https://*) url="${'$'}target" ;;
  *) url="http://127.0.0.1:${'$'}target" ;;
esac
echo "检测 URL：${'$'}url"
if command -v curl >/dev/null 2>&1; then
  curl -fsSIL --connect-timeout 5 --max-time 12 "${'$'}url" | sed -n '1,12p'
  code="${'$'}?"
  if [ "${'$'}code" -eq 0 ]; then
    echo "本机 HTTP 可访问。"
  else
    echo "本机 HTTP 访问失败。请确认服务已启动、端口正确、监听地址不是仅限其他接口。"
  fi
else
  echo "curl 不存在，无法发起 HTTP 检测。"
fi
echo
echo "当前监听端口："
list-listen-ports | sed -n '1,40p'
echo
echo "提示：同机浏览器优先访问 http://127.0.0.1:<port>；局域网访问需要服务监听 0.0.0.0。"
AIDEV_SERVER_CHECK_EOF
            chmod 755 "${'$'}AIDEV_ROOTFS/usr/local/bin/check-local-server"

            cat > "${'$'}AIDEV_ROOTFS/etc/profile.d/aidev.sh" <<'AIDEV_PROFILE_EOF'
export TERM=xterm-256color
export COLORTERM=truecolor
export PATH="${'$'}HOME/.opencode/bin:${'$'}PATH"
alias ll='ls -lah --color=auto'
AIDEV_PROFILE_EOF

            cat > "${'$'}AIDEV_ROOTFS/root/.bash_profile" <<'AIDEV_BASHPROFILE_EOF'
if [ -f ~/.bashrc ]; then
  . ~/.bashrc
fi
AIDEV_BASHPROFILE_EOF

            if ! grep -q 'AIDev Terminal' "${'$'}AIDEV_ROOTFS/root/.bashrc" 2>/dev/null; then
                cat >> "${'$'}AIDEV_ROOTFS/root/.bashrc" <<'AIDEV_BASHRC_EOF'

# AIDev Terminal
export TERM=xterm-256color
export COLORTERM=truecolor
alias ll='ls -lah --color=auto'
export PATH="${'$'}HOME/.opencode/bin:${'$'}PATH"
echo "AIDev 提示：输入 env-menu 查看环境检测与配置菜单。"
AIDEV_BASHRC_EOF
            fi
        }

        _aidev_enter_ubuntu() {
            _aidev_prepare_ubuntu
            unset LD_PRELOAD
            "${'$'}AIDEV_PROOT" \
                --kill-on-exit \
                --link2symlink \
                -0 \
                -r "${'$'}AIDEV_ROOTFS" \
                -b /dev \
                -b /proc \
                -b /sys \
                -b /dev/urandom:/dev/random \
                -b /proc/self/fd:/dev/fd \
                -b /proc/self/fd/0:/dev/stdin \
                -b /proc/self/fd/1:/dev/stdout \
                -b /proc/self/fd/2:/dev/stderr \
                -b /sdcard \
                -b /system \
                -b "${'$'}AIDEV_HOME:/aidev" \
                -w /root \
                /usr/bin/env -i \
                    HOME=/root USER=root LOGNAME=root \
                    TERM=xterm-256color COLORTERM=truecolor \
                    LANG=C.UTF-8 LC_ALL=C.UTF-8 \
                    PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/system/bin:/system/xbin:/aidev \
                    /bin/bash --login
        }

        _aidev_ubuntu_exec() {
            _aidev_prepare_ubuntu
            unset LD_PRELOAD
            "${'$'}AIDEV_PROOT" \
                --kill-on-exit \
                --link2symlink \
                -0 \
                -r "${'$'}AIDEV_ROOTFS" \
                -b /dev \
                -b /proc \
                -b /sys \
                -b /dev/urandom:/dev/random \
                -b /proc/self/fd:/dev/fd \
                -b /proc/self/fd/0:/dev/stdin \
                -b /proc/self/fd/1:/dev/stdout \
                -b /proc/self/fd/2:/dev/stderr \
                -b /sdcard \
                -b /system \
                -b "${'$'}AIDEV_HOME:/aidev" \
                -w /root \
                /usr/bin/env -i \
                    HOME=/root USER=root LOGNAME=root \
                    TERM=xterm-256color COLORTERM=truecolor \
                    LANG=C.UTF-8 LC_ALL=C.UTF-8 \
                    DEBIAN_FRONTEND=noninteractive \
                    PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/system/bin:/system/xbin:/aidev \
                    /bin/bash -lc "${'$'}*"
        }

        ubuntu() {
            if _aidev_has_ubuntu; then
                _aidev_enter_ubuntu
            else
                echo "ubuntu 尚未安装。运行：install-ubuntu"
                return 1
            fi
        }

        # 默认从清华源拉取 Ubuntu Base 24.04 arm64，体积约 28MB；解包后约 80MB。
        AIDEV_UBUNTU_URL="${'$'}{AIDEV_UBUNTU_URL:-https://mirrors.tuna.tsinghua.edu.cn/ubuntu-cdimage/ubuntu-base/releases/24.04.4/release/ubuntu-base-24.04.4-base-arm64.tar.gz}"
        AIDEV_UBUNTU_SHA256="${'$'}{AIDEV_UBUNTU_SHA256:-}"

        aidev_fast_download() {
            url="${'$'}1"
            out="${'$'}2"
            threads="${'$'}{3:-8}"
            [ -z "${'$'}url" ] || [ -z "${'$'}out" ] && {
                echo "usage: fast-download <url> <output> [threads]"
                return 1
            }
            out_dir=${'$'}(dirname "${'$'}out")
            out_name=${'$'}(basename "${'$'}out")
            part="${'$'}out.part"
            mkdir -p "${'$'}out_dir"

            echo "下载地址：${'$'}url"
            echo "保存位置：${'$'}out"

            if command -v aria2c >/dev/null 2>&1; then
                echo "下载器：aria2c，多连接 ${'$'}threads 线程，支持断点续传；已隐藏详细进度"
                aria2c -c -x "${'$'}threads" -s "${'$'}threads" -k 1M \
                    --console-log-level=warn \
                    --summary-interval=0 \
                    --allow-overwrite=true \
                    --auto-file-renaming=false \
                    -d "${'$'}out_dir" -o "${'$'}out_name" "${'$'}url"
                [ -s "${'$'}out" ] && echo "下载完成：${'$'}(ls -lh "${'$'}out" | awk '{print ${'$'}5}')"
                return ${'$'}?
            fi

            if command -v curl >/dev/null 2>&1; then
                echo "下载器：curl，支持断点续传；已隐藏详细进度"
                curl -fsSL --retry 3 --connect-timeout 10 -C - \
                    -o "${'$'}part" "${'$'}url" || return ${'$'}?
                [ -s "${'$'}part" ] && mv "${'$'}part" "${'$'}out"
                [ -s "${'$'}out" ] && echo "下载完成：${'$'}(ls -lh "${'$'}out" | awk '{print ${'$'}5}')"
                return 0
            fi

            if command -v wget >/dev/null 2>&1; then
                echo "下载器：wget，支持断点续传；已隐藏详细进度"
                wget -q -c -O "${'$'}part" "${'$'}url" || return ${'$'}?
                [ -s "${'$'}part" ] && mv "${'$'}part" "${'$'}out"
                [ -s "${'$'}out" ] && echo "下载完成：${'$'}(ls -lh "${'$'}out" | awk '{print ${'$'}5}')"
                return 0
            fi

            if /system/bin/toybox wget --help >/dev/null 2>&1; then
                echo "下载器：toybox wget，不支持多线程，作为兜底使用；已隐藏详细进度"
                /system/bin/toybox wget -O "${'$'}part" "${'$'}url" || return ${'$'}?
                [ -s "${'$'}part" ] && mv "${'$'}part" "${'$'}out"
                [ -s "${'$'}out" ] && echo "下载完成：${'$'}(ls -lh "${'$'}out" | awk '{print ${'$'}5}')"
                return 0
            fi

            echo "未找到可用下载器：aria2c/curl/wget/toybox wget"
            return 1
        }

        aidev_install_ubuntu() {
            fast=0
            retry=0
            clean=0
            while [ "${'$'}#" -gt 0 ]; do
                case "${'$'}1" in
                    --fast) fast=1 ;;
                    --retry) retry=1 ;;
                    --clean) clean=1 ;;
                    --help|-h)
                        cat <<'EOF'
usage: install-ubuntu [--fast] [--retry] [--clean]
  --fast   优先使用 aria2c 多线程下载；没有 aria2c 时自动降级为断点续传
  --retry  保留已下载 rootfs 包，清理失败的 ubuntu-rootfs 后重新解包
  --clean  删除 ubuntu-rootfs 和下载缓存后退出
EOF
                        return 0
                        ;;
                    *) echo "未知参数：${'$'}1"; return 1 ;;
                esac
                shift
            done

            abi=${'$'}(getprop ro.product.cpu.abi 2>/dev/null)
            case "${'$'}abi" in
                arm64-v8a|aarch64) : ;;
                *) echo "暂只支持 arm64-v8a，当前 abi=${'$'}abi"; return 1 ;;
            esac
            if [ ! -x "${'$'}AIDEV_PROOT" ]; then
                echo "proot 未随 APK 解出（${'$'}AIDEV_PROOT 不存在），请重新安装应用。"
                return 1
            fi
            mkdir -p "${'$'}AIDEV_ROOTFS" "${'$'}AIDEV_HOME/dev-env/tmp"
            tar_file="${'$'}AIDEV_HOME/dev-env/tmp/ubuntu-base.tar.gz"
            tar_log="${'$'}AIDEV_HOME/dev-env/tmp/tar-install.log"

            if [ "${'$'}clean" -eq 1 ]; then
                case "${'$'}AIDEV_ROOTFS" in
                    */ubuntu-rootfs) rm -rf "${'$'}AIDEV_ROOTFS" ;;
                    *) echo "rootfs 路径异常：${'$'}AIDEV_ROOTFS"; return 1 ;;
                esac
                rm -f "${'$'}tar_file" "${'$'}tar_file.part" "${'$'}tar_log"
                echo "已清理 Ubuntu rootfs 和下载缓存。"
                return 0
            fi

            if [ "${'$'}retry" -eq 1 ]; then
                case "${'$'}AIDEV_ROOTFS" in
                    */ubuntu-rootfs) rm -rf "${'$'}AIDEV_ROOTFS" && mkdir -p "${'$'}AIDEV_ROOTFS" ;;
                    *) echo "rootfs 路径异常：${'$'}AIDEV_ROOTFS"; return 1 ;;
                esac
                echo "已清理 rootfs，保留下载缓存，开始重试。"
            fi

            if _aidev_has_ubuntu && [ "${'$'}retry" -eq 0 ]; then
                echo "ubuntu 已安装，直接执行 ubuntu 进入。"
                return 0
            fi

            start_ts=${'$'}(date +%s 2>/dev/null || echo 0)

            if [ ! -s "${'$'}tar_file" ]; then
                if [ "${'$'}fast" -eq 1 ]; then
                    echo "[1/4] 快速下载 Ubuntu Base 24.04（约 28MB）..."
                else
                    echo "[1/4] 下载 Ubuntu Base 24.04（约 28MB）..."
                fi
                if ! aidev_fast_download "${'$'}AIDEV_UBUNTU_URL" "${'$'}tar_file" 8; then
                    echo "Android 系统未提供 curl/wget。"
                    echo "请用浏览器或电脑下载："
                    echo "  ${'$'}AIDEV_UBUNTU_URL"
                    echo "保存为："
                    echo "  ${'$'}tar_file"
                    echo "之后再次运行：install-ubuntu"
                    return 1
                fi
            else
                echo "[1/4] 已发现下载缓存，跳过下载：${'$'}tar_file"
            fi

            if [ -n "${'$'}AIDEV_UBUNTU_SHA256" ] && command -v sha256sum >/dev/null 2>&1; then
                echo "[2/4] 校验 sha256..."
                got=${'$'}(sha256sum "${'$'}tar_file" | awk '{print ${'$'}1}')
                if [ "${'$'}got" != "${'$'}AIDEV_UBUNTU_SHA256" ]; then
                    echo "sha256 校验失败：got=${'$'}got expect=${'$'}AIDEV_UBUNTU_SHA256"
                    return 1
                fi
            else
                echo "[2/4] 未配置 sha256，跳过校验。"
            fi

            echo "[3/4] 解包 rootfs 到 ${'$'}AIDEV_ROOTFS ..."
            echo "      这一步主要受手机存储速度影响，通常不能靠多线程明显加速。"
            case "${'$'}AIDEV_ROOTFS" in
                */ubuntu-rootfs) rm -rf "${'$'}AIDEV_ROOTFS" && mkdir -p "${'$'}AIDEV_ROOTFS" ;;
                *) echo "rootfs 路径异常：${'$'}AIDEV_ROOTFS"; return 1 ;;
            esac
            tar_opts=""
            if tar --no-same-owner --no-same-permissions -tzf "${'$'}tar_file" >/dev/null 2>&1; then
                tar_opts="--no-same-owner --no-same-permissions"
            fi
            tar_log="${'$'}AIDEV_HOME/dev-env/tmp/tar-install.log"
            ( cd "${'$'}AIDEV_ROOTFS" && tar ${'$'}tar_opts -xzf "${'$'}tar_file" ) 2>"${'$'}tar_log"
            tar_rc=${'$'}?

            # Android 私有目录通常禁止 hardlink。Ubuntu rootfs 里少量 hardlink 失败时，
            # 用 symlink 补齐；proot 运行时已使用 --link2symlink，容器内可正常工作。
            mkdir -p "${'$'}AIDEV_ROOTFS/usr/bin"
            [ -e "${'$'}AIDEV_ROOTFS/usr/bin/perl" ] || [ ! -e "${'$'}AIDEV_ROOTFS/usr/bin/perl5.38.2" ] || \
                ln -sf perl5.38.2 "${'$'}AIDEV_ROOTFS/usr/bin/perl"
            [ -e "${'$'}AIDEV_ROOTFS/usr/bin/uncompress" ] || [ ! -e "${'$'}AIDEV_ROOTFS/usr/bin/gunzip" ] || \
                ln -sf gunzip "${'$'}AIDEV_ROOTFS/usr/bin/uncompress"

            if [ "${'$'}tar_rc" -ne 0 ]; then
                if [ -f "${'$'}AIDEV_ROOTFS/etc/os-release" ] && [ -x "${'$'}AIDEV_ROOTFS/bin/bash" ]; then
                    echo "解包遇到 Android hardlink 限制，已转为 symlink 继续。"
                else
                    echo "解包失败"
                    [ -s "${'$'}tar_log" ] && tail -20 "${'$'}tar_log"
                    return 1
                fi
            fi

            echo "[4/4] 初始化 apt 源和容器脚本..."
            mkdir -p "${'$'}AIDEV_ROOTFS/etc/apt"
            cat > "${'$'}AIDEV_ROOTFS/etc/apt/sources.list" <<'AIDEV_APT_EOF'
deb http://mirrors.tuna.tsinghua.edu.cn/ubuntu-ports/ noble main universe multiverse restricted
deb http://mirrors.tuna.tsinghua.edu.cn/ubuntu-ports/ noble-updates main universe multiverse restricted
deb http://mirrors.tuna.tsinghua.edu.cn/ubuntu-ports/ noble-security main universe multiverse restricted
AIDEV_APT_EOF

            end_ts=${'$'}(date +%s 2>/dev/null || echo 0)
            if [ "${'$'}start_ts" -gt 0 ] && [ "${'$'}end_ts" -gt 0 ]; then
                echo "耗时：${'$'}((end_ts - start_ts)) 秒"
            fi
            echo "ubuntu 安装完成。运行：ubuntu"
        }

        aidev_deploy_dev_env() {
            if _aidev_has_ubuntu; then
                _aidev_ubuntu_exec "deploy-dev-env"
            else
                echo "ubuntu 尚未安装。请先运行：install-ubuntu"
                return 1
            fi
        }

        aidev_install_aitool() {
            if _aidev_has_ubuntu; then
                _aidev_ubuntu_exec "install-aitool"
            else
                echo "ubuntu 尚未安装。请先运行：install-ubuntu"
                return 1
            fi
        }

        aidev_ubuntu_run() {
            if _aidev_has_ubuntu; then
                [ "${'$'}#" -eq 0 ] && { echo "usage: ubuntu-run <command...>"; return 1; }
                _aidev_ubuntu_exec "${'$'}*"
            else
                echo "ubuntu 尚未安装。请先运行：install-ubuntu"
                return 1
            fi
        }

        alias aidev-version=aidev_version
        alias install-ubuntu=aidev_install_ubuntu
        alias deploy-dev-env=aidev_deploy_dev_env
        alias install-aitool=aidev_install_aitool
        alias fast-download=aidev_fast_download
        alias ubuntu-run=aidev_ubuntu_run
        alias task-run=aidev_task_run
        alias task-list=aidev_task_list
        alias task-log=aidev_task_log
        alias task-tail=aidev_task_tail
        alias task-stop=aidev_task_stop

        sysnotify() {
            t="${'$'}1"; shift; m="${'$'}*"
            am broadcast -p com.aidev.terminal -a com.aidev.terminal.SYSNOTIFY \
                --es title "${'$'}t" --es msg "${'$'}m" >/dev/null
        }
        sysclip()   { am broadcast -p com.aidev.terminal -a com.aidev.terminal.SYSCLIP --es text "${'$'}*" >/dev/null; }
        sysinfo()   {
            printf '{"manufacturer":"%s","model":"%s","sdk":"%s","release":"%s","abi":"%s","serial":"%s"}\n' \
                "${'$'}(getprop ro.product.manufacturer)" "${'$'}(getprop ro.product.model)" \
                "${'$'}(getprop ro.build.version.sdk)" "${'$'}(getprop ro.build.version.release)" \
                "${'$'}(getprop ro.product.cpu.abi)" "${'$'}(getprop ro.serialno)"
        }
        screencap() { o="${'$'}{1:-${'$'}AIDEV_HOME/screenshot.png}"; /system/bin/screencap -p "${'$'}o"; echo "${'$'}o"; }
        keyevent()  { input keyevent "${'$'}1"; }
        pkglist()   { pm list packages "${'$'}@"; }
        startapp()  { monkey -p "${'$'}1" 1 >/dev/null; }
        stopapp()   { am force-stop "${'$'}1"; }
        installapk(){ pm install -r "${'$'}1"; }
        uninstallapp(){ pm uninstall "${'$'}1"; }

        help() {
            cat <<EOF
ubuntu          enter ubuntu root (needs install-ubuntu)
ubuntu-run <cmd> 在 Ubuntu 容器内非交互执行命令
install-ubuntu [--fast|--retry|--clean]  安装/重试/清理 Ubuntu rootfs
fast-download <url> <output> [threads]  多线程/断点续传下载
deploy-dev-env  安装 curl/git/node/npm/jdk 等基础开发工具
install-aitool  安装 opencode AI 编程工具
task-run <name> <cmd> | task-list | task-log <id> | task-tail <id> | task-stop <id>
hl <file>       关键字高亮显示文件（sh/py/js/ts/kt/java/c/cpp/go/rs）
sysnotify <t> <m> | sysclip <t> | sysinfo
screencap [out] | keyevent <code>
pkglist | startapp <pkg> | stopapp <pkg>
installapk <apk> | uninstallapp <pkg>
提示：双指捏合可平滑缩放字号；ls/grep/diff 已默认彩色。
EOF
        }
    """.trimIndent() + "\n"

    // ---------- Shizuku & permissions ----------

    private fun checkShizuku() {
        try {
            val version = Shizuku.getVersion()
            val granted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            if (!granted) Shizuku.requestPermission(1001)
            sendCommand("echo 'shizuku v$version granted=$granted'")
        } catch (e: Throwable) {
            val msg = e.message ?: "unknown"
            sendCommand("echo 'shizuku unavailable: $msg'")
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2001)
        }
    }

    private fun streamHaptic() {
        if (!prefs.getBoolean("haptic_stream", false)) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastStreamHapticAt < 850) return
        lastStreamHapticAt = now
        terminalView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }
}
