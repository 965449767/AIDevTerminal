package com.aidev.terminal
import com.google.android.material.dialog.MaterialAlertDialogBuilder

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.io.File
import kotlin.math.abs

/**
 * 全局 App Shell：
 * 1. 底部导航和背景层是持久化的，不会随页面切换重建。
 * 2. 中间内容区由 ShellPage 提供 View，切换只替换内容区。
 * 3. 左右滑动手势由 Shell 集中处理，所有顶级页面共用同一动画方向。
 */
class ShellActivity : Activity() {
    private lateinit var prefs: PreferencesManager
    private lateinit var ui: AIDevUi
    private lateinit var contentHost: FrameLayout
    private lateinit var bottomNavView: View
    private lateinit var navHost: LinearLayout
    private val bottomNavItems = mutableListOf<TextView>()
    private val pages: List<ShellPage> by lazy {
        listOf(
            EmbeddedTerminalPage(),
            EmbeddedFilesPage(),
            EmbeddedSettingsPage(),
            KnowledgeBasePage()
        )
    }
    private val pageViews = mutableMapOf<Int, View>()
    private var currentIndex: Int = 0
    private var backPressedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        prefs = PreferencesManager(this)
        ui = AIDevUi(this, prefs.sharedPreferences)
        requestEssentialPermissions()
        ensureNotificationChannel()
        runCatching { KeepAliveService.start(this) }
            .onFailure { Log.e("ShellActivity", "KeepAliveService start failed", it) }
        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT
            ) {
                if (currentIndex in pages.indices) {
                    if (pages[currentIndex].onBackPressed()) return@registerOnBackInvokedCallback
                }
                handleBack()
            }
        }
        buildShell()
        val requestedTab = intent?.getIntExtra("shell_tab", -1) ?: -1
        val shouldAutoBootstrapUbuntu = shouldAutoBootstrapUbuntu(requestedTab)
        if (shouldAutoBootstrapUbuntu) {
            TerminalCommandBus.post("aidev-auto-bootstrap")
        }
        val initial = if (shouldAutoBootstrapUbuntu) TAB_TERMINAL else requestedTab.takeIf { it in pages.indices } ?: TAB_TERMINAL
        switchTo(initial, animateForward = null, force = true)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        refreshShellSkin()
        renderCurrent(currentIndex, animateForward = null)
    }

    override fun onDestroy() {
        super.onDestroy()
        pages.forEach { it.onDestroy(this) }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (currentIndex in pages.indices) {
            if (pages[currentIndex].onBackPressed()) return
        }
        handleBack()
    }

    private fun handleBack() {
        val now = SystemClock.uptimeMillis()
        if (now - backPressedAt < 2000) {
            moveTaskToBack(true)
        } else {
            backPressedAt = now
            ui.pulse()
            Toast.makeText(this, "再按一次退出应用", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shouldAutoBootstrapUbuntu(requestedTab: Int): Boolean {
        if (requestedTab in pages.indices && requestedTab != TAB_TERMINAL) return false
        return true
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        val tab = intent?.getIntExtra("shell_tab", -1) ?: -1
        if (tab in pages.indices) {
            switchTo(tab, animateForward = null, force = true)
        } else {
            // 保留用户当前页面，仅在终端页面时触发 bootstrap
            if (currentIndex == TAB_TERMINAL) {
                TerminalCommandBus.post("aidev-auto-bootstrap")
                renderCurrent(currentIndex, animateForward = null)
            }
        }
    }

    @Deprecated("保留传统回调以兼容当前无 Compose/ActivityResult 的原生 View 架构")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_BACKGROUND_IMAGE || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        runCatching {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        prefs.bgMode = "image"
        prefs.bgImageUri = uri.toString()
        refreshShellSkin()
    }

    override fun onResume() {
        super.onResume()
        // 主题或密度变更后重建当前页内容，但不会重建底部导航和背景。
        ui = AIDevUi(this, prefs.sharedPreferences)
        applyShellSkin()
        rebuildBottomNav()
        renderCurrent(currentIndex, animateForward = null)
    }

    fun refreshShellSkin() {
        ui = AIDevUi(this, prefs.sharedPreferences)
        applyShellSkin()
        rebuildBottomNav()
    }

    fun pickBackgroundImage() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, REQ_BACKGROUND_IMAGE)
    }

    fun showCommandPalette() {
        val items = arrayOf(
            "进入 Ubuntu",
            "环境诊断",
            "监听端口",
            "安装 OpenCode",
            "选择背景图片",
            "当前项目",
            "当前项目 Git",
            "当前项目测试",
            "当前项目构建",
            "清除当前项目",
            "打开任务日志",
            "当前项目诊断",
            "当前项目日志",
            "当前项目修复",
            "AI 代理上下文",
            "AI 上下文文件",
            "AI 日志摘要",
            "AI 代理日志"
        )
        MaterialAlertDialogBuilder(this)
            .setTitle("命令面板")
            .setItems(items) { _, which ->
                handleCommandPalette(which)
            }
            .setPositiveButton("最近") { _, _ -> showRecentProjectActions() }
            .setNeutralButton("搜索") { _, _ -> showCommandPaletteSearch(items) }
            .show()
    }

    private fun showRecentProjectActions() {
        val rows = prefs.projectActionHistory.lines().filter { it.isNotBlank() }.takeLast(20).reversed()
        if (rows.isEmpty()) {
            MaterialAlertDialogBuilder(this)
                .setTitle("最近命令")
                .setMessage("暂无最近项目操作。")
                .setPositiveButton("关闭", null)
                .show()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("最近命令")
            .setItems(rows.map { row ->
                val parts = row.split("\t", limit = 4)
                "${parts.getOrNull(1) ?: "命令"}\n${parts.getOrNull(3) ?: row}"
            }.toTypedArray()) { _, which ->
                val parts = rows[which].split("\t", limit = 4)
                val path = parts.getOrNull(2).orEmpty()
                val command = parts.getOrNull(3).orEmpty()
                if (path.isNotBlank() && command.isNotBlank()) openTerminalCommand("cd \"$path\" && $command")
            }
            .setNegativeButton("清空") { _, _ -> prefs.projectActionHistory = "" }
            .show()
    }

    private fun showCommandPaletteSearch(items: Array<String>) {
        val edit = EditText(this).apply { hint = "输入 git、test、build、fix、log、ubuntu" }
        MaterialAlertDialogBuilder(this)
            .setTitle("搜索命令")
            .setView(edit)
            .setPositiveButton("搜索") { _, _ ->
                val keyword = edit.text.toString().trim()
                val matches = items.mapIndexed { index, label -> index to label }
                    .filter { keyword.isBlank() || commandMatches(it.second, keyword) }
                    .take(30)
                if (matches.isEmpty()) return@setPositiveButton
                MaterialAlertDialogBuilder(this)
                    .setTitle("命令结果")
                    .setItems(matches.map { it.second }.toTypedArray()) { _, which ->
                        handleCommandPalette(matches[which].first)
                    }
                    .show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun commandMatches(label: String, keyword: String): Boolean {
        if (label.contains(keyword, ignoreCase = true)) return true
        val lower = keyword.lowercase()
        return when (lower) {
            "git" -> label.contains("Git", true)
            "test", "pytest" -> label.contains("测试")
            "build", "gradle" -> label.contains("构建") || label.contains("Gradle", true)
            "fix", "repair" -> label.contains("修复")
            "log", "logs", "logcat" -> label.contains("日志") || label.contains("Logcat", true)
            "ubuntu", "linux" -> label.contains("Ubuntu", true)
            "ai", "agent", "opencode" -> label.contains("AI", true) || label.contains("OpenCode", true)
            "task" -> label.contains("任务")
            "file" -> label.contains("文件")
            "setting" -> label.contains("设置")
            else -> false
        }
    }

    private fun handleCommandPalette(which: Int) {
        when (which) {
            0 -> openTerminalCommand("ubuntu")
            1 -> openTerminalCommand("check-dev-env")
            2 -> openTerminalCommand("list-listen-ports")
            3 -> openTerminalCommand("opencode-check")
            4 -> pickBackgroundImage()
            5 -> openCurrentProject("pwd && ls -la")
            6 -> openCurrentProject("git status --short --branch")
            7 -> openCurrentProject(ProjectCommands.testCommand(currentProjectDir()))
            8 -> openCurrentProject(ProjectCommands.buildCommand(currentProjectDir()))
            9 -> {
                prefs.currentProjectPath = ""
                switchTo(TAB_FILES)
            }
            10 -> openTerminalCommand("ls -lah \"${filesDir.absolutePath}/home/tasks\"")
            11 -> openCurrentProject(ProjectCommands.healthCommand(currentProjectDir()))
            12 -> openTerminalCommand("ls -lt \"${filesDir.absolutePath}/home/tasks\"/*.log 2>/dev/null | head -20")
            13 -> confirmCurrentProjectRepair()
            14 -> openCurrentProject("aidev-agent-context")
            15 -> openCurrentProject("aidev-agent-context-file")
            16 -> openTerminalCommand("aidev-agent-summary")
            17 -> openTerminalCommand("aidev-agent-log")
        }
    }

    private fun openTerminalCommand(command: String) {
        TerminalCommandBus.post(command)
        switchTo(TAB_TERMINAL)
    }

    private fun confirmCurrentProjectRepair() {
        val dir = currentProjectDir()
        if (dir == null) {
            switchTo(TAB_FILES)
            return
        }
        val command = ProjectCommands.repairCommand(dir)
        MaterialAlertDialogBuilder(this)
            .setTitle("确认修复当前项目")
            .setMessage("将进入：${dir.absolutePath}\n\n执行：\n$command\n\n注意：某些修复会删除缓存、依赖目录或锁文件，请确认当前项目不需要保留这些中间文件。")
            .setPositiveButton("确认执行") { _, _ ->
                rememberProjectAction("修复", dir, command)
                openCurrentProject(command)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun currentProjectDir(): File? =
        prefs.currentProjectPath.takeIf { it.isNotBlank() }?.let { File(it) }?.takeIf { it.isDirectory }

    private fun openCurrentProject(command: String?) {
        val dir = currentProjectDir()
        if (dir == null) {
            switchTo(TAB_FILES)
            return
        }
        TerminalCommandBus.post("cd \"${dir.absolutePath}\" && ${command ?: "pwd && ls -la"}")
        switchTo(TAB_TERMINAL)
    }

    private fun rememberProjectAction(label: String, dir: File, command: String) {
        val line = "${System.currentTimeMillis()}\t$label\t${dir.absolutePath}\t$command"
        val old = prefs.projectActionHistory
        val next = (old.lines().filter { it.isNotBlank() } + line).takeLast(20).joinToString("\n")
        prefs.projectActionHistory = next
    }

    private fun buildShell() {
        navHost = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        contentHost = FrameLayout(this)
        navHost.addView(contentHost, LinearLayout.LayoutParams(-1, 0, 1f))
        rebuildBottomNav()
        applyShellSkin()
        setContentView(navHost)
        ViewCompat.setOnApplyWindowInsetsListener(navHost) { view, windowInsets ->
            val sysBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(sysBars.left, sysBars.top, sysBars.right, sysBars.bottom)
            val imeHeight = windowInsets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val bottomNavH = bottomNavView.height
            val extra = imeHeight.coerceAtLeast(sysBars.bottom + bottomNavH) - sysBars.bottom - bottomNavH
            contentHost.setPadding(0, 0, 0, extra.coerceAtLeast(0))
            windowInsets
        }
        attachSwipe(navHost)
    }

    private fun applyShellSkin() {
        // 由设计系统统一处理背景模式（纯色、渐变、自定义图）
        val bgRoot = ui.pageRoot()
        navHost.background = bgRoot.background
    }

    private fun rebuildBottomNav() {
        if (::bottomNavView.isInitialized) navHost.removeView(bottomNavView)
        bottomNavItems.clear()
        bottomNavView = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(ui.dp(DesignTokens.SPACE_8), ui.dp(DesignTokens.SPACE_8), ui.dp(DesignTokens.SPACE_8), ui.dp(DesignTokens.SPACE_8))
            setBackgroundColor(ui.palette.bg)
            bottomLabels().forEachIndexed { index, label ->
                val item = TextView(this@ShellActivity).apply {
                    text = label
                    textSize = 13f
                    gravity = Gravity.CENTER
                    setOnClickListener {
                        ui.pulse()
                        switchTo(index)
                    }
                    setOnLongClickListener {
                        ui.pulse()
                        val descriptions = listOf(
                            "终端：嵌入式 Shell 终端，支持 Ubuntu 和命令执行",
                            "文件：浏览和管理本地文件与项目目录",
                            "设置：主题、背景、密度等 UI 偏好设置",
                            "知识库：命令速查手册，支持搜索和一键执行"
                        )
                        Toast.makeText(this@ShellActivity, descriptions.getOrElse(index) { bottomLabels()[index] }, Toast.LENGTH_SHORT).show()
                        true
                    }
                }
                bottomNavItems.add(item)
                addView(item, LinearLayout.LayoutParams(0, ui.dp(44), 1f))
            }
        }
        navHost.addView(bottomNavView, LinearLayout.LayoutParams(-1, -2))
        updateBottomNavSelection()
    }

    private fun bottomLabels(): List<String> = listOf("终端", "文件", "设置", "知识库")

    private fun updateBottomNavSelection() {
        bottomNavItems.forEachIndexed { index, item ->
            val selected = index == currentIndex
            item.setTextColor(if (selected) ui.palette.accent else ui.palette.text)
            item.typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            item.background = if (selected) {
                GradientDrawable().apply {
                    cornerRadius = ui.dp(18).toFloat()
                    setColor((ui.palette.accent and 0x00FFFFFF) or 0x22000000)
                    setStroke(ui.dp(1), ui.palette.accent)
                }
            } else {
                null
            }
        }
    }

    fun switchTo(index: Int, animateForward: Boolean? = null, force: Boolean = false) {
        if (!force && index == currentIndex) return
        val forward = animateForward ?: (index > currentIndex)
        val previous = currentIndex
        currentIndex = index
        updateBottomNavSelection()
        renderCurrent(previous, animateForward = forward)
    }

    fun syncBrowserToDir(targetDir: File) {
        val filesPage = pages[TAB_FILES] as? EmbeddedFilesPage ?: return
        filesPage.syncNavigateTo(targetDir)
    }

    fun syncTerminalCd(ubuntuPath: String) {
        val termPage = pages[TAB_TERMINAL] as? EmbeddedTerminalPage ?: return
        termPage.silentCd(ubuntuPath)
    }

    private fun renderCurrent(previousIndex: Int = currentIndex, animateForward: Boolean?) {
        val next = pageViews.getOrPut(currentIndex) {
            pages[currentIndex].create(this, ui, ShellHost(this, prefs.sharedPreferences, ui)).also {
                it.visibility = View.GONE
                contentHost.addView(it, FrameLayout.LayoutParams(-1, -1))
            }
        }
        val old = pageViews[previousIndex]?.takeIf { it != next && it.visibility == View.VISIBLE }
        pageViews.values.forEach { if (it != old && it != next) it.visibility = View.GONE }

        if (old != null && animateForward != null) {
            val width = navHost.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
            val sign = if (animateForward) 1 else -1
            next.visibility = View.VISIBLE
            next.translationX = (sign * width).toFloat()
            next.animate().translationX(0f).setDuration(220).start()
            old.animate().translationX((-sign * width).toFloat())
                .setDuration(220)
                .withEndAction {
                    old.visibility = View.GONE
                    old.translationX = 0f
                }
                .start()
        } else {
            old?.visibility = View.GONE
            next.translationX = 0f
            next.visibility = View.VISIBLE
        }
        old?.let { pages[previousIndex].onDestroy(this) }
        pages[currentIndex].onSelected(activity = this, view = next)
        // 键盘管理：切换到终端时根据开关决定是否隐藏，离开终端时关闭键盘
        val imm = getSystemService(InputMethodManager::class.java)
        if (currentIndex == TAB_TERMINAL) {
            val autoShow = prefs.autoShowKeyboard
            if (!autoShow) {
                // onSelected 中 focusTerminalInput 触发了 requestFocus 自动弹键盘，立即隐藏
                imm?.hideSoftInputFromWindow(next.windowToken, 0)
            }
        } else {
            imm?.hideSoftInputFromWindow(next.windowToken, 0)
        }
    }

    private fun attachSwipe(target: View) {
        var downX = 0f
        var downY = 0f
        target.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    false
                }
                MotionEvent.ACTION_UP -> {
                    target.performClick()
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (abs(dx) > ui.dp(DesignTokens.SWIPE_TRIGGER_DP) && abs(dy) < ui.dp(DesignTokens.SWIPE_SLOP_DP)) {
                        if (dx < 0) {
                            val target = (currentIndex + 1).coerceAtMost(pages.size - 1)
                            if (target != currentIndex) switchTo(target, animateForward = true)
                        } else {
                            val target = (currentIndex - 1).coerceAtLeast(0)
                            if (target != currentIndex) switchTo(target, animateForward = false)
                        }
                        true
                    } else {
                        false
                    }
                }
                else -> false
            }
        }
    }

    companion object {
        private const val REQ_BACKGROUND_IMAGE = 4301
        private const val REQ_NOTIFICATION = 4302
        const val TAB_TERMINAL = 0
        const val TAB_FILES = 1
        const val TAB_SETTINGS = 2
        const val TAB_KNOWLEDGE = 3

        fun open(activity: Activity, tab: Int) {
            val intent = Intent(activity, ShellActivity::class.java)
                .putExtra("shell_tab", tab)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            activity.startActivity(intent)
            if (Build.VERSION.SDK_INT >= 34) {
                activity.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, R.anim.slide_in_left, R.anim.slide_out_right)
            } else {
                @Suppress("DEPRECATION")
                activity.overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
            }
        }
    }

    /** 请求必要的运行时权限 */
    private fun requestEssentialPermissions() {
        // Android 13+ 需要 POST_NOTIFICATIONS 权限
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFICATION)
            }
        }
        // WRITE_SETTINGS 需要特殊处理（引导用户到系统设置）
        if (Build.VERSION.SDK_INT >= 23) {
            if (!android.provider.Settings.System.canWrite(this)) {
                // 首次启动时提示用户，但不强制跳转（避免打断用户体验）
                if (!prefs.writeSettingsPrompted) {
                    prefs.writeSettingsPrompted = true
                    MaterialAlertDialogBuilder(this)
                        .setTitle("需要修改系统设置权限")
                        .setMessage("亮度调节等功能需要\"修改系统设置\"权限。请在接下来的系统设置中开启此权限。")
                        .setPositiveButton("去开启") { _, _ ->
                            startActivity(Intent(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                                data = android.net.Uri.parse("package:$packageName")
                            })
                        }
                        .setNegativeButton("稍后", null)
                        .show()
                }
            }
        }
    }

    /** 确保通知渠道已创建 */
    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                "aidev_terminal",
                "AIDev Terminal",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "AIDev Terminal 系统通知"
            }
            nm.createNotificationChannel(channel)
        }
    }
}

