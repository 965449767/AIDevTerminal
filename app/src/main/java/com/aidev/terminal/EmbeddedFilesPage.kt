package com.aidev.terminal
import com.google.android.material.dialog.MaterialAlertDialogBuilder

import android.animation.Animator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.drawable.Drawable
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
import android.text.TextWatcher
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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.core.widget.NestedScrollView
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

class EmbeddedFilesPage : ShellPage, FilePageHost {
    private lateinit var activity: Activity
    private lateinit var ui: AIDevUi
    private lateinit var leftList: LinearLayout
    private lateinit var rightList: LinearLayout
    private lateinit var pathBar: LinearLayout
    private lateinit var pathBarChipRow: LinearLayout
    private lateinit var pathBarStatus: TextView
    private lateinit var loadingSpinner: ProgressBar
    private var searchInput: EditText? = null
    private var filterRow: LinearLayout? = null
    private var filterMode: String = "all"
    private var leftSwipeRefresh: SwipeRefreshLayout? = null
    private var rightSwipeRefresh: SwipeRefreshLayout? = null
    private lateinit var splitView: View
    private lateinit var treeView: View
    private lateinit var projectTree: ProjectTreeView
    private lateinit var gf: GestureFeedbackManager
    private lateinit var modeToggle: View
    private var collapseBtn: View? = null
    private val swipeMaxDp = 200
    private val swipeActionThreshold = 80
    private val swipePeekThreshold = 60
    private var toolbarActions: View? = null
    private var treeContainer: View? = null
    private lateinit var previewMgr: PreviewManager
    private val filePreviewPanel get() = previewMgr.panel
    private val previewName get() = previewMgr.nameView
    private val previewInfo get() = previewMgr.infoView
    private val previewContent get() = previewMgr.contentFrame
    private val previewEdit get() = previewMgr.editText
    private val previewScroll get() = previewMgr.scrollView
    private val previewText get() = previewMgr.plainText
    private val previewWeb get() = previewMgr.webView
    private val previewImage get() = previewMgr.imageView
    private val previewNavPrev get() = previewMgr.navPrev
    private val previewNavNext get() = previewMgr.navNext
    private val previewDirtyDot get() = previewMgr.dirtyDot
    private val previewHtmlToggle get() = previewMgr.htmlToggle
    private val previewEditToggle get() = previewMgr.editToggle
    private val previewSaveBtn get() = previewMgr.saveBtn
    private val previewTabRow get() = previewMgr.tabRow
    private val previewInfoPanel get() = previewMgr.infoPanel
    private val previewPermsPanel get() = previewMgr.permsPanel
    private var previewImageBitmap get() = previewMgr.imageBitmap
        set(v) { previewMgr.imageBitmap = v }
    private var previewImageWidth get() = previewMgr.imageWidth
        set(v) { previewMgr.imageWidth = v }
    private var previewImageHeight get() = previewMgr.imageHeight
        set(v) { previewMgr.imageHeight = v }
    private var previewTabIndex get() = previewMgr.tabIndex
        set(v) { previewMgr.tabIndex = v }
    private var previewFileList get() = previewMgr.fileList
        set(v) { previewMgr.fileList = v }
    private var previewInfoExtra: String = ""
    private val dirtyHandler = Handler(Looper.getMainLooper())
    private val dirtyCheck = object : Runnable {
        override fun run() {
            val current = previewEdit.text.toString()
            isPreviewDirty = previewFile != null && current != previewOriginalText
            updatePreviewButtons()
        }
    }
    private var contentContainer: FrameLayout? = null
    private lateinit var fileActionBar: HorizontalScrollView
    private lateinit var fileActionInfo: TextView
    private lateinit var dragDropBar: View
    private var leftPane: View? = null
    private var rightPane: View? = null
    private var currentAnimators: MutableMap<View, android.animation.ValueAnimator> = mutableMapOf()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _state = MutableStateFlow(FilePageState(
        activeLeft = true,
        leftDir = Environment.getExternalStorageDirectory(),
        rightDir = File("/"),
        selectedFile = null,
        multiMode = false,
        multiPaneSide = true
    ))
    private val state get() = _state.value
    private var activeLeft: Boolean
        get() = state.activeLeft
        set(v) { _state.update { it.copy(activeLeft = v) } }
    private var leftDir: File
        get() = state.leftDir
        set(v) { _state.update { it.copy(leftDir = v) } }
    private var rightDir: File
        get() = state.rightDir
        set(v) { _state.update { it.copy(rightDir = v) } }
    private var selectedFile: File?
        get() = state.selectedFile
        set(v) { _state.update { it.copy(selectedFile = v) } }
    private var multiMode: Boolean
        get() = state.multiMode
        set(v) { _state.update { it.copy(multiMode = v) } }
    private var multiPaneSide: Boolean
        get() = state.multiPaneSide
        set(v) { _state.update { it.copy(multiPaneSide = v) } }
    private var isPreviewViewMode: Boolean
        get() = state.isPreviewViewMode
        set(v) { _state.update { it.copy(isPreviewViewMode = v) } }
    private var isHtmlSourceMode: Boolean
        get() = state.isHtmlSourceMode
        set(v) { _state.update { it.copy(isHtmlSourceMode = v) } }
    private var isPreviewEditMode: Boolean
        get() = state.isPreviewEditMode
        set(v) { _state.update { it.copy(isPreviewEditMode = v) } }
    private var previewFile: File?
        get() = state.previewFile
        set(v) { _state.update { it.copy(previewFile = v) } }
    private var previewOriginalText: String
        get() = state.previewOriginalText
        set(v) { _state.update { it.copy(previewOriginalText = v) } }
    private var isPreviewDirty: Boolean
        get() = state.isPreviewDirty
        set(v) { _state.update { it.copy(isPreviewDirty = v) } }
    private var previewLineCount: Int
        get() = state.previewLineCount
        set(v) { _state.update { it.copy(previewLineCount = v) } }
    private var lastOpenTime: Long
        get() = state.lastOpenTime
        set(v) { _state.update { it.copy(lastOpenTime = v) } }
    private var lastOpenFile: File?
        get() = state.lastOpenFile
        set(v) { _state.update { it.copy(lastOpenFile = v) } }
    private var pendingSyncPath: String?
        get() = state.pendingSyncPath
        set(v) { _state.update { it.copy(pendingSyncPath = v) } }
    private var multiSelected: Set<String>
        get() = state.multiSelected
        set(v) { _state.update { it.copy(multiSelected = v) } }
    private var anchorFile: String?
        get() = state.anchorFile
        set(v) { _state.update { it.copy(anchorFile = v) } }
    private val pm by lazy { PreferencesManager(activity) }
    private val projectTools = ProjectToolsHelper(this)
    private val fileOps = FileOperationsHelper(this)
    private val navHelper = NavigationHelper(this)
    private val navHandler = NavigationHandler(this)
    private val multiSelectHandler = MultiSelectHandler(this)
    private val paneBg: GradientDrawable by lazy {
        val c = (ui.palette.surface and 0x00FFFFFF) or (0xCC000000.toInt())
        GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(c, c)).apply {
            cornerRadius = ui.dp(6).toFloat()
        }
    }

    private fun workspaceDir(activity: Activity): File =
        File(activity.filesDir, "home/ubuntu-rootfs/root/Workspace")

    override fun create(activity: Activity, ui: AIDevUi, host: ShellHost): View {
        this.activity = activity
        this.ui = ui
        gf = GestureFeedbackManager(activity, pm.sharedPreferences)
        runCatching {
            workspaceDir(activity).mkdirs()
            File(workspaceDir(activity), "Android").mkdirs()
        }
        _state.update { it.copy(leftDir = workspaceDir(activity)) }
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8))
        }
        root.addView(toolbar(host), LinearLayout.LayoutParams(-1, ui.dp(42)))
        pathBar = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = paneHeaderBg()
            setPadding(ui.dp(4), ui.dp(2), ui.dp(6), ui.dp(2))
            val chipRow = FrameLayout(activity).apply {
                val scroll = HorizontalScrollView(activity).apply {
                    isHorizontalScrollBarEnabled = false
                    clipToPadding = false
                    setPadding(ui.dp(4), 0, ui.dp(4), 0)
                    addView(LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL
                    }.also { pathBarChipRow = it })
                }
                addView(scroll, FrameLayout.LayoutParams(-1, -2))
                loadingSpinner = ProgressBar(activity, null, android.R.attr.progressBarStyleSmall).apply {
                    layoutParams = FrameLayout.LayoutParams(ui.dp(16), ui.dp(16), Gravity.RIGHT or Gravity.CENTER_VERTICAL)
                    visibility = View.GONE
                }
                addView(loadingSpinner)
            }
            addView(chipRow, LinearLayout.LayoutParams(-1, -2))
            pathBarStatus = ui.text("", 10f, ui.palette.muted).apply {
                setPadding(ui.dp(6), ui.dp(2), ui.dp(6), ui.dp(2))
                maxLines = 1
            }
            addView(pathBarStatus)
        }
        root.addView(pathBar, LinearLayout.LayoutParams(-1, -2).apply { setMargins(ui.dp(4), 0, ui.dp(4), ui.dp(8)) })
        root.addView(LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(4), 0, ui.dp(4), 0)
            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = paneHeaderBg()
                setPadding(ui.dp(4), ui.dp(2), ui.dp(4), ui.dp(2))
                val edit = EditText(activity).apply {
                    hint = "搜索文件..."
                    textSize = 12f
                    setTextColor(ui.palette.text)
                    setHintTextColor(ui.palette.muted)
                    setBackgroundColor(Color.TRANSPARENT)
                    setPadding(ui.dp(6), 0, ui.dp(6), 0)
                    addTextChangedListener(object : TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                        override fun afterTextChanged(s: Editable?) { applyFilter(); applyFilter(false) }
                    })
                }
                searchInput = edit
                addView(edit, LinearLayout.LayoutParams(0, -1, 1f))
                val clearBtn = ImageView(activity).apply {
                    layoutParams = LinearLayout.LayoutParams(ui.dp(22), ui.dp(22)).apply { setMargins(0, 0, ui.dp(4), 0) }
                    setImageResource(R.drawable.ic_empty_file)
                    setColorFilter(ui.palette.muted, PorterDuff.Mode.SRC_IN)
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                    visibility = View.GONE
                    setOnClickListener { searchInput?.setText("") }
                }
                addView(clearBtn)
                edit.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                    override fun afterTextChanged(s: Editable?) { clearBtn.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE }
                })
            }, LinearLayout.LayoutParams(-1, ui.dp(36)))
            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                val tabs = listOf("全部" to "all", "文件夹" to "folder", "文件" to "file")
                for ((label, mode) in tabs) {
                    val btn = ui.text(label, 11f, ui.palette.text).apply {
                        setPadding(ui.dp(8), ui.dp(3), ui.dp(8), ui.dp(3))
                        setOnClickListener {
                            filterMode = mode
                            updateFilterTabs()
                            applyFilter()
                            applyFilter(false)
                        }
                    }
                    addView(btn)
                    if (mode == "all") updateFilterTabs()
                }
            }.also { filterRow = it }, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, ui.dp(4)) })
        }, LinearLayout.LayoutParams(-1, -2).apply { setMargins(ui.dp(4), 0, ui.dp(4), ui.dp(4)) })
        splitView = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }.also { panes ->
            leftList = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
            rightList = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
            panes.addView(pane(true), LinearLayout.LayoutParams(0, -1, 1f).apply { setMargins(0, 0, ui.dp(4), 0) })
            panes.addView(pane(false), LinearLayout.LayoutParams(0, -1, 1f).apply { setMargins(ui.dp(4), 0, 0, 0) })
        }
        projectTree = ProjectTreeView(activity, ui).apply {
            setCallbacks(
                dirClick = { file -> fileOps.notifyTerminalCd(file)
                    },
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
        contentContainer?.let { root.addView(it, LinearLayout.LayoutParams(-1, 0, 1f)) }
        previewMgr = PreviewManager(activity, ui, contentContainer!!)
        val textWatcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                dirtyHandler.removeCallbacks(dirtyCheck)
                dirtyHandler.postDelayed(dirtyCheck, 250)
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        }
        previewMgr.build(textWatcher) { i -> switchPreviewTab(i) }
        previewMgr.onNavigate = { dir -> previewNav(dir) }
        previewMgr.onClose = { closeFilePreview() }
        buildFileActionBar()
        root.addView(fileActionBar, LinearLayout.LayoutParams(-1, ui.dp(42)))
        buildDragDropBar()
        contentContainer?.addView(dragDropBar)
        reloadAll()
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
                    addView(action("复制") { fileOps.copyToOther(false) })
                    addView(action("移动") { fileOps.copyToOther(true) })
                    addView(action("新建") { fileOps.newFolder() })
                    addView(action("粘贴") { fileOps.pasteClipboard() })
                    addView(action("搜索") { navHelper.searchActiveDir() })
                    addView(action("更多") { showFileMoreMenu(host) })
                }.also { toolbarActions = it })
            }
            addView(rightScroll, FrameLayout.LayoutParams(-2, -1, Gravity.RIGHT or Gravity.CENTER_VERTICAL))
        }

    private fun showFileMoreMenu(host: ShellHost) {
        val actions = listOf(
            "文件 · 信息" to { previewSelected() },
            "文件 · 编辑" to { editSelected() },
            "文件 · 重命名" to { fileOps.renameSelected() },
            "文件 · 另存" to { fileOps.saveSelectedAs() },
            "文件 · 复制路径" to { copySelectedPath() },
            "位置 · 收藏当前目录" to { navHelper.addFavorite() },
            "位置 · 收藏/跳转" to { navHelper.showFavorites() },
            "位置 · 常用目录" to { navHelper.showQuickDirs() },
            "位置 · 最近项目" to { navHelper.showRecentProjects() },
            "系统 · 安装 APK" to { installSelectedApk(host) },
            "系统 · 清空回收站" to { fileOps.emptyTrash() }
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
                        val paths = event.localState as? List<*>
                        if (paths != null) showFloatingPills(paths.mapNotNull { it as? String })
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
                    DragEvent.ACTION_DRAG_LOCATION -> {
                        updateDragPosition(v, event.x, event.y)
                        true
                    }
                    DragEvent.ACTION_DRAG_EXITED -> {
                        v.background = paneBg
                        true
                    }
                    DragEvent.ACTION_DROP -> {
                        hideFloatingPills()
                        val paths = event.localState as? List<*>
                        dragLog("DROP isLeft=$isLeft paths=${paths?.joinToString(",")}")
                        if (paths != null) {
                            val srcPaths = paths.mapNotNull { it as? String }
                            if (srcPaths.any { File(it).isDirectory }) {
                                scope.launch {
                                    val ok = withContext(Dispatchers.IO) {
                                        srcPaths.all { path ->
                                            val src = File(path)
                                            val dst = File(if (isLeft) leftDir else rightDir, src.name)
                                            src.exists() && !dst.exists() && runCatching {
                                                fileOps.copyDir(src, dst)
                                                true
                                            }.getOrDefault(false)
                                        }
                                    }
                                    dragLog("DROP async ok=$ok")
                                    if (ok) { gf.confirm(); loadPane(isLeft); updatePathBar() }
                                }
                            } else {
                                val ok = srcPaths.all { path ->
                                    val src = File(path)
                                    val dst = File(if (isLeft) leftDir else rightDir, src.name)
                                    src.exists() && !dst.exists() && runCatching {
                                        src.copyTo(dst)
                                        true
                                    }.getOrDefault(false)
                                }
                                dragLog("DROP ok=$ok")
                                if (ok) { gf.confirm(); loadPane(isLeft); updatePathBar() }
                            }
                            true
                        } else {
                            dragLog("DROP localState null or not List")
                            false
                        }
                    }
                    DragEvent.ACTION_DRAG_ENDED -> {
                        v.background = ui.surfaceBackground()
                        hideFloatingPills()
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
        outer.addView(SwipeRefreshLayout(activity).apply {
            setOnRefreshListener { reloadAll() }
            setColorSchemeColors(0xFF7C3AED.toInt())
            setProgressBackgroundColorSchemeColor(ui.palette.surface)
            if (isLeft) leftSwipeRefresh = this else rightSwipeRefresh = this
            addView(ScrollView(activity).apply {
                setOnTouchListener { v, event ->
                    if (!multiMode && event.action == MotionEvent.ACTION_DOWN && activeLeft != isLeft) {
                        activeLeft = isLeft
                        selectedFile = null
                        ui.pulse()
                        updatePathBar()
                        updatePaneHighlight()
                        refreshHighlight(true); refreshHighlight(false)
                    }
                    if (event.action == MotionEvent.ACTION_UP) v.performClick()
                    false
                }
                isFillViewport = true
                addView(list)
            }, LinearLayout.LayoutParams(-1, 0, 1f))
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
        loadingSpinner.visibility = View.VISIBLE
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
            loadingSpinner.visibility = View.GONE
            return
        }
        val total = files.size
        if (total == 0) {
            list.addView(emptyState())
        } else {
            files.take(300).forEach { list.addView(row(label(it), it, isLeft, false)) }
            if (total > 300) {
                list.addView(info("共 $total 项，仅显示前 300 项"))
            }
        }
        updatePathBar()
        loadingSpinner.visibility = View.GONE
        (if (isLeft) leftSwipeRefresh else rightSwipeRefresh)?.isRefreshing = false
        filterMode = "all"
        updateFilterTabs()
        searchInput?.setText("")
        applyFilter(isLeft)
        applyFilter(!isLeft)
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
        pathBarStatus.text = "${status}  ${storage}"
        pathBarChipRow.removeAllViews()
        val home = File(activity.filesDir, "home")
        val displayPath = PathBridge.androidToUbuntu(home, dir)
        if (displayPath != null) {
            val parts = displayPath.removePrefix("/").split("/")
            var running = home.parentFile!!
            addChip("~", home)
            for (part in parts) {
                addSep()
                running = File(running, part)
                addChip(part, running)
            }
        } else {
            val parts = mutableListOf<Pair<String, File>>()
            var current = dir
            while (true) {
                parts.add(current.name.ifEmpty { "/" } to current)
                current = current.parentFile ?: break
            }
            parts.reverse()
            for ((name, target) in parts) {
                if (target != File("/")) addSep()
                addChip(name, target)
            }
        }
    }

    private fun addChip(label: String, target: File) {
        val chip = ui.text(label, 12f, ui.palette.accent, bold = true).apply {
            setPadding(ui.dp(3), ui.dp(4), ui.dp(3), ui.dp(4))
            setTextColor(ui.palette.accent)
            minimumWidth = 0
            background = GradientDrawable().apply {
                setColor(0x1A7C3AED.toInt())
                cornerRadius = ui.dp(4).toFloat()
            }
            setOnClickListener {
                if (target.exists() && target.isDirectory) {
                    if (activeLeft) leftDir = target else rightDir = target
                    updatePaneHighlight(); reloadAll()
                }
            }
        }
        pathBarChipRow.addView(chip)
    }

    private fun addSep() {
        val sep = ui.text("/", 12f, ui.palette.muted).apply { setPadding(ui.dp(2), 0, ui.dp(2), 0) }
        pathBarChipRow.addView(sep)
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

    private fun row(text: String, file: File, isLeft: Boolean, parent: Boolean): View {
        val content = TextView(activity).apply {
            this.text = text
            textSize = 12f
            setTextColor(ui.palette.text)
            setPadding(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8))
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.MIDDLE
            includeFontPadding = false
            setBackgroundColor(Color.TRANSPARENT)
            outlineProvider = ViewOutlineProvider.BOUNDS
            tag = "row_content"
            pivotY = 0f
        }

        val actionOverlay = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL or Gravity.RIGHT
            visibility = View.GONE
            setPadding(0, 0, ui.dp(4), 0)
            addView(swipeActionBtn("📋", 0xFF3B82F6.toInt()) {
                selectedFile = file; activeLeft = isLeft; updatePaneHighlight()
                if (!parent) fileOps.copyToOther(false)
                gf.snapSpring(content, 0f, 0f)
            })
            addView(swipeActionBtn("🗑", 0xFFEF4444.toInt()) {
                selectedFile = file; activeLeft = isLeft; updatePaneHighlight()
                fileOps.deleteSelected()
                gf.snapSpring(content, 0f, 0f)
            })
            addView(swipeActionBtn("⋯", 0xFF6366F1.toInt()) {
                selectedFile = file; activeLeft = isLeft; updatePaneHighlight()
                fileActions(file)
                gf.snapSpring(content, 0f, 0f)
            })
        }

        val previewOverlay = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            val info = if (file.isDirectory) "📁 ${file.name}" else "📄 ${file.name}  ${formatSize(file.length())}"
            addView(ui.text(info, 10f, ui.palette.accent).apply {
                setPadding(ui.dp(6), 0, ui.dp(6), 0); setBackgroundColor(0x1A7C3AED); includeFontPadding = false
            })
        }

        var progressRing: View? = null
        var arcDrawable: ArcProgressDrawable? = null
        val iconSize = ui.dp(22)

        val wrapper = FrameLayout(activity).apply {
            addView(actionOverlay, FrameLayout.LayoutParams(-1, -1, Gravity.RIGHT or Gravity.CENTER_VERTICAL))
            addView(previewOverlay, FrameLayout.LayoutParams(-2, -1, Gravity.LEFT or Gravity.CENTER_VERTICAL))
            if (!parent) {
                addView(ImageView(activity).apply {
                    tag = "file_icon"
                    layoutParams = FrameLayout.LayoutParams(iconSize, iconSize, Gravity.LEFT or Gravity.CENTER_VERTICAL).apply {
                        leftMargin = ui.dp(8)
                    }
                    setImageResource(if (file.isDirectory) R.drawable.ic_doc_folder else R.drawable.ic_doc_generic)
                    setColorFilter(ui.palette.text, PorterDuff.Mode.SRC_IN)
                })
            }
            addView(content, FrameLayout.LayoutParams(-1, -1).apply {
                if (!parent) leftMargin = ui.dp(8) + iconSize + ui.dp(6)
            })
            addView(View(activity).apply {
                tag = "row_divider"
                layoutParams = FrameLayout.LayoutParams(-1, ui.dp(1), Gravity.BOTTOM).apply { leftMargin = ui.dp(8) }
                setBackgroundColor(0x18FFFFFF.toInt())
            })
            if (file.isDirectory && !parent) {
                val a = ArcProgressDrawable(0xFF7C3AED.toInt(), ui.dp(3).toFloat())
                arcDrawable = a
                val ring = View(activity).apply {
                    layoutParams = FrameLayout.LayoutParams(ui.dp(28), ui.dp(28), Gravity.RIGHT or Gravity.CENTER_VERTICAL)
                    background = a; visibility = View.GONE
                }
                addView(ring)
                progressRing = ring
            }
            tag = file
        }

        RowHandler(content, actionOverlay, previewOverlay, wrapper, file, isLeft, parent, progressRing, arcDrawable).install()
        return wrapper
    }

    private fun swipeActionBtn(label: String, bgColor: Int, click: () -> Unit): TextView =
        TextView(activity).apply {
            text = label
            textSize = 13f
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(Color.WHITE)
            val s = ui.dp(32)
            layoutParams = LinearLayout.LayoutParams(s, s).apply {
                setMargins(ui.dp(2), 0, ui.dp(2), 0)
            }
            background = GradientDrawable().apply {
                setColor(bgColor)
                cornerRadius = s.toFloat() / 2f
            }
            setOnClickListener {
                gf.tick()
                click()
            }
        }

    private fun dragLog(msg: String) {
        Log.d("DragLog", msg)
    }

    private fun selected(): File? = selectedFile
    private fun activeDir(): File = navHandler.activeDir()
    private fun otherDir(): File = navHandler.otherDir()

    private fun syncPanes() {
        navHandler.syncPanes()
    }

    override fun onBackPressed(): Boolean {
        if (navHandler.onBackPressed()) {
            ui.pulse()
            return true
        }
        return false
    }

    fun syncNavigateTo(targetDir: File) {
        android.util.Log.d("AIDEV_SYNC", "syncNavigateTo: ${targetDir.absolutePath}, exists=${targetDir.exists()}, isDir=${targetDir.isDirectory}")
        if (navHandler.syncNavigateToSplit(targetDir)) return
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

    private fun navigateTo(dir: File) {
        navHandler.navigateTo(dir)
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
            val workspace = workspaceDir(activity)
            val root = workspace.takeIf { it.isDirectory() }
                ?: ProjectDetector.findProjectRoot(home)
                ?: home
            projectTree.setRoot(root)
            val projPath = pm.currentProjectPath.takeIf { it.isNotBlank() }
            if (projPath != null) projectTree.expandTo(File(projPath))
            projectTree.restoreExpanded(pm.treeExpandedPaths)
            pendingSyncPath?.let {
                projectTree.expandTo(File(it))
                pendingSyncPath = null
            }
        }
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
                addView(action("\u5220\u9664") { fileOps.deleteSelected() })
            })
        }
    }

    private fun buildDragDropBar() {
        dragDropBar = FrameLayout(activity)
        (dragDropBar as FrameLayout).visibility = View.GONE
    }

    private var currentDragPaths: List<String>? = null
    private var pillViews = mutableListOf<View>()
    private var pillColors = intArrayOf(0xFF3B82F6.toInt(), 0xFF10B981.toInt(), 0xFFEF4444.toInt(), 0xFF6366F1.toInt())

    private fun showFloatingPills(paths: List<String>) {
        currentDragPaths = paths
        if (pillViews.isNotEmpty()) return
        val labels = listOf("\uD83D\uDCCB \u590D\u5236", "\u2702\uFE0F \u79FB\u52A8", "\uD83D\uDDD1 \u5220\u9644", "\u2139\uFE0F \u8BE6\u60C5")
        val colors = pillColors
        val actions = listOf<(List<String>) -> Unit>(
            { p -> executeDragCopy(p) }, { p -> executeDragMove(p) },
            { p -> executeDragDelete(p) }, { p -> executeDragInfo(p) }
        )
        val bar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(ui.dp(4), ui.dp(2), ui.dp(4), ui.dp(2))
            background = GradientDrawable().apply {
                setColor(0xDD1E1E2E.toInt())
                cornerRadius = ui.dp(28).toFloat()
            }
            for (i in labels.indices) {
                val color = colors[i]
                addView(TextView(activity).apply {
                    text = labels[i]; textSize = 11f; gravity = Gravity.CENTER
                    setTextColor(Color.WHITE); typeface = android.graphics.Typeface.DEFAULT_BOLD
                    includeFontPadding = false
                    setPadding(ui.dp(8), 0, ui.dp(8), 0)
                    layoutParams = LinearLayout.LayoutParams(0, ui.dp(38), 1f).apply { setMargins(ui.dp(2), 0, ui.dp(2), 0) }
                    background = GradientDrawable().apply {
                        setColor(color and 0x00FFFFFF or 0xAA000000.toInt())
                        cornerRadius = ui.dp(19).toFloat(); setStroke(ui.dp(1), color)
                    }
                    setOnDragListener { pv, event ->
                        runCatching {
                            when (event.action) {
                                DragEvent.ACTION_DRAG_STARTED -> { pv.alpha = 0.6f; true }
                                DragEvent.ACTION_DRAG_ENTERED -> {
                                    pv.alpha = 1f; gf.confirm()
                                    pv.background = GradientDrawable().apply {
                                        setColor(color); cornerRadius = ui.dp(19).toFloat()
                                    }
                                    true
                                }
                                DragEvent.ACTION_DRAG_EXITED -> {
                                    pv.alpha = 0.6f
                                    pv.background = GradientDrawable().apply {
                                        setColor(color and 0x00FFFFFF or 0xAA000000.toInt())
                                        cornerRadius = ui.dp(19).toFloat(); setStroke(ui.dp(1), color)
                                    }
                                    true
                                }
                                DragEvent.ACTION_DROP -> {
                                    val p = event.localState as? List<*>
                                    if (p != null) actions[i](p.mapNotNull { it as? String })
                                    gf.drop(); true
                                }
                                DragEvent.ACTION_DRAG_ENDED -> { pv.alpha = 1f; true }
                                else -> true
                            }
                        }.getOrDefault(true)
                    }
                })
            }
        }
        val container = dragDropBar as? FrameLayout ?: return
        container.addView(bar, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        container.visibility = View.VISIBLE
    }

    private fun hideFloatingPills() {
        val container = dragDropBar as? FrameLayout ?: return
        container.visibility = View.GONE
        container.removeAllViews()
        pillViews.clear()
        currentDragPaths = null
    }

    fun updateDragPosition(v: View, eventX: Float, eventY: Float) {
        val container = dragDropBar as? FrameLayout ?: return
        if (container.visibility != View.VISIBLE) return
        val vLoc = IntArray(2)
        v.getLocationOnScreen(vLoc)
        val parentLoc = IntArray(2)
        (container.parent as? View)?.getLocationOnScreen(parentLoc) ?: return
        val absX = vLoc[0] + eventX
        val absY = vLoc[1] + eventY
        val relX = absX - parentLoc[0] - container.width / 2f
        val relY = absY - parentLoc[1] - container.height - ui.dp(20)
        val p = container.parent as? View ?: return
        container.translationX = relX.coerceIn(0f, (p.width - container.width).coerceAtLeast(0).toFloat())
        container.translationY = relY.coerceAtLeast(ui.dp(4).toFloat())
    }

    private fun executeDragCopy(paths: List<String>) {
        val sources = paths.map { File(it) }.filter { it.exists() }; if (sources.isEmpty()) return
        val dstDir = if (activeLeft) rightDir else leftDir
        executeFileTransfer(sources, dstDir, move = false)
    }

    private fun executeDragMove(paths: List<String>) {
        val sources = paths.map { File(it) }.filter { it.exists() }; if (sources.isEmpty()) return
        val dstDir = if (activeLeft) rightDir else leftDir
        executeFileTransfer(sources, dstDir, move = true)
    }

    private fun executeFileTransfer(sources: List<File>, dstDir: File, move: Boolean) {
        val hasDirs = sources.any { it.isDirectory }
        val run = {
            sources.forEach { src ->
                val dst = File(dstDir, src.name)
                if (!dst.exists()) runCatching {
                    if (src.isDirectory) fileOps.copyDir(src, dst)
                    else src.copyTo(dst)
                    if (move && !src.deleteRecursively()) dragLog("delete after move failed: ${src.name}")
                }.onFailure { dragLog("transfer failed: ${src.name}: ${it.message}") }
            }
            gf.confirm()
            toast(if (move) "已移动 ${sources.size} 项" else "已复制 ${sources.size} 项")
            reloadAll()
        }
        if (hasDirs) scope.launch { withContext(Dispatchers.IO) { run() } } else run()
    }

    private fun executeDragDelete(paths: List<String>) {
        val files = paths.map { File(it) }.filter { it.exists() }; if (files.isEmpty()) return
        var fail = 0
        files.forEach { if (!it.deleteRecursively()) fail++ }
        gf.drop()
        toast(if (fail > 0) "已删除 ${files.size - fail} 项（${fail} 项失败）" else "已删除 ${files.size} 项")
        reloadAll()
    }

    private fun executeDragInfo(paths: List<String>) {
        val file = paths.firstOrNull()?.let { File(it) } ?: return
        if (!file.exists()) return toast("文件已不存在")
        if (file.isDirectory) toast("\uD83D\uDCC1 ${file.name}  (${file.listFiles()?.size ?: 0} 项)")
        else toast("\uD83D\uDCC4 ${file.name}  ${formatSize(file.length())}")
    }

    private fun showFilePreview(file: File, editMode: Boolean = false) {
        if (!file.isFile) return
        val isFirstShow = !previewMgr.isVisible()
        if (isFirstShow) {
            val dir = if (activeLeft) leftDir else rightDir
            previewFileList = dir.listFiles()?.filter { it.isFile && it.name != "." && it.name != ".." }?.sortedBy { it.name.lowercase() }
            previewTabIndex = 0
            switchPreviewTab(0)
        }
        previewMgr.show(isFirstShow)
        loadPreviewContent(file, editMode)
    }

    private fun loadPreviewContent(file: File, editMode: Boolean = false) {
        previewFile = file
        isPreviewEditMode = editMode
        isPreviewViewMode = false
        isHtmlSourceMode = false
        isPreviewDirty = false
        previewMgr.resetViews()
        previewInfoExtra = ""
        if (isImageFile(file)) {
            showPreviewImage(file)
            updatePreviewHeader(file, "")
            updatePreviewButtons()
            updateNavButtons()
            return
        }
        if (file.length() > 5 * 1024 * 1024) {
            showPreviewBinaryInfo(file)
            updatePreviewHeader(file, "二进制文件")
            updatePreviewButtons()
            updateNavButtons()
            return
        }
        val isHtml = isHtmlFile(file)
        previewHtmlToggle?.visibility = if (isHtml || isEnhancedFile(file)) View.VISIBLE else View.GONE
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
                else -> previewScroll.visibility = View.VISIBLE
            }
            updatePreviewButtons()
            updateNavButtons()
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
        previewMgr.hide {
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
            selectedFile = null
        }
        refreshHighlight(true); refreshHighlight(false)
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
        previewHtmlToggle?.visibility = if (showViewToggle) View.VISIBLE else View.GONE
        (previewHtmlToggle as? TextView)?.let {
            if (html) it.text = if (isPreviewViewMode && !isHtmlSourceMode) "源码" else "渲染"
            else it.text = if (isPreviewViewMode) "源码" else "预览"
        }
        (previewEditToggle as? TextView)?.text = when {
            isPreviewViewMode -> "编辑"
            isPreviewEditMode -> "预览"
            else -> "编辑"
        }
        previewSaveBtn?.isEnabled = isPreviewEditMode && isPreviewDirty
        previewDirtyDot?.visibility = if (isPreviewEditMode && isPreviewDirty) View.VISIBLE else View.GONE
    }

    private fun enterMultiMode(file: File) {
        multiSelectHandler.enterMultiMode(file)
        if (!multiMode) return
        fileActionBar.visibility = View.VISIBLE
        updateMultiInfo()
        dragLog("enterMultiMode file=${file.name} anchor=$anchorFile selected=${multiSelected.toList()}")
        refreshHighlight(true); refreshHighlight(false)
    }

    private fun exitMultiMode() {
        multiSelectHandler.exitMultiMode()
        if (multiMode) return
        fileActionBar.visibility = View.GONE
        refreshHighlight(true); refreshHighlight(false)
    }

    private fun toggleMultiSelect(file: File, isLeft: Boolean) {
        when (multiSelectHandler.toggleMultiSelect(file, isLeft)) {
            MultiSelectEvent.EXITED -> {
                fileActionBar.visibility = View.GONE
                refreshHighlight(true); refreshHighlight(false)
            }
            MultiSelectEvent.TOGGLED -> {
                updateMultiInfo()
                refreshHighlight(true); refreshHighlight(false)
            }
            else -> {}
        }
    }

    private fun updateMultiInfo() {
        fileActionInfo.text = if (multiSelected.isEmpty()) "\u70B9\u51FB\u9009\u62E9\u6587\u4EF6" else "${multiSelected.size} \u9879\u5DF2\u9009"
    }

    private fun toggleSelectAll() {
        when (multiSelectHandler.toggleSelectAll()) {
            MultiSelectEvent.TOAST_EMPTY -> toast("\u5F53\u524D\u76EE\u5F55\u6CA1\u6709\u6587\u4EF6")
            MultiSelectEvent.ENTERED -> {
                fileActionBar.visibility = View.VISIBLE
                updateMultiInfo()
                refreshHighlight(true); refreshHighlight(false)
            }
            MultiSelectEvent.EXITED -> {
                fileActionBar.visibility = View.GONE
                refreshHighlight(true); refreshHighlight(false)
            }
            else -> {}
        }
    }

    private fun invertSelection() {
        when (multiSelectHandler.invertSelection()) {
            MultiSelectEvent.TOAST_EMPTY -> toast("\u5F53\u524D\u76EE\u5F55\u6CA1\u6709\u6587\u4EF6")
            MultiSelectEvent.ENTERED -> {
                fileActionBar.visibility = View.VISIBLE
                updateMultiInfo()
                refreshHighlight(true); refreshHighlight(false)
            }
            MultiSelectEvent.EXITED -> {
                fileActionBar.visibility = View.GONE
                refreshHighlight(true); refreshHighlight(false)
            }
            else -> {}
        }
    }

    private fun getPaneFiles(isLeft: Boolean): List<File> {
        val dir = if (isLeft) leftDir else rightDir
        return dir.listFiles()?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })?.toList() ?: emptyList()
    }

    private fun refreshHighlight(isLeft: Boolean) {
        val list = if (isLeft) leftList else rightList
        for (i in 0 until list.childCount) {
            val wrapper = list.getChildAt(i)
            val f = wrapper.tag as? File ?: continue
            val selected = (multiMode && f.absolutePath in multiSelected && multiPaneSide == isLeft) ||
                (!multiMode && selectedFile?.absolutePath == f.absolutePath && activeLeft == isLeft)
            wrapper.setBackgroundColor(if (selected) 0x087C3AED.toInt() else Color.TRANSPARENT)
            val content = wrapper.findViewWithTag<View>("row_content") as? TextView
            if (content != null) {
                content.pivotY = 0f
                if (selected && multiMode) {
                    content.scaleX = 1.05f; content.scaleY = 1.05f
                } else {
                    content.scaleX = 1f; content.scaleY = 1f
                    content.translationX = 0f; content.translationY = 0f
                }
                content.setTextColor(if (selected) 0xFF7C3AED.toInt() else ui.palette.text)
            }
            val icon = wrapper.findViewWithTag<ImageView>("file_icon")
            if (icon != null) {
                icon.setColorFilter(if (selected) 0xFF7C3AED.toInt() else ui.palette.text, PorterDuff.Mode.SRC_IN)
            }
        }
    }

    private fun rangeSelect(file: File, isLeft: Boolean) {
        when (multiSelectHandler.rangeSelect(file, isLeft)) {
            MultiSelectEvent.TOGGLED -> {
                updateMultiInfo()
                refreshHighlight(true); refreshHighlight(false)
            }
            else -> {}
        }
    }

    private fun previewSelected() {
        val src = selected() ?: return toast("请先选择文件")
        if (!src.isFile) return toast("目录不能预览")
        if (src.name.endsWith(".apk", ignoreCase = true)) { projectTools.showApkInfo(src); return }
        showFilePreview(src)
    }

    private fun editSelected() {
        val src = selected() ?: return toast("请先选择文本文件")
        if (!src.isFile) return toast("目录不能编辑")
        if (src.length() > 1024 * 1024) return toast("文件过大，请用终端编辑")
        scope.launch {
            val isText = withContext(Dispatchers.IO) { isLikelyText(src) }
            if (!isText) toast("该文件不像文本文件")
            else showFilePreview(src, editMode = true)
        }
    }

    private fun isHtmlFile(file: File?): Boolean = FileUtils.isHtmlFile(file)
    private fun isEnhancedFile(file: File?): Boolean = FileUtils.isEnhancedFile(file)
    private fun getHighlightLanguage(file: File?): String = FileUtils.getHighlightLanguage(file)

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

    private fun isImageFile(file: File): Boolean = FileUtils.isImageFile(file)
    private fun isLikelyText(file: File): Boolean = FileUtils.isLikelyText(file)

    private fun showPreviewImage(file: File) {
        previewEdit.visibility = View.GONE
        previewScroll.visibility = View.GONE
        previewWeb.visibility = View.GONE
        previewImage.visibility = View.VISIBLE
        previewImage.setImageDrawable(null)
        scope.launch {
            val maxDim = ui.dp(600).coerceAtLeast(400)
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(file.absolutePath, bounds)
                    val (w, h) = (bounds.outWidth to bounds.outHeight)
                    var sample = 1
                    while (w / sample > maxDim || h / sample > maxDim) sample *= 2
                    if (sample < 1) sample = 1
                    Triple(
                        BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample }),
                        w, h
                    )
                }.getOrNull()
            }
            if (result != null) {
                val (bm, w, h) = result
                previewImageBitmap = bm
                previewImageWidth = w
                previewImageHeight = h
                previewImage.setImageBitmap(bm)
                previewImage.scaleType = ImageView.ScaleType.FIT_CENTER
                previewImage.setOnClickListener { switchPreviewTab(1) }
            } else {
                previewImage.setImageResource(R.drawable.ic_doc_generic)
                previewImage.scaleType = ImageView.ScaleType.CENTER
                previewImage.setOnClickListener(null)
            }
        }
    }

    private fun showPreviewBinaryInfo(file: File) {
        previewEdit.visibility = View.GONE
        previewScroll.visibility = View.GONE
        previewWeb.visibility = View.GONE
        previewImage.visibility = View.GONE
        previewInfoExtra = "二进制文件 · ${formatSize(file.length())}"
        switchPreviewTab(1)
    }

    private fun showInfoTab(file: File, extra: String = "") {
        previewMgr.populateInfoTab(file, extra)
    }

    private fun updateNavButtons() {
        val files = previewFileList ?: getPreviewFiles()
        val idx = files.indexOfFirst { it.absolutePath == previewFile?.absolutePath }
        previewNavPrev.alpha = if (idx > 0) 1f else 0.3f
        previewNavNext.alpha = if (idx >= 0 && idx < files.size - 1) 1f else 0.3f
    }

    private fun previewNav(dir: Int) {
        val files = previewFileList ?: getPreviewFiles()
        val idx = files.indexOfFirst { it.absolutePath == previewFile?.absolutePath }
        val target = idx + dir
        if (target < 0 || target >= files.size) return
        loadPreviewContent(files[target])
    }

    private fun getPreviewFiles(): List<File> {
        return previewFileList ?: let {
            val dir = if (activeLeft) leftDir else rightDir
            dir.listFiles()?.filter { it.isFile && it.name != "." && it.name != ".." }?.sortedBy { it.name.lowercase() }
                ?: emptyList()
        }
    }

    private fun switchPreviewTab(index: Int) {
        previewMgr.switchTab(index)
        val file = previewFile ?: return
        when (index) {
            1 -> {
                previewMgr.populateInfoTab(file, previewInfoExtra)
                previewInfoExtra = ""
            }
            2 -> previewMgr.populatePermsTab(file)
        }
    }

    private fun showPermsTab(file: File) {
        previewMgr.populatePermsTab(file)
    }

    private fun copySelectedPath() {
        val src = selected() ?: activeDir()
        copyText("AIDev 文件路径", src.absolutePath)
        toast("已复制路径")
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
                    "cd 到终端" -> fileOps.notifyTerminalCd(file)
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
                    "另存为" -> fileOps.saveSelectedAs()
                    "标记当前项目" -> projectTools.markCurrentProject()
                    "跳到当前项目" -> projectTools.jumpCurrentProject()
                    "项目识别" -> projectTools.inspectProject()
                    "项目工作区" -> projectTools.projectWorkspace()
                    "复制路径" -> copySelectedPath()
                    "复制到对侧" -> fileOps.copyToOther(false)
                    "移动到对侧" -> fileOps.copyToOther(true)
                    "重命名" -> fileOps.renameSelected()
                    "删除" -> fileOps.deleteSelected()
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
        if (file.name.endsWith(".apk", ignoreCase = true)) { projectTools.showApkInfo(file); return }
        showFilePreview(file)
    }

    private fun applyFilter(isLeft: Boolean = true) {
        val list = if (isLeft) leftList else rightList
        val keyword = searchInput?.text?.toString()?.trim()?.lowercase() ?: ""
        val showParent = keyword.isEmpty() && filterMode == "all"
        for (i in 0 until list.childCount) {
            val child = list.getChildAt(i)
            val f = child.tag as? File ?: continue
            val name = f.name.lowercase()
            val matches = keyword.isEmpty() || name.contains(keyword)
            val typeOk = filterMode == "all" || (filterMode == "folder") == f.isDirectory
            child.visibility = if (matches && typeOk) View.VISIBLE else View.GONE
        }
        val parentRow = if (list.childCount > 0) list.getChildAt(0) else null
        if (parentRow?.tag is File && (parentRow.tag as File).name == "..") {
            parentRow.visibility = if (showParent) View.VISIBLE else View.GONE
        }
    }

    private fun updateFilterTabs() {
        val row = filterRow ?: return
        val labels = listOf("全部", "文件夹", "文件")
        val modes = listOf("all", "folder", "file")
        for (i in 0 until minOf(row.childCount, labels.size)) {
            val btn = row.getChildAt(i) as? TextView ?: continue
            val active = modes[i] == filterMode
            btn.setTextColor(if (active) ui.palette.accent else ui.palette.text)
            btn.setBackgroundColor(if (active) 0x1A7C3AED.toInt() else Color.TRANSPARENT)
        }
    }

    private fun label(file: File): String = file.name + if (file.isFile) "  ${formatSize(file.length())}" else ""
    private fun formatSize(n: Long): String = FileUtils.formatSize(n)
    private fun info(text: String): TextView = ui.text(text, 12f, ui.palette.muted).apply { setPadding(ui.dp(8), ui.dp(10), ui.dp(8), ui.dp(10)) }
    private fun emptyState(): View = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(-1, ui.dp(140))
        setPadding(0, ui.dp(24), 0, ui.dp(24))
        val iconView = ImageView(activity).apply {
            setImageResource(R.drawable.ic_empty_file)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            layoutParams = LinearLayout.LayoutParams(ui.dp(64), ui.dp(64))
        }
        addView(iconView)
        addView(ui.text("此文件夹为空", 13f, ui.palette.muted).apply {
            setPadding(0, ui.dp(12), 0, 0)
            gravity = Gravity.CENTER
        })
    }
    private fun copyText(label: String, text: String) {
        ClipboardHelper.copy(activity, label, text)
    }
    private fun toast(text: String) = Toast.makeText(activity, text, Toast.LENGTH_SHORT).show()

    override fun hostActivity(): Activity = activity
    override fun hostPm(): PreferencesManager = pm
    override fun hostSelectedFile(): File? = selectedFile
    override fun hostSetSelectedFile(file: File?) { selectedFile = file }
    override fun hostActiveDir(): File = if (activeLeft) leftDir else rightDir
    override var hostActiveLeft: Boolean
        get() = activeLeft
        set(v) { activeLeft = v }
    override var hostLeftDir: File
        get() = leftDir
        set(v) { leftDir = v }
    override var hostRightDir: File
        get() = rightDir
        set(v) { rightDir = v }
    override fun hostClearSelection() { clearSelection() }
    override fun hostLoadPane(left: Boolean) { loadPane(left) }
    override fun hostReloadAll() { reloadAll() }
    override fun hostCopyText(label: String, text: String) { copyText(label, text) }
    override fun hostToast(msg: String) { toast(msg) }
    override fun hostEditSelected() { editSelected() }
    override fun hostCopySelectedPath() { copySelectedPath() }
    override fun hostRememberRecentDir(dir: File) { rememberRecentDir(dir) }
    override fun hostFormatSize(n: Long): String = formatSize(n)

    override var hostMultiMode: Boolean
        get() = multiMode
        set(v) { multiMode = v }
    override var hostMultiPaneSide: Boolean
        get() = multiPaneSide
        set(v) { multiPaneSide = v }
    override var hostMultiSelected: Set<String>
        get() = multiSelected
        set(v) { multiSelected = v }
    override var hostAnchorFile: String?
        get() = anchorFile
        set(v) { anchorFile = v }
    override fun hostGetPaneFiles(left: Boolean): List<File> = getPaneFiles(left)
    override fun hostDragLog(msg: String) { dragLog(msg) }
    override fun hostExitMultiMode() { exitMultiMode() }
    override fun hostUpdateMultiInfo() { updateMultiInfo() }
    override fun hostInputAllowAny(title: String, hint: String, cb: (String) -> Unit) { inputAllowAny(title, hint, cb) }
    override fun hostNavigateTo(dir: File) { navigateTo(dir) }

    override val hostScope: CoroutineScope get() = scope

    private inner class RowHandler(
        private val content: View,
        private val actionOverlay: View,
        private val previewOverlay: View,
        private val wrapper: FrameLayout,
        private val file: File,
        private val isLeft: Boolean,
        private val parent: Boolean,
        private val progressRing: View? = null,
        private val arcDrawable: ArcProgressDrawable? = null
    ) {
        private var swipeConsumed = false
        private var longPressTriggered = false
        private var dragStarted = false
        private var swipeOffset = 0f
        private var downX = 0f
        private var hoverAnimator: ValueAnimator? = null
        private var isHoverRunning = false

        private val maxSwipePx = ui.dp(swipeMaxDp)
        private val actionThresholdPx = ui.dp(swipeActionThreshold)
        private val peekThresholdPx = ui.dp(swipePeekThreshold)

        private lateinit var gd: GestureDetector

        fun install() {
            setupGestureDetector()
            setupTouchListener()
            if (file.isDirectory && !parent && progressRing != null && arcDrawable != null) {
                setupDragListener()
            }
        }

        private fun disallowTouch() {
            (wrapper.parent as? android.view.ViewParent)?.requestDisallowInterceptTouchEvent(true)
        }

        private fun setupGestureDetector() {
            gd = GestureDetector(activity, object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean {
                    swipeConsumed = false; longPressTriggered = false; dragStarted = false
                    swipeOffset = 0f; downX = e.x
                    return true
                }

                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    gf.tick()
                    activeLeft = isLeft
                    updatePaneHighlight()
                    if (multiMode && multiPaneSide != isLeft) {
                        multiMode = false; multiPaneSide = true
                        multiSelected = emptySet(); selectedFile = null
                        fileActionBar.visibility = View.GONE
                        refreshHighlight(true); refreshHighlight(false)
                    }
                    if (multiMode) toggleMultiSelect(file, isLeft)
                    else if (file.isDirectory) navigateTo(file)
                    else if (!parent) openFile(file)
                    content.performClick()
                    return true
                }

                override fun onLongPress(e: MotionEvent) {
                    dragLog("onLongPress file=${file.name} multiMode=$multiMode")
                    gf.pickup()
                    content.performLongClick()
                    if (multiMode) {
                        if (multiPaneSide != isLeft) { exitMultiMode(); return }
                        longPressTriggered = true; disallowTouch()
                        content.scaleX = 1.05f; content.scaleY = 1.05f
                        updateMultiInfo(); return
                    }
                    activeLeft = isLeft; updatePaneHighlight()
                    selectedFile = file; longPressTriggered = true; disallowTouch()
                    fileActionBar.visibility = View.VISIBLE; updateMultiInfo()
                }

                override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                    if (swipeConsumed) return true
                    if (longPressTriggered) return false
                    val deltaX = (e2.x - (e1?.x ?: e2.x))
                    val absDx = abs(deltaX)
                    val absDy = abs(e2.y - (e1?.y ?: e2.y))
                    if (absDx > absDy * 2f && absDx > 30f) {
                        if (multiMode) { swipeConsumed = true; disallowTouch(); wrapper.post { rangeSelect(file, isLeft) }; return true }
                        swipeConsumed = true; disallowTouch()
                        swipeOffset = deltaX.coerceIn(-maxSwipePx.toFloat(), maxSwipePx.toFloat())
                        content.translationX = swipeOffset
                        when {
                            swipeOffset < -actionThresholdPx * 0.3f -> { actionOverlay.visibility = View.VISIBLE; previewOverlay.visibility = View.GONE }
                            swipeOffset > peekThresholdPx * 0.3f -> { previewOverlay.visibility = View.VISIBLE; actionOverlay.visibility = View.GONE }
                            else -> { actionOverlay.visibility = View.GONE; previewOverlay.visibility = View.GONE }
                        }
                        return true
                    }
                    return false
                }
            })
        }

        private fun setupTouchListener() {
            wrapper.setOnTouchListener { _, event ->
                if (!multiMode && event.action == MotionEvent.ACTION_DOWN && activeLeft != isLeft) {
                    activeLeft = isLeft; selectedFile = null
                    ui.pulse(); updatePathBar(); updatePaneHighlight()
                    refreshHighlight(true); refreshHighlight(false)
                }
                val gdResult = gd.onTouchEvent(event)
                if (longPressTriggered) {
                    when (event.action) {
                        MotionEvent.ACTION_MOVE -> {
                            if (!dragStarted && abs(event.x - downX) > 60f) {
                                dragStarted = true; gf.confirm(); longPressTriggered = false
                                content.scaleX = 1.05f; content.scaleY = 1.05f
                                fileActionBar.visibility = View.GONE
                                val paths = if (multiMode && multiPaneSide == isLeft && multiSelected.isNotEmpty())
                                    multiSelected.toList() else listOf(file.absolutePath)
                                val clip = ClipData("file", arrayOf("text/plain"), ClipData.Item(paths.first()))
                                paths.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
                                dragLog("startDragAndDrop paths=${paths.joinToString(",")}")
                                runCatching { content.startDragAndDrop(clip, View.DragShadowBuilder(content), paths, 0) }
                                    .onFailure { dragLog("startDragAndDrop failed: ${it.message}") }
                            }
                            disallowTouch(); true
                        }
                        MotionEvent.ACTION_UP -> {
                            longPressTriggered = false
                            if (!dragStarted) {
                                content.scaleX = 1.05f; content.scaleY = 1.05f
                                activeLeft = isLeft; selectedFile = file
                                enterMultiMode(file)
                            } else {
                                content.scaleX = 1f; content.scaleY = 1f
                                content.elevation = 0f; content.translationZ = 0f
                            }
                            true
                        }
                        MotionEvent.ACTION_CANCEL -> {
                            longPressTriggered = false
                            content.animate().cancel()
                            content.scaleX = 1f; content.scaleY = 1f
                            content.elevation = 0f; content.translationZ = 0f
                            false
                        }
                        else -> true
                    }
                } else if (swipeConsumed) {
                    when (event.action) {
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            val offset = content.translationX
                            when {
                                offset < -actionThresholdPx -> gf.snapSpring(content, offset, -maxSwipePx.toFloat()) { actionOverlay.visibility = View.VISIBLE }
                                offset > peekThresholdPx -> gf.snapSpring(content, offset, peekThresholdPx.toFloat()) { previewOverlay.visibility = View.VISIBLE }
                                else -> gf.snapSpring(content, offset, 0f) { actionOverlay.visibility = View.GONE; previewOverlay.visibility = View.GONE }
                            }
                            swipeConsumed = false; swipeOffset = 0f; true
                        }
                        else -> true
                    }
                } else gdResult
            }
        }

        private fun setupDragListener() {
            val a = arcDrawable ?: return
            val ring = progressRing ?: return
            wrapper.setOnDragListener { v, event ->
                when (event.action) {
                    DragEvent.ACTION_DRAG_STARTED -> {
                        val paths = event.localState as? List<*>
                        if (paths != null) showFloatingPills(paths.mapNotNull { it as? String })
                        true
                    }
                    DragEvent.ACTION_DRAG_ENTERED -> {
                        if (isHoverRunning) { true } else {
                            var navigated = false
                            isHoverRunning = true
                            gf.targetGlow(v, true, 0xFF7C3AED.toInt())
                            a.sweep = 0f; ring.visibility = View.VISIBLE; ring.alpha = 1f
                            hoverAnimator?.cancel()
                            hoverAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
                                duration = 700L
                                addUpdateListener { anim ->
                                    a.sweep = (anim.animatedFraction * 360f).coerceAtMost(360f)
                                    if (!navigated && anim.animatedFraction >= 1f && file.isDirectory) {
                                        navigated = true; navigateTo(file)
                                    }
                                }
                                start()
                            }
                            true
                        }
                    }
                    DragEvent.ACTION_DRAG_EXITED, DragEvent.ACTION_DRAG_ENDED -> {
                        isHoverRunning = false
                        gf.targetGlow(v, false, 0xFF7C3AED.toInt())
                        hoverAnimator?.cancel(); hoverAnimator = null
                        ring.visibility = View.GONE; true
                    }
                    DragEvent.ACTION_DRAG_LOCATION -> { updateDragPosition(v, event.x, event.y); true }
                    DragEvent.ACTION_DROP -> {
                        hideFloatingPills()
                        gf.targetGlow(v, false, 0xFF7C3AED.toInt()); gf.drop()
                        hoverAnimator?.cancel(); hoverAnimator = null; ring.visibility = View.GONE
                        val paths = event.localState as? List<*>
                        if (paths != null) {
                            val srcPaths = paths.mapNotNull { it as? String }
                            if (srcPaths.any { File(it).isDirectory }) {
                                scope.launch {
                                    var ok = 0; var fail = 0
                                    withContext(Dispatchers.IO) { srcPaths.forEach { path ->
                                        try { fileOps.copyDir(File(path), File(file, File(path).name)); ok++ }
                                        catch (e: Exception) { fail++; hostDragLog("drop to dir failed: $path: ${e.message}") }
                                    } }
                                    toast(if (fail > 0) "已复制 ${ok} 项（${fail} 项失败）" else "已复制 ${ok} 项")
                                    gf.confirm(); loadPane(isLeft); updatePathBar()
                                }
                            } else {
                                var ok = 0; var fail = 0
                                srcPaths.forEach { path ->
                                    try { File(path).copyTo(File(file, File(path).name)); ok++ }
                                    catch (e: Exception) { fail++; hostDragLog("drop to dir failed: $path: ${e.message}") }
                                }
                                toast(if (fail > 0) "已复制 ${ok} 项（${fail} 项失败）" else "已复制 ${ok} 项")
                                gf.confirm(); loadPane(isLeft); updatePathBar()
                            }
                        }
                        true
                    }
                    else -> true
                }
            }
        }
    }

    private class ArcProgressDrawable(private val color: Int, strokeWidth: Float) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth
            strokeCap = Paint.Cap.ROUND
        }
        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color and 0x00FFFFFF or 0x30000000.toInt()
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth
        }

        var sweep: Float = 0f
            set(value) {
                field = value.coerceIn(0f, 360f)
                invalidateSelf()
            }

        override fun draw(canvas: Canvas) {
            val halfStroke = paint.strokeWidth / 2f
            val rect = RectF(bounds).apply { inset(halfStroke, halfStroke) }
            canvas.drawOval(rect, bgPaint)
            canvas.drawArc(rect, -90f, sweep, false, paint)
        }

        override fun setAlpha(alpha: Int) { paint.alpha = alpha; bgPaint.alpha = alpha / 3 }
        override fun setColorFilter(cf: ColorFilter?) { paint.colorFilter = cf }
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
