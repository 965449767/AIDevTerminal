package com.aidev.terminal

import android.app.Activity
import android.os.Environment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

internal class NavigationHelper(private val h: FilePageHost) {

    fun searchActiveDir() {
        h.hostInputAllowAny("搜索文件", "输入文件名关键词") { keyword ->
            val base = h.hostActiveDir()
            h.hostScope.launch {
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
                        h.hostToast("没有找到匹配文件")
                        return@withContext
                    }
                    MaterialAlertDialogBuilder(h.hostActivity())
                        .setTitle("搜索结果")
                        .setItems(matches.map { it.absolutePath.removePrefix(base.absolutePath).ifBlank { it.absolutePath } }.toTypedArray()) { _, which ->
                            val file = matches[which]
                            if (file.isDirectory) {
                                h.hostNavigateTo(file)
                            } else {
                                h.hostSetSelectedFile(file)
                                h.hostLoadPane(h.hostActiveLeft)
                            }
                        }
                        .show()
                }
            }
        }
    }

    fun addFavorite() {
        val set = h.hostPm().fileFavorites.toMutableSet()
        set.add(h.hostActiveDir().absolutePath)
        h.hostPm().fileFavorites = set
        h.hostToast("已收藏当前路径")
    }

    fun showFavorites() {
        val favorites = h.hostPm().fileFavorites.toList().sorted()
        if (favorites.isEmpty()) {
            h.hostToast("暂无收藏路径")
            return
        }
        MaterialAlertDialogBuilder(h.hostActivity())
            .setTitle("路径收藏")
            .setItems(favorites.toTypedArray()) { _, which ->
                val dir = File(favorites[which])
                if (!dir.isDirectory) {
                    h.hostToast("路径不可用")
                    return@setItems
                }
                h.hostNavigateTo(dir)
            }
            .setNegativeButton("清空收藏") { _, _ ->
                h.hostPm().fileFavorites = emptySet()
                h.hostToast("已清空收藏")
            }
            .show()
    }

    fun showQuickDirs() {
        val dirs = listOf(
            "工作目录" to File(h.hostActivity().filesDir, "home/ubuntu-rootfs/root/Workspace"),
            "内部存储" to Environment.getExternalStorageDirectory(),
            "下载目录" to File(Environment.getExternalStorageDirectory(), "Download"),
            "AIDev Home" to File(h.hostActivity().filesDir, "home"),
            "Ubuntu Root" to File(h.hostActivity().filesDir, "home/ubuntu-rootfs"),
            "项目目录" to File(h.hostActivity().filesDir, "home/ubuntu-rootfs/root/projects"),
            "任务日志" to File(h.hostActivity().filesDir, "home/tasks")
        ).filter { it.second.exists() }
        if (dirs.isEmpty()) return h.hostToast("暂无可用常用目录")
        MaterialAlertDialogBuilder(h.hostActivity())
            .setTitle("常用目录")
            .setItems(dirs.map { "${it.first}\n${it.second.absolutePath}" }.toTypedArray()) { _, which ->
                val dir = dirs[which].second
                h.hostRememberRecentDir(dir)
                h.hostNavigateTo(dir)
            }
            .show()
    }

    fun showRecentProjects() {
        val recent = h.hostPm().fileRecentDirs.toMutableSet()
        val roots = listOf(
            File(h.hostActivity().filesDir, "home/ubuntu-rootfs/root/Workspace"),
            File(h.hostActivity().filesDir, "home/ubuntu-rootfs/root/projects"),
            File(h.hostActivity().filesDir, "home/projects"),
            File(Environment.getExternalStorageDirectory(), "Download")
        )
        roots.filter { it.isDirectory }.flatMap { root ->
            root.listFiles()?.filter { it.isDirectory }?.take(20).orEmpty()
        }.forEach { recent.add(it.absolutePath) }
        val dirs = recent.map { File(it) }.filter { it.isDirectory }.sortedBy { it.name.lowercase() }.take(60)
        if (dirs.isEmpty()) return h.hostToast("暂无最近项目")
        MaterialAlertDialogBuilder(h.hostActivity())
            .setTitle("最近项目")
            .setItems(dirs.map { "${it.name}\n${it.absolutePath}" }.toTypedArray()) { _, which ->
                val dir = dirs[which]
                h.hostRememberRecentDir(dir)
                h.hostNavigateTo(dir)
            }
            .show()
    }
}
