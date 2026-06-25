package com.aidev.terminal

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.drawable.GradientDrawable
import java.io.File

class ProjectTreeView(
    private val ctx: Context,
    private val ui: AIDevUi
) : LinearLayout(ctx) {

    private val expanded = mutableSetOf<String>()
    private var activePath: String? = null
    private var root: File? = null
    val currentRoot: File? get() = root
    private var onDirClick: ((File) -> Unit)? = null
    private var onFileClick: ((File) -> Unit)? = null
    private var onFileAction: ((File) -> Unit)? = null
    private var saveExpanded: ((Set<String>) -> Unit)? = null

    fun setCallbacks(dirClick: ((File) -> Unit)? = null, fileClick: ((File) -> Unit)? = null, fileAction: (File) -> Unit, onSaveExpanded: ((Set<String>) -> Unit)? = null) {
        onDirClick = dirClick
        onFileClick = fileClick
        onFileAction = fileAction
        saveExpanded = onSaveExpanded
    }

    fun setRoot(dir: File) {
        root = dir
        expanded.clear()
        expanded.add(dir.absolutePath)
        activePath = null
        render()
    }

    fun setActivePath(path: String) {
        activePath = path
        render()
    }

    fun expandTo(file: File) {
        val r = root ?: return
        val rPath = r.absolutePath
        var p = file.parentFile
        while (p != null && p.absolutePath.startsWith(rPath)) {
            expanded.add(p.absolutePath)
            p = p.parentFile
        }
        activePath = file.absolutePath
        render()
    }

    fun refresh() {
        render()
    }

    fun restoreExpanded(paths: Set<String>) {
        val rPath = root?.absolutePath ?: return
        expanded.addAll(paths.filter { it.startsWith(rPath) })
        render()
    }

    fun collapseAll() {
        val rPath = root?.absolutePath ?: return
        expanded.clear()
        expanded.add(rPath)
        saveExpanded?.invoke(setOf(rPath))
        render()
    }

    private fun render() {
        removeAllViews()
        orientation = VERTICAL

        val dir = root ?: return
        val nodes = buildFlatList(dir, 0)
        for (node in nodes) {
            addView(createRow(node))
        }
    }

    private data class FlatNode(
        val file: File,
        val name: String,
        val depth: Int,
        val isDir: Boolean,
        val isExpanded: Boolean,
        val isActive: Boolean
    )

    private fun buildFlatList(dir: File, depth: Int): List<FlatNode> {
        val result = mutableListOf<FlatNode>()
        val children = dir.listFiles()?.sortedBy { it.name.lowercase() } ?: return result
        for (child in children) {
            if (child.name.startsWith(".")) continue
            val path = child.absolutePath
            val isDir = child.isDirectory
            val isExp = path in expanded
            val isActive = path == activePath
            result.add(FlatNode(child, child.name, depth, isDir, isExp, isActive))
            if (isDir && isExp) {
                result.addAll(buildFlatList(child, depth + 1))
            }
        }
        return result
    }

    private fun createRow(node: FlatNode): View {
        val row = LinearLayout(ctx).apply {
            orientation = HORIZONTAL
            setPadding(ui.dp(8 + node.depth * 16), ui.dp(6), ui.dp(8), ui.dp(6))
            if (node.isActive) {
                setBackgroundColor(0x227C3AED)
            }
            layoutParams = LinearLayout.LayoutParams(-1, -2)
        }

        val arrow = TextView(ctx).apply {
            text = when {
                node.isDir && node.isExpanded -> "\u25BC"
                node.isDir -> "\u25B6"
                else -> "  "
            }
            textSize = 11f
            setTextColor(ui.palette.muted)
            layoutParams = LinearLayout.LayoutParams(ui.dp(16), -2)
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(arrow)

        val (iconRes, iconBg) = FileIconMapper.iconFor(node.file)
        val icon = ImageView(ctx).apply {
            setImageResource(iconRes)
            setColorFilter(0xFFFFFFFF.toInt())
            val size = ui.dp(22)
            layoutParams = LinearLayout.LayoutParams(size, size)
            setPadding(ui.dp(3), ui.dp(3), ui.dp(3), ui.dp(3))
            scaleType = ImageView.ScaleType.FIT_CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(iconBg)
            }
        }
        row.addView(icon)

        val label = TextView(ctx).apply {
            text = node.name
            textSize = 13f
            setTextColor(if (node.isDir) ui.palette.text else ui.palette.muted)
            typeface = if (node.isDir) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
            layoutParams = LinearLayout.LayoutParams(-1, -2)
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(label)

        row.setOnClickListener {
            if (node.isDir) {
                toggle(node.file.absolutePath)
                onDirClick?.invoke(node.file)
            } else {
                activePath = node.file.absolutePath
                onFileClick?.invoke(node.file)
                render()
            }
        }
        row.setOnLongClickListener {
            onFileAction?.invoke(node.file)
            true
        }
        return row
    }

    private fun toggle(path: String) {
        if (path in expanded) expanded.remove(path) else expanded.add(path)
        saveExpanded?.invoke(expanded.toSet())
        render()
    }
}
