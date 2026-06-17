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
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File

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
    private var syncEnabled = false
    private var syncDot: TextView? = null

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
        syncEnabled = SyncCoordinator.isEnabled(activity.getSharedPreferences("aidev_ui", Activity.MODE_PRIVATE))
        syncDot?.let { tv ->
            tv.text = if (syncEnabled) "●" else "○"
            tv.setTextColor(if (syncEnabled) 0xFF22D3A7.toInt() else 0xFF4B5563.toInt())
        }
    }

    private fun toolbar(host: ShellHost): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(syncDot(activity).also { syncDot = it })
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
            "系统 · 旧版文件管理" to { host.switchTab(ShellActivity.TAB_FILES) }
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
            background = ui.surfaceBackground()
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
        var dir = if (isLeft) leftDir else rightDir
        val list = if (isLeft) leftList else rightList
        val path = if (isLeft) leftPath else rightPath
        // 如果当前目录不可读，自动回退到可读目录
        if (dir.listFiles() == null) {
            val fallback = Environment.getExternalStorageDirectory()
            dir = fallback
            if (isLeft) leftDir = fallback else rightDir = fallback
        }
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
                    if (syncEnabled) notifyTerminalCd(file)
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

    fun syncNavigateTo(targetDir: File) {
        android.util.Log.d("AIDEV_SYNC", "syncNavigateTo: ${targetDir.absolutePath}, exists=${targetDir.exists()}, isDir=${targetDir.isDirectory}")
        if (!targetDir.isDirectory) return
        if (activeLeft) { leftDir = targetDir; leftSelected = null }
        else { rightDir = targetDir; rightSelected = null }
        loadPane(activeLeft)
    }

    private fun notifyTerminalCd(dir: File) {
        val act = activity
        val home = File(act.filesDir, "home")
        val prefs = act.getSharedPreferences("aidev_ui", Activity.MODE_PRIVATE)
        SyncCoordinator.onBrowserDirChanged(dir, home, prefs) { ubuntuPath ->
            if (act is ShellActivity) act.syncTerminalCd(ubuntuPath)
        }
    }

    private fun syncDot(activity: Activity): TextView =
        TextView(activity).apply {
            gravity = Gravity.CENTER
            textSize = 11f
            val on = SyncCoordinator.isEnabled(activity.getSharedPreferences("aidev_ui", Activity.MODE_PRIVATE))
            text = if (on) "●" else "○"
            setTextColor(if (on) 0xFF22D3A7.toInt() else 0xFF4B5563.toInt())
            setOnClickListener {
                val prefs = activity.getSharedPreferences("aidev_ui", Activity.MODE_PRIVATE)
                val current = SyncCoordinator.isEnabled(prefs)
                SyncCoordinator.setEnabled(prefs, !current)
                val nowOn = !current
                syncEnabled = nowOn
                text = if (nowOn) "●" else "○"
                setTextColor(if (nowOn) 0xFF22D3A7.toInt() else 0xFF4B5563.toInt())
                Toast.makeText(activity, if (nowOn) "联动已开启" else "联动已关闭", Toast.LENGTH_SHORT).show()
            }

        }

    private fun copyToOther(move: Boolean) {
        val src = selected() ?: return toast("请先选择文件或目录")
        val dst = File(otherDir(), src.name)
        if (dst.exists()) return toast("目标已存在")
        try {
            if (src.isDirectory) copyDir(src, dst) else src.copyTo(dst)
            if (move) src.deleteRecursively()
            clearSelection()
            reloadAll()
            toast(if (move) "移动完成" else "复制完成")
        } catch (e: Exception) {
            toast("操作失败：${e.message ?: "未知错误"}")
        }
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
                try {
                    val ok = if (src.isDirectory) src.deleteRecursively() else src.delete()
                    clearSelection()
                    reloadAll()
                    toast(if (ok) "已删除" else "删除失败")
                } catch (e: Exception) {
                    toast("删除失败：${e.message ?: "未知错误"}")
                }
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
                    TerminalCommandBus.post(runCmd)
                    (activity as? ShellActivity)?.switchTo(ShellActivity.TAB_TERMINAL)
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
                    3 -> runInTerminal("cd \"${dir.absolutePath}\" && ${ProjectCommands.healthCommand(dir)}")
                    4 -> confirmProjectRepair(dir)
                    5 -> showProjectHistory()
                    6 -> showProjectScripts(dir)
                    7 -> copyProjectReport(dir)
                    8 -> copyRepairCommand(dir)
                    9 -> inspectProject()
                    10 -> showReadme(dir)
                    11 -> runInTerminal("cd \"${dir.absolutePath}\" && git status --short --branch")
                    12 -> runInTerminal("cd \"${dir.absolutePath}\" && git diff --stat")
                    13 -> runInTerminal("cd \"${dir.absolutePath}\" && ${ProjectCommands.installCommand(dir)}")
                    14 -> runInTerminal("cd \"${dir.absolutePath}\" && ${ProjectCommands.devCommand(dir)}")
                    15 -> runInTerminal("cd \"${dir.absolutePath}\" && ${ProjectCommands.testCommand(dir)}")
                    16 -> runInTerminal("cd \"${dir.absolutePath}\" && ${ProjectCommands.buildCommand(dir)}")
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
            "健康摘要：${ProjectCommands.detectSummary(dir)}",
            "安装命令：cd \"${dir.absolutePath}\" && ${ProjectCommands.installCommand(dir)}",
            "开发命令：cd \"${dir.absolutePath}\" && ${ProjectCommands.devCommand(dir)}",
            "测试命令：cd \"${dir.absolutePath}\" && ${ProjectCommands.testCommand(dir)}",
            "构建命令：cd \"${dir.absolutePath}\" && ${ProjectCommands.buildCommand(dir)}",
            "修复命令：cd \"${dir.absolutePath}\" && ${ProjectCommands.repairCommand(dir)}"
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
            ProjectCommands.installCommand(dir),
            ProjectCommands.devCommand(dir),
            ProjectCommands.testCommand(dir),
            ProjectCommands.buildCommand(dir)
        ).joinToString("\n")
        copyText("AIDev 项目命令", body)
        toast("已复制项目命令")
    }

    private fun copyProjectReport(dir: File) {
        val body = listOf(
            "项目：${dir.name}",
            "路径：${dir.absolutePath}",
            "健康摘要：${ProjectCommands.detectSummary(dir)}",
            "README：${if (listOf("README.md", "README.txt", "readme.md").any { File(dir, it).isFile }) "有" else "无"}",
            "Git：${if (File(dir, ".git").exists()) "有" else "无"}",
            "安装：${ProjectCommands.installCommand(dir)}",
            "开发：${ProjectCommands.devCommand(dir)}",
            "测试：${ProjectCommands.testCommand(dir)}",
            "构建：${ProjectCommands.buildCommand(dir)}",
            "诊断：${ProjectCommands.healthCommand(dir)}",
            "修复：${ProjectCommands.repairCommand(dir)}"
        ).joinToString("\n")
        copyText("AIDev 项目诊断", body)
        toast("已复制诊断报告")
    }

    private fun copyRepairCommand(dir: File) {
        copyText("AIDev 修复命令", ProjectCommands.repairCommand(dir))
        toast("已复制修复命令")
    }

    private fun confirmProjectRepair(dir: File) {
        val command = "cd \"${dir.absolutePath}\" && ${ProjectCommands.repairCommand(dir)}"
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
            "健康摘要：${ProjectCommands.detectSummary(dir)}",
            "安装：${ProjectCommands.installCommand(dir)}",
            "开发：${ProjectCommands.devCommand(dir)}",
            "测试：${ProjectCommands.testCommand(dir)}",
            "构建：${ProjectCommands.buildCommand(dir)}",
            "诊断：${ProjectCommands.healthCommand(dir)}",
            "修复：${ProjectCommands.repairCommand(dir)}"
        ).joinToString("\n")
        runCatching { out.writeText(body) }
            .onSuccess {
                reloadAll()
                toast("已导出项目摘要")
            }
            .onFailure { toast("导出失败：${it.message}") }
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

    private fun runInTerminal(command: String) {
        val dir = selected()?.takeIf { it.isDirectory } ?: activeDir()
        rememberProjectAction(commandLabel(command), dir, command)
        val act = activity
        if (act is ShellActivity) {
            TerminalCommandBus.post(command)
            act.switchTo(ShellActivity.TAB_TERMINAL)
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
            background = ui.subtleButtonBackground()
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
        (activity.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(ClipData.newPlainText(label, text))
    }
    private fun toast(text: String) = Toast.makeText(activity, text, Toast.LENGTH_SHORT).show()
}
