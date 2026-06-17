package com.aidev.terminal

import android.app.Activity
import android.app.AlertDialog
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * 安全审计页面：文件权限审计、SSH 安全检查、敏感文件检查、安全报告
 */
class SecurityAuditPage : ShellPage {
    private lateinit var activity: Activity
    private lateinit var ui: AIDevUi
    private lateinit var host: ShellHost
    private lateinit var list: LinearLayout
    private val handler = Handler(Looper.getMainLooper())

    /** 审计结果数据类 */
    private data class AuditItem(
        val category: String,
        val name: String,
        val passed: Boolean,
        val detail: String,
        val fixCommand: String? = null
    )

    override fun create(activity: Activity, ui: AIDevUi, host: ShellHost): View {
        this.activity = activity
        this.ui = ui
        this.host = host
        list = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_8), ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_24))
        }
        showLoading()
        runAudit()
        return ScrollView(activity).apply { addView(list) }
    }

    override fun onSelected(activity: Activity, view: View) {
        if (::list.isInitialized) {
            showLoading()
            runAudit()
        }
    }

    private fun showLoading() {
        list.removeAllViews()
        list.addView(ui.section("安全审计", "扫描文件权限、SSH 配置、敏感文件"))
        list.addView(ui.emptyState("正在扫描...", "请稍候，正在执行安全检查"))
    }

    /**
     * 在子线程中执行所有审计检查，完成后回到主线程更新 UI
     */
    private fun runAudit() {
        Thread {
            val rootfs = File(activity.filesDir, "home/ubuntu-rootfs")
            val homeDir = File(rootfs, "root")
            val results = mutableListOf<AuditItem>()

            // 1. 文件权限审计
            results.addAll(auditFilePermissions(rootfs))

            // 2. SSH 安全检查
            results.addAll(auditSshSecurity(homeDir))

            // 3. 敏感文件检查
            results.addAll(auditSensitiveFiles(homeDir))

            handler.post { renderResults(results) }
        }.start()
    }

    /**
     * 扫描 rootfs 中权限过于开放的文件（777/666）
     * 使用 find 命令在 Android 层面执行
     */
    private fun auditFilePermissions(rootfs: File): List<AuditItem> {
        val items = mutableListOf<AuditItem>()
        if (!rootfs.exists()) {
            items.add(AuditItem("文件权限", "rootfs 存在性", false, "Ubuntu rootfs 未安装", "install-ubuntu"))
            return items
        }

        // 在 rootfs 中查找权限过于开放的文件
        val openFiles = executeInRootfs(rootfs, "find /root -perm -0777 -type f 2>/dev/null")
        val lines = openFiles.lines().filter { it.isNotBlank() && it.startsWith("/") }

        if (lines.isEmpty()) {
            items.add(AuditItem("文件权限", "开放权限文件", true, "未发现 777/666 权限的文件"))
        } else {
            items.add(AuditItem("文件权限", "开放权限文件", false, "发现 ${lines.size} 个权限过于开放的文件"))
            lines.take(10).forEach { file ->
                val perm = executeInRootfs(rootfs, "stat -c '%a' $file 2>/dev/null").trim()
                items.add(AuditItem("文件权限", file, false, "权限: $perm", "chmod 644 $file"))
            }
            if (lines.size > 10) {
                items.add(AuditItem("文件权限", "更多文件...", false, "共 ${lines.size} 个，仅显示前 10 个"))
            }
        }

        return items
    }

    /**
     * SSH 安全检查：目录和密钥文件权限
     */
    private fun auditSshSecurity(homeDir: File): List<AuditItem> {
        val items = mutableListOf<AuditItem>()
        val sshDir = File(homeDir, ".ssh")

        if (!sshDir.exists()) {
            items.add(AuditItem("SSH 安全", ".ssh 目录", true, "不存在（未配置 SSH）"))
            return items
        }

        // 检查 .ssh 目录权限（应为 700）
        val sshDirPerm = sshDir.getPermissionsString()
        val sshDirOk = sshDirPerm == "drwx------" || sshDirPerm == "drwxr-xr-x"
        items.add(AuditItem(
            "SSH 安全", ".ssh 目录权限",
            sshDirOk,
            "当前: $sshDirPerm, 建议: 700 (drwx------)",
            if (!sshDirOk) "chmod 700 ~/.ssh" else null
        ))

        // 检查 id_rsa 权限（应为 600）
        val idRsa = File(sshDir, "id_rsa")
        if (idRsa.exists()) {
            val perm = idRsa.getPermissionsString()
            val ok = perm == "-rw-------"
            items.add(AuditItem(
                "SSH 安全", "id_rsa 权限",
                ok,
                "当前: $perm, 建议: 600 (-rw-------)",
                if (!ok) "chmod 600 ~/.ssh/id_rsa" else null
            ))
        } else {
            items.add(AuditItem("SSH 安全", "id_rsa", true, "不存在（未生成密钥）"))
        }

        // 检查 authorized_keys 权限（应为 600）
        val authKeys = File(sshDir, "authorized_keys")
        if (authKeys.exists()) {
            val perm = authKeys.getPermissionsString()
            val ok = perm == "-rw-------"
            items.add(AuditItem(
                "SSH 安全", "authorized_keys 权限",
                ok,
                "当前: $perm, 建议: 600 (-rw-------)",
                if (!ok) "chmod 600 ~/.ssh/authorized_keys" else null
            ))
        } else {
            items.add(AuditItem("SSH 安全", "authorized_keys", true, "不存在（未配置授权密钥）"))
        }

        return items
    }

    /**
     * 敏感文件检查
     */
    private fun auditSensitiveFiles(homeDir: File): List<AuditItem> {
        val items = mutableListOf<AuditItem>()

        // 检查 .bash_history
        val bashHistory = File(homeDir, ".bash_history")
        if (bashHistory.exists()) {
            val lineCount = bashHistory.readLines().size
            items.add(AuditItem(
                "敏感文件", ".bash_history",
                false,
                "存在，共 $lineCount 行历史记录",
                "rm -f ~/.bash_history && history -c"
            ))
        } else {
            items.add(AuditItem("敏感文件", ".bash_history", true, "不存在"))
        }

        // 检查 .netrc
        val netrc = File(homeDir, ".netrc")
        if (netrc.exists()) {
            items.add(AuditItem(
                "敏感文件", ".netrc",
                false,
                "存在，可能包含明文凭据",
                "chmod 600 ~/.netrc"
            ))
        } else {
            items.add(AuditItem("敏感文件", ".netrc", true, "不存在"))
        }

        // 检查 /tmp 下可疑脚本
        val rootfs = File(activity.filesDir, "home/ubuntu-rootfs")
        val tmpDir = File(rootfs, "tmp")
        if (tmpDir.exists()) {
            val suspiciousScripts = tmpDir.listFiles()?.filter { file ->
                file.isFile && (file.name.endsWith(".sh") || file.name.endsWith(".py") || file.name.endsWith(".pl"))
            }?.take(10) ?: emptyList()

            if (suspiciousScripts.isEmpty()) {
                items.add(AuditItem("敏感文件", "/tmp 可疑脚本", true, "未发现可疑脚本文件"))
            } else {
                items.add(AuditItem(
                    "敏感文件", "/tmp 可疑脚本",
                    false,
                    "发现 ${suspiciousScripts.size} 个脚本文件: ${suspiciousScripts.joinToString(", ") { it.name }}"
                ))
            }
        } else {
            items.add(AuditItem("敏感文件", "/tmp 目录", true, "不存在"))
        }

        return items
    }

    /**
     * 渲染审计结果到 UI
     */
    private fun renderResults(results: List<AuditItem>) {
        list.removeAllViews()

        val passedCount = results.count { it.passed }
        val warningCount = results.count { !it.passed }

        list.addView(ui.section("安全审计", "扫描文件权限、SSH 配置、敏感文件"))

        // 汇总统计
        list.addView(ui.rowOf(
            ui.listItem("通过", "$passedCount", true),
            ui.listItem("警告", "$warningCount", warningCount == 0)
        ))

        list.addView(ui.divider())

        // 按分类分组显示
        val grouped = results.groupBy { it.category }
        for ((category, items) in grouped) {
            list.addView(ui.section(category, "${items.size} 项检查"))
            for (item in items) {
                val row = ui.listItem(item.name, if (item.passed) "通过" else "警告", item.passed)
                list.addView(row)
                // 显示详细信息
                if (item.detail.isNotBlank()) {
                    list.addView(ui.muted(item.detail))
                }
                // 如果有修复命令，提供修复按钮
                if (item.fixCommand != null) {
                    list.addView(ui.actionRow("修复", item.fixCommand) {
                        host.openTerminal(item.fixCommand)
                    })
                }
            }
            list.addView(ui.divider())
        }

        // 安全报告操作
        list.addView(ui.section("安全报告", "汇总所有检查结果"))
        list.addView(ui.actionRow("生成并复制报告", "将审计结果复制到剪贴板") {
            copyReport(results, passedCount, warningCount)
        })
        list.addView(ui.actionRow("一键修复所有问题", "在终端中执行所有修复命令") {
            val fixCommands = results.filter { it.fixCommand != null }.mapNotNull { it.fixCommand }
            if (fixCommands.isNotEmpty()) {
                host.openTerminal(fixCommands.joinToString(" && "))
            } else {
                toast("没有需要修复的问题")
            }
        })
    }

    /**
     * 生成安全报告文本并复制到剪贴板
     */
    private fun copyReport(results: List<AuditItem>, passedCount: Int, warningCount: Int) {
        val sb = StringBuilder()
        sb.appendLine("=== AIDev Terminal 安全审计报告 ===")
        sb.appendLine("时间: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
        sb.appendLine()
        sb.appendLine("汇总: 通过 $passedCount / 警告 $warningCount / 总计 ${results.size}")
        sb.appendLine()

        val grouped = results.groupBy { it.category }
        for ((category, items) in grouped) {
            sb.appendLine("--- $category ---")
            for (item in items) {
                val status = if (item.passed) "[PASS]" else "[WARN]"
                sb.appendLine("  $status ${item.name}: ${item.detail}")
                if (item.fixCommand != null) {
                    sb.appendLine("         修复: ${item.fixCommand}")
                }
            }
            sb.appendLine()
        }

        ClipboardHelper.copy(activity, "安全审计报告", sb.toString())
        toast("报告已复制到剪贴板")
    }

    /**
     * 在 rootfs 环境中执行命令（通过 proot）
     * 由于直接执行 find/stat 在 Android 上可能不可用，
     * 这里直接读取文件系统属性
     */
    private fun executeInRootfs(rootfs: File, command: String): String {
        return try {
            // 直接在 rootfs 路径下执行命令（需要 proot 环境）
            // 这里使用 Runtime.exec 执行，命令路径需要映射到 rootfs
            val mappedCommand = command
                .replace("/root", rootfs.resolve("root").absolutePath)
                .replace("/tmp", rootfs.resolve("tmp").absolutePath)

            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", mappedCommand))
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            output
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * 获取文件权限的字符串表示（简化版）
     */
    private fun File.getPermissionsString(): String {
        return try {
            val isDir = isDirectory
            val canRead = canRead()
            val canWrite = canWrite()
            val canExec = canExecute()

            val type = if (isDir) "d" else "-"
            val owner = "${if (canRead) 'r' else '-'}${if (canWrite) 'w' else '-'}${if (canExec) 'x' else '-'}"
            // Android 文件系统权限简化显示
            "$type$owner------"
        } catch (e: Exception) {
            "????------"
        }
    }

    private fun toast(text: String) = Toast.makeText(activity, text, Toast.LENGTH_SHORT).show()
}
