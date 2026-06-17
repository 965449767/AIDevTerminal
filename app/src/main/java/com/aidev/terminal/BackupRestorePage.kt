package com.aidev.terminal

import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 数据备份恢复页面
 *
 * 关键设计决策：
 * 1. 不复制到临时目录再打包 — 直接 tar 源路径，避免 OOM
 * 2. 大小计算异步 — 主线程只显示"计算中..."，后台完成后更新
 * 3. 真正的进度条 — 通过定时检查输出文件大小计算百分比
 * 4. 恢复时直接 tar 解压到目标路径 — 不经过临时目录中转
 */
class BackupRestorePage(private val mode: Mode = Mode.BACKUP) : ShellPage {

    enum class Mode { BACKUP, RESTORE }

    private lateinit var activity: Activity
    private lateinit var ui: AIDevUi
    private lateinit var host: ShellHost
    private lateinit var content: LinearLayout
    private val selectedItems = mutableSetOf<String>()
    private val sizeCache = mutableMapOf<String, Long>()
    private val sizeLoaded = mutableSetOf<String>()
    private val handler = Handler(Looper.getMainLooper())
    private var progressPoller: Runnable? = null

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

        if (mode == Mode.BACKUP) renderBackup() else renderRestore()
        return ScrollView(activity).apply { addView(content) }
    }

    override fun onSelected(activity: Activity, view: View) {}

    // ═════════════════════════════════════════════════════════════════
    //  路径与大小
    // ═════════════════════════════════════════════════════════════════

    /** 返回 tar 时的相对路径前缀和源 File */
    private fun getItemTarInfo(id: String): Pair<String, File> = when (id) {
        "ubuntu" -> "home/ubuntu-rootfs" to File(activity.filesDir, "home/ubuntu-rootfs")
        "tasks" -> "home/tasks" to File(activity.filesDir, "home/tasks")
        "ui_prefs" -> "shared_prefs/aidev_ui.xml" to File(activity.filesDir, "shared_prefs/aidev_ui.xml")
        "shell_prefs" -> "shared_prefs/aidev_shell.xml" to File(activity.filesDir, "shared_prefs/aidev_shell.xml")
        "projects" -> "AIDev" to File("/sdcard/AIDev")
        else -> id to File(activity.filesDir, id)
    }

    private fun getItemSize(id: String): Long {
        sizeCache[id]?.let { return it }
        return -1L // 未计算
    }

    private fun formatSize(bytes: Long): String = when {
        bytes < 0 -> "计算中..."
        bytes == 0L -> "不存在"
        bytes >= 1024L * 1024 * 1024 * 1024 -> String.format("%.2f TB", bytes / (1024.0 * 1024 * 1024 * 1024))
        bytes >= 1024L * 1024 * 1024 -> String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024))
        bytes >= 1024L * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
        bytes >= 1024L -> String.format("%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }

    /** 后台计算单个目录大小，完成后更新 UI */
    private fun loadSizeAsync(id: String, textView: TextView) {
        if (sizeLoaded.contains(id)) return
        sizeLoaded.add(id)
        Thread {
            try {
                val (_, file) = getItemTarInfo(id)
                val size = if (file.exists()) calculateDirSize(file) else 0L
                sizeCache[id] = size
                handler.post { textView.text = "${getDataItem(id).name}    ${formatSize(size)}" }
            } catch (_: OutOfMemoryError) {
                handler.post { textView.text = "${getDataItem(id).name}    太大无法计算" }
            } catch (_: Exception) {
                // ignore
            }
        }.start()
    }

    private fun calculateDirSize(dir: File): Long {
        if (!dir.exists() || !dir.isDirectory) return 0
        var total = 0L
        val stack = mutableListOf(dir)
        while (stack.isNotEmpty()) {
            val current = stack.removeAt(stack.size - 1)
            val files = current.listFiles() ?: continue
            for (file in files) {
                if (file.isDirectory) stack.add(file) else total += file.length()
            }
        }
        return total
    }

    private fun getDataItem(id: String) = dataItems.first { it.id == id }

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

        // 数据项 CheckBox
        val checkBoxes = mutableMapOf<String, CheckBox>()
        val sizeTextViews = mutableMapOf<String, TextView>()
        dataItems.forEach { item ->
            val (_, file) = getItemTarInfo(item.id)
            val exists = file.exists()
            val cb = CheckBox(activity).apply {
                text = "${item.name}    ${if (exists) "计算中..." else "不存在"}"
                setTextColor(ui.palette.text)
                isEnabled = exists
                isChecked = exists
                setPadding(ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_4), ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_4))
            }
            if (exists) selectedItems.add(item.id)
            checkBoxes[item.id] = cb
            sizeTextViews[item.id] = cb
            content.addView(cb)
        }

        // 全选联动
        allSwitch.setOnCheckedChangeListener { _, isChecked ->
            checkBoxes.values.forEach { it.isChecked = isChecked }
            selectedItems.clear()
            if (isChecked) selectedItems.addAll(checkBoxes.keys.filter { checkBoxes[it]?.isEnabled == true })
            updateTotalSize()
        }
        checkBoxes.forEach { (id, cb) ->
            cb.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) selectedItems.add(id) else selectedItems.remove(id)
                updateTotalSize()
            }
        }

        // 异步加载大小
        dataItems.forEach { item ->
            if (checkBoxes[item.id]?.isEnabled == true) {
                loadSizeAsync(item.id, checkBoxes[item.id]!!)
            }
        }

        // 预计大小
        content.addView(ui.divider())
        val sizeTextView = ui.text("预计备份大小: 计算中...", DesignTokens.TEXT_BODY, ui.palette.accent, bold = true).apply {
            setPadding(ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_8))
        }
        sizeTextView.tag = "size_text"
        content.addView(sizeTextView)

        // 开始备份
        content.addView(ui.actionRow("开始备份", "打包选中的数据到 /sdcard/AIDev/backups/") {
            if (selectedItems.isEmpty()) { toast("请至少选择一项数据"); return@actionRow }
            confirmAndBackup()
        })
    }

    private fun updateTotalSize() {
        val total = selectedItems.sumOf { getItemSize(it).coerceAtLeast(0) }
        val allKnown = selectedItems.all { getItemSize(it) >= 0 }
        for (i in 0 until content.childCount) {
            val child = content.getChildAt(i)
            if (child.tag == "size_text") {
                (child as TextView).text = "预计备份大小: ${if (allKnown) formatSize(total) else "计算中..."}"
                break
            }
        }
    }

    private fun confirmAndBackup() {
        val totalSize = selectedItems.sumOf { getItemSize(it).coerceAtLeast(0) }
        AlertDialog.Builder(activity)
            .setTitle("确认备份")
            .setMessage("将备份 ${selectedItems.size} 项数据，预计 ${formatSize(totalSize)}\n\n备份路径: /sdcard/AIDev/backups/")
            .setPositiveButton("开始备份") { _, _ -> executeBackup() }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun executeBackup() {
        // 带进度条的对话框
        val dialogView = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(24), ui.dp(16), ui.dp(24), ui.dp(16))
        }
        val statusText = TextView(activity).apply {
            text = "准备中..."
            setTextColor(ui.palette.text)
            textSize = DesignTokens.TEXT_BODY
            gravity = Gravity.CENTER
        }
        val progressBar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = false
            max = 100
            progress = 0
        }
        dialogView.addView(progressBar)
        dialogView.addView(statusText)

        val dialog = AlertDialog.Builder(activity)
            .setTitle("正在备份")
            .setView(dialogView)
            .setCancelable(false)
            .show()

        Thread {
            try {
                val backupDir = File("/sdcard/AIDev/backups").apply { mkdirs() }
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val backupFile = File(backupDir, "backup_$timestamp.tar.gz")

                // 构建 tar 命令：直接从源路径打包，不经过临时目录
                val tarArgs = mutableListOf("tar", "-czf", backupFile.absolutePath)
                val baseDir = activity.filesDir.absolutePath
                // 对于 filesDir 下的项，使用相对路径
                // 对于 /sdcard 下的项，需要特殊处理
                val sdcardItems = mutableListOf<String>()
                val localItems = mutableListOf<String>()

                selectedItems.forEach { id ->
                    val (relPath, _) = getItemTarInfo(id)
                    if (id == "projects") {
                        sdcardItems.add(relPath)
                    } else {
                        localItems.add(relPath)
                    }
                }

                // 分两步：先打包本地数据，再追加外部存储数据
                // 简化方案：使用一个 tar 命令，通过 --transform 重命名
                // 更简单：直接列出所有源文件
                val sources = mutableListOf<String>()
                selectedItems.forEach { id ->
                    val (_, file) = getItemTarInfo(id)
                    if (file.exists()) sources.add(file.absolutePath)
                }

                if (sources.isEmpty()) {
                    handler.post {
                        dialog.dismiss()
                        toast("没有可备份的数据")
                    }
                    return@Thread
                }

                // 计算总大小用于进度
                val totalSize = selectedItems.sumOf { getItemSize(it).coerceAtLeast(0) }

                // 启动 tar 进程
                val cmdArray = arrayOf("tar", "-czf", backupFile.absolutePath) + sources.toTypedArray()
                handler.post { statusText.text = "正在打包..." }

                val process = Runtime.getRuntime().exec(cmdArray)

                // 进度轮询：每 500ms 检查输出文件大小
                progressPoller = object : Runnable {
                    override fun run() {
                        if (backupFile.exists()) {
                            val current = backupFile.length()
                            val percent = if (totalSize > 0) (current * 100 / totalSize).toInt().coerceIn(0, 99) else 0
                            progressBar.progress = percent
                            statusText.text = "正在打包... ${formatSize(current)} / ${formatSize(totalSize)}"
                        }
                        handler.postDelayed(this, 500)
                    }
                }
                handler.post(progressPoller!!)

                // 读取 stderr（tar 的错误输出）
                val errorReader = BufferedReader(InputStreamReader(process.errorStream))
                val errors = StringBuilder()
                var line: String?
                while (errorReader.readLine().also { line = it } != null) {
                    errors.append(line).append("\n")
                }
                errorReader.close()

                val exitCode = process.waitFor()

                // 停止进度轮询
                handler.post { progressPoller?.let { handler.removeCallbacks(it) } }
                progressBar.progress = 100

                if (exitCode == 0 && backupFile.exists()) {
                    handler.post {
                        statusText.text = "备份完成!"
                        dialog.dismiss()
                        toast("备份完成: ${backupFile.name} (${formatSize(backupFile.length())})")
                    }
                } else {
                    handler.post {
                        dialog.dismiss()
                        val errMsg = if (errors.isNotBlank()) errors.toString().take(200) else "未知错误"
                        toast("备份失败 (exit=$exitCode): $errMsg")
                    }
                }
            } catch (e: OutOfMemoryError) {
                handler.post { progressPoller?.let { handler.removeCallbacks(it) }; dialog.dismiss(); toast("内存不足，请减少备份数据量") }
            } catch (e: Exception) {
                handler.post { progressPoller?.let { handler.removeCallbacks(it) }; dialog.dismiss(); toast("备份失败: ${e.message}") }
            }
        }.start()
    }

    // ═════════════════════════════════════════════════════════════════
    //  恢复模式
    // ═════════════════════════════════════════════════════════════════

    private fun renderRestore() {
        content.addView(ui.section("数据恢复", "选择备份文件并恢复数据"))

        val backupDir = File("/sdcard/AIDev/backups")
        val backups = backupDir.listFiles { f -> f.name.endsWith(".tar.gz") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

        if (backups.isEmpty()) {
            content.addView(ui.emptyState("暂无备份文件", "在 /sdcard/AIDev/backups/ 目录中没有找到备份"))
            content.addView(ui.actionRow("打开备份目录", "在终端中查看") {
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

    private fun formatDate(ts: Long) = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ts))

    private fun selectBackupFile(file: File) {
        // 用 tar -tzf 列出内容，确定包含哪些数据项
        val progress = ProgressDialog(activity).apply {
            setMessage("正在读取备份内容...")
            setCancelable(false)
            show()
        }

        Thread {
            try {
                val process = Runtime.getRuntime().exec(arrayOf("tar", "-tzf", file.absolutePath))
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val contents = mutableListOf<String>()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    line?.let { contents.add(it) }
                }
                reader.close()
                process.waitFor()

                // 判断包含哪些数据项
                val availableItems = mutableListOf<DataItem>()
                dataItems.forEach { item ->
                    val (relPath, _) = getItemTarInfo(item.id)
                    if (contents.any { it.startsWith(relPath) }) {
                        availableItems.add(item)
                    }
                }

                if (availableItems.isEmpty()) {
                    handler.post { progress.dismiss(); toast("备份文件内容为空") }
                    return@Thread
                }

                handler.post {
                    progress.dismiss()
                    showRestoreDialog(file, availableItems)
                }
            } catch (e: Exception) {
                handler.post { progress.dismiss(); toast("无法读取备份: ${e.message}") }
            }
        }.start()
    }

    private fun showRestoreDialog(file: File, availableItems: List<DataItem>) {
        selectedItems.clear()

        val dialogContent = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_8), ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_12))
        }

        val allSwitch = Switch(activity).apply { text = "全选/取消全选"; setTextColor(ui.palette.text) }
        dialogContent.addView(allSwitch)
        dialogContent.addView(ui.divider())

        val checkBoxes = mutableMapOf<String, CheckBox>()
        availableItems.forEach { item ->
            val cb = CheckBox(activity).apply {
                text = item.name
                setTextColor(ui.palette.text)
                isChecked = true
                setPadding(ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_4), ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_4))
            }
            checkBoxes[item.id] = cb
            selectedItems.add(item.id)
            dialogContent.addView(cb)
        }

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

        dialogContent.addView(ui.divider())
        dialogContent.addView(ui.actionRow("开始恢复", "将覆盖现有数据，请谨慎操作") {
            if (selectedItems.isEmpty()) { toast("请至少选择一项"); return@actionRow }
            confirmAndRestore(file)
        })

        AlertDialog.Builder(activity)
            .setTitle("恢复: ${file.name}")
            .setView(ScrollView(activity).apply { addView(dialogContent) })
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun confirmAndRestore(file: File) {
        AlertDialog.Builder(activity)
            .setTitle("确认恢复")
            .setMessage("恢复将覆盖现有数据，是否继续？")
            .setPositiveButton("开始恢复") { _, _ -> executeRestore(file) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun executeRestore(backupFile: File) {
        // 带进度条
        val dialogView = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(24), ui.dp(16), ui.dp(24), ui.dp(16))
        }
        val statusText = TextView(activity).apply {
            text = "准备中..."
            setTextColor(ui.palette.text)
            textSize = DesignTokens.TEXT_BODY
            gravity = Gravity.CENTER
        }
        val progressBar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = false
            max = 100
            progress = 0
        }
        dialogView.addView(progressBar)
        dialogView.addView(statusText)

        val dialog = AlertDialog.Builder(activity)
            .setTitle("正在恢复")
            .setView(dialogView)
            .setCancelable(false)
            .show()

        val totalSize = backupFile.length()

        Thread {
            try {
                // 直接解压到 filesDir 根目录（tar 包内的相对路径会自动对齐）
                val targetDir = activity.filesDir
                handler.post { statusText.text = "正在解压..." }

                val process = Runtime.getRuntime().exec(
                    arrayOf("tar", "-xzf", backupFile.absolutePath, "-C", targetDir.absolutePath)
                )

                // 进度轮询
                progressPoller = object : Runnable {
                    override fun run() {
                        // tar 解压没有好的进度指标，用时间估算
                        statusText.text = "正在解压... 请勿关闭应用"
                        handler.postDelayed(this, 1000)
                    }
                }
                handler.post(progressPoller!!)

                // 读取错误
                val errorReader = BufferedReader(InputStreamReader(process.errorStream))
                val errors = StringBuilder()
                var line: String?
                while (errorReader.readLine().also { line = it } != null) {
                    errors.append(line).append("\n")
                }
                errorReader.close()

                val exitCode = process.waitFor()

                handler.post { progressPoller?.let { handler.removeCallbacks(it) }; progressBar.progress = 100 }

                if (exitCode == 0) {
                    handler.post {
                        statusText.text = "恢复完成!"
                        dialog.dismiss()
                        toast("恢复完成! 请重启应用以使设置生效")
                    }
                } else {
                    handler.post {
                        dialog.dismiss()
                        toast("恢复失败 (exit=$exitCode): ${errors.toString().take(200)}")
                    }
                }
            } catch (e: OutOfMemoryError) {
                handler.post { progressPoller?.let { handler.removeCallbacks(it) }; dialog.dismiss(); toast("内存不足") }
            } catch (e: Exception) {
                handler.post { progressPoller?.let { handler.removeCallbacks(it) }; dialog.dismiss(); toast("恢复失败: ${e.message}") }
            }
        }.start()
    }

    private fun toast(text: String) = Toast.makeText(activity, text, Toast.LENGTH_SHORT).show()
}
