package com.aidev.terminal

import android.app.Activity
import android.content.ClipboardManager
import android.widget.EditText
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

internal class FileOperationsHelper(private val h: FilePageHost) {

    fun deleteSelected() {
        val targets = if (h.hostMultiMode) {
            if (h.hostMultiSelected.isEmpty()) return h.hostToast("请先选择文件")
            h.hostMultiSelected.toList()
        } else {
            val src = h.hostSelectedFile() ?: return h.hostToast("请先选择文件或目录")
            listOf(src.absolutePath)
        }
        val hasDirs = targets.any { File(it).isDirectory }
        if (!hasDirs) {
            MaterialAlertDialogBuilder(h.hostActivity())
                .setTitle("确认删除")
                .setMessage("确定删除这 ${targets.size} 个文件？")
                .setPositiveButton("删除") { _, _ ->
                    var fail = 0
                    targets.forEach { if (!File(it).deleteRecursively()) { fail++; h.hostDragLog("delete failed: $it") } }
                    if (h.hostMultiMode) h.hostExitMultiMode() else h.hostClearSelection()
                    h.hostReloadAll()
                    h.hostToast("已删除 ${targets.size} 项${if (fail > 0) "（${fail} 项失败）" else ""}")
                }
                .setNegativeButton("取消", null)
                .show()
            return
        }
        MaterialAlertDialogBuilder(h.hostActivity())
            .setTitle("确认删除")
            .setMessage("确定删除这 ${targets.size} 个文件/目录？")
            .setPositiveButton("删除") { _, _ ->
                h.hostScope.launch {
                    var fail = 0
                    withContext(Dispatchers.IO) {
                        targets.forEach { if (!File(it).deleteRecursively()) { fail++; h.hostDragLog("delete failed: $it") } }
                    }
                    if (h.hostMultiMode) h.hostExitMultiMode() else h.hostClearSelection()
                    h.hostReloadAll()
                    h.hostToast("已删除 ${targets.size} 项${if (fail > 0) "（${fail} 项失败）" else ""}")
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun copyToClipbook(paths: List<String>, move: Boolean) {
        val serialized = paths.joinToString("\n")
        val label = if (move) "aidiv_move" else "aidiv_copy"
        h.hostPm().sharedPreferences.edit().putString(label, serialized).apply()
    }

    fun hasClipboardItems(): Boolean {
        val copy = h.hostPm().sharedPreferences.getString("aidiv_copy", null)
        val move = h.hostPm().sharedPreferences.getString("aidiv_move", null)
        return !copy.isNullOrBlank() || !move.isNullOrBlank()
    }

    fun pasteClipboard() {
        val dir = h.hostActiveDir()
        val prefs = h.hostPm().sharedPreferences
        val movePaths = prefs.getString("aidiv_move", null)
        val copyPaths = prefs.getString("aidiv_copy", null)

        if (!movePaths.isNullOrBlank()) {
            val files = movePaths.lines().map { File(it) }.filter { it.exists() }
            if (files.isEmpty()) return h.hostToast("剪切板中的文件已不存在")
            val hasDirs = files.any { it.isDirectory }
            if (!hasDirs) {
                var moveFail = 0
                files.forEach { src ->
                    val dst = File(dir, src.name)
                    runCatching { src.renameTo(dst) }.onFailure { moveFail++; h.hostDragLog("move failed: ${src.name}: ${it.message}") }
                }
                prefs.edit().remove("aidiv_move").apply()
                h.hostToast(if (moveFail > 0) "已移动 ${files.size - moveFail} 项（${moveFail} 项失败）" else "已移动 ${files.size} 项")
                h.hostReloadAll()
            } else {
                h.hostScope.launch {
                    var moveFail = 0
                    withContext(Dispatchers.IO) {
                        files.forEach { src ->
                            val dst = File(dir, src.name)
                            runCatching { src.renameTo(dst) }.onFailure { moveFail++; h.hostDragLog("move failed: ${src.name}: ${it.message}") }
                        }
                    }
                    prefs.edit().remove("aidiv_move").apply()
                    h.hostToast(if (moveFail > 0) "已移动 ${files.size - moveFail} 项（${moveFail} 项失败）" else "已移动 ${files.size} 项")
                    h.hostReloadAll()
                }
            }
            return
        }
        if (!copyPaths.isNullOrBlank()) {
            val files = copyPaths.lines().map { File(it) }.filter { it.exists() }
            if (files.isEmpty()) return h.hostToast("剪贴板中的文件已不存在")
            val hasDirs = files.any { it.isDirectory }
            if (!hasDirs) {
                var copyFail = 0
                files.forEach { src ->
                    val dst = File(dir, src.name)
                    try {
                        src.copyTo(dst, overwrite = false)
                    } catch (e: Exception) {
                        copyFail++; h.hostDragLog("copy failed: ${src.name}: ${e.message}")
                    }
                }
                prefs.edit().remove("aidiv_copy").apply()
                h.hostToast(if (copyFail > 0) "已复制 ${files.size - copyFail} 项（${copyFail} 项失败）" else "已复制 ${files.size} 项")
                h.hostReloadAll()
            } else {
                h.hostScope.launch {
                    var copyFail = 0
                    withContext(Dispatchers.IO) {
                        files.forEach { src ->
                            val dst = File(dir, src.name)
                            try {
                                if (src.isDirectory) src.copyRecursively(dst, overwrite = false)
                                else src.copyTo(dst, overwrite = false)
                            } catch (e: Exception) {
                                copyFail++; h.hostDragLog("copy failed: ${src.name}: ${e.message}")
                            }
                        }
                    }
                    prefs.edit().remove("aidiv_copy").apply()
                    h.hostToast(if (copyFail > 0) "已复制 ${files.size - copyFail} 项（${copyFail} 项失败）" else "已复制 ${files.size} 项")
                    h.hostReloadAll()
                }
            }
            return
        }

        val clipMgr = h.hostActivity().getSystemService(Activity.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipMgr.primaryClip
        val clipText = clip?.getItemAt(0)?.text?.toString()
        if (!clipText.isNullOrBlank()) {
            input("粘贴为文件", "clipboard.txt") { name ->
                if (name.isBlank()) return@input h.hostToast("名称不能为空")
                runCatching {
                    File(dir, name).writeText(clipText)
                    h.hostReloadAll()
                    h.hostToast("已粘贴为 $name")
                }.onFailure { h.hostToast("粘贴失败：${it.message}") }
            }
            return
        }

        h.hostToast("没有可粘贴的内容")
    }

    fun notifyTerminalCd(dir: File) {
        val act = h.hostActivity()
        val home = File(act.filesDir, "home")
        val ubuntuPath = SyncCoordinator.toUbuntuPath(dir, home) ?: return
        if (act is ShellActivity) act.syncTerminalCd(ubuntuPath)
    }

    fun copyToOther(move: Boolean) {
        val sources = if (h.hostMultiMode) {
            if (h.hostMultiSelected.isEmpty()) return h.hostToast("请先选择文件")
            h.hostMultiSelected.toList()
        } else {
            val src = h.hostSelectedFile() ?: return h.hostToast("请先选择文件或目录")
            listOf(src.absolutePath)
        }
        val fromSide = if (h.hostMultiMode) h.hostMultiPaneSide else h.hostActiveLeft
        val dstDir = if (fromSide) h.hostRightDir else h.hostLeftDir
        val hasDirs = sources.any { File(it).isDirectory }
        if (!hasDirs) {
            val ok = sources.count { path ->
                val src = File(path)
                val dst = File(dstDir, src.name)
                if (dst.exists()) return@count false
                val ok = try {
                    src.copyTo(dst).let { true }
                } catch (e: Exception) {
                    h.hostDragLog("copy error: ${src.name}: ${e.message}"); false
                }
                if (ok && move && !src.deleteRecursively()) h.hostDragLog("delete after move failed: ${src.name}")
                ok
            }
            if (h.hostMultiMode) h.hostExitMultiMode() else h.hostClearSelection()
            h.hostReloadAll()
            val fail = sources.size - ok
            h.hostToast(if (fail > 0) "已完成 ${ok}/${sources.size} 项（${fail} 项失败）"
                  else if (move) "已移动 ${ok} 项" else "已复制 ${ok} 项")
            copyToClipbook(sources, move)
        } else {
            h.hostScope.launch {
                var ok = 0
                withContext(Dispatchers.IO) {
                    sources.forEach { path ->
                        val src = File(path)
                        val dst = File(dstDir, src.name)
                        if (dst.exists()) return@forEach
                        try {
                            if (src.isDirectory) copyDir(src, dst) else src.copyTo(dst)
                            if (move && !src.deleteRecursively()) h.hostDragLog("delete after move failed: ${src.name}")
                            ok++
                        } catch (e: Exception) {
                            h.hostDragLog("copy error: ${src.name}: ${e.message}")
                        }
                    }
                }
                if (h.hostMultiMode) h.hostExitMultiMode() else h.hostClearSelection()
                h.hostReloadAll()
                val fail = sources.size - ok
                h.hostToast(if (fail > 0) "已完成 ${ok}/${sources.size} 项（${fail} 项失败）"
                      else if (move) "已移动 ${ok} 项" else "已复制 ${ok} 项")
                copyToClipbook(sources, move)
            }
        }
    }

    fun newFolder() = input("新建", "") { name ->
        if (name.isBlank()) return@input h.hostToast("名称不能为空")
        val target = File(h.hostActiveDir(), name)
        val ok = if (name.contains(".")) target.createNewFile() else target.mkdir()
        h.hostToast(if (ok) "已创建" else "创建失败")
        h.hostReloadAll()
    }

    fun renameSelected() {
        val src = h.hostSelectedFile() ?: return h.hostToast("请先选择文件或目录")
        input("重命名", src.name) { name ->
            h.hostToast(if (src.renameTo(File(src.parentFile, name))) "已重命名" else "重命名失败")
            h.hostClearSelection()
            h.hostReloadAll()
        }
    }

    fun saveSelectedAs() {
        val src = h.hostSelectedFile() ?: return h.hostToast("请先选择文件或目录")
        h.hostInputAllowAny("另存为", src.name) { name ->
            val dst = File(h.hostActiveDir(), name)
            if (dst.exists()) return@hostInputAllowAny h.hostToast("目标已存在")
            if (!src.isDirectory) {
                runCatching { src.copyTo(dst) }.onSuccess {
                    h.hostReloadAll()
                    h.hostToast("已另存为 $name")
                }.onFailure { h.hostToast("另存失败：${it.message}") }
            } else {
                h.hostScope.launch {
                    val ok = withContext(Dispatchers.IO) { copyDir(src, dst) }
                    if (ok) { h.hostReloadAll(); h.hostToast("已另存为 $name") }
                    else h.hostToast("另存失败")
                }
            }
        }
    }

    fun copyDir(src: File, dst: File): Boolean = try {
        dst.mkdirs()
        src.listFiles()?.all { child ->
            if (child.isDirectory) copyDir(child, File(dst, child.name))
            else child.copyTo(File(dst, child.name), overwrite = false).let { true }
        } ?: true
    } catch (e: Exception) {
        h.hostDragLog("copyDir error: ${src.name} → ${dst.name}: ${e.message}")
        false
    }

    private fun input(title: String, hint: String, cb: (String) -> Unit) {
        val edit = EditText(h.hostActivity()).apply {
            setText(hint)
            selectAll()
        }
        MaterialAlertDialogBuilder(h.hostActivity()).setTitle(title).setView(edit).setPositiveButton("确定") { _, _ ->
            val text = edit.text.toString().trim()
            if (text.isNotEmpty() && !text.contains("/")) cb(text)
        }.setNegativeButton("取消", null).show()
    }
}
