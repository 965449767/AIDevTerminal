package com.aidev.terminal

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File

internal interface CompletionHost {
    val completionInputBuffer: String
    val completionComposingBuffer: String
    val completionHomeDir: File?
    val completionCachedPwd: String
    fun completionWriteSession(text: String)
    fun completionClearComposing()
    fun completionSetInputBuffer(value: String)
    fun completionFocusInput()
    fun completionRefresh()
}

internal class CompletionEngine(private val host: CompletionHost) {

    private fun pm(activity: Activity) = PreferencesManager(activity)

    fun buildBar(activity: Activity, ui: AIDevUi): HorizontalScrollView =
        HorizontalScrollView(activity).apply {
            isHorizontalScrollBarEnabled = false
            setBackgroundColor(0xFF0D1117.toInt())
        }

    fun refresh(activity: Activity, ui: AIDevUi, row: LinearLayout) {
        row.removeAllViews()
        val suggestions = suggestions(activity).take(8)
        if (suggestions.isEmpty()) {
            row.addView(hintChip(activity, ui), LinearLayout.LayoutParams(-2, -1))
            return
        }
        suggestions.forEach { item ->
            row.addView(chip(activity, ui, item), LinearLayout.LayoutParams(-2, -1).apply {
                setMargins(0, 0, ui.dp(5), 0)
            })
        }
    }

    fun suggestions(activity: Activity): List<TerminalCompletion> {
        val prefix = input().trimStart()
        val pinned = pinnedCompletions(activity)
        val paths = pathCompletions(prefix)
        val builtIns = builtinCompletions()
        val source = (paths + pinned + builtIns).distinctBy { it.insertText }
        if (prefix.isBlank()) return source.take(8)
        val direct = source.filter { it.insertText.startsWith(prefix, ignoreCase = true) || it.label.startsWith(prefix, ignoreCase = true) }
        val matches = direct.ifEmpty { source.filter { fuzzyCompletionMatch(prefix, it) } }
        return matches
            .sortedWith(compareBy<TerminalCompletion> { completionRank(prefix, it) }.thenBy { it.insertText.length }.thenBy { it.insertText })
            .take(8)
    }

    fun applyCompletion(item: TerminalCompletion) {
        val target = item.insertText
        val committed = host.completionInputBuffer
        val current = input()
        val insert = if (host.completionComposingBuffer.isNotEmpty() && target.equals(current, ignoreCase = true)) {
            if (target.startsWith(committed, ignoreCase = true)) target.drop(committed.length) else target
        } else if (target.equals(current, ignoreCase = true) || target.equals(committed, ignoreCase = true)) {
            ""
        } else if (target.startsWith(committed, ignoreCase = true) && host.completionComposingBuffer.isEmpty()) {
            target.drop(committed.length)
        } else {
            "\u007F".repeat(committed.length) + target
        }
        if (insert.isEmpty()) return
        host.completionClearComposing()
        host.completionWriteSession(insert)
        host.completionSetInputBuffer(target)
        host.completionRefresh()
        host.completionFocusInput()
    }

    fun executeCompletion(activity: Activity, item: TerminalCompletion) {
        host.completionWriteSession(item.insertText.trimEnd() + "\r")
        host.completionSetInputBuffer("")
        host.completionClearComposing()
        host.completionRefresh()
        host.completionFocusInput()
    }

