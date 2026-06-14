package com.aidev.terminal

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File

class FileManagerActivity : Activity() {
    private lateinit var leftList: LinearLayout
    private lateinit var rightList: LinearLayout
    private lateinit var leftPath: TextView
    private lateinit var rightPath: TextView
    private var leftDir: File = Environment.getExternalStorageDirectory()
    private var rightDir: File = File("/")
    private var activeLeft = true
    private var leftSelected: File? = null
    private var rightSelected: File? = null
    private lateinit var ui: AIDevUi

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = AIDevUi(this, getSharedPreferences("aidev_ui", MODE_PRIVATE))
        buildUi()
        reloadAll()
    }

    private fun buildUi() {
        val root = ui.pageRoot()
        AppNav.attach(this, ui, root, FileManagerActivity::class.java)
        root.addView(ui.topBar("文件管理器", "权限" to { openStorageSettings() }, "刷新" to { reloadAll() }, "关闭" to { finish() }))
        root.addView(toolbar())

        val panes = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8))
        }
        leftList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        rightList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val leftPane = pane(true)
        val rightPane = pane(false)
        panes.addView(leftPane, LinearLayout.LayoutParams(0, -1, 1f))
        panes.addView(rightPane, LinearLayout.LayoutParams(0, -1, 1f))
        root.addView(panes, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(ui.bottomNav(AppNav.bottom(this)))
        setContentView(root)
    }

    private fun topBar(): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, dp(8), 0)
            setBackgroundColor(0xFF111418.toInt())
            addView(TextView(this@FileManagerActivity).apply {
                text = "文件管理器"
                textSize = 17f
                setTextColor(0xFFE5E7EB.toInt())
                gravity = Gravity.CENTER_VERTICAL
            }, LinearLayout.LayoutParams(0, dp(46), 1f))
            addView(barButton("权限") { openStorageSettings() })
            addView(barButton("刷新") { reloadAll() })
            addView(barButton("关闭") { finish() })
        }

    private fun toolbar(): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(4))
            addView(actionButton("复制到对侧") { copyToOther(false) })
            addView(actionButton("移动到对侧") { copyToOther(true) })
            addView(actionButton("新建文件夹") { newFolder() })
            addView(actionButton("重命名") { renameSelected() })
            addView(actionButton("删除") { deleteSelected() })
        }

    private fun pane(isLeft: Boolean): View {
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(4), ui.dp(4), ui.dp(4), ui.dp(4))
            background = ui.infoPanelBackground()
        }
        val path = TextView(this).apply {
            textSize = 12f
            setTextColor(ui.palette.primary)
            setPadding(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8))
            maxLines = 2
        }
        if (isLeft) leftPath = path else rightPath = path
        outer.addView(path, LinearLayout.LayoutParams(-1, -2))
        outer.addView(ScrollView(this).apply {
            addView(if (isLeft) leftList else rightList)
        }, LinearLayout.LayoutParams(-1, 0, 1f))
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
        if (dir.parentFile != null) {
            list.addView(row("..", dir.parentFile!!, isLeft, true))
        }
        val files = dir.listFiles()?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
        if (files == null) {
            list.addView(info("无法读取目录，可能需要存储权限或系统限制。"))
            return
        }
        files.forEach { file -> list.addView(row(label(file), file, isLeft, false)) }
    }

    private fun row(text: String, file: File, isLeft: Boolean, parent: Boolean): View =
        TextView(this).apply {
            this.text = text
            textSize = 13f
            setTextColor(ui.palette.text)
            setPadding(ui.dp(8), ui.dp(7), ui.dp(8), ui.dp(7))
            setBackgroundColor(rowColor(file, isLeft))
            setOnClickListener {
                ui.pulse()
                activeLeft = isLeft
                if (file.isDirectory) {
                    if (isLeft) {
                        leftDir = file
                        leftSelected = null
                    } else {
                        rightDir = file
                        rightSelected = null
                    }
                    loadPane(isLeft)
                } else if (!parent) {
                    if (isLeft) leftSelected = file else rightSelected = file
                    loadPane(isLeft)
                }
            }
            setOnLongClickListener {
                activeLeft = isLeft
                if (isLeft) leftSelected = file else rightSelected = file
                showFileActions(file)
                true
            }
        }

    private fun rowColor(file: File, isLeft: Boolean): Int {
        val selected = if (isLeft) leftSelected else rightSelected
        return if (selected?.absolutePath == file.absolutePath) ui.palette.primary else ui.palette.surface
    }

    private fun label(file: File): String {
        val prefix = if (file.isDirectory) "📁 " else "📄 "
        val size = if (file.isFile) "  ${formatSize(file.length())}" else ""
        return prefix + file.name + size
    }

    private fun showFileActions(file: File) {
        AlertDialog.Builder(this)
            .setTitle(file.name)
            .setItems(arrayOf("复制到对侧", "移动到对侧", "重命名", "删除")) { _, which ->
                when (which) {
                    0 -> copyToOther(false)
                    1 -> copyToOther(true)
                    2 -> renameSelected()
                    3 -> deleteSelected()
                }
            }
            .show()
    }

    private fun selected(): File? = if (activeLeft) leftSelected else rightSelected
    private fun activeDir(): File = if (activeLeft) leftDir else rightDir
    private fun otherDir(): File = if (activeLeft) rightDir else leftDir

    private fun copyToOther(move: Boolean) {
        val src = selected() ?: return toast("请先选择文件或目录")
        val dst = File(otherDir(), src.name)
        if (dst.exists()) return toast("目标已存在：${dst.name}")
        try {
            if (src.isDirectory) copyDir(src, dst) else src.copyTo(dst)
            if (move) src.deleteRecursively()
            clearSelection()
            reloadAll()
            toast(if (move) "移动完成" else "复制完成")
        } catch (e: Throwable) {
            toast("操作失败：${e.message}")
        }
    }

    private fun newFolder() {
        input("新建文件夹", "文件夹名称") { name ->
            val dir = File(activeDir(), name)
            if (dir.mkdir()) {
                reloadAll()
                toast("已创建")
            } else {
                toast("创建失败")
            }
        }
    }

    private fun renameSelected() {
        val src = selected() ?: return toast("请先选择文件或目录")
        input("重命名", src.name) { name ->
            val dst = File(src.parentFile, name)
            if (src.renameTo(dst)) {
                clearSelection()
                reloadAll()
                toast("已重命名")
            } else {
                toast("重命名失败")
            }
        }
    }

    private fun deleteSelected() {
        val src = selected() ?: return toast("请先选择文件或目录")
        AlertDialog.Builder(this)
            .setTitle("删除")
            .setMessage("确认删除：${src.absolutePath}")
            .setPositiveButton("删除") { _, _ ->
                val ok = if (src.isDirectory) src.deleteRecursively() else src.delete()
                clearSelection()
                reloadAll()
                toast(if (ok) "已删除" else "删除失败")
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun copyDir(src: File, dst: File) {
        dst.mkdirs()
        src.listFiles()?.forEach {
            val out = File(dst, it.name)
            if (it.isDirectory) copyDir(it, out) else it.copyTo(out)
        }
    }

    private fun clearSelection() {
        leftSelected = null
        rightSelected = null
    }

    private fun input(title: String, hint: String, cb: (String) -> Unit) {
        val edit = EditText(this).apply {
            setText(hint)
            selectAll()
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(edit)
            .setPositiveButton("确定") { _, _ ->
                val text = edit.text.toString().trim()
                if (text.isNotEmpty() && !text.contains("/")) cb(text)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun openStorageSettings() {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                })
            } catch (_: Throwable) {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            })
        }
    }

    private fun barButton(text: String, click: () -> Unit): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 14f
            setTextColor(0xFF93C5FD.toInt())
            gravity = Gravity.CENTER
            setOnClickListener { click() }
            layoutParams = LinearLayout.LayoutParams(dp(52), dp(46))
        }

    private fun actionButton(text: String, click: () -> Unit): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 12f
            setTextColor(ui.palette.text)
            gravity = Gravity.CENTER
            setPadding(ui.dp(4), ui.dp(8), ui.dp(4), ui.dp(8))
            background = ui.subtleCommandButtonBackground()
            setOnClickListener {
                ui.pulse()
                click()
            }
            val lp = LinearLayout.LayoutParams(0, -2, 1f)
            lp.setMargins(ui.dp(2), 0, ui.dp(2), 0)
            layoutParams = lp
        }

    private fun info(text: String): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 13f
            setTextColor(ui.palette.muted)
            setPadding(ui.dp(8), ui.dp(10), ui.dp(8), ui.dp(10))
        }

    private fun formatSize(n: Long): String {
        if (n < 1024) return "${n}B"
        val kb = n / 1024.0
        if (kb < 1024) return "%.1fK".format(kb)
        val mb = kb / 1024.0
        if (mb < 1024) return "%.1fM".format(mb)
        return "%.1fG".format(mb / 1024.0)
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
