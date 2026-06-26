package com.aidev.terminal
import com.google.android.material.dialog.MaterialAlertDialogBuilder

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.provider.Settings
import android.text.TextUtils
import android.text.Editable
import android.util.Log
import android.view.Gravity
import android.view.DragEvent
import android.view.GestureDetector
import android.view.MotionEvent
import android.content.ClipData
import android.content.ClipboardManager
import android.util.Base64
import android.view.View
import android.view.ViewOutlineProvider
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.NestedScrollView
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EmbeddedFilesPage : ShellPage {
    private lateinit var activity: Activity
    private lateinit var ui: AIDevUi
    private lateinit var leftList: LinearLayout
    private lateinit var rightList: LinearLayout
    private lateinit var pathBar: TextView
    private lateinit var splitView: View
    private lateinit var treeView: View
    private lateinit var projectTree: ProjectTreeView
    private lateinit var modeToggle: View
    private var collapseBtn: View? = null
    private var toolbarActions: View? = null
    private var treeContainer: View? = null
    private lateinit var filePreviewPanel: LinearLayout
    private lateinit var previewName: TextView
    private lateinit var previewInfo: TextView
    private lateinit var previewContent: FrameLayout
    private lateinit var previewEdit: EditText
    private lateinit var previewScroll: NestedScrollView
    private lateinit var previewText: TextView
    private lateinit var previewWeb: WebView
    private var isPreviewViewMode = false
    private var isHtmlSourceMode = false
    private var isPreviewEditMode = false
    private var previewDirtyDot: View? = null
    private val dirtyHandler = Handler(Looper.getMainLooper())
    private val dirtyCheck = object : Runnable {
        override fun run() {
            val current = previewEdit.text.toString()
            isPreviewDirty = previewFile != null && current != previewOriginalText
            updatePreviewButtons()
        }
    }
    private var previewFile: File? = null
    private var previewOriginalText = ""
    private var isPreviewDirty = false
    private var previewLineCount = 0
    private lateinit var previewHtmlToggle: View
    private lateinit var previewEditToggle: View
    private lateinit var previewSaveBtn: View
    private var contentContainer: FrameLayout? = null
    private var lastOpenTime = 0L
    private var lastOpenFile: File? = null
    private var pendingSyncPath: String? = null
    private var multiMode = false
    private var multiPaneSide = true
    private val multiSelected = mutableSetOf<String>()
    private var anchorFile: String? = null
    private lateinit var fileActionBar: HorizontalScrollView
    private lateinit var fileActionInfo: TextView
    private var leftDir: File = Environment.getExternalStorageDirectory()
    private var rightDir: File = File("/")
    private var activeLeft = true
    private var selectedFile: File? = null
    private var leftPane: View? = null
    private var rightPane: View? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val pm by lazy { PreferencesManager(activity) }
    private val paneBg: GradientDrawable by lazy {
        val c = (ui.palette.surface and 0x00FFFFFF) or (0xCC000000.toInt())
        GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(c, c)).apply {
            cornerRadius = ui.dp(6).toFloat()
        }
    }

    override fun create(activity: Activity, ui: AIDevUi, host: ShellHost): View {
        this.activity = activity
        this.ui = ui
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8))
        }
        root.addView(toolbar(host), LinearLayout.LayoutParams(-1, ui.dp(42)))
        pathBar = ui.text("", 12f, ui.palette.accent, bold = true).apply {
            setPadding(ui.dp(8), ui.dp(7), ui.dp(8), ui.dp(7))
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.MIDDLE
            background = paneHeaderBg()
        }
        root.addView(pathBar, LinearLayout.LayoutParams(-1, -2).apply { setMargins(ui.dp(4), 0, ui.dp(4), ui.dp(8)) })
        splitView = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }.also { panes ->
            leftList = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
            rightList = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
            panes.addView(pane(true), LinearLayout.LayoutParams(0, -1, 1f).apply { setMargins(0, 0, ui.dp(4), 0) })
            panes.addView(pane(false), LinearLayout.LayoutParams(0, -1, 1f).apply { setMargins(ui.dp(4), 0, 0, 0) })
        }
        projectTree = ProjectTreeView(activity, ui).apply {
            setCallbacks(
                dirClick = { file -> notifyTerminalCd(file) },
                fileClick = { file -> openFile(file) },
                fileAction = { file ->
                    selectedFile = file
                    activeLeft = true
                    fileActions(file)
                },
                onSaveExpanded = { paths -> pm.treeExpandedPaths = paths }
            )
        }
        treeView = projectTree
        treeContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(treeView, LinearLayout.LayoutParams(-1, -1))
        }
        contentContainer = FrameLayout(activity).apply {
            addView(splitView, FrameLayout.LayoutParams(-1, -1))
            addView(treeContainer, FrameLayout.LayoutParams(-1, -1))
        }
        root.addView(contentContainer!!, LinearLayout.LayoutParams(-1, 0, 1f))
        buildFilePreviewPanel()
        root.addView(filePreviewPanel, LinearLayout.LayoutParams(-1, 0, 1f))
        buildFileActionBar()
        root.addView(fileActionBar, LinearLayout.LayoutParams(-1, ui.dp(42)))
        reloadAll()
        applyLayoutMode()
        return root
    }

    override fun onSelected(activity: Activity, view: View) {
        if (::leftList.isInitialized) reloadAll()
        if (::projectTree.isInitialized && pm.fileLayoutMode == "tree") {
            projectTree.refresh()
        }
    }

    override fun onDestroy(activity: Activity) {
        scope.coroutineContext[Job]?.children?.forEach { it.cancel() }
    }

    private fun toolbar(host: ShellHost): View =
        FrameLayout(activity).apply {
            val leftGroup = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(modeButton("E") { toggleMode() }.also { modeToggle = it })
                addView(modeButton("▼") { if (::projectTree.isInitialized) projectTree.collapseAll() }.also { collapseBtn = it }.apply { visibility = View.GONE })
            }
            addView(leftGroup, FrameLayout.LayoutParams(-2, -1, Gravity.LEFT or Gravity.CENTER_VERTICAL))
            val rightScroll = HorizontalScrollView(activity).apply {
                isHorizontalScrollBarEnabled = false
                addView(LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    addView(action("同步") { syncPanes() })
                    addView(action("复制") { copyToOther(false) })
                    addView(action("移动") { copyToOther(true) })
                    addView(action("新建") { newFolder() })
                    addView(action("粘贴") { pasteClipboard() })
                    addView(action("搜索") { searchActiveDir() })
                    addView(action("更多") { showFileMoreMenu(host) })
                }.also { toolbarActions = it })
            }
            addView(rightScroll, FrameLayout.LayoutParams(-2, -1, Gravity.RIGHT or Gravity.CENTER_VERTICAL))
        }

    private fun showFileMoreMenu(host: ShellHost) {
        val actions = listOf(
            "文件 · 信息" to { previewSelected() },
            "文件 · 编辑" to { editSelected() },
            "文件 · 重命名" to { renameSelected() },
            "文件 · 另存" to { saveSelectedAs() },
            "文件 · 复制路径" to { copySelectedPath() },
            "位置 · 收藏当前目录" to { addFavorite() },
            "位置 · 收藏/跳转" to { showFavorites() },
            "位置 · 常用目录" to { showQuickDirs() },
            "位置 · 最近项目" to { showRecentProjects() },
            "系统 · 安装 APK" to { installSelectedApk(host) }
        )
        val recent = recentFileMenuLabels().filter { label -> actions.any { it.first == label } }
        val display = recent.map { "最近 · ${it.substringAfter(" · ")}" to it } + actions.filterNot { recent.contains(it.first) }.map { it.first to it.first }
        val items = display.map { (showLabel, original) ->
            MenuBottomSheet.MenuItem(showLabel, "") {
                rememberFileMenuLabel(original)
                actions.firstOrNull { it.first == original }?.second?.invoke()
            }
        } + MenuBottomSheet.MenuItem("搜索", "关键词搜索菜单项") {
            searchFileMoreMenu(actions)
        }
        MenuBottomSheet(activity, ui).show("文件更多", items)
    }

    private fun searchFileMoreMenu(actions: List<Pair<String, () -> Unit>>) {
        val edit = EditText(activity).apply { hint = "输入 删除、项目、权限、路径" }
        MaterialAlertDialogBuilder(activity)
            .setTitle("搜索文件更多")
            .setView(edit)
            .setPositiveButton("搜索") { _, _ ->
                val keyword = edit.text.toString().trim()
                val matches = actions.filter { keyword.isBlank() || it.first.contains(keyword, true) }.take(30)
                if (matches.isEmpty()) return@setPositiveButton toast("没有匹配项")
                val items = matches.map { (label, action) ->
                    MenuBottomSheet.MenuItem(label, "") {
                        rememberFileMenuLabel(label)
                        action.invoke()
                    }
                }
                MenuBottomSheet(activity, ui).show("搜索结果", items)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun recentFileMenuLabels(): List<String> =
        pm.recentFileMore.lines().filter { it.isNotBlank() }.takeLast(3).reversed()

    private fun rememberFileMenuLabel(label: String) {
        val prefs = pm.sharedPreferences
        val old = prefs.getString("recent_file_more", "")?.lines()?.filter { it.isNotBlank() && it != label }.orEmpty()
        prefs.edit().putString("recent_file_more", (old + label).takeLast(6).joinToString("\n")).apply()
    }

    private fun pane(isLeft: Boolean): View {
        val outer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(4), ui.dp(4), ui.dp(4), ui.dp(4))
            background = paneBg
            setOnDragListener { v, event ->
                when (event.action) {
                    DragEvent.ACTION_DRAG_STARTED -> {
                        dragLog("STARTED cd=${event.clipDescription} mime=${event.clipDescription?.getMimeType(0)}")
                        true
                    }
                    DragEvent.ACTION_DRAG_ENTERED -> {
                        v.background = android.graphics.drawable.GradientDrawable().apply {
                            setColor(0x1A7C3AED.toInt())
                            cornerRadius = ui.dp(10).toFloat()
                            setStroke(ui.dp(2), 0xFF7C3AED.toInt())
                        }
                        true
                    }
                    DragEvent.ACTION_DRAG_EXITED -> {
                        v.background = paneBg
                        true
                    }
                    DragEvent.ACTION_DRAG_ENDED -> {
                        v.background = paneBg
                        true
                    }
                    DragEvent.ACTION_DROP -> {
                        val paths = event.localState as? List<*>
                        dragLog("DROP isLeft=$isLeft paths=${paths?.joinToString(",")}")
                        if (paths != null) {
                            val results = paths.mapNotNull { it as? String }.map { path ->
                                val src = File(path)
                                val dst = File(if (isLeft) leftDir else rightDir, src.name)
                                src.exists() && !dst.exists() && runCatching {
                                    if (src.isDirectory) copyDir(src, dst) else src.copyTo(dst)
                                    true
                                }.getOrDefault(false)
                            }
                            val ok = results.isNotEmpty() && results.all { it }
                            dragLog("DROP ok=$ok")
                            if (ok) { ui.pulse(); loadPane(isLeft); updatePathBar() }
                            ok
                        } else {
                            dragLog("DROP localState null or not List")
                            false
                        }
                    }
                    DragEvent.ACTION_DRAG_ENDED -> {
                        v.background = ui.surfaceBackground()
                        if (multiMode) exitMultiMode()
                        true
                    }
                    else -> true
                }
            }
            setOnClickListener {
                if (!multiMode) {
                    activeLeft = isLeft
                    selectedFile = null
                    updatePathBar()
                    updatePaneHighlight()
                    refreshHighlight(true); refreshHighlight(false)
                }
            }
        }
        if (isLeft) leftPane = outer else rightPane = outer
        val list = if (isLeft) leftList else rightList
        outer.addView(ScrollView(activity).apply {
            setOnTouchListener { _, event ->
                if (!multiMode && event.action == MotionEvent.ACTION_DOWN && activeLeft != isLeft) {
                    activeLeft = isLeft
                    selectedFile = null
                    ui.pulse()
                    updatePathBar()
                    updatePaneHighlight()
                    refreshHighlight(true); refreshHighlight(false)
                }
                false
            }
            isFillViewport = true
            addView(list)
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        return outer
    }

    private fun reloadAll() {
        loadPane(true)
        loadPane(false)
        updatePaneHighlight()
        if (::projectTree.isInitialized && pm.fileLayoutMode == "tree") {
            projectTree.refresh()
        }
    }

    private fun loadPane(isLeft: Boolean) {
        var dir = if (isLeft) leftDir else rightDir
        val list = if (isLeft) leftList else rightList
        if (dir.listFiles() == null) {
            val fallback = Environment.getExternalStorageDirectory()
            dir = fallback
            if (isLeft) leftDir = fallback else rightDir = fallback
        }
        list.removeAllViews()
        dir.parentFile?.let { list.addView(row("..", it, isLeft, true)) }
        val files = dir.listFiles()?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
        if (files == null) {
            list.addView(info("无法读取，可能需要存储权限。"))
            updatePathBar()
            return
        }
        files.take(300).forEach { list.addView(row(label(it), it, isLeft, false)) }
        updatePathBar()
    }

    private fun updatePathBar() {
        val dir = activeDir()
        val files = dir.listFiles()
        val dirCount = files?.count { it.isDirectory } ?: 0
        val fileCount = files?.count { it.isFile } ?: 0
        val status = if (files == null) "无法读取" else "文件夹:$dirCount  文件:$fileCount"
        val stat = StatFs(Environment.getExternalStorageDirectory().absolutePath)
        val totalGB = stat.totalBytes.toDouble() / (1024 * 1024 * 1024)
        val freeGB = stat.availableBytes.toDouble() / (1024 * 1024 * 1024)
        val storage = "储存:${"%.2f".format(freeGB)}G/${"%.2f".format(totalGB)}G"
        val home = File(activity.filesDir, "home")
        val displayPath = PathBridge.androidToUbuntu(home, dir) ?: dir.absolutePath
        pathBar.text = "${displayPath}\n${status}  ${storage}"
    }

    private fun updatePaneHighlight() {
        val c = (ui.palette.surface and 0x00FFFFFF) or (0xCC000000.toInt())
        val active = android.graphics.drawable.GradientDrawable().apply {
            setColor(c)
            cornerRadius = ui.dp(6).toFloat()
            setStroke(ui.dp(2), 0xFF7C3AED.toInt())
        }
        leftPane?.background = if (activeLeft) active else paneBg
        rightPane?.background = if (!activeLeft) active else paneBg
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
            setBackgroundColor(
                if (multiMode && file.absolutePath in multiSelected && multiPaneSide == isLeft) ui.palette.accent
                else if (!multiMode && selectedFile?.absolutePath == file.absolutePath && activeLeft == isLeft) ui.palette.accent
                else Color.TRANSPARENT
            )
            tag = file
            outlineProvider = ViewOutlineProvider.BOUNDS
            var swipeConsumed = false
            var longPressTriggered = false
            var dragStarted = false
            var downX = 0f

            val gd = GestureDetector(activity, object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean {
                    swipeConsumed = false
                    longPressTriggered = false
                    dragStarted = false
                    downX = e.x
                    return true
                }

                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    ui.pulse()
                    activeLeft = isLeft
                    updatePaneHighlight()
                    if (multiMode && multiPaneSide != isLeft) {
                        multiMode = false
                        multiPaneSide = true
                        multiSelected.clear()
                        selectedFile = null
                        fileActionBar.visibility = View.GONE
                        refreshHighlight(true); refreshHighlight(false)
                    }
                    if (multiMode) {
                        toggleMultiSelect(file, isLeft)
                    } else if (file.isDirectory) {
                        if (isLeft) leftDir = file else rightDir = file
                        selectedFile = null
                        rememberRecentDir(file)
                        notifyTerminalCd(file)
                        loadPane(true); loadPane(false)
                    } else if (!parent) {
                        openFile(file)
                    }
                    this@apply.performClick()
                    return true
                }

                override fun onLongPress(e: MotionEvent) {
                    dragLog("onLongPress file=${file.name} multiMode=$multiMode")
                    this@apply.performLongClick()
                    if (multiMode) {
                        if (multiPaneSide != isLeft) {
                            exitMultiMode()
                            return
                        }
                        longPressTriggered = true
                        this@apply.parent.requestDisallowInterceptTouchEvent(true)
                        this@apply.elevation = ui.dp(4).toFloat()
                        this@apply.translationZ = ui.dp(4).toFloat()
                        updateMultiInfo()
                        return
                    }
                    activeLeft = isLeft
                    updatePaneHighlight()
                    selectedFile = file
                    longPressTriggered = true
                    this@apply.parent.requestDisallowInterceptTouchEvent(true)
                    this@apply.elevation = ui.dp(4).toFloat()
                    this@apply.translationZ = ui.dp(4).toFloat()
                    fileActionBar.visibility = View.VISIBLE
                    updateMultiInfo()
                }

                override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                    if (swipeConsumed) return true
                    if (longPressTriggered) return false
                    val dx = abs(e2.x - (e1?.x ?: e2.x))
                    val dy = abs(e2.y - (e1?.y ?: e2.y))
                    if (dx > dy * 2f && dx > 30f) {
                        swipeConsumed = true
                        this@apply.parent.requestDisallowInterceptTouchEvent(true)
                        if (multiMode) {
                            dragLog("SWIPE rangeSelect file=${file.name} multi=${multiSelected.toList()}")
                            this@apply.post { rangeSelect(file, isLeft) }
                        } else {
                            dragLog("SWIPE enterMultiMode file=${file.name}")
                            this@apply.post {
                                activeLeft = isLeft
                                updatePaneHighlight()
                                selectedFile = file
                                enterMultiMode(file)
                            }
                        }
                        return true
                    }
                    return false
                }
            })

            setOnTouchListener { _, event ->
                if (!multiMode && event.action == MotionEvent.ACTION_DOWN && activeLeft != isLeft) {
                    activeLeft = isLeft
                    selectedFile = null
                    ui.pulse()
                    updatePathBar()
                    updatePaneHighlight()
                    refreshHighlight(true); refreshHighlight(false)
                }
                val gdResult = gd.onTouchEvent(event)
                if (longPressTriggered) {
                    when (event.action) {
                        MotionEvent.ACTION_MOVE -> {
                            if (!dragStarted && abs(event.x - downX) > 60f) {
                                dragStarted = true
                                ui.pulse()
                                longPressTriggered = false
                                this@apply.elevation = 0f
                                this@apply.translationZ = 0f
                                fileActionBar.visibility = View.GONE
                                val paths = if (multiMode && multiPaneSide == isLeft && multiSelected.isNotEmpty())
                                    multiSelected.toList() else listOf(file.absolutePath)
                                val clip = ClipData("file", arrayOf("text/plain"), ClipData.Item(paths.first()))
                                paths.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
                                dragLog("startDragAndDrop paths=${paths.joinToString(",")}")
                                runCatching { this@apply.startDragAndDrop(clip, View.DragShadowBuilder(this@apply), paths, 0) }
                                    .onFailure { dragLog("startDragAndDrop failed: ${it.message}") }
                            }
                            this@apply.parent.requestDisallowInterceptTouchEvent(true)
                            true
                        }
                        MotionEvent.ACTION_UP -> {
                            longPressTriggered = false
                            this@apply.elevation = 0f
                            this@apply.translationZ = 0f
                            if (!dragStarted) {
                                activeLeft = isLeft
                                selectedFile = file
                                enterMultiMode(file)
                            }
                            true
                        }
                        MotionEvent.ACTION_CANCEL -> {
                            longPressTriggered = false
                            this@apply.elevation = 0f
                            this@apply.translationZ = 0f
                            false
                        }
                        else -> true
                    }
                } else {
                    gdResult
                }
            }

            if (file.isDirectory && !parent) {
                var hoverPending = false
                setOnDragListener { v, event ->
                    when (event.action) {
                        DragEvent.ACTION_DRAG_STARTED -> true
                        DragEvent.ACTION_DRAG_ENTERED -> {
                            v.setBackgroundColor(0x3A7C3AED.toInt())
                            hoverPending = true
                            v.postDelayed({
                                if (hoverPending && file.isDirectory) {
                                    hoverPending = false
                                    if (isLeft) leftDir = file else rightDir = file
                                    selectedFile = null
                                    rememberRecentDir(file)
                                    notifyTerminalCd(file)
                                    loadPane(true); loadPane(false)
                                }
                            }, 500)
                            true
                        }
                        DragEvent.ACTION_DRAG_EXITED, DragEvent.ACTION_DRAG_ENDED -> {
                            v.setBackgroundColor(Color.TRANSPARENT)
                            hoverPending = false
                            true
                        }
                        DragEvent.ACTION_DROP -> {
                            v.setBackgroundColor(Color.TRANSPARENT)
                            hoverPending = false
                            val paths = event.localState as? List<*>
                            if (paths != null) {
                                var ok = 0; var fail = 0
                                paths.mapNotNull { it as? String }.forEach { path ->
                                    val src = File(path)
                                    val dst = File(file, src.name)
                                    try {
                                        if (src.isDirectory) copyDir(src, dst) else src.copyTo(dst)
                                        ok++
                                    } catch (e: Exception) {
                                        fail++; dragLog("drop to dir failed: ${src.name}: ${e.message}")
                                    }
                                }
                                toast(if (fail > 0) "已复制 ${ok} 项（${fail} 项失败）" else "已复制 ${ok} 项")
                                ui.pulse()
                                loadPane(isLeft); updatePathBar()
                            }
                            true
                        }
                        else -> true
                    }
                }
            }
        }

    private fun dragLog(msg: String) {
        runCatching { File("/storage/emulated/0/drag.log").appendText("$msg\n") }
    }

    private fun selected(): File? = selectedFile
    private fun activeDir(): File = if (activeLeft) leftDir else rightDir

    private fun syncPanes() {
        val src = activeDir()
        if (activeLeft) rightDir = src else leftDir = src
        reloadAll()
    }

    override fun onBackPressed(): Boolean {
        val dir = activeDir()
        val sdRoot = Environment.getExternalStorageDirectory()
        val parent = dir.parentFile
        if (dir != sdRoot && parent != null) {
            if (activeLeft) leftDir = parent else rightDir = parent
            reloadAll()
            ui.pulse()
            return true
        }
        return false
    }
    private fun otherDir(): File = if (activeLeft) rightDir else leftDir

    fun syncNavigateTo(targetDir: File) {
        android.util.Log.d("AIDEV_SYNC", "syncNavigateTo: ${targetDir.absolutePath}, exists=${targetDir.exists()}, isDir=${targetDir.isDirectory}")
        if (!targetDir.isDirectory) return
        if (pm.fileLayoutMode == "split") {
            if (activeLeft) { leftDir = targetDir; selectedFile = null }
            else { rightDir = targetDir; selectedFile = null }
            loadPane(activeLeft)
        } else {
            if (::projectTree.isInitialized) {
                val r = projectTree.currentRoot
                if (r != null && !targetDir.absolutePath.startsWith(r.absolutePath)) {
                    val newRoot = ProjectDetector.findProjectRoot(targetDir) ?: targetDir
                    projectTree.setRoot(newRoot)
                    projectTree.restoreExpanded(pm.treeExpandedPaths)
                }
                projectTree.expandTo(targetDir)
            } else {
                pendingSyncPath = targetDir.absolutePath
            }
        }
    }

    private fun modeButton(text: String, click: () -> Unit): View =
        TextView(activity).apply {
            this.text = text
            textSize = 13f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
            setPadding(ui.dp(10), 0, ui.dp(10), 0)
            layoutParams = LinearLayout.LayoutParams(-2, ui.dp(30)).apply {
                setMargins(0, 0, ui.dp(4), 0)
            }
            setOnClickListener { ui.pulse(); click() }
        }

    private fun toggleMode() {
        pm.fileLayoutMode = if (pm.fileLayoutMode == "split") "tree" else "split"
        applyLayoutMode()
    }

    private fun applyLayoutMode() {
        val split = pm.fileLayoutMode == "split"
        splitView.visibility = if (split) View.VISIBLE else View.GONE
        treeContainer?.visibility = if (split) View.GONE else View.VISIBLE
        toolbarActions?.visibility = if (split) View.VISIBLE else View.GONE
        collapseBtn?.visibility = if (split) View.GONE else View.VISIBLE
        (modeToggle as TextView).apply {
            text = if (split) "E" else "P"
            setTextColor(if (split) 0xFF7C3AED.toInt() else 0xFF06D6A0.toInt())
            textSize = 15f
        }
        if (!split && ::projectTree.isInitialized) {
            val home = File(activity.filesDir, "home")
            val root = pm.currentProjectPath.takeIf { it.isNotBlank() }?.let { File(it) }
                ?: ProjectDetector.findProjectRoot(home)
                ?: home
            projectTree.setRoot(root)
            projectTree.restoreExpanded(pm.treeExpandedPaths)
            pendingSyncPath?.let {
                projectTree.expandTo(File(it))
                pendingSyncPath = null
            }
        }
    }

    private fun buildFilePreviewPanel() {
        filePreviewPanel = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(4), 0, ui.dp(4), 0)
            visibility = View.GONE
        }
        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(ui.dp(6), ui.dp(6), ui.dp(6), ui.dp(6))
            background = paneHeaderBg()
        }
        val closeBtn = TextView(activity).apply {
            text = "\u2715"
            textSize = 16f
            setTextColor(ui.palette.accent)
            gravity = Gravity.CENTER
            setPadding(ui.dp(8), ui.dp(4), ui.dp(8), ui.dp(4))
            setOnClickListener { closeFilePreview() }
        }
        val nameLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, ui.dp(8), 0)
        }
        previewName = ui.text("", 13f, ui.palette.accent, bold = true).apply {
            setPadding(0, 0, 0, ui.dp(2))
        }
        previewInfo = ui.text("", 11f, ui.palette.muted)
        nameLayout.addView(previewName, LinearLayout.LayoutParams(-1, -2))
        nameLayout.addView(previewInfo, LinearLayout.LayoutParams(-1, -2))
        header.addView(nameLayout, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(closeBtn, LinearLayout.LayoutParams(-2, -2))
        filePreviewPanel.addView(header, LinearLayout.LayoutParams(-1, -2))

        previewContent = FrameLayout(activity).apply {
            setPadding(0, ui.dp(4), 0, ui.dp(4))
        }
        previewEdit = EditText(activity).apply {
            isFocusable = false
            isClickable = true
            setTextColor(ui.palette.text)
            setHintTextColor(ui.palette.muted)
            setBackgroundColor(ui.palette.surfaceAlt)
            setPadding(ui.dp(6), ui.dp(6), ui.dp(6), ui.dp(6))
            textSize = 12f
            typeface = android.graphics.Typeface.MONOSPACE
            gravity = Gravity.TOP
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    dirtyHandler.removeCallbacks(dirtyCheck)
                    dirtyHandler.postDelayed(dirtyCheck, 250)
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        previewContent.addView(previewEdit)
        previewText = TextView(activity).apply {
            setTextColor(ui.palette.text)
            setBackgroundColor(ui.palette.surfaceAlt)
            setPadding(ui.dp(6), ui.dp(6), ui.dp(6), ui.dp(6))
            textSize = 12f
            typeface = android.graphics.Typeface.MONOSPACE
            gravity = Gravity.TOP
        }
        previewScroll = NestedScrollView(activity).apply {
            addView(previewText)
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            visibility = View.GONE
        }
        previewContent.addView(previewScroll)
        previewWeb = WebView(activity).apply {
            settings.javaScriptEnabled = true
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            settings.allowFileAccess = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.builtInZoomControls = true
            settings.setSupportZoom(true)
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            visibility = View.GONE
        }
        previewContent.addView(previewWeb)
        filePreviewPanel.addView(previewContent, LinearLayout.LayoutParams(-1, 0, 1f))

        val bottomBar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(ui.dp(4), ui.dp(2), ui.dp(4), ui.dp(2))
            background = ui.surfaceBackground()
        }
        previewHtmlToggle = action("源码") { toggleHtmlMode() }
        previewEditToggle = action("编辑") { togglePreviewEditMode() }
        previewSaveBtn = action("保存") { savePreviewContent() }
        previewDirtyDot = View(activity).apply {
            setBackgroundColor(0xFFFFD700.toInt())
            val s = ui.dp(8)
            layoutParams = LinearLayout.LayoutParams(s, s)
            (layoutParams as LinearLayout.LayoutParams).setMargins(0, 0, ui.dp(2), 0)
            visibility = View.GONE
        }
        bottomBar.addView(previewHtmlToggle)
        bottomBar.addView(previewEditToggle)
        bottomBar.addView(previewDirtyDot)
        bottomBar.addView(previewSaveBtn)
        filePreviewPanel.addView(bottomBar, LinearLayout.LayoutParams(-1, -2))
    }

    private fun buildFileActionBar() {
        fileActionInfo = ui.text("", 12f, ui.palette.text).apply {
            gravity = Gravity.CENTER
            setPadding(ui.dp(8), 0, ui.dp(8), 0)
        }
        fileActionBar = HorizontalScrollView(activity).apply {
            isFillViewport = true
            visibility = View.GONE
            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(ui.dp(4), 0, ui.dp(4), 0)
                background = ui.surfaceBackground()
                addView(action("\u2715 \u53D6\u6D88") { exitMultiMode() })
                addView(fileActionInfo, LinearLayout.LayoutParams(0, -1, 1f))
                addView(action("\u5168\u9009") { toggleSelectAll() })
                addView(action("\u53CD\u9009") { invertSelection() })
                addView(action("\u5220\u9664") { deleteSelected() })
            })
        }
    }

    private fun showFilePreview(file: File, editMode: Boolean = false) {
        if (!file.isFile) return
        previewFile = file
        isPreviewEditMode = editMode
        isPreviewViewMode = false
        isHtmlSourceMode = false
        isPreviewDirty = false
        pathBar.visibility = View.GONE
        modeToggle.visibility = View.GONE
        contentContainer?.visibility = View.GONE
        filePreviewPanel.visibility = View.VISIBLE
        previewEdit.visibility = View.GONE
        previewScroll.visibility = View.VISIBLE
        previewWeb.visibility = View.GONE
        val isHtml = isHtmlFile(file)
        previewHtmlToggle.visibility = if (isHtml || isEnhancedFile(file)) View.VISIBLE else View.GONE
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { file.readText() }.getOrElse { "无法读取：${it.message}" }
            }
            previewOriginalText = text
            previewText.text = text
            previewEdit.setText(text)
            previewEdit.setSelection(0)
            previewEdit.isFocusable = false
            previewEdit.isClickable = true
            previewLineCount = text.count { it == '\n' } + (if (text.isNotEmpty()) 1 else 0)
            val trunc = text.length > 512 * 1024 && isEnhancedFile(file) && !isHtml
            updatePreviewHeader(file, text, trunc)
            when {
                isHtml && !editMode -> showHtmlRender(file)
                isEnhancedFile(file) && !editMode -> loadEnhancedPreview(file, text)
            }
            updatePreviewButtons()
        }
    }

    private fun closeFilePreview() {
        if (isPreviewDirty && previewFile != null) {
            MaterialAlertDialogBuilder(activity)
                .setTitle("未保存的修改")
                .setMessage("文件 \"${previewFile?.name}\" 已修改，是否保存？")
                .setPositiveButton("保存") { _, _ -> savePreviewContent(after = { doClosePreview() }) }
                .setNegativeButton("不保存") { _, _ -> doClosePreview() }
                .setNeutralButton("取消", null)
                .show()
        } else {
            doClosePreview()
        }
    }

    private fun doClosePreview() {
        pathBar.visibility = View.VISIBLE
        modeToggle.visibility = View.VISIBLE
        filePreviewPanel.visibility = View.GONE
        contentContainer?.visibility = View.VISIBLE
        previewFile = null
        previewText.text = ""
        previewEdit.setText("")
        previewEdit.isFocusable = false
        previewWeb.loadUrl("about:blank")
        previewWeb.visibility = View.GONE
        previewEdit.visibility = View.VISIBLE
        previewScroll.visibility = View.GONE
        isPreviewViewMode = false
        isHtmlSourceMode = false
        isPreviewDirty = false
    }

    private fun showHtmlRender(file: File?) {
        val f = file ?: return
        isPreviewViewMode = true
        isHtmlSourceMode = false
        previewScroll.visibility = View.GONE
        previewEdit.visibility = View.GONE
        previewWeb.visibility = View.VISIBLE
        previewWeb.loadUrl("file://${f.absolutePath}")
        updatePreviewButtons()
    }

    private fun toggleHtmlMode() {
        val file = previewFile ?: return
        if (isHtmlFile(file)) {
            if (isPreviewViewMode && !isHtmlSourceMode) {
                isHtmlSourceMode = true
                loadEnhancedPreview(file, previewEdit.text.toString())
            } else {
                showHtmlRender(file)
            }
        } else if (isPreviewViewMode) {
            isPreviewViewMode = false
            previewWeb.visibility = View.GONE
            previewScroll.visibility = View.VISIBLE
            previewEdit.visibility = View.GONE
        } else {
            loadEnhancedPreview(file, previewEdit.text.toString())
        }
        updatePreviewButtons()
    }

    private fun togglePreviewEditMode() {
        val file = previewFile ?: return
        if (isPreviewViewMode) {
            isPreviewViewMode = false
            previewWeb.visibility = View.GONE
            previewScroll.visibility = View.GONE
            previewEdit.visibility = View.VISIBLE
            isPreviewEditMode = true
            previewEdit.isFocusable = true
            previewEdit.isFocusableInTouchMode = true
            previewEdit.requestFocus()
        } else if (isPreviewEditMode) {
            isPreviewEditMode = false
            previewEdit.isFocusable = false
            previewEdit.isClickable = true
            when {
                isHtmlFile(file) -> showHtmlRender(file)
                isEnhancedFile(file) -> loadEnhancedPreview(file, previewEdit.text.toString())
                else -> {
                    previewText.text = previewEdit.text.toString()
                    previewEdit.visibility = View.GONE
                    previewScroll.visibility = View.VISIBLE
                }
            }
        } else {
            previewScroll.visibility = View.GONE
            previewEdit.visibility = View.VISIBLE
            previewEdit.setText(previewText.text)
            previewEdit.setSelection(previewEdit.text?.length ?: 0)
            isPreviewEditMode = true
            previewEdit.isFocusable = true
            previewEdit.isFocusableInTouchMode = true
            previewEdit.requestFocus()
        }
        updatePreviewButtons()
    }

    private fun savePreviewContent(after: (() -> Unit)? = null) {
        val file = previewFile ?: return
        val text = previewEdit.text.toString()
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { file.writeText(text) }.isSuccess
            }
            if (ok) {
                isPreviewDirty = false
                previewOriginalText = text
                isPreviewEditMode = false
                previewEdit.isFocusable = false
                if (::projectTree.isInitialized) projectTree.refresh()
                toast("已保存")
                after?.invoke()
            } else {
                toast("保存失败")
            }
            updatePreviewButtons()
        }
    }

    private fun updatePreviewHeader(file: File, text: String, truncated: Boolean = false) {
        val icon = if (file.name.endsWith(".html", ignoreCase = true) || file.name.endsWith(".htm", ignoreCase = true)) "\uD83C\uDF10" else "\uD83D\uDCC4"
        previewName.text = "$icon ${file.name}"
        val kb = formatSize(file.length())
        val mod = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(file.lastModified())
        previewInfo.text = "$kb · ${previewLineCount}行 · $mod${if (truncated) " · 截断至512KB" else ""}"
    }

    private fun updatePreviewButtons() {
        val file = previewFile
        val html = isHtmlFile(file)
        val enhanced = !html && isEnhancedFile(file)
        val showViewToggle = html || enhanced
        previewHtmlToggle.visibility = if (showViewToggle) View.VISIBLE else View.GONE
        (previewHtmlToggle as? TextView)?.let {
            if (html) it.text = if (isPreviewViewMode && !isHtmlSourceMode) "源码" else "渲染"
            else it.text = if (isPreviewViewMode) "源码" else "预览"
        }
        (previewEditToggle as? TextView)?.text = when {
            isPreviewViewMode -> "编辑"
            isPreviewEditMode -> "预览"
            else -> "编辑"
        }
        previewSaveBtn.isEnabled = isPreviewEditMode && isPreviewDirty
        previewDirtyDot?.visibility = if (isPreviewEditMode && isPreviewDirty) View.VISIBLE else View.GONE
    }

    private fun enterMultiMode(file: File) {
        if (multiMode) return
        multiMode = true
        multiPaneSide = activeLeft
        multiSelected.clear()
        multiSelected.add(file.absolutePath)
        anchorFile = file.absolutePath
        fileActionBar.visibility = View.VISIBLE
        updateMultiInfo()
        dragLog("enterMultiMode file=${file.name} anchor=$anchorFile selected=${multiSelected.toList()}")
        refreshHighlight(true); refreshHighlight(false)
    }

    private fun exitMultiMode() {
        if (!multiMode) return
        multiMode = false
        multiPaneSide = true
        multiSelected.clear()
        anchorFile = null
        selectedFile = null
        fileActionBar.visibility = View.GONE
        refreshHighlight(true); refreshHighlight(false)
    }

    private fun toggleMultiSelect(file: File, isLeft: Boolean) {
        if (multiPaneSide != isLeft) return
        val path = file.absolutePath
        if (path in multiSelected) multiSelected.remove(path) else multiSelected.add(path)
        if (multiSelected.isEmpty()) { exitMultiMode(); return }
        updateMultiInfo()
        refreshHighlight(true); refreshHighlight(false)
    }

    private fun updateMultiInfo() {
        fileActionInfo.text = if (multiSelected.isEmpty()) "\u70B9\u51FB\u9009\u62E9\u6587\u4EF6" else "${multiSelected.size} \u9879\u5DF2\u9009"
    }

    private fun toggleSelectAll() {
        val files = getPaneFiles(if (multiMode) multiPaneSide else activeLeft).map { it.absolutePath }.toSet()
        if (files.isEmpty()) return toast("\u5F53\u524D\u76EE\u5F55\u6CA1\u6709\u6587\u4EF6")
        if (multiMode && files.all { it in multiSelected }) {
            exitMultiMode()
            return
        }
        if (!multiMode) {
            multiMode = true
            multiPaneSide = activeLeft
            fileActionBar.visibility = View.VISIBLE
        }
        multiSelected.clear()
        multiSelected.addAll(files)
        anchorFile = null
        updateMultiInfo()
        refreshHighlight(true); refreshHighlight(false)
    }

    private fun invertSelection() {
        val files = getPaneFiles(if (multiMode) multiPaneSide else activeLeft).map { it.absolutePath }.toSet()
        if (files.isEmpty()) return toast("\u5F53\u524D\u76EE\u5F55\u6CA1\u6709\u6587\u4EF6")
        if (!multiMode) {
            multiMode = true
            multiPaneSide = activeLeft
            fileActionBar.visibility = View.VISIBLE
        }
        val inverted = files - multiSelected
        if (inverted.isEmpty()) {
            exitMultiMode()
            return
        }
        multiSelected.clear()
        multiSelected.addAll(inverted)
        anchorFile = null
        updateMultiInfo()
        refreshHighlight(true); refreshHighlight(false)
    }

    private fun getPaneFiles(isLeft: Boolean): List<File> {
        val dir = if (isLeft) leftDir else rightDir
        return dir.listFiles()?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })?.toList() ?: emptyList()
    }

    private fun refreshHighlight(isLeft: Boolean) {
        val list = if (isLeft) leftList else rightList
        for (i in 0 until list.childCount) {
            val v = list.getChildAt(i) as? TextView ?: continue
            val f = v.tag as? File ?: continue
            v.setBackgroundColor(
                if (multiMode && f.absolutePath in multiSelected && multiPaneSide == isLeft) ui.palette.accent
                else if (!multiMode && selectedFile?.absolutePath == f.absolutePath && activeLeft == isLeft) ui.palette.accent
                else Color.TRANSPARENT
            )
        }
    }

    private fun rangeSelect(file: File, isLeft: Boolean) {
        if (multiPaneSide != isLeft) return
        val anchor = anchorFile ?: return
        val files = getPaneFiles(isLeft)
        val anchorIdx = files.indexOfFirst { it.absolutePath == anchor }
        val currentIdx = files.indexOfFirst { it.absolutePath == file.absolutePath }
        if (anchorIdx < 0 || currentIdx < 0) return
        var changed = false
        val range = minOf(anchorIdx, currentIdx)..maxOf(anchorIdx, currentIdx)
        for (i in range) {
            if (multiSelected.add(files[i].absolutePath)) changed = true
        }
        dragLog("rangeSelect anchor=${File(anchor).name}[$anchorIdx] file=${file.name}[$currentIdx] range=$range selected=${multiSelected.toList()}")
        if (changed) {
            updateMultiInfo()
            refreshHighlight(true); refreshHighlight(false)
        }
    }

    private fun deleteSelected() {
        val targets = if (multiMode) {
            if (multiSelected.isEmpty()) return toast("\u8BF7\u5148\u9009\u62E9\u6587\u4EF6")
            multiSelected.toList()
        } else {
            val src = selected() ?: return toast("\u8BF7\u5148\u9009\u62E9\u6587\u4EF6\u6216\u76EE\u5F55")
            listOf(src.absolutePath)
        }
        MaterialAlertDialogBuilder(activity)
            .setTitle("\u786E\u8BA4\u5220\u9664")
            .setMessage("\u786E\u5B9A\u5220\u9664\u8FD9 ${targets.size} \u4E2A\u6587\u4EF6/\u76EE\u5F55\uFF1F")
            .setPositiveButton("\u5220\u9664") { _, _ ->
                var fail = 0
                targets.forEach { if (!File(it).deleteRecursively()) { fail++; dragLog("delete failed: $it") } }
                if (multiMode) exitMultiMode() else clearSelection()
                reloadAll()
                toast("\u5DF2\u5220\u9664 ${targets.size} \u9879${if (fail > 0) "（${fail} 项失败）" else ""}")
            }
            .setNegativeButton("\u53D6\u6D88", null)
            .show()
    }

    private fun copyToClipbook(paths: List<String>, move: Boolean) {
        val serialized = paths.joinToString("\n")
        val label = if (move) "aidiv_move" else "aidiv_copy"
        pm.sharedPreferences.edit().putString(label, serialized).apply()
    }

    private fun hasClipboardItems(): Boolean {
        val copy = pm.sharedPreferences.getString("aidiv_copy", null)
        val move = pm.sharedPreferences.getString("aidiv_move", null)
        return !copy.isNullOrBlank() || !move.isNullOrBlank()
    }

    private fun pasteClipboard() {
        val dir = activeDir()
        val prefs = pm.sharedPreferences
        val movePaths = prefs.getString("aidiv_move", null)
        val copyPaths = prefs.getString("aidiv_copy", null)

        if (!movePaths.isNullOrBlank()) {
            val files = movePaths.lines().map { File(it) }.filter { it.exists() }
            if (files.isEmpty()) return toast("剪切板中的文件已不存在")
            var moveFail = 0
            files.forEach { src ->
                val dst = File(dir, src.name)
                runCatching { src.renameTo(dst) }.onFailure { moveFail++; dragLog("move failed: ${src.name}: ${it.message}") }
            }
            prefs.edit().remove("aidiv_move").apply()
            toast(if (moveFail > 0) "已移动 ${files.size - moveFail} 项（${moveFail} 项失败）" else "已移动 ${files.size} 项")
            reloadAll()
            return
        }
        if (!copyPaths.isNullOrBlank()) {
            val files = copyPaths.lines().map { File(it) }.filter { it.exists() }
            if (files.isEmpty()) return toast("剪贴板中的文件已不存在")
            var copyFail = 0
            files.forEach { src ->
                val dst = File(dir, src.name)
                try {
                    if (src.isDirectory) src.copyRecursively(dst, overwrite = false)
                    else src.copyTo(dst, overwrite = false)
                } catch (e: Exception) {
                    copyFail++; dragLog("copy failed: ${src.name}: ${e.message}")
                }
            }
            prefs.edit().remove("aidiv_copy").apply()
            toast(if (copyFail > 0) "已复制 ${files.size - copyFail} 项（${copyFail} 项失败）" else "已复制 ${files.size} 项")
            reloadAll()
            return
        }

        val clipMgr = activity.getSystemService(Activity.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipMgr.primaryClip
        val clipText = clip?.getItemAt(0)?.text?.toString()
        if (!clipText.isNullOrBlank()) {
            input("粘贴为文件", "clipboard.txt") { name ->
                if (name.isBlank()) return@input toast("名称不能为空")
                runCatching {
                    File(dir, name).writeText(clipText)
                    reloadAll()
                    toast("已粘贴为 $name")
                }.onFailure { toast("粘贴失败：${it.message}") }
            }
            return
        }

        toast("没有可粘贴的内容")
    }

    private fun notifyTerminalCd(dir: File) {
        val act = activity
        val home = File(act.filesDir, "home")
        val ubuntuPath = SyncCoordinator.toUbuntuPath(dir, home) ?: return
        if (act is ShellActivity) act.syncTerminalCd(ubuntuPath)
    }

    private fun copyToOther(move: Boolean) {
        val sources = if (multiMode) {
            if (multiSelected.isEmpty()) return toast("请先选择文件")
            multiSelected.toList()
        } else {
            val src = selected() ?: return toast("请先选择文件或目录")
            listOf(src.absolutePath)
        }
        val fromSide = if (multiMode) multiPaneSide else activeLeft
        val dstDir = if (fromSide) rightDir else leftDir
        val ok = sources.count { path ->
            val src = File(path)
            val dst = File(dstDir, src.name)
            if (dst.exists()) return@count false
            val ok = try {
                if (src.isDirectory) copyDir(src, dst) else src.copyTo(dst).let { true }
            } catch (e: Exception) {
                dragLog("copy error: ${src.name}: ${e.message}"); false
            }
            if (ok && move && !src.deleteRecursively()) dragLog("delete after move failed: ${src.name}")
            ok
        }
        if (multiMode) exitMultiMode() else clearSelection()
        reloadAll()
        val fail = sources.size - ok
        toast(if (fail > 0) "已完成 ${ok}/${sources.size} 项（${fail} 项失败）"
              else if (move) "已移动 ${ok} 项" else "已复制 ${ok} 项")
        copyToClipbook(sources, move)
    }

    private fun newFolder() = input("新建", "") { name ->
        if (name.isBlank()) return@input toast("名称不能为空")
        val target = File(activeDir(), name)
        val ok = if (name.contains(".")) target.createNewFile() else target.mkdir()
        toast(if (ok) "已创建" else "创建失败")
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

    private fun previewSelected() {
        val src = selected() ?: return toast("请先选择文件")
        if (!src.isFile) return toast("目录不能预览")
        when {
            src.name.endsWith(".apk", ignoreCase = true) -> showApkInfo(src)
            isImageFile(src) -> showImageInfo(src)
            isLikelyText(src) && src.length() <= 512 * 1024 -> showFilePreview(src)
            else -> showBinaryInfo(src)
        }
    }

    private fun editSelected() {
        val src = selected() ?: return toast("请先选择文本文件")
        if (!src.isFile) return toast("目录不能编辑")
        if (!isLikelyText(src)) return toast("该文件不像文本文件")
        if (src.length() > 1024 * 1024) return toast("文件过大，请用终端编辑")
        showFilePreview(src, editMode = true)
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
        MaterialAlertDialogBuilder(activity)
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
        MaterialAlertDialogBuilder(activity)
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
        pm.currentProjectPath = dir.absolutePath
        rememberRecentDir(dir)
        toast("已标记当前项目")
    }

    private fun jumpCurrentProject() {
        val path = pm.currentProjectPath
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
        pm.currentProjectPath = ""
        toast("已清除当前项目")
    }

    private fun projectOverview(dir: File) {
        val current = pm.currentProjectPath == dir.absolutePath
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
        MaterialAlertDialogBuilder(activity)
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
        MaterialAlertDialogBuilder(activity)
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
        val prefs = pm.sharedPreferences
        val rows = prefs.getString("project_action_history", "")?.lines()?.filter { it.isNotBlank() }.orEmpty().takeLast(20).reversed()
        if (rows.isEmpty()) return toast("暂无项目操作历史")
        MaterialAlertDialogBuilder(activity)
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
        MaterialAlertDialogBuilder(activity)
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
        MaterialAlertDialogBuilder(activity)
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
        MaterialAlertDialogBuilder(activity)
            .setTitle(readme.name)
            .setMessage(text)
            .setPositiveButton("编辑") { _, _ ->
                selectedFile = readme
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
        val prefs = pm.sharedPreferences
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
        MaterialAlertDialogBuilder(activity)
            .setTitle("APK 信息")
            .setMessage(body)
            .setPositiveButton("复制路径") { _, _ -> copySelectedPath() }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun showImageInfo(file: File) {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        MaterialAlertDialogBuilder(activity)
            .setTitle("图片信息")
            .setMessage("文件：${file.name}\n尺寸：${options.outWidth} × ${options.outHeight}\n类型：${options.outMimeType ?: file.extension}\n大小：${formatSize(file.length())}\n路径：${file.absolutePath}")
            .setPositiveButton("复制路径") { _, _ -> copySelectedPath() }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun showBinaryInfo(file: File) {
        MaterialAlertDialogBuilder(activity)
            .setTitle("文件信息")
            .setMessage("文件：${file.name}\n类型：${file.extension.ifBlank { "未知/二进制" }}\n大小：${formatSize(file.length())}\n路径：${file.absolutePath}\n\n该文件不适合直接作为文本预览。")
            .setPositiveButton("复制路径") { _, _ -> copySelectedPath() }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun isHtmlFile(file: File?): Boolean =
        file?.name?.let { it.endsWith(".html", ignoreCase = true) || it.endsWith(".htm", ignoreCase = true) } ?: false

    private fun isEnhancedFile(file: File?): Boolean {
        if (file == null) return false
        val ext = file.extension.lowercase()
        return ext in setOf("md", "kt", "kts", "java", "js", "mjs", "ts", "py", "rs", "go", "sh", "bash", "cpp", "cc", "c", "h", "swift", "xml", "json", "yaml", "yml", "css", "scss", "sql", "gradle", "toml", "proto", "rb", "php", "pl", "lua", "r", "dart")
    }

    private fun getHighlightLanguage(file: File?): String {
        val ext = file?.extension?.lowercase() ?: return ""
        return when (ext) {
            "kt", "kts" -> "kotlin"
            "js", "mjs" -> "javascript"
            "ts" -> "typescript"
            "py" -> "python"
            "sh", "bash" -> "bash"
            "cpp", "cc" -> "cpp"
            "md" -> "md"
            "yml" -> "yaml"
            "scss" -> "scss"
            "gradle" -> "gradle"
            "toml" -> "ini"
            else -> ext
        }
    }

    private fun loadEnhancedPreview(file: File, text: String, maxLen: Int = 512 * 1024) {
        val lang = getHighlightLanguage(file)
        val css = readAssetText("atom-one-dark.min.css")
        val hljs = readAssetText("highlight.min.js")
        val isMd = lang == "md"
        val truncated = text.length > maxLen
        val displayText = if (truncated) text.take(maxLen) + "\n\n… 文件过大，仅显示前 ${maxLen / 1024}KB" else text
        val jsLibs = if (isMd) {
            val marked = readAssetText("marked.min.js")
            "$hljs\n$marked"
        } else hljs
        val bodyContent = if (isMd) {
            val b64 = Base64.encodeToString(displayText.toByteArray(), Base64.NO_WRAP)
            """<div id="content"></div>
<script>
var b64='$b64';var raw=atob(b64);var bytes=new Uint8Array(raw.length);
for(var i=0;i<raw.length;i++)bytes[i]=raw.charCodeAt(i);
var decoder=new TextDecoder('utf-8');
document.getElementById('content').innerHTML=marked.parse(decoder.decode(bytes));
document.querySelectorAll('pre code').forEach(function(b){hljs.highlightElement(b)});
</script>"""
        } else {
            """<pre><code class="language-$lang">${
                displayText.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            }</code></pre>
<script>
document.querySelectorAll('pre code').forEach(function(b){hljs.highlightElement(b)});
(function(){document.querySelectorAll('pre code').forEach(function(block){
  var lines=block.innerHTML.split('\n');
  block.innerHTML=lines.map(function(l){return '<span class=ln>'+(l||'&nbsp;')+'</span>';}).join('\n');
})})();
</script>"""
        }
        val lnCss = if (!isMd) """
pre code{counter-reset:ln}
pre code .ln{display:block;padding-left:3.2em;position:relative;min-height:1.2em}
pre code .ln::before{counter-increment:ln;content:counter(ln);position:absolute;left:0;width:2.6em;text-align:right;color:#636d83;user-select:none}
""" else ""
        val html = """<!DOCTYPE html>
<html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=3">
<style>$css</style>
<style>body{background:#282c34;color:#abb2bf;padding:16px;font:14px/1.6 -apple-system,BlinkMacSystemFont,monospace;overflow-x:hidden;word-wrap:break-word}
pre{background:transparent;white-space:pre-wrap;word-break:break-all;font-size:13px}
code{font-family:'Fira Code','Cascadia Code','JetBrains Mono','Droid Sans Mono',monospace}
pre code.hljs{padding:0;background:transparent}#content img{max-width:100%}
#content table{border-collapse:collapse;width:100%;margin:8px 0}
#content th,#content td{border:1px solid #4b5263;padding:6px 10px;text-align:left}
#content th{background:#3b4252}
#content blockquote{border-left:4px solid #7C3AED;padding-left:12px;color:#8f9aa8;margin:8px 0}
#content h1,#content h2,#content h3{color:#e5e9f0;margin:16px 0 8px}
#content a{color:#7C3AED}#content hr{border:0;border-top:1px solid #4b5263;margin:16px 0}
#content ul,#content ol{padding-left:20px}#content p{margin:4px 0}
$lnCss
</style>
<script>$jsLibs</script>
</head><body>$bodyContent</body></html>"""
        isPreviewViewMode = true
        previewEdit.visibility = View.GONE
        previewScroll.visibility = View.GONE
        previewWeb.visibility = View.VISIBLE
        previewWeb.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "UTF-8", null)
        updatePreviewButtons()
    }

    private fun readAssetText(name: String): String =
        runCatching { activity.assets.open(name).bufferedReader().use { it.readText() } }.getOrDefault("")

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
            scope.launch {
                val matches = withContext(Dispatchers.IO) {
                    runCatching {
                        base.walkTopDown()
                            .maxDepth(4)
                            .filter { it.name.contains(keyword, ignoreCase = true) }
                            .take(60)
                            .toList()
                    }.getOrDefault(emptyList())
                }
                withContext(Dispatchers.Main) {
                    if (matches.isEmpty()) {
                        toast("没有找到匹配文件")
                        return@withContext
                    }
                    MaterialAlertDialogBuilder(activity)
                        .setTitle("搜索结果")
                        .setItems(matches.map { it.absolutePath.removePrefix(base.absolutePath).ifBlank { it.absolutePath } }.toTypedArray()) { _, which ->
                            val file = matches[which]
                            if (file.isDirectory) {
                                if (activeLeft) leftDir = file else rightDir = file
                            } else {
                                selectedFile = file
                            }
                            loadPane(activeLeft)
                        }
                        .show()
                }
            }
        }
    }

    private fun addFavorite() {
        val set = pm.fileFavorites.toMutableSet()
        set.add(activeDir().absolutePath)
        pm.fileFavorites = set
        toast("已收藏当前路径")
    }

    private fun showFavorites() {
        val favorites = pm.fileFavorites.toList().sorted()
        if (favorites.isEmpty()) {
            toast("暂无收藏路径")
            return
        }
        MaterialAlertDialogBuilder(activity)
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
                pm.fileFavorites = emptySet()
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
        MaterialAlertDialogBuilder(activity)
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
        val recent = pm.fileRecentDirs.toMutableSet()
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
        MaterialAlertDialogBuilder(activity)
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
        val path = file.absolutePath
        val inProot = path.contains("ubuntu-rootfs")
        val onSdcard = path.startsWith("/sdcard/") || path.startsWith("/storage/")
        if (inProot) {
            toast("APK 在 PRoot 内部，请先复制到 /sdcard/")
            return
        }
        if (!onSdcard) {
            toast("APK 路径不可访问，请移至 /sdcard/ 目录")
            return
        }

        val state = ShizukuLogcat.checkState(activity)
        when (state) {
            is ShizukuState.NotInstalled -> {
                MaterialAlertDialogBuilder(activity)
                    .setTitle("需要 Shizuku")
                    .setMessage("静默安装 APK 需要 Shizuku 权限，但未检测到 Shizuku 应用。")
                    .setPositiveButton("去安装") { _, _ ->
                        runCatching {
                            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=moe.shizuku.privileged.api")))
                        }.onFailure {
                            toast("请在应用商店搜索 Shizuku")
                        }
                    }
                    .setNegativeButton("取消", null)
                    .show()
                return
            }
            is ShizukuState.NotRunning -> {
                MaterialAlertDialogBuilder(activity)
                    .setTitle("Shizuku 未运行")
                    .setMessage("请在 Shizuku 应用中启动服务后重试。")
                    .setPositiveButton("打开 Shizuku") { _, _ ->
                        activity.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")?.let {
                            activity.startActivity(it)
                        }
                    }
                    .setNegativeButton("取消", null)
                    .show()
                return
            }
            is ShizukuState.NotAuthorized -> {
                MaterialAlertDialogBuilder(activity)
                    .setTitle("Shizuku 未授权")
                    .setMessage("请在 Shizuku 应用中为本应用授予权限后重试。")
                    .setPositiveButton("打开 Shizuku") { _, _ ->
                        activity.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")?.let {
                            activity.startActivity(it)
                        }
                    }
                    .setNegativeButton("取消", null)
                    .show()
                return
            }
            is ShizukuState.Ready -> {
                MaterialAlertDialogBuilder(activity)
                    .setTitle("安装 APK")
                    .setMessage("将通过 Shizuku 静默安装：\n${file.name}")
                    .setPositiveButton("安装") { _, _ -> executeShizukuInstall(file) }
                    .setNeutralButton("诊断安装") { _, _ -> diagnoseShizukuInstall(file) }
                    .setNegativeButton("取消", null)
                    .show()
            }
        }
    }

    private fun executeShizukuInstall(file: java.io.File) {
        val srcPath = file.absolutePath.replace("'", "'\\''")
        ShizukuLogcat.executeFireAndForget("cat '$srcPath' | pm install -r -d -S \$(stat -c%s '$srcPath')")
        toast("正在安装...")
    }

    private fun diagnoseShizukuInstall(file: java.io.File) {
        val srcPath = file.absolutePath.replace("'", "'\\''")
        val cmd = "cat '$srcPath' | pm install -r -d -S \$(stat -c%s '$srcPath')"
        scope.launch {
            val result = ShizukuLogcat.executeCommand(cmd)
            val hint = ShizukuLogcat.pmInstallErrorHint(result)
            val msg = """
命令:
$cmd

退出码: ${result.exitCode}

stdout:
${result.stdout.take(500).ifBlank { "(空)" }}

stderr:
${result.stderr.take(500).ifBlank { "(空)" }}

结果解析: $hint
            """.trimIndent()
            MaterialAlertDialogBuilder(activity)
                .setTitle("诊断结果")
                .setMessage(msg)
                .setPositiveButton("确定", null)
                .show()
        }
    }

    private fun rememberRecentDir(dir: File) {
        val set = pm.fileRecentDirs.toMutableSet()
        set.add(dir.absolutePath)
        while (set.size > 80) set.remove(set.first())
        pm.fileRecentDirs = set
    }

    private fun fileActions(file: File) {
        val items = mutableListOf(
            "预览", "编辑", "另存为", "标记当前项目",
            "跳到当前项目", "项目识别", "项目工作区",
            "复制路径", "复制到对侧", "移动到对侧", "重命名", "删除"
        )
        if (file.isDirectory && pm.fileLayoutMode == "tree") {
            items.add(0, "新建文件夹")
            items.add(0, "新建文件")
        }
        if (file.isDirectory) items.add(0, "cd 到终端")
        MaterialAlertDialogBuilder(activity)
            .setTitle(file.name)
            .setItems(items.toTypedArray()) { _, which ->
                when (items[which]) {
                    "cd 到终端" -> notifyTerminalCd(file)
                    "新建文件" -> input("新建文件", "file.txt") { name ->
                        if (!name.contains("/") && File(file, name).createNewFile()) {
                            if (::projectTree.isInitialized) projectTree.refresh()
                            toast("已创建")
                        } else {
                            toast("创建失败")
                        }
                    }
                    "新建文件夹" -> input("新建文件夹", "folder") { name ->
                        if (File(file, name).mkdir()) {
                            if (::projectTree.isInitialized) projectTree.refresh()
                            toast("已创建")
                        } else {
                            toast("创建失败")
                        }
                    }
                    "预览" -> previewSelected()
                    "编辑" -> editSelected()
                    "另存为" -> saveSelectedAs()
                    "标记当前项目" -> markCurrentProject()
                    "跳到当前项目" -> jumpCurrentProject()
                    "项目识别" -> inspectProject()
                    "项目工作区" -> projectWorkspace()
                    "复制路径" -> copySelectedPath()
                    "复制到对侧" -> copyToOther(false)
                    "移动到对侧" -> copyToOther(true)
                    "重命名" -> renameSelected()
                    "删除" -> deleteSelected()
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
        MaterialAlertDialogBuilder(activity).setTitle(title).setView(edit).setPositiveButton("确定") { _, _ ->
            val text = edit.text.toString().trim()
            if (text.isNotEmpty() && !text.contains("/")) cb(text)
        }.setNegativeButton("取消", null).show()
    }

    private fun inputAllowAny(title: String, hint: String, cb: (String) -> Unit) {
        val edit = EditText(activity).apply {
            setHint(hint)
        }
        MaterialAlertDialogBuilder(activity).setTitle(title).setView(edit).setPositiveButton("确定") { _, _ ->
            val text = edit.text.toString().trim()
            if (text.isNotEmpty()) cb(text)
        }.setNegativeButton("取消", null).show()
    }

    private fun action(label: String, click: () -> Unit): View =
        TextView(activity).apply {
            text = label
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(ui.palette.text)
            includeFontPadding = false
            setPadding(ui.dp(12), 0, ui.dp(12), 0)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFF1C2430.toInt())
                cornerRadius = ui.dp(9).toFloat()
                setStroke(ui.dp(1), 0xFF334155.toInt())
            }
            layoutParams = LinearLayout.LayoutParams(-2, ui.dp(34)).apply {
                setMargins(ui.dp(3), 0, ui.dp(3), 0)
            }
            setOnClickListener { ui.pulse(); click() }
        }

    private fun paneHeaderBg(): android.graphics.drawable.GradientDrawable =
        android.graphics.drawable.GradientDrawable().apply {
            setColor(0x1A7C3AED)
            cornerRadius = ui.dp(10).toFloat()
            setStroke(ui.dp(1), ui.palette.outline)
        }

    private fun copyDir(src: File, dst: File): Boolean = try {
        dst.mkdirs()
        src.listFiles()?.all { child ->
            if (child.isDirectory) copyDir(child, File(dst, child.name))
            else child.copyTo(File(dst, child.name), overwrite = false).let { true }
        } ?: true
    } catch (e: Exception) {
        dragLog("copyDir error: ${src.name} → ${dst.name}: ${e.message}")
        false
    }

    private fun clearSelection() {
        selectedFile = null
    }

    private fun openFile(file: File) {
        if (!file.isFile) return
        val now = System.currentTimeMillis()
        if (now - lastOpenTime < 500 && file == lastOpenFile) return
        lastOpenTime = now
        lastOpenFile = file
        selectedFile = file
        if (file.name.endsWith(".apk", ignoreCase = true)) { showApkInfo(file); return }
        if (isImageFile(file)) { showImageInfo(file); return }
        if (file.length() > 5 * 1024 * 1024) { showBinaryInfo(file); return }
        scope.launch {
            val isText = withContext(Dispatchers.IO) { isLikelyText(file) }
            withContext(Dispatchers.Main) {
                if (isText) showFilePreview(file) else showBinaryInfo(file)
            }
        }
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
        ClipboardHelper.copy(activity, label, text)
    }
    private fun toast(text: String) = Toast.makeText(activity, text, Toast.LENGTH_SHORT).show()
}
