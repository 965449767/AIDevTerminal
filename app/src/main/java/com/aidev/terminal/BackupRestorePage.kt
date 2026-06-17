package com.aidev.terminal

import android.app.Activity
import android.app.AlertDialog
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 数据备份恢复页面 v5
 *
 * 核心设计：
 * 1. 逐项打包：每个数据项单独打 tar，完成后合并，精确进度
 * 2. proot 统一执行壳：所有 tar/dpkg 在 Ubuntu 环境内执行
 * 3. manifest.json：备份时生成清单，恢复时秒开
 * 4. 中断清理：AtomicBoolean + process.destroy() + 残留文件删除
 * 5. 恢复前备份：目标路径重命名为 .backup.old
 * 6. 大小预估提示：确认对话框显示耗时预期
 */
class BackupRestorePage(private val mode: Mode = Mode.BACKUP) : ShellPage {

    enum class Mode { BACKUP, RESTORE }

    private lateinit var activity: Activity
    private lateinit var ui: AIDevUi
    private lateinit var host: ShellHost
    private lateinit var content: LinearLayout
    private val selectedItems = mutableSetOf<String>()
    private val handler = Handler(Looper.getMainLooper())
    private var cancelled = AtomicBoolean(false)
    private var currentProcess: Process? = null

    data class BackupItem(
        val id: String,
        val name: String,
        val desc: String,
        val isLarge: Boolean,
        val defaultSelected: Boolean,
        val getSourcePaths: () -> List<String>,
        val isInRootfs: Boolean
    )

    private val backupItems by lazy {
        listOf(
            BackupItem("ubuntu_config", "Ubuntu 配置", ".bashrc, .bash_history 等", false, true,
                { listOf("root/.bashrc", "root/.bash_history", "root/.profile", "root/.gitconfig") }, true),
            BackupItem("ubuntu_packages", "已安装软件包", "dpkg --get-selections 列表", false, true,
                { listOf("/host-home/tasks/.backup_packages.list") }, true),
            BackupItem("ubuntu_projects", "Ubuntu 项目", "/root/projects/ 目录", false, true,
                { listOf("root/projects") }, true),
            BackupItem("ubuntu_rootfs", "完整 Ubuntu 环境", "整个 rootfs (1-2GB)", true, false,
                { listOf(".") }, true),
            BackupItem("tasks", "任务数据", "后台任务日志和元数据", false, true,
                { listOf(File(activity.filesDir, "home/tasks").absolutePath) }, false),
            BackupItem("ui_prefs", "UI 设置", "主题、背景、字号等", false, true,
                { listOf(File(activity.filesDir, "shared_prefs/aidev_ui.xml").absolutePath) }, false),
            BackupItem("shell_prefs", "Shell 设置", "别名、收藏夹等", false, true,
                { listOf(File(activity.filesDir, "shared_prefs/aidev_shell.xml").absolutePath) }, false),
            BackupItem("projects", "Android 项目", "/sdcard/AIDev/ 目录", true, false,
                { listOf("/sdcard/AIDev") }, false)
        )
    }

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
    //  proot 统一执行壳
    // ═════════════════════════════════════════════════════════════════