    fun showMenu(activity: Activity, item: TerminalCompletion) {
        val options = if (item.kind == "PIN") {
            arrayOf("补全", "执行并回车", "复制命令", "取消固定")
        } else {
            arrayOf("补全", "执行并回车", "复制命令", "固定到常用")
        }
        MaterialAlertDialogBuilder(activity)
            .setTitle(item.label)
            .setItems(options) { _, which ->
                when (options[which]) {
                    "补全" -> applyCompletion(item)
                    "执行并回车" -> executeCompletion(activity, item)
                    "复制命令" -> {
                        (activity.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
                            ?.setPrimaryClip(ClipData.newPlainText("AIDev command", item.insertText))
                        Toast.makeText(activity, "已复制命令", Toast.LENGTH_SHORT).show()
                    }
                    "固定到常用" -> pinCompletion(activity, item)
                    "取消固定" -> unpinCompletion(activity, item)
                }
            }
            .show()
    }

    fun pinCompletion(activity: Activity, item: TerminalCompletion) {
        val p = pm(activity)
        val old = p.terminalPinnedCompletions.lines().filter { it.isNotBlank() && it != item.insertText }
        p.terminalPinnedCompletions = (listOf(item.insertText) + old).take(12).joinToString("\n")
        host.completionRefresh()
        Toast.makeText(activity, "已固定到常用", Toast.LENGTH_SHORT).show()
    }

    fun unpinCompletion(activity: Activity, item: TerminalCompletion) {
        val p = pm(activity)
        val old = p.terminalPinnedCompletions.lines().filter { it.isNotBlank() && it != item.insertText }
        p.terminalPinnedCompletions = old.joinToString("\n")
        host.completionRefresh()
        Toast.makeText(activity, "已取消固定", Toast.LENGTH_SHORT).show()
    }

    private fun input(): String = host.completionInputBuffer + host.completionComposingBuffer

    private fun pinnedCompletions(activity: Activity): List<TerminalCompletion> =
        pm(activity).terminalPinnedCompletions
            .lines()
            .filter { it.isNotBlank() }
            .map { TerminalCompletion(it, it, "PIN") }

    private fun pathCompletions(input: String): List<TerminalCompletion> {
        val home = host.completionHomeDir ?: return emptyList()
        val token = input.substringAfterLast(' ', "")
        val command = input.substringBefore(' ', "").lowercase()
        val pathMode = input.contains(' ') && command in setOf("cd", "ls", "cat", "less", "tail", "head", "nano", "vim", "rm", "cp", "mv", "mkdir", "touch", "grep") ||
            token.startsWith("/") || token.startsWith("./") || token.startsWith("../") || token.startsWith("~")
        if (!pathMode) return emptyList()
        val currentDir = currentUbuntuDirFile(home)
        val hostHome = home
        val (baseDir, typedPrefix, displayPrefix) = when {
            token.startsWith("/root/") -> {
                pathParts(token.removePrefix("/root/"), "/root/", File(home, "ubuntu-rootfs/root"))
            }
            token == "/root" || token == "~" || token == "~/" -> Triple(File(home, "ubuntu-rootfs/root"), "", if (token.startsWith("~")) "~/" else "/root/")
            token.startsWith("/host-home/") -> {
                pathParts(token.removePrefix("/host-home/"), "/host-home/", hostHome)
            }
            token.startsWith("/") -> {
                val relative = token.removePrefix("/")
                pathParts(relative, "/", File(home, "ubuntu-rootfs"))
            }
            token.contains('/') -> {
                val slash = token.lastIndexOf('/')
                val dirPart = token.substring(0, slash)
                val namePart = token.substring(slash + 1)
                val cleanDir = dirPart.removePrefix("./")
                Triple(File(currentDir, cleanDir), namePart, if (dirPart.isBlank()) "" else "$dirPart/")
            }
            else -> Triple(currentDir, token, "")
        }
        if (!baseDir.isDirectory) return emptyList()
        val beforeToken = input.dropLast(token.length)
        return baseDir.listFiles().orEmpty()
            .asSequence()
            .filter { it.name.startsWith(typedPrefix, ignoreCase = true) }
            .sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
            .take(8)
            .map { file ->
                val suffix = if (file.isDirectory) "/" else ""
                val path = displayPrefix + file.name + suffix
                TerminalCompletion(path, beforeToken + path, "PATH")
            }
            .toList()
    }

    private fun currentUbuntuDirFile(home: File): File {
        val pwd = host.completionCachedPwd.takeIf { it.isNotBlank() }
            ?: File(home, ".aidev-current-pwd").takeIf { it.isFile }?.readText()?.trim().orEmpty()
            .ifBlank { "/root" }
        return when {
            pwd == "/host-home" -> home
            pwd.startsWith("/host-home/") -> File(home, pwd.removePrefix("/host-home/"))
            pwd == "/root" -> File(home, "ubuntu-rootfs/root")
            pwd.startsWith("/root/") -> File(home, "ubuntu-rootfs/root/${pwd.removePrefix("/root/")}")
            pwd.startsWith("/") -> File(home, "ubuntu-rootfs/${pwd.removePrefix("/")}")
            else -> File(home, "ubuntu-rootfs/root")
        }
    }

    // ── UI builders ──

    private fun hintChip(activity: Activity, ui: AIDevUi): TextView =
        TextView(activity).apply {
            text = "点击输入框获取焦点"
            textSize = 11f
            gravity = Gravity.CENTER
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(0xFF9CA3AF.toInt())
            setPadding(ui.dp(10), 0, ui.dp(10), 0)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFF111827.toInt())
                cornerRadius = ui.dp(12).toFloat()
                setStroke(ui.dp(1), 0xFF374151.toInt())
            }
            setOnClickListener { host.completionFocusInput() }
        }

    private fun chip(activity: Activity, ui: AIDevUi, item: TerminalCompletion): TextView =
        TextView(activity).apply {
            text = when (item.kind) {
                "PIN" -> "固定 ${item.label}"
                "ENV" -> "环境 ${item.label}"
                "PATH" -> "路径 ${item.label}"
                else -> item.label
            }
            textSize = 11f
            gravity = Gravity.CENTER
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(when (item.kind) {
                "PIN" -> 0xFFA7F3D0.toInt()
                "ENV" -> 0xFFBFDBFE.toInt()
                "PATH" -> 0xFFFDE68A.toInt()
                else -> 0xFFD1D5DB.toInt()
            })
            setPadding(ui.dp(10), 0, ui.dp(10), 0)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(when (item.kind) {
                    "PIN" -> 0xFF0F2A22.toInt()
                    "ENV" -> 0xFF111D35.toInt()
                    "PATH" -> 0xFF2A220C.toInt()
                    else -> 0xFF172033.toInt()
                })
                cornerRadius = ui.dp(12).toFloat()
                setStroke(ui.dp(1), when (item.kind) {
                    "PIN" -> 0xFF059669.toInt()
                    "ENV" -> 0xFF2563EB.toInt()
                    "PATH" -> 0xFFD97706.toInt()
                    else -> 0xFF2B3650.toInt()
                })
            }
            setOnClickListener { applyCompletion(item) }
            setOnLongClickListener {
                showMenu(activity, item)
                true
            }
        }
}