/** 内容页统一接口：每个页面只关心如何创建自己的内容 View。 */
interface ShellPage {
    fun create(activity: Activity, ui: AIDevUi, host: ShellHost): View
    fun onSelected(activity: Activity, view: View) {}
    fun onDestroy(activity: Activity) {}
    fun onBackPressed(): Boolean = false
}

/** 提供给 ShellPage 的容器能力。后续二级页面可使用 host 打开终端、AI 中心等。 */
class ShellHost(
    val activity: Activity,
    val prefs: SharedPreferences,
    val ui: AIDevUi
) {
    fun open(cls: Class<out Activity>) {
        activity.startActivity(Intent(activity, cls))
        if (Build.VERSION.SDK_INT >= 34) {
            activity.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, R.anim.slide_in_right, R.anim.slide_out_left)
        } else {
            @Suppress("DEPRECATION")
            activity.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
    }

    fun openTerminal(command: String) {
        if (activity is ShellActivity) {
            TerminalCommandBus.post(command)
            activity.switchTo(ShellActivity.TAB_TERMINAL)
        } else {
            AppNav.openTerminal(activity, command)
        }
    }

    fun switchTab(tab: Int) {
        if (activity is ShellActivity) {
            activity.switchTo(tab)
        } else {
            ShellActivity.open(activity, tab)
        }
    }

    fun refreshShellSkin() {
        if (activity is ShellActivity) activity.refreshShellSkin()
    }

    fun pickBackgroundImage() {
        if (activity is ShellActivity) {
            activity.pickBackgroundImage()
        } else {
            ShellActivity.open(activity, ShellActivity.TAB_SETTINGS)
        }
    }

    fun showCommandPalette() {
        if (activity is ShellActivity) activity.showCommandPalette()
    }
}

object TerminalCommandBus {
    @Volatile var pending: String = ""
    private val lock = Any()

    fun consume(): String? = synchronized(lock) {
        pending.takeIf { it.isNotBlank() }.also { pending = "" }
    }

    fun post(command: String) = synchronized(lock) {
        pending = command
    }
}
