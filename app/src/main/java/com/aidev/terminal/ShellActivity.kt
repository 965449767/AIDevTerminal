package com.aidev.terminal

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File
import kotlin.math.abs

/**
 * 全局 App Shell：
 * 1. 底部导航和背景层是持久化的，不会随页面切换重建。
 * 2. 中间内容区由 ShellPage 提供 View，切换只替换内容区。
 * 3. 左右滑动手势由 Shell 集中处理，所有顶级页面共用同一动画方向。
 */
class ShellActivity : Activity() {
    private lateinit var prefs: SharedPreferences
    private lateinit var ui: AIDevUi
    private lateinit var contentHost: FrameLayout
    private lateinit var bottomNavView: View
    private lateinit var navHost: LinearLayout
    private val bottomNavItems = mutableListOf<TextView>()
    private val pages: List<ShellPage> by lazy {
        listOf(
            DashboardPage(),
            EmbeddedTerminalPage(),
            EmbeddedFilesPage(),
            EmbeddedTasksPage(),
            EmbeddedSettingsPage()
        )
    }
    private val pageViews = mutableMapOf<Int, View>()
    private var currentIndex: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        prefs = getSharedPreferences("aidev_ui", MODE_PRIVATE)
        ui = AIDevUi(this, prefs)
        if (prefs.getBoolean("keepalive_auto", true)) runCatching { KeepAliveService.start(this) }
        buildShell()
        val requestedTab = intent?.getIntExtra("shell_tab", -1) ?: -1
        val shouldAutoBootstrapUbuntu = shouldAutoBootstrapUbuntu(requestedTab)
        if (shouldAutoBootstrapUbuntu) {
            TerminalCommandBus.pending = "aidev-auto-bootstrap"
        }
        val initial = if (shouldAutoBootstrapUbuntu) TAB_TERMINAL else requestedTab.takeIf { it in pages.indices } ?: 0
        switchTo(initial, animateForward = null, force = true)
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
            TerminalCommandBus.pending = "aidev-auto-bootstrap"
            switchTo(TAB_TERMINAL, animateForward = null, force = true)
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
        prefs.edit()
            .putString("bg_mode", "image")
            .putString("bg_image_uri", uri.toString())
            .apply()
        refreshShellSkin()
    }

    override fun onResume() {
        super.onResume()
        // 主题或密度变更后重建当前页内容，但不会重建底部导航和背景。
        ui = AIDevUi(this, prefs)
        applyShellSkin()
        rebuildBottomNav()
        renderCurrent(currentIndex, animateForward = null)
    }

    fun refreshShellSkin() {
        ui = AIDevUi(this, prefs)
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
            "打开工作台",
            "打开终端",
            "打开文件",
            "打开任务",
            "打开设置",
            "进入 Ubuntu",
            "环境诊断",
            "监听端口",
            "安装 OpenCode",
            "启动后台常驻",
            "选择背景图片",
            "任务模板",
            "Python HTTP 服务",
            "Node Dev 服务",
            "Gradle Debug 构建",
            "Logcat 任务",
            "Git 状态",
            "清屏",
            "当前项目",
            "当前项目 Git",
            "当前项目测试",
            "当前项目构建",
            "清除当前项目",
            "打开任务日志",
            "当前项目诊断",
            "当前项目日志",
            "当前项目修复",
            "OpenCode 前台",
            "OpenCode 后台",
            "OpenCode Serve",
            "AI 代理上下文",
            "AI 上下文文件",
            "AI 日志摘要",
            "OpenCode 启动检查",
            "AI 代理日志"
        )
        AlertDialog.Builder(this)
            .setTitle("命令面板")
            .setItems(items) { _, which ->
                handleCommandPalette(which)
            }
            .setPositiveButton("最近") { _, _ -> showRecentProjectActions() }
            .setNeutralButton("搜索") { _, _ -> showCommandPaletteSearch(items) }
            .show()
    }

    private fun showRecentProjectActions() {
        val rows = prefs.getString("project_action_history", "")?.lines()?.filter { it.isNotBlank() }.orEmpty().takeLast(20).reversed()
        if (rows.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("最近命令")
                .setMessage("暂无最近项目操作。")
                .setPositiveButton("关闭", null)
                .show()
            return
        }
        AlertDialog.Builder(this)
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
            .setNegativeButton("清空") { _, _ -> prefs.edit().remove("project_action_history").apply() }
            .show()
    }

    private fun showCommandPaletteSearch(items: Array<String>) {
        val edit = EditText(this).apply { hint = "输入 git、test、build、fix、log、ubuntu" }
        AlertDialog.Builder(this)
            .setTitle("搜索命令")
            .setView(edit)
            .setPositiveButton("搜索") { _, _ ->
                val keyword = edit.text.toString().trim()
                val matches = items.mapIndexed { index, label -> index to label }
                    .filter { keyword.isBlank() || commandMatches(it.second, keyword) }
                    .take(30)
                if (matches.isEmpty()) return@setPositiveButton
                AlertDialog.Builder(this)
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
            0 -> switchTo(TAB_DASHBOARD)
            1 -> switchTo(TAB_TERMINAL)
            2 -> switchTo(TAB_FILES)
            3 -> switchTo(TAB_TASKS)
            4 -> switchTo(TAB_SETTINGS)
            5 -> openTerminalCommand("ubuntu")
            6 -> openTerminalCommand("check-dev-env")
            7 -> openTerminalCommand("list-listen-ports")
            8 -> openTerminalCommand("install-aitool")
            9 -> runCatching { KeepAliveService.start(this) }
            10 -> pickBackgroundImage()
            11 -> switchTo(TAB_TASKS)
            12 -> openTerminalCommand("task-run pyserver 'python3 -m http.server 8000'")
            13 -> openTerminalCommand("task-run npm-dev 'npm run dev'")
            14 -> openTerminalCommand("task-run gradle './gradlew assembleDebug'")
            15 -> openTerminalCommand("task-run logcat 'logcat'")
            16 -> openTerminalCommand("git status")
            17 -> openTerminalCommand("clear")
            18 -> openCurrentProject("pwd && ls -la")
            19 -> openCurrentProject("git status --short --branch")
            20 -> openCurrentProject(projectTestCommand(currentProjectDir()))
            21 -> openCurrentProject(projectBuildCommand(currentProjectDir()))
            22 -> {
                prefs.edit().remove("current_project_path").apply()
                switchTo(TAB_FILES)
            }
            23 -> openTerminalCommand("ls -lah \"${filesDir.absolutePath}/home/tasks\"")
            24 -> openCurrentProject(projectHealthCommand(currentProjectDir()))
            25 -> openTerminalCommand("ls -lt \"${filesDir.absolutePath}/home/tasks\"/*.log 2>/dev/null | head -20")
            26 -> confirmCurrentProjectRepair()
            27 -> showOpenCodeLaunchOptions()
            28 -> openCurrentProject("aidev-opencode-task")
            29 -> openCurrentProject("task-run opencode-serve 'opencode serve'")
            30 -> openCurrentProject("aidev-agent-context")
            31 -> openCurrentProject("aidev-agent-context-file")
            32 -> openTerminalCommand("aidev-agent-summary")
            33 -> openCurrentProject("aidev-opencode-preflight")
            34 -> openTerminalCommand("aidev-agent-log")
        }
    }

    private fun showOpenCodeLaunchOptions() {
        val dir = currentProjectDir()
        if (dir == null) {
            switchTo(TAB_FILES)
            return
        }
        val status = listOf(
            "项目：${dir.name}",
            "路径：${dir.absolutePath}",
            "Git：${if (File(dir, ".git").exists()) "有" else "无"}",
            "README：${if (listOf("README.md", "README.txt", "readme.md").any { File(dir, it).isFile }) "有" else "无"}",
            "项目标记：${projectMarkers(dir)}"
        ).joinToString("\n")
        AlertDialog.Builder(this)
            .setTitle("OpenCode 启动前检查")
            .setMessage(status)
            .setPositiveButton("直接启动") { _, _ -> openCurrentProject("aidev-opencode") }
            .setNeutralButton("先导出上下文") { _, _ -> openCurrentProject("aidev-agent-context-file") }
            .setNegativeButton("后台启动") { _, _ -> openCurrentProject("aidev-opencode-task") }
            .show()
    }

    private fun projectMarkers(dir: File): String {
        val markers = mutableListOf<String>()
        if (File(dir, "package.json").exists()) markers.add("Node")
        if (File(dir, "pyproject.toml").exists() || File(dir, "requirements.txt").exists()) markers.add("Python")
        if (File(dir, "build.gradle").exists() || File(dir, "build.gradle.kts").exists()) markers.add("Gradle")
        if (File(dir, "go.mod").exists()) markers.add("Go")
        if (File(dir, "Cargo.toml").exists()) markers.add("Rust")
        return markers.ifEmpty { listOf("未识别") }.joinToString("、")
    }

    private fun openTerminalCommand(command: String) {
        TerminalCommandBus.pending = command
        switchTo(TAB_TERMINAL)
    }

    private fun confirmCurrentProjectRepair() {
        val dir = currentProjectDir()
        if (dir == null) {
            switchTo(TAB_FILES)
            return
        }
        val command = projectRepairCommand(dir)
        AlertDialog.Builder(this)
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
        prefs.getString("current_project_path", "")?.takeIf { it.isNotBlank() }?.let { File(it) }?.takeIf { it.isDirectory }

    private fun openCurrentProject(command: String?) {
        val dir = currentProjectDir()
        if (dir == null) {
            switchTo(TAB_FILES)
            return
        }
        TerminalCommandBus.pending = "cd \"${dir.absolutePath}\" && ${command ?: "pwd && ls -la"}"
        switchTo(TAB_TERMINAL)
    }

    private fun rememberProjectAction(label: String, dir: File, command: String) {
        val line = "${System.currentTimeMillis()}\t$label\t${dir.absolutePath}\t$command"
        val old = prefs.getString("project_action_history", "") ?: ""
        val next = (old.lines().filter { it.isNotBlank() } + line).takeLast(20).joinToString("\n")
        prefs.edit().putString("project_action_history", next).apply()
    }

    private fun projectTestCommand(dir: File?): String = when {
        dir == null -> "pwd"
        File(dir, "package.json").exists() -> "npm test"
        File(dir, "build.gradle").exists() || File(dir, "build.gradle.kts").exists() -> "./gradlew test"
        File(dir, "requirements.txt").exists() || File(dir, "pyproject.toml").exists() -> "python3 -m pytest"
        File(dir, "Cargo.toml").exists() -> "cargo test"
        File(dir, "go.mod").exists() -> "go test ./..."
        else -> "ls -la"
    }

    private fun projectBuildCommand(dir: File?): String = when {
        dir == null -> "pwd"
        File(dir, "package.json").exists() -> "npm run build"
        File(dir, "build.gradle").exists() || File(dir, "build.gradle.kts").exists() -> "./gradlew assembleDebug"
        File(dir, "Cargo.toml").exists() -> "cargo build"
        File(dir, "go.mod").exists() -> "go build ./..."
        else -> "ls -la"
    }

    private fun projectHealthCommand(dir: File?): String = when {
        dir == null -> "pwd"
        File(dir, "package.json").exists() -> "node -e \"const p=require('./package.json'); console.log('name:',p.name||'-'); console.log('scripts:', Object.keys(p.scripts||{}).join(','))\" && npm pkg get scripts"
        File(dir, "build.gradle").exists() || File(dir, "build.gradle.kts").exists() -> "./gradlew tasks --all | head -80"
        File(dir, "requirements.txt").exists() || File(dir, "pyproject.toml").exists() -> "python3 --version && python3 -m pip --version && python3 -m pytest --collect-only"
        File(dir, "Cargo.toml").exists() -> "cargo metadata --no-deps"
        File(dir, "go.mod").exists() -> "go list ./..."
        else -> "pwd && ls -la"
    }

    private fun projectRepairCommand(dir: File?): String = when {
        dir == null -> "pwd"
        File(dir, "package.json").exists() -> "rm -rf node_modules package-lock.json && npm install"
        File(dir, "build.gradle").exists() || File(dir, "build.gradle.kts").exists() -> "./gradlew --stop; ./gradlew clean"
        File(dir, "requirements.txt").exists() -> "python3 -m pip install -r requirements.txt --break-system-packages"
        File(dir, "pyproject.toml").exists() -> "python3 -m pip install . --break-system-packages"
        File(dir, "Cargo.toml").exists() -> "cargo clean && cargo fetch"
        File(dir, "go.mod").exists() -> "go clean -cache && go mod tidy"
        else -> "pwd && ls -la"
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
            setPadding(ui.dp(DesignTokens.SPACE_SM), ui.dp(DesignTokens.SPACE_SM), ui.dp(DesignTokens.SPACE_SM), ui.dp(DesignTokens.SPACE_SM))
            setBackgroundColor(ui.palette.nav)
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
                        showCommandPalette()
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

    private fun bottomLabels(): List<String> = listOf("工作台", "终端", "文件", "任务", "设置")

    private fun updateBottomNavSelection() {
        bottomNavItems.forEachIndexed { index, item ->
            val selected = index == currentIndex
            item.setTextColor(if (selected) ui.palette.primary else ui.palette.text)
            item.typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            item.background = if (selected) {
                GradientDrawable().apply {
                    cornerRadius = ui.dp(18).toFloat()
                    setColor((ui.palette.primary and 0x00FFFFFF) or 0x22000000)
                    setStroke(ui.dp(1), ui.palette.primary)
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
            pages[currentIndex].create(this, ui, ShellHost(this, prefs, ui)).also {
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
        const val TAB_DASHBOARD = 0
        const val TAB_TERMINAL = 1
        const val TAB_FILES = 2
        const val TAB_TASKS = 3
        const val TAB_SETTINGS = 4

        fun open(activity: Activity, tab: Int) {
            val intent = Intent(activity, ShellActivity::class.java)
                .putExtra("shell_tab", tab)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            activity.startActivity(intent)
            activity.overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
    }
}

/** 内容页统一接口：每个页面只关心如何创建自己的内容 View。 */
interface ShellPage {
    fun create(activity: Activity, ui: AIDevUi, host: ShellHost): View
    fun onSelected(activity: Activity, view: View) {}
    fun onDestroy(activity: Activity) {}
}

/** 提供给 ShellPage 的容器能力。后续二级页面可使用 host 打开终端、AI 中心等。 */
class ShellHost(
    val activity: Activity,
    val prefs: SharedPreferences,
    val ui: AIDevUi
) {
    fun open(cls: Class<out Activity>) {
        activity.startActivity(Intent(activity, cls))
        activity.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
    }

    fun openTerminal(command: String) {
        if (activity is ShellActivity) {
            TerminalCommandBus.pending = command
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
            activity.startActivity(Intent(activity, ThemeCenterActivity::class.java))
        }
    }

    fun showCommandPalette() {
        if (activity is ShellActivity) activity.showCommandPalette()
    }
}

object TerminalCommandBus {
    var pending: String = ""
}
