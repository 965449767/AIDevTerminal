package com.aidev.terminal

import android.app.Activity
import android.content.ClipboardManager
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.EditText
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

internal class FileOperationsHelper(private val h: FilePageHost) {

    private val undoStack = mutableListOf<List<Pair<File, File>>>()
    private val trashCleanupHandler = Handler(Looper.getMainLooper())
    private val cleanupTasks = mutableListOf<Runnable>()

    fun deleteSelected() {
        val targets = if (h.hostMultiMode) {
            if (h.hostMultiSelected.isEmpty()) return h.hostToast("请先选择文件")
            h.hostMultiSelected.toList()
        } else {
            val src = h.hostSelectedFile() ?: return h.hostToast("请先选择文件或目录")
            listOf(src.absolutePath)
        }
        val hasDirs = targets.any { File(it).isDirectory }
        val msg = if (hasDirs) "确定删除这 ${targets.size} 个文件/目录？\n（文件将移动到 .trash，可撤销）"
                  else "确定删除这 ${targets.size} 个文件？\n（文件将移动到 .trash，可撤销）"
        MaterialAlertDialogBuilder(h.hostActivity())
            .setTitle("删除")
            .setMessage(msg)
            .setPositiveButton("删除") { _, _ ->
                val dir = h.hostActiveDir()
                val trashDir = File(dir, ".trash").apply { mkdirs() }
                val timestamp = System.currentTimeMillis()
                var fail = 0
                val trashed = mutableListOf<Pair<File, File>>()
                targets.forEach { path ->
                    val src = File(path)
                    val trash = File(trashDir, "${timestamp}_${src.name}")
                    if (src.renameTo(trash)) {
                        trashed.add(src.absoluteFile to trash)
                    } else {
                        fail++
                    }
                }
                if (h.hostMultiMode) h.hostExitMultiMode() else h.hostClearSelection()
                h.hostReloadAll()
                if (trashed.isNotEmpty()) {
                    undoStack.add(trashed)
                    showUndo(trashed.size, dir)
                } else if (fail > 0) {
                    h.hostToast("删除失败")
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showUndo(count: Int, dir: File) {
        val root = h.hostActivity().findViewById<View>(android.R.id.content)
        Snackbar.make(root, "已移动到回收站（${count} 项）", Snackbar.LENGTH_LONG)
            .setAction("撤销") { undoLast() }
            .show()
        scheduleTrashCleanup(dir)
    }

    private fun undoLast() {
        if (undoStack.isEmpty()) return
        val batch = undoStack.removeLast()
        var ok = 0
        for ((original, trash) in batch) {
            if (trash.renameTo(original)) {
                ok++
            } else {
                h.hostDragLog("undo failed: ${trash.name} → ${original.path}")
            }
        }
        h.hostReloadAll()
        h.hostToast("已撤销 ${ok} 项")
    }

    fun emptyTrash() {
        val dir = h.hostActiveDir()
        val trashDir = File(dir, ".trash")
        if (!trashDir.exists() || trashDir.listFiles().isNullOrEmpty()) {
            return h.hostToast("回收站已空")
        }
        val count = trashDir.listFiles()?.size ?: 0
        MaterialAlertDialogBuilder(h.hostActivity())
            .setTitle("清空回收站")
            .setMessage("确定永久删除回收站中的 $count 个文件？此操作不可撤销。")
            .setPositiveButton("清空") { _, _ ->
                var fail = 0
                trashDir.listFiles()?.forEach { if (!it.deleteRecursively()) fail++ }
                if (fail == 0) h.hostToast("回收站已清空")
                else h.hostToast("${fail} 项清理失败")
                undoStack.clear()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun scheduleTrashCleanup(dir: File) {
        val trashDir = File(dir, ".trash")
        if (!trashDir.exists()) return
        val task = Runnable {
            val cutoff = System.currentTimeMillis() - 180_000
            trashDir.listFiles()?.forEach { file ->
                val parts = file.name.split("_", limit = 2)
                val ts = parts.firstOrNull()?.toLongOrNull() ?: return@forEach
                if (ts < cutoff && !file.deleteRecursively()) {
                    h.hostDragLog("trash cleanup failed: ${file.name}")
                }
            }
        }
        cleanupTasks.add(task)
        trashCleanupHandler.postDelayed(task, 180_000)
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