    private fun runInProot(command: String): Process {
        val appInfo = activity.applicationInfo
        val nativeDir = appInfo.nativeLibraryDir
        val proot = File(nativeDir, "libproot.so").absolutePath
        val prootLoader = File(nativeDir, "libproot_loader.so").absolutePath
        val rootfs = File(activity.filesDir, "home/ubuntu-rootfs").absolutePath
        val aidevHome = File(activity.filesDir, "home").absolutePath
        val prootTmpDir = File(activity.cacheDir, "proot_tmp").apply { mkdirs() }.absolutePath

        // 检查 proot 和 rootfs 是否存在
        if (!File(proot).exists()) {
            Log.e("AIDevBackup", "libproot.so not found at: $proot")
        }
        if (!File(prootLoader).exists()) {
            Log.e("AIDevBackup", "libproot_loader.so not found at: $prootLoader")
        }
        if (!File(rootfs).exists()) {
            Log.e("AIDevBackup", "rootfs not found at: $rootfs")
        }

        // 使用 ProcessBuilder 正确设置环境变量
        // proot 需要 PROOT_LOADER 和 PROOT_TMP_DIR 才能正常工作
        val shellCmd = buildString {
            append("$proot --link2symlink -0 -r $rootfs ")
            append("-b /dev -b /proc -b /sys -b /system/bin -b /system/etc ")
            append("-b /system/framework -b /sdcard -b /storage ")
            append("-b $aidevHome:/host-home -w /root ")
            append("/usr/bin/env -i HOME=/root ")
            append("PATH=/host-home/dev-env/bin:/system/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin ")
            append("TERM=xterm-256color LANG=C.UTF-8 LC_ALL=C.UTF-8 ")
            append("/bin/sh -c \"$command\"")
        }

        Log.d("AIDevBackup", "runInProot cmd: ${shellCmd.take(200)}...")

        val pb = ProcessBuilder("/system/bin/sh", "-c", shellCmd)
        val env = pb.environment()
        env["PROOT_LOADER"] = prootLoader
        env["PROOT_TMP_DIR"] = prootTmpDir
        env["LD_LIBRARY_PATH"] = nativeDir
        return pb.start()
    }

    private fun runNative(command: Array<String>): Process {
        Log.d("AIDevBackup", "runNative: ${command.joinToString(" ")}")
        return Runtime.getRuntime().exec(command)
    }

    // ═════════════════════════════════════════════════════════════════
    //  流消费（防止死锁）
    // ═════════════════════════════════════════════════════════════════

    /**
     * 消费并收集 stdout 和 stderr，返回 Pair<stdout, stderr>
     */
    private fun collectOutput(process: Process): Pair<String, String> {
        val stdout = process.inputStream.bufferedReader().use { it.readText() }
        val stderr = process.errorStream.bufferedReader().use { it.readText() }
        return Pair(stdout, stderr)
    }

    /**
     * 在后台线程消费流，防止死锁。用于不需要读取输出的场景。
     */
    private fun drainStream(process: Process) {
        Thread { process.inputStream.bufferedReader().use { it.readText() } }.start()
        Thread { process.errorStream.bufferedReader().use { it.readText() } }.start()
    }

    private fun waitForProcess(process: Process): Int {
        drainStream(process)
        return process.waitFor()
    }

    // ═════════════════════════════════════════════════════════════════
    //  备份模式
    // ═════════════════════════════════════════════════════════════════

