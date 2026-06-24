package com.aidev.terminal
import com.google.android.material.dialog.MaterialAlertDialogBuilder

import android.app.Activity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 容器管理页面：rootfs 管理、环境信息、项目隔离、环境快照
 */
class ContainerManagerPage : ShellPage {
    private lateinit var activity: Activity
    private lateinit var ui: AIDevUi
    private lateinit var host: ShellHost
    private lateinit var list: LinearLayout
    private val pm by lazy { PreferencesManager(activity) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** 开发工具信息 */
    private data class DevTool(val name: String, val command: String, val installed: Boolean)

    override fun create(activity: Activity, ui: AIDevUi, host: ShellHost): View {
        this.activity = activity
        this.ui = ui
        this.host = host
        list = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_8), ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_24))
        }
        showLoading()
        loadContainerInfo()
        return ScrollView(activity).apply { addView(list) }
    }

    override fun onSelected(activity: Activity, view: View) {
        if (::list.isInitialized) {
            showLoading()
            loadContainerInfo()
        }
    }

    override fun onDestroy(activity: Activity) {
        scope.cancel()
    }

    private fun showLoading() {
        list.removeAllViews()
        list.addView(ui.section("Ubuntu 管理", "rootfs 管理、环境隔离、快照备份"))
        list.addView(ui.emptyState("正在加载...", "请稍候，正在获取容器信息"))
    }

    /**
     * 在子线程中加载容器信息，完成后回到主线程更新 UI
     */
    private fun loadContainerInfo() {
        scope.launch(Dispatchers.IO) {
            val rootfs = File(activity.filesDir, "home/ubuntu-rootfs")
            val homeDir = File(activity.filesDir, "home")

            val rootfsSize = calculateDirectorySize(rootfs)
            val rootfsSizeText = formatFileSize(rootfsSize)

            val prootLib = File(homeDir, "proot-lib/libtalloc.so.2")
            val prootOk = prootLib.exists()

            val devTools = detectDevTools(rootfs)

            val projectPath = pm.currentProjectPath

            withContext(Dispatchers.Main) {
                renderContainerInfo(rootfs, rootfsSizeText, prootOk, devTools, projectPath)
            }
        }
    }

    /**
     * 检测 rootfs 中已安装的开发工具
     */
    private fun detectDevTools(rootfs: File): List<DevTool> {
        val toolCommands = listOf(
            "node" to "Node.js",
            "python3" to "Python3",
            "git" to "Git",
            "java" to "JDK",
            "npm" to "npm",
            "pip3" to "pip3",
            "gradle" to "Gradle",
            "go" to "Go",
            "cargo" to "Rust/Cargo",
            "opencode" to "OpenCode",
            "vim" to "Vim",
            "curl" to "curl",
            "wget" to "wget",
            "openssh-client" to "SSH Client"
        )

        return toolCommands.map { (cmd, name) ->
            val binPaths = listOf(
                File(rootfs, "usr/bin/$cmd"),
                File(rootfs, "usr/local/bin/$cmd"),
                File(rootfs, "root/.opencode/bin/$cmd"),
                File(rootfs, "bin/$cmd")
            )
            // Java 特殊处理：检查 /usr/lib/jvm
            val exists = if (cmd == "java") {
                binPaths.any { it.exists() } || File(rootfs, "usr/lib/jvm").listFiles()?.any { it.isDirectory } == true
            } else {
                binPaths.any { it.exists() }
            }
            DevTool(name, cmd, exists)
        }
    }

    /**
     * 渲染容器信息到 UI
     */
    private fun renderContainerInfo(
        rootfs: File,
        rootfsSizeText: String,
        prootOk: Boolean,
        devTools: List<DevTool>,
        projectPath: String?
    ) {
        list.removeAllViews()

        // === 当前环境信息 ===
        list.addView(ui.section("当前环境", "容器运行状态和基本信息"))
        list.addView(ui.listItem("rootfs 路径", rootfs.absolutePath, rootfs.exists()))
        list.addView(ui.listItem("rootfs 大小", rootfsSizeText, true))
        list.addView(ui.listItem("PRoot 状态", if (prootOk) "已安装" else "未安装", prootOk))

        // 已安装开发工具统计
        val installedCount = devTools.count { it.installed }
        val totalCount = devTools.size
        list.addView(ui.listItem("开发工具", "$installedCount / $totalCount 已安装", installedCount > 0))

        list.addView(ui.divider())

        // === rootfs 管理 ===
        list.addView(ui.section("rootfs 管理", "清理缓存、重新安装"))
        list.addView(ui.actionRow("清理 APT 缓存", "释放 apt 缓存占用的空间") {
            host.openTerminal("apt clean && rm -rf /var/cache/apt/archives/*")
        })
        list.addView(ui.actionRow("清理临时文件", "删除 /tmp 和日志文件") {
            host.openTerminal("rm -rf /tmp/* && rm -rf /var/log/*.old")
        })
        list.addView(ui.actionRow("查看磁盘使用", "详细查看各目录占用空间") {
            host.openTerminal("du -sh /* 2>/dev/null | sort -rh | head -20")
        })
        list.addView(ui.actionRow("重新安装 rootfs", "重新下载并安装 Ubuntu 环境") {
            MaterialAlertDialogBuilder(activity)
                .setTitle("确认重新安装")
                .setMessage("这将删除当前的 Ubuntu rootfs 并重新安装，所有已安装的包和配置将丢失。确定继续？")
                .setPositiveButton("确认重装") { _, _ ->
                    host.openTerminal("install-ubuntu")
                }
                .setNegativeButton("取消", null)
                .show()
        })

        list.addView(ui.divider())

        // === 已安装工具列表 ===
        list.addView(ui.section("开发工具", "已安装的开发工具列表"))
        devTools.chunked(2).forEach { pair ->
            val left = ui.listItem(pair[0].name, if (pair[0].installed) "已安装" else "未安装", pair[0].installed)
            val right = if (pair.size > 1) {
                ui.listItem(pair[1].name, if (pair[1].installed) "已安装" else "未安装", pair[1].installed)
            } else {
                View(activity)
            }
            list.addView(ui.rowOf(left, right))
        }

        list.addView(ui.divider())

        // === 项目环境隔离 ===
        list.addView(ui.section("项目环境隔离", "为不同项目创建独立的运行环境"))
        list.addView(ui.infoRow("当前项目路径", projectPath ?: "/root"))

        list.addView(ui.actionRow("创建项目级 .bashrc", "为当前项目创建独立的 shell 配置") {
            createProjectBashrc(projectPath)
        })

        list.addView(ui.actionRow("项目级 Node.js 环境", "使用项目级 node_modules 隔离依赖") {
            showProjectIsolationInfo("Node.js", "在项目根目录执行 npm install 后，依赖将安装在 ./node_modules 中，与全局环境隔离。\n\n建议在项目中使用 .npmrc 配置文件指定本地缓存路径。")
        })

        list.addView(ui.actionRow("项目级 Python 环境", "使用虚拟环境隔离 Python 依赖") {
            showProjectIsolationInfo("Python", "在项目根目录执行以下命令创建虚拟环境：\n\npython3 -m venv venv\nsource venv/bin/activate\n\n激活后 pip install 安装的包仅在当前虚拟环境中。")
        })

        list.addView(ui.divider())

        // === 环境快照 ===
        list.addView(ui.section("环境快照", "导出和恢复已安装包列表"))
        list.addView(ui.actionRow("导出包列表", "将当前已安装的包列表保存到文件") {
            host.openTerminal("dpkg --get-selections > ~/packages.list && echo '已导出到 ~/packages.list'")
        })
        list.addView(ui.actionRow("从包列表恢复", "根据包列表重新安装所有包") {
            showRestoreInfo()
        })
        list.addView(ui.actionRow("导出 pip 包列表", "保存 Python 包依赖") {
            host.openTerminal("pip3 freeze > ~/requirements.txt 2>/dev/null && echo '已导出到 ~/requirements.txt'")
        })
    }

    /**
     * 创建项目级 .bashrc 文件
     */
    private fun createProjectBashrc(projectPath: String?) {
        val path = projectPath ?: "/root"
        val bashrcContent = "# AIDev Terminal 项目级配置\n" +
            "# 此文件为当前项目提供独立的 shell 环境配置\n" +
            "\n" +
            "# 项目特定的 PATH 追加\n" +
            "# export PATH=\"\$PWD/node_modules/.bin:\$PATH\"\n" +
            "\n" +
            "# 项目特定的别名\n" +
            "# alias run=\"npm run dev\"\n" +
            "# alias test=\"npm test\"\n" +
            "\n" +
            "# 项目环境变量\n" +
            "# export NODE_ENV=development\n" +
            "\n" +
            "echo \"[项目 .bashrc] 已加载项目级配置: \$(basename \$PWD)\"\n"
        // 将内容写入终端命令
        val escapedContent = bashrcContent.replace("\n", "\\n").replace("\"", "\\\"").replace("$", "\\$")
        val command = "cat > $path/.bashrc << 'AIDEV_BASHRC_EOF'\n$bashrcContent\nAIDEV_BASHRC_EOF"
        host.openTerminal(command)
        toast("请在终端中确认创建")
    }

    /**
     * 显示项目隔离说明
     */
    private fun showProjectIsolationInfo(title: String, content: String) {
        MaterialAlertDialogBuilder(activity)
            .setTitle("$title 环境隔离说明")
            .setMessage(content)
            .setPositiveButton("复制说明") { _, _ ->
                ClipboardHelper.copy(activity, "$title 隔离说明", content)
                toast("已复制到剪贴板")
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    /**
     * 显示从包列表恢复的说明
     */
    private fun showRestoreInfo() {
        val info = """从包列表恢复环境：

1. 确保已导出包列表文件 ~/packages.list
2. 执行以下命令恢复：

   dpkg --clear-selections
   dpkg --set-selections < ~/packages.list
   apt-get dselect-upgrade

注意：恢复过程需要网络连接，且可能需要较长时间。建议先备份重要数据。"""

        MaterialAlertDialogBuilder(activity)
            .setTitle("从包列表恢复")
            .setMessage(info)
            .setPositiveButton("执行恢复") { _, _ ->
                host.openTerminal("dpkg --clear-selections && dpkg --set-selections < ~/packages.list && apt-get dselect-upgrade -y")
            }
            .setNeutralButton("复制说明") { _, _ ->
                ClipboardHelper.copy(activity, "恢复说明", info)
                toast("已复制到剪贴板")
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 递归计算目录大小
     */
    private fun calculateDirectorySize(dir: File): Long {
        if (!dir.exists()) return 0L
        var size = 0L
        dir.walkTopDown().forEach { file ->
            if (file.isFile) {
                size += file.length()
            }
        }
        return size
    }

    /**
     * 格式化文件大小为人类可读格式
     */
    private fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> String.format("%.1f KB", size / 1024.0)
            size < 1024 * 1024 * 1024 -> String.format("%.1f MB", size / (1024.0 * 1024))
            else -> String.format("%.2f GB", size / (1024.0 * 1024 * 1024))
        }
    }

    private fun toast(text: String) = Toast.makeText(activity, text, Toast.LENGTH_SHORT).show()
}
