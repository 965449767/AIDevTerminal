package com.aidev.terminal

import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Context
import android.os.Handler
import android.os.Looper
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
 *
 * 设计原则：
 * 1. 所有耗时操作（目录遍历、tar 打包）在后台线程执行
 * 2. UI 更新通过 Handler 切回主线程
 * 3. 大小计算使用缓存，避免重复遍历
 * 4. 恢复模式使用独立 Activity 风格的对话框，不嵌套在 AlertDialog 中
 * 5. 临时文件使用 try-finally 确保清理
 */
class BackupRestorePage(private val mode: Mode = Mode.BACKUP) : ShellPage {

    enum class Mode { BACKUP, RESTORE }

    private lateinit var activity: Activity
    private lateinit var ui: AIDevUi
    private lateinit var host: ShellHost
    private lateinit var content: LinearLayout
    private val selectedItems = mutableSetOf<String>()
    private val sizeCache = mutableMapOf<String, Long>()
    private val handler = Handler(Looper.getMainLooper())

    data class DataItem(val id: String, val name: String, val desc: String)

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

        // 预加载大小（后台线程）
        preloadSizes()

        if (mode == Mode.BACKUP) {
            renderBackup()
        } else {
            renderRestore()
        }
        return ScrollView(activity).apply { addView(content) }
    }

    override fun onSelected(activity: Activity, view: View) {}

    // ═════════════════════════════════════════════════════════════════
    //  大小计算（带缓存）
    // ═════════════════════════════════════════════════════════════════

    private fun preloadSizes() {
        Thread {
            dataItems.forEach { item ->
                sizeCache[item.id] = getItemSize(item.id)
            }
        }.start()
    }

    private fun getItemPath(id: String): File = when (id) {
        "ubuntu" -> File(activity.filesDir, "home/ubuntu-rootfs")
        "tasks" -> File(activity.filesDir, "home/tasks")
        "ui_prefs" -> File(activity.filesDir, "../shared_prefs/aidev_ui.xml")
        "shell_prefs" -> File(activity.filesDir, "../shared_prefs/aidev_shell.xml")
        "projects" -> File("/sdcard/AIDev")
        else -> File(activity.filesDir, id)
    }

    private fun getItemSize(id: String): Long {
        sizeCache[id]?.let { return it }
        val path = getItemPath(id)
        val size = if (path.isDirectory) calculateDirSize(path) else if (path.exists()) path.length() else 0
        sizeCache[id] = size
        return size
    }

    private fun calculateDirSize(dir: File): Long {
        if (!dir.exists() || !dir.isDirectory) return 0
        var total = 0L
        val files = dir.listFiles() ?: return 0
        for (file in files) {
            total += if (file.isDirectory) calculateDirSize(file) else file.length()
        }
        return total
    }

    private fun formatSize(bytes: Long): String = when {
        bytes >= 1024L * 1024 * 1024 * 1024 -> String.format("%.2f TB", bytes / (1024.0 * 1024 * 1024 * 1024))
        bytes >= 1024L * 1024 * 1024 -> String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024))
        bytes >= 1024L * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
        bytes >= 1024L -> String.format("%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }

    // ═════════════════════════════════════════════════════════════════
    //  备份模式
    // ═════════════════════════════════════════════════════════════════

    private fun renderBackup() {
        content.addView(ui.section("数据备份", "选择要备份的数据项"))

        // 全选开关
        val allSwitch = Switch(activity).apply {
            text = "全选/取消全选"
            setTextColor(ui.palette.text)
            setPadding(ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_8), ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_8))
        }
        content.addView(allSwitch)
        content.addView(ui.divider())

        // 数据项 CheckBox 列表
        val checkBoxes = mutableMapOf<String, CheckBox>()
        dataItems.forEach { item ->
            val cb = CheckBox(activity).apply {
                text = "${item.name}    ${formatSize(getItemSize(item.id))}"
                setTextColor(ui.palette.text)
                setPadding(ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_4), ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_4))
            }
            checkBoxes[item.id] = cb
            content.addView(cb)
        }

        // 全选开关联动
        allSwitch.setOnCheckedChangeListener { _, isChecked ->
            checkBoxes.values.forEach { it.isChecked = isChecked }
            selectedItems.clear()
            if (isChecked) selectedItems.addAll(dataItems.map { it.id })
            updateTotalSize()
        }

        // 单个 CheckBox 联动
        checkBoxes.forEach { (id, cb) ->
            cb.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) selectedItems.add(id) else selectedItems.remove(id)
                updateTotalSize()
            }
        }

        // 预计大小
        content.addView(ui.divider())
        val sizeTextView = ui.text("预计备份大小: 0 B", DesignTokens.TEXT_BODY, ui.palette.accent, bold = true).apply {
            setPadding(ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_8))
        }
        sizeTextView.tag = "size_text"
        content.addView(sizeTextView)

        // 开始备份按钮
        content.addView(ui.actionRow("开始备份", "打包选中的数据到 /sdcard/AIDev/backups/") {
            if (selectedItems.isEmpty()) {
                toast("请至少选择一项数据")
                return@actionRow
            }
            confirmAndBackup()
        })
    }

    private fun updateTotalSize() {
        val total = selectedItems.sumOf { getItemSize(it) }
        for (i in 0 until content.childCount) {
            val child = content.getChildAt(i)
            if (child.tag == "size_text") {
                (child as android.widget.TextView).text = "预计备份大小: ${formatSize(total)}"
                break
            }
        }
    }

    private fun confirmAndBackup() {
        val totalSize = selectedItems.sumOf { getItemSize(it) }
        AlertDialog.Builder(activity)
            .setTitle("确认备份")
            .setMessage("将备份 ${selectedItems.size} 项数据，预计 ${formatSize(totalSize)}\n\n备份路径: /sdcard/AIDev/backups/")
            .setPositiveButton("开始备份") { _, _ -> executeBackup() }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun executeBackup() {
        val progress = ProgressDialog(activity).apply {
            setMessage("正在备份，请勿关闭应用...")
            setCancelable(false)
            show()
        }

        Thread {
            var tempDir: File? = null
            try {
                val backupDir = File("/sdcard/AIDev/backups").apply { mkdirs() }
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val backupFile = File(backupDir, "backup_$timestamp.tar.gz")

                // 创建临时目录
                tempDir = File(activity.cacheDir, "backup_temp_$timestamp")
                tempDir.mkdirs()

                // 复制选中的数据到临时目录
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
                val exitCode = process.waitFor()

                if (exitCode == 0) {
                    handler.post {
                        progress.dismiss()
                        toast("备份完成: ${backupFile.name}")
                    }
                } else {
                    val error = process.errorStream.bufferedReader().readText()
                    handler.post {
                        progress.dismiss()
                        toast("备份失败 (exit=$exitCode): $error")
                    }
                }
            } catch (e: Exception) {
                handler.post {
                    progress.dismiss()
                    toast("备份失败: ${e.message}")
                }
            } finally {
                tempDir?.deleteRecursively()
            }
        }.start()
    }

    // ═════════════════════════════════════════════════════════════════
    //  恢复模式
    // ═════════════════════════════════════════════════════════════════

    private fun renderRestore() {
        content.addView(ui.section("数据恢复", "选择备份文件并恢复数据"))

        // 扫描备份文件
        val backupDir = File("/sdcard/AIDev/backups")
        val backups = backupDir.listFiles { f -> f.name.endsWith(".tar.gz") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

        if (backups.isEmpty()) {
            content.addView(ui.emptyState("暂无备份文件", "在 /sdcard/AIDev/backups/ 目录中没有找到备份"))
            content.addView(ui.actionRow("打开备份目录", "在文件管理器中查看") {
                host.openTerminal("ls -la /sdcard/AIDev/backups/")
            })
            return
        }

        content.addView(ui.text("选择备份文件", DesignTokens.TEXT_BODY, ui.palette.text, bold = true).apply {
            setPadding(0, ui.dp(DesignTokens.SPACE_8), 0, ui.dp(DesignTokens.SPACE_4))
        })

        backups.forEach { file ->
            content.addView(ui.actionRow(file.name, "${formatSize(file.length())}  ·  ${formatDate(file.lastModified())}") {
                selectBackupFile(file)
            })
        }
    }

    private fun formatDate(timestamp: Long): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
    }

    private fun selectBackupFile(file: File) {
        val progress = ProgressDialog(activity).apply {
            setMessage("正在读取备份内容...")
            setCancelable(false)
            show()
        }

        Thread {
            var tempDir: File? = null
            try {
                // 临时解压预览
                tempDir = File(activity.cacheDir, "restore_preview_${System.currentTimeMillis()}")
                tempDir.mkdirs()

                val process = Runtime.getRuntime().exec(
                    arrayOf("tar", "-xzf", file.absolutePath, "-C", tempDir.absolutePath)
                )
                process.waitFor()

                // 检查备份中包含哪些数据项
                val availableItems = dataItems.filter { File(tempDir, it.id).exists() }

                if (availableItems.isEmpty()) {
                    handler.post {
                        progress.dismiss()
                        toast("备份文件内容为空或格式不正确")
                    }
                    tempDir.deleteRecursively()
                    return@Thread
                }

                handler.post {
                    progress.dismiss()
                    showRestoreDialog(file, tempDir, availableItems)
                }
            } catch (e: Exception) {
                handler.post {
                    progress.dismiss()
                    toast("无法读取备份文件: ${e.message}")
                }
                tempDir?.deleteRecursively()
            }
        }.start()
    }

    private fun showRestoreDialog(file: File, tempDir: File, availableItems: List<DataItem>) {
        selectedItems.clear()

        val dialogContent = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_8), ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_12))
        }

        // 全选开关
        val allSwitch = Switch(activity).apply {
            text = "全选/取消全选"
            setTextColor(ui.palette.text)
        }
        dialogContent.addView(allSwitch)
        dialogContent.addView(ui.divider())

        // 数据项列表
        val checkBoxes = mutableMapOf<String, CheckBox>()
        availableItems.forEach { item ->
            val size = formatSize(calculateDirSize(File(tempDir, item.id)))
            val cb = CheckBox(activity).apply {
                text = "${item.name}    $size"
                setTextColor(ui.palette.text)
                isChecked = true
                setPadding(ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_4), ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_4))
            }
            checkBoxes[item.id] = cb
            selectedItems.add(item.id)
            dialogContent.addView(cb)
        }

        // 联动
        allSwitch.setOnCheckedChangeListener { _, isChecked ->
            checkBoxes.values.forEach { it.isChecked = isChecked }
            selectedItems.clear()
            if (isChecked) selectedItems.addAll(checkBoxes.keys)
        }
        checkBoxes.forEach { (id, cb) ->
            cb.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) selectedItems.add(id) else selectedItems.remove(id)
            }
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
            .setOnDismissListener { tempDir.deleteRecursively() }
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
            setMessage("正在恢复，请勿关闭应用...")
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

                handler.post {
                    progress.dismiss()
                    toast("恢复完成: $success 成功${if (failed > 0) ", $failed 失败" else ""}")
                }
            } catch (e: Exception) {
                handler.post {
                    progress.dismiss()
                    toast("恢复失败: ${e.message}")
                }
            } finally {
                tempDir.deleteRecursively()
            }
        }.start()
    }

    // ═════════════════════════════════════════════════════════════════
    //  通用工具
    // ═════════════════════════════════════════════════════════════════

    private fun copyRecursive(src: File, dest: File) {
        if (src.isDirectory) {
            dest.mkdirs()
            val files = src.listFiles() ?: return
            for (file in files) {
                copyRecursive(file, File(dest, file.name))
            }
        } else {
            dest.parentFile?.mkdirs()
            src.copyTo(dest, overwrite = true)
        }
    }

    private fun toast(text: String) {
        Toast.makeText(activity, text, Toast.LENGTH_SHORT).show()
    }
}
