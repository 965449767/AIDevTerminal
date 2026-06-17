package com.aidev.terminal

import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.Toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 数据备份恢复页面：可视化选择备份/恢复项，显示大小，支持全选
 */
class BackupRestorePage(private val mode: Mode = Mode.BACKUP) : ShellPage {

    enum class Mode { BACKUP, RESTORE }

    private lateinit var activity: Activity
    private lateinit var ui: AIDevUi
    private lateinit var host: ShellHost
    private lateinit var content: LinearLayout
    private val selectedItems = mutableSetOf<String>()
    private var allSwitch: Switch? = null
    private var backupFile: File? = null

    // 数据项定义
    private val dataItems = listOf(
        DataItem("ubuntu", "Ubuntu 环境", "PRoot rootfs 和系统配置"),
        DataItem("tasks", "任务数据", "后台任务日志和元数据"),
        DataItem("ui_prefs", "UI 设置", "主题、背景、字号、快捷键等"),
        DataItem("shell_prefs", "Shell 设置", "别名、收藏夹等"),
        DataItem("projects", "项目目录", "外部存储中的项目文件")
    )

    override fun create(activity: Activity, ui: AIDevUi, host: ShellHost): View {
        this.activity = activity
        this.ui = ui
        this.host = host
        content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_8), ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_24))
        }
        if (mode == Mode.BACKUP) {
            renderBackup()
        } else {
            renderRestore()
        }
        return ScrollView(activity).apply { addView(content) }
    }

    override fun onSelected(activity: Activity, view: View) {}

    // ─────────────────────────────────────────────────────────────────
    //  备份模式
    // ─────────────────────────────────────────────────────────────────

    private fun renderBackup() {
        content.addView(ui.section("数据备份", "选择要备份的数据项"))

        // 全选开关
        allSwitch = Switch(activity).apply {
            text = "全选/取消全选"
            setTextColor(ui.palette.text)
            setPadding(ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_8), ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_8))
            setOnCheckedChangeListener { _, isChecked ->
                dataItems.forEach { item ->
                    if (isChecked) selectedItems.add(item.id) else selectedItems.remove(item.id)
                }
                reloadBackupItems()
            }
        }
        content.addView(allSwitch)
        content.addView(ui.divider())

        // 数据项列表
        reloadBackupItems()

        // 预计大小 + 开始按钮
        content.addView(ui.divider())
        val totalSize = calculateSelectedSize()
        val sizeText = ui.text("预计备份大小: ${formatSize(totalSize)}", DesignTokens.TEXT_BODY, ui.palette.accent, bold = true)
        sizeText.tag = "size_text"
        content.addView(sizeText.apply {
            setPadding(ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_8))
        })

        content.addView(ui.actionRow("开始备份", "打包选中的数据到 /sdcard/AIDev/backups/") {
            if (selectedItems.isEmpty()) {
                toast("请至少选择一项数据")
                return@actionRow
            }
            confirmAndBackup()
        })
    }

    private fun reloadBackupItems() {
        // 移除旧的数据项视图（保留标题、开关、分割线、大小文本、按钮）
        val toRemove = mutableListOf<View>()
        var foundDivider = false
        for (i in 0 until content.childCount) {
            val child = content.getChildAt(i)
            if (child.tag == "backup_item") {
                toRemove.add(child)
            }
        }
        toRemove.forEach { content.removeView(it) }

        // 找到开关后的分割线位置
        var insertIndex = 2 // 标题 + 开关 + 分割线
        dataItems.forEach { item ->
            val checkBox = CheckBox(activity).apply {
                text = "${item.name}    ${formatSize(getItemSize(item.id))}"
                setTextColor(ui.palette.text)
                isChecked = selectedItems.contains(item.id)
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) selectedItems.add(item.id) else selectedItems.remove(item.id)
                    updateSizeText()
                }
                tag = "backup_item"
            }
            content.addView(checkBox, insertIndex)
            insertIndex++
        }
    }

    private fun updateSizeText() {
        val totalSize = calculateSelectedSize()
        for (i in 0 until content.childCount) {
            val child = content.getChildAt(i)
            if (child.tag == "size_text") {
                (child as android.widget.TextView).text = "预计备份大小: ${formatSize(totalSize)}"
                break
            }
        }
    }

    private fun confirmAndBackup() {
        AlertDialog.Builder(activity)
            .setTitle("确认备份")
            .setMessage("将备份 ${selectedItems.size} 项数据，预计 ${formatSize(calculateSelectedSize())}，是否继续？")
            .setPositiveButton("开始备份") { _, _ -> executeBackup() }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun executeBackup() {
        val progress = ProgressDialog(activity).apply {
            setMessage("正在备份...")
            setCancelable(false)
            show()
        }

        Thread {
            try {
                val backupDir = File("/sdcard/AIDev/backups").apply { mkdirs() }
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val backupFile = File(backupDir, "backup_$timestamp.tar.gz")

                // 创建临时目录收集数据
                val tempDir = File(activity.cacheDir, "backup_temp_$timestamp")
                tempDir.mkdirs()

                selectedItems.forEach { id ->
                    val src = getItemPath(id)
                    if (src.exists()) {
                        val dest = File(tempDir, id)
                        copyRecursive(src, dest)
                    }
                }

                // 使用 tar 打包
                val process = Runtime.getRuntime().exec(
                    arrayOf("tar", "-czf", backupFile.absolutePath, "-C", tempDir.absolutePath, ".")
                )
                process.waitFor()

                // 清理临时目录
                tempDir.deleteRecursively()

                activity.runOnUiThread {
                    progress.dismiss()
                    toast("备份完成: ${backupFile.name}")
                }
            } catch (e: Exception) {
                activity.runOnUiThread {
                    progress.dismiss()
                    toast("备份失败: ${e.message}")
                }
            }
        }.start()
    }

    // ─────────────────────────────────────────────────────────────────
    //  恢复模式
    // ─────────────────────────────────────────────────────────────────

    private fun renderRestore() {
        content.addView(ui.section("数据恢复", "选择备份文件并恢复数据"))

        // 扫描备份文件
        val backupDir = File("/sdcard/AIDev/backups")
        val backups = backupDir.listFiles { f -> f.name.endsWith(".tar.gz") }?.sortedByDescending { it.lastModified() } ?: emptyList()

        if (backups.isEmpty()) {
            content.addView(ui.emptyState("暂无备份文件", "在 /sdcard/AIDev/backups/ 目录中没有找到备份"))
            return
        }

        // 备份文件选择
        content.addView(ui.text("选择备份文件", DesignTokens.TEXT_BODY, ui.palette.text, bold = true).apply {
            setPadding(0, ui.dp(DesignTokens.SPACE_8), 0, ui.dp(DesignTokens.SPACE_4))
        })

        backups.forEach { file ->
            content.addView(ui.actionRow(file.name, formatSize(file.length())) {
                selectBackupFile(file)
            })
        }
    }

    private fun selectBackupFile(file: File) {
        backupFile = file
        selectedItems.clear()

        // 临时解压预览内容
        val tempDir = File(activity.cacheDir, "restore_preview")
        tempDir.deleteRecursively()
        tempDir.mkdirs()

        try {
            val process = Runtime.getRuntime().exec(
                arrayOf("tar", "-xzf", file.absolutePath, "-C", tempDir.absolutePath)
            )
            process.waitFor()
        } catch (e: Exception) {
            toast("无法读取备份文件: ${e.message}")
            return
        }

        // 显示恢复选项
        val dialogContent = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_8), ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_12))
        }

        // 全选开关
        val switch = Switch(activity).apply {
            text = "全选/取消全选"
            setTextColor(ui.palette.text)
            setOnCheckedChangeListener { _, isChecked ->
                dataItems.forEach { item ->
                    val exists = File(tempDir, item.id).exists()
                    if (exists) {
                        if (isChecked) selectedItems.add(item.id) else selectedItems.remove(item.id)
                    }
                }
            }
        }
        dialogContent.addView(switch)
        dialogContent.addView(ui.divider())

        // 数据项列表（只显示备份中存在的）
        dataItems.forEach { item ->
            val exists = File(tempDir, item.id).exists()
            val size = if (exists) formatSize(calculateDirSize(File(tempDir, item.id))) else "未包含"
            val checkBox = CheckBox(activity).apply {
                text = "${item.name}    $size"
                setTextColor(ui.palette.text)
                isEnabled = exists
                isChecked = exists
                if (exists) selectedItems.add(item.id)
            }
            dialogContent.addView(checkBox)
        }

        // 开始恢复按钮
        dialogContent.addView(ui.divider())
        dialogContent.addView(ui.actionRow("开始恢复", "将覆盖现有数据，请谨慎操作") {
            if (selectedItems.isEmpty()) {
                toast("请至少选择一项数据")
                return@actionRow
            }
            confirmAndRestore(tempDir)
        })

        AlertDialog.Builder(activity)
            .setTitle("恢复: ${file.name}")
            .setView(ScrollView(activity).apply { addView(dialogContent) })
            .setNegativeButton("关闭") { _, _ -> tempDir.deleteRecursively() }
            .show()
    }

    private fun confirmAndRestore(tempDir: File) {
        AlertDialog.Builder(activity)
            .setTitle("确认恢复")
            .setMessage("恢复将覆盖现有数据，是否继续？")
            .setPositiveButton("开始恢复") { _, _ -> executeRestore(tempDir) }
            .setNegativeButton("取消") { _, _ -> tempDir.deleteRecursively() }
            .show()
    }

    private fun executeRestore(tempDir: File) {
        val progress = ProgressDialog(activity).apply {
            setMessage("正在恢复...")
            setCancelable(false)
            show()
        }

        Thread {
            try {
                var success = 0
                var failed = 0

                selectedItems.forEach { id ->
                    val src = File(tempDir, id)
                    val dest = getItemPath(id)
                    if (src.exists()) {
                        try {
                            if (dest.exists()) dest.deleteRecursively()
                            copyRecursive(src, dest)
                            success++
                        } catch (e: Exception) {
                            failed++
                        }
                    }
                }

                tempDir.deleteRecursively()

                activity.runOnUiThread {
                    progress.dismiss()
                    toast("恢复完成: $success 成功, $failed 失败")
                }
            } catch (e: Exception) {
                tempDir.deleteRecursively()
                activity.runOnUiThread {
                    progress.dismiss()
                    toast("恢复失败: ${e.message}")
                }
            }
        }.start()
    }

    // ─────────────────────────────────────────────────────────────────
    //  工具方法
    // ─────────────────────────────────────────────────────────────────

    private fun getItemPath(id: String): File = when (id) {
        "ubuntu" -> File(activity.filesDir, "home/ubuntu-rootfs")
        "tasks" -> File(activity.filesDir, "home/tasks")
        "ui_prefs" -> File(activity.filesDir, "../shared_prefs/aidev_ui.xml")
        "shell_prefs" -> File(activity.filesDir, "../shared_prefs/aidev_shell.xml")
        "projects" -> File("/sdcard/AIDev")
        else -> File(activity.filesDir, id)
    }

    private fun getItemSize(id: String): Long {
        val path = getItemPath(id)
        return if (path.isDirectory) calculateDirSize(path) else path.length()
    }

    private fun calculateSelectedSize(): Long {
        return selectedItems.sumOf { getItemSize(it) }
    }

    private fun calculateDirSize(dir: File): Long {
        if (!dir.exists() || !dir.isDirectory) return 0
        return dir.listFiles()?.sumOf { if (it.isDirectory) calculateDirSize(it) else it.length() } ?: 0
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024))
            bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
            bytes >= 1024 -> String.format("%.1f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }

    private fun copyRecursive(src: File, dest: File) {
        if (src.isDirectory) {
            dest.mkdirs()
            src.listFiles()?.forEach { copyRecursive(it, File(dest, it.name)) }
        } else {
            dest.parentFile?.mkdirs()
            src.copyTo(dest, overwrite = true)
        }
    }

    private fun toast(text: String) = Toast.makeText(activity, text, Toast.LENGTH_SHORT).show()

    private data class DataItem(val id: String, val name: String, val desc: String)
}