    private fun renderBackup() {
        content.addView(ui.section("数据备份", "选择要备份的数据项"))

        val allSwitch = Switch(activity).apply {
            text = "全选/取消全选"
            setTextColor(ui.palette.text)
            setPadding(ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_8), ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_8))
        }
        content.addView(allSwitch)
        content.addView(ui.divider())

        val checkBoxes = mutableMapOf<String, CheckBox>()
        backupItems.forEach { item ->
            val cb = CheckBox(activity).apply {
                text = item.name
                setTextColor(ui.palette.text)
                isEnabled = true
                isChecked = item.defaultSelected
                setPadding(ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_4), ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_4))
            }
            if (item.defaultSelected) selectedItems.add(item.id)
            checkBoxes[item.id] = cb
            content.addView(cb)
        }

        allSwitch.setOnCheckedChangeListener { _, isChecked ->
            checkBoxes.values.forEach { it.isChecked = isChecked }
            selectedItems.clear()
            if (isChecked) selectedItems.addAll(backupItems.map { it.id })
        }
        checkBoxes.forEach { (id, cb) ->
            cb.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) selectedItems.add(id) else selectedItems.remove(id)
            }
        }

        content.addView(ui.divider())
        content.addView(ui.actionRow("开始备份", "打包选中的数据") {
            if (selectedItems.isEmpty()) { toast("请至少选择一项数据"); return@actionRow }
            showBackupConfirm()
        })
    }

    private fun showBackupConfirm() {
        val hasLarge = selectedItems.any { id -> backupItems.find { it.id == id }?.isLarge == true }
        val estimate = if (hasLarge) {
            "包含大体积数据，预计耗时 3-10 分钟，请确保存储空间充足"
        } else {
            "预计很快完成（通常 10-30 秒）"
        }
        AlertDialog.Builder(activity)
            .setTitle("确认备份")
            .setMessage("将备份 ${selectedItems.size} 项数据\n\n$estimate\n\n备份路径: /sdcard/AIDev/backups/")
            .setPositiveButton("开始备份") { _, _ -> executeBackup() }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun executeBackup() {
        cancelled.set(false)

        // 进度对话框
        val dialogView = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(24), ui.dp(16), ui.dp(24), ui.dp(16))
        }
        val statusText = TextView(activity).apply {
            text = "准备中..."
            setTextColor(ui.palette.text)
            textSize = DesignTokens.TEXT_BODY
        }
        val progressBar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
        }
        val itemStatusLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, ui.dp(8), 0, 0)
        }
        val itemStatusViews = mutableMapOf<String, TextView>()
        selectedItems.forEach { id ->
            val name = backupItems.find { it.id == id }?.name ?: id
            val tv = TextView(activity).apply {
                text = "  ○ $name"
                setTextColor(ui.palette.muted)
                textSize = DesignTokens.TEXT_CAPTION
            }
            itemStatusViews[id] = tv
            itemStatusLayout.addView(tv)
        }

        dialogView.addView(progressBar)
        dialogView.addView(statusText)
        dialogView.addView(itemStatusLayout)

        val dialog = AlertDialog.Builder(activity)
            .setTitle("正在备份")
            .setView(dialogView)
            .setCancelable(false)
            .setNegativeButton("取消") { _, _ ->
                cancelled.set(true)
                currentProcess?.destroy()
            }
            .show()

        Thread {
            var tempDir: File? = null
            try {
                val backupDir = File("/sdcard/AIDev/backups").apply { mkdirs() }
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val finalFile = File(backupDir, "backup_$timestamp.tar.gz")
                tempDir = File(activity.cacheDir, "backup_v5_$timestamp")
                tempDir.mkdirs()

                val selectedList = backupItems.filter { selectedItems.contains(it.id) }
                val total = selectedList.size
                var completed = 0

                // 生成包列表（ubuntu_packages 特殊处理）
                if (selectedItems.contains("ubuntu_packages")) {
                    handler.post { statusText.text = "正在获取软件包列表..." }
                    val pkgFile = File(activity.filesDir, "home/tasks/.backup_packages.list")
                    pkgFile.parentFile?.mkdirs()
                    val proc = runInProot("dpkg --get-selections > /host-home/tasks/.backup_packages.list 2>/dev/null || echo 'no-dpkg' > /host-home/tasks/.backup_packages.list")
                    waitForProcess(proc)
                }

                // 逐项打包
                for (item in selectedList) {
                    if (cancelled.get()) break

                    handler.post {
                        statusText.text = "正在打包: ${item.name} (${completed + 1}/$total)"
                        itemStatusViews[item.id]?.text = "  ⟳ ${item.name}"
                        itemStatusViews[item.id]?.setTextColor(ui.palette.accent)
                    }

                    val sourcePaths = item.getSourcePaths()
                    val subTar = File(tempDir, "${item.id}.tar.gz")
                    var exitCode = 1

                    try {
                        if (item.isInRootfs) {
                            // proot 内打包：生成文件列表避免参数过长
                            val fileListContent = sourcePaths.joinToString("\n") { it.trim() }
                            val listFile = File(activity.filesDir, "home/tasks/.backup_filelist_${item.id}.txt")
                            listFile.parentFile?.mkdirs()
                            listFile.writeText(fileListContent)
                            Log.d("AIDevBackup", "[${item.id}] fileList: $fileListContent")

                            // 先检查文件是否存在 - 结果写入临时文件避免流竞争
                            val checkResultFile = File(activity.filesDir, "home/tasks/.backup_check_${item.id}.txt")
                            val checkCmd = sourcePaths.joinToString(" && ") { "test -e $it" }
                            val checkProc = runInProot("$checkCmd && echo EXISTS > /host-home/tasks/.backup_check_${item.id}.txt || echo MISSING > /host-home/tasks/.backup_check_${item.id}.txt")
                            // 收集 stderr 诊断错误
                            val checkStderr = checkProc.errorStream.bufferedReader().readText()
                            val checkCode = checkProc.waitFor()
                            val checkResult = if (checkResultFile.exists()) checkResultFile.readText().trim() else "NO_RESULT"
                            Log.d("AIDevBackup", "[${item.id}] checkCode=$checkCode, checkResult=$checkResult")
                            if (checkStderr.isNotBlank()) {
                                Log.e("AIDevBackup", "[${item.id}] check stderr: $checkStderr")
                            }

                            if (checkResult == "EXISTS") {
                                val tarCmd = "tar -czf /host-home/tasks/.backup_temp.tar.gz -C / -T /host-home/tasks/.backup_filelist_${item.id}.txt"
                                val proc = runInProot(tarCmd)
                                exitCode = waitForProcess(proc)
                                Log.d("AIDevBackup", "[${item.id}] tar exitCode=$exitCode")

                                val tempTarInRootfs = File(activity.filesDir, "home/tasks/.backup_temp.tar.gz")
                                if (tempTarInRootfs.exists() && tempTarInRootfs.length() > 0) {
                                    tempTarInRootfs.renameTo(subTar)
                                    Log.d("AIDevBackup", "[${item.id}] moved to ${subTar.absolutePath}, size=${subTar.length()}")
                                } else {
                                    Log.w("AIDevBackup", "[${item.id}] temp tar not found or empty after tar command")
                                }
                            } else {
                                Log.w("AIDevBackup", "[${item.id}] source files missing or check failed, result=$checkResult")
                            }
                        } else {
                            // 原生打包
                            val existingPaths = sourcePaths.filter { File(it).exists() }
                            if (existingPaths.isEmpty()) {
                                Log.w("AIDevBackup", "[${item.id}] no existing paths: $sourcePaths")
                            } else {
                                // 使用 -T 文件列表
                                val listFile = File(tempDir, "${item.id}_filelist.txt")
                                listFile.writeText(existingPaths.joinToString("\n"))
                                val proc = runNative(arrayOf("tar", "-czf", subTar.absolutePath, "-T", listFile.absolutePath))
                                exitCode = waitForProcess(proc)
                                Log.d("AIDevBackup", "[${item.id}] native tar exitCode=$exitCode, size=${subTar.length()}")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("AIDevBackup", "[${item.id}] exception: ${e.message}", e)
                        exitCode = 1
                    }

                    completed++
                    val percent = (completed * 100 / total)
                    handler.post {
                        progressBar.progress = percent
                        if (exitCode == 0 && subTar.exists() && subTar.length() > 0) {
                            itemStatusViews[item.id]?.text = "  ✓ ${item.name}"
                            itemStatusViews[item.id]?.setTextColor(ui.palette.success ?: ui.palette.accent)
                        } else {
                            itemStatusViews[item.id]?.text = "  ✗ ${item.name} (失败)"
                            itemStatusViews[item.id]?.setTextColor(ui.palette.danger ?: ui.palette.text)
                        }
                    }
                }

                if (cancelled.get()) {
                    handler.post {
                        dialog.dismiss()
                        toast("备份已取消")
                    }
                    return@Thread
                }

                // 合并所有子 tar
                handler.post { statusText.text = "正在合并..." }
                val mergeList = File(tempDir, "merge_list.txt")
                selectedList.filter { File(tempDir, "${it.id}.tar.gz").exists() }.forEach {
                    mergeList.appendText("${it.id}.tar.gz\n")
                }

                if (mergeList.exists() && mergeList.length() > 0) {
                    val mergeProc = runNative(arrayOf("tar", "-czf", finalFile.absolutePath, "-C", tempDir.absolutePath, "-T", mergeList.absolutePath))
                    val mergeCode = waitForProcess(mergeProc)

                    if (mergeCode == 0) {
                        // 生成 manifest.json
                        val manifest = JSONObject().apply {
                            put("version", 1)
                            put("timestamp", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date()))
                            put("items", JSONArray(selectedList.map { JSONObject().apply {
                                put("id", it.id)
                                put("name", it.name)
                            } }))
                        }
                        File(backupDir, "backup_$timestamp.manifest.json").writeText(manifest.toString(2))

                        handler.post {
                            dialog.dismiss()
                            toast("备份完成: ${finalFile.name}")
                        }
                    } else {
                        handler.post {
                            dialog.dismiss()
                            toast("合并失败")
                        }
                    }
                } else {
                    handler.post {
                        dialog.dismiss()
                        toast("没有可备份的数据")
                    }
                }
            } catch (e: Exception) {
                handler.post {
                    dialog.dismiss()
                    toast("备份失败: ${e.message}")
                }
            } finally {
                tempDir?.deleteRecursively()
                currentProcess = null
            }
        }.start()
    }

    // ═════════════════════════════════════════════════════════════════
    //  恢复模式
    // ═════════════════════════════════════════════════════════════════

    private fun renderRestore() {
        content.addView(ui.section("数据恢复", "选择备份文件"))

        val backupDir = File("/sdcard/AIDev/backups")
        val manifests = backupDir.listFiles { f -> f.name.endsWith(".manifest.json") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

        if (manifests.isEmpty()) {
            content.addView(ui.emptyState("暂无备份", "未找到备份文件"))
            content.addView(ui.actionRow("打开备份目录", "在终端查看") {
                host.openTerminal("ls -la /sdcard/AIDev/backups/")
            })
            return
        }

        manifests.forEach { manifestFile ->
            val tarName = manifestFile.name.replace(".manifest.json", ".tar.gz")
            val tarFile = File(backupDir, tarName)
            val dateStr = formatDate(manifestFile.lastModified())
            content.addView(ui.actionRow(manifestFile.name.replace(".manifest.json", ""), dateStr) {
                if (tarFile.exists()) {
                    selectBackupFile(manifestFile, tarFile)
                } else {
                    toast("备份文件不存在: $tarName")
                }
            })
        }
    }

    private fun selectBackupFile(manifestFile: File, tarFile: File) {
        try {
            val manifest = JSONObject(manifestFile.readText())
            val itemsArray = manifest.getJSONArray("items")
            val availableItems = mutableListOf<BackupItem>()
            for (i in 0 until itemsArray.length()) {
                val obj = itemsArray.getJSONObject(i)
                val id = obj.getString("id")
                backupItems.find { it.id == id }?.let { availableItems.add(it) }
            }

            if (availableItems.isEmpty()) {
                toast("备份清单为空")
                return
            }

            showRestoreDialog(tarFile, availableItems)
        } catch (e: Exception) {
            toast("无法读取备份清单: ${e.message}")
        }
    }

    private fun showRestoreDialog(tarFile: File, availableItems: List<BackupItem>) {
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
        dialogContent.addView(ui.actionRow("开始恢复", "将覆盖现有数据") {
            if (selectedItems.isEmpty()) { toast("请至少选择一项"); return@actionRow }
            confirmRestore(tarFile, availableItems.filter { selectedItems.contains(it.id) })
        })

        AlertDialog.Builder(activity)
            .setTitle("恢复数据")
            .setView(ScrollView(activity).apply { addView(dialogContent) })
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun confirmRestore(tarFile: File, itemsToRestore: List<BackupItem>) {
        AlertDialog.Builder(activity)
            .setTitle("确认恢复")
            .setMessage("恢复将覆盖现有数据，原数据会备份为 .backup.old，是否继续？")
            .setPositiveButton("开始恢复") { _, _ -> executeRestore(tarFile, itemsToRestore) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun executeRestore(tarFile: File, itemsToRestore: List<BackupItem>) {
        cancelled.set(false)

        val dialogView = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(24), ui.dp(16), ui.dp(24), ui.dp(16))
        }
        val statusText = TextView(activity).apply {
            text = "准备中..."
            setTextColor(ui.palette.text)
            textSize = DesignTokens.TEXT_BODY
        }
        val progressBar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
        }
        dialogView.addView(progressBar)
        dialogView.addView(statusText)

        val dialog = AlertDialog.Builder(activity)
            .setTitle("正在恢复")
            .setView(dialogView)
            .setCancelable(false)
            .setNegativeButton("取消") { _, _ ->
                cancelled.set(true)
                currentProcess?.destroy()
            }
            .show()

        Thread {
            var tempDir: File? = null
            try {
                tempDir = File(activity.cacheDir, "restore_v5_${System.currentTimeMillis()}")
                tempDir.mkdirs()

                // 先解压整个 tar 到临时目录
                handler.post { statusText.text = "正在解压备份..." }
                val extractProc = runNative(arrayOf("tar", "-xzf", tarFile.absolutePath, "-C", tempDir.absolutePath))
                val extractCode = waitForProcess(extractProc)

                if (extractCode != 0) {
                    handler.post { dialog.dismiss(); toast("解压失败") }
                    return@Thread
                }

                val total = itemsToRestore.size
                var completed = 0

                for (item in itemsToRestore) {
                    if (cancelled.get()) break

                    handler.post {
                        statusText.text = "正在恢复: ${item.name} (${completed + 1}/$total)"
                    }

                    val subTar = File(tempDir, "${item.id}.tar.gz")
                    if (!subTar.exists()) {
                        completed++
                        continue
                    }

                    // 恢复前备份原数据（对每个路径）
                    val targetPaths = item.getSourcePaths()
                    val targetFiles = targetPaths.map { path ->
                        if (item.isInRootfs) {
                            File(activity.filesDir, "home/ubuntu-rootfs/$path")
                        } else {
                            File(path)
                        }
                    }

                    targetFiles.forEach { tf ->
                        if (tf.exists()) {
                            val backupOld = File(tf.parentFile, "${tf.name}.backup.old")
                            if (backupOld.exists()) backupOld.deleteRecursively()
                            tf.renameTo(backupOld)
                        }
                    }

                    // 确定解压目标目录：使用第一个路径的父目录
                    val parentDir = targetFiles.firstOrNull()?.parentFile ?: continue
                    parentDir.mkdirs()

                    val restoreProc = if (item.isInRootfs) {
                        // 先把子 tar 复制到 proot 能访问的位置，再解压
                        val restoreTemp = File(activity.filesDir, "home/tasks/.restore_temp_${item.id}.tar.gz")
                        subTar.copyTo(restoreTemp, overwrite = true)
                        val proc = runInProot("tar -xzf /host-home/tasks/.restore_temp_${item.id}.tar.gz -C / 2>/dev/null")
                        waitForProcess(proc)
                        restoreTemp.delete()
                    } else {
                        val proc = runNative(arrayOf("tar", "-xzf", subTar.absolutePath, "-C", parentDir.absolutePath))
                        waitForProcess(proc)
                    }

                    completed++
                    val percent = (completed * 100 / total)
                    handler.post { progressBar.progress = percent }
                }

                if (cancelled.get()) {
                    handler.post { dialog.dismiss(); toast("恢复已取消") }
                } else {
                    handler.post {
                        dialog.dismiss()
                        toast("恢复完成! 请重启应用")
                    }
                }
            } catch (e: Exception) {
                handler.post { dialog.dismiss(); toast("恢复失败: ${e.message}") }
            } finally {
                tempDir?.deleteRecursively()
                currentProcess = null
            }
        }.start()
    }

    // ═════════════════════════════════════════════════════════════════
    //  工具
    // ═════════════════════════════════════════════════════════════════

    private fun formatDate(ts: Long): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ts))
    }

    private fun toast(text: String) {
        Toast.makeText(activity, text, Toast.LENGTH_SHORT).show()
    }
}
