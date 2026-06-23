package com.aidev.terminal

import android.app.Activity
import android.app.AlertDialog
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File

/**
 * Shell 增强页面：命令历史统计、别名管理、One-liners 收藏夹、快速模板。
 * 使用 SharedPreferences ("aidev_shell") 存储 One-liners 收藏。
 */
class ShellEnhancementsPage : ShellPage {
    private lateinit var activity: Activity
    private lateinit var ui: AIDevUi
    private lateinit var host: ShellHost
    private lateinit var content: LinearLayout
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** bash_history 文件路径 */
    private val bashHistoryFile: File
        get() = File(activity.filesDir, "home/ubuntu-rootfs/root/.bash_history")

    /** .bashrc 文件路径 */
    private val bashrcFile: File
        get() = File(activity.filesDir, "home/ubuntu-rootfs/root/.bashrc")

    /** One-liners 存储的 SharedPreferences */
    private val shellPrefs: SharedPreferences
        get() = activity.getSharedPreferences("aidev_shell", Activity.MODE_PRIVATE)

    override fun create(activity: Activity, ui: AIDevUi, host: ShellHost): View {
        this.activity = activity
        this.ui = ui
        this.host = host
        content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_8), ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_24))
        }
        reload()
        return ScrollView(activity).apply { addView(content) }
    }

    override fun onSelected(activity: Activity, view: View) {
        if (::content.isInitialized) reload()
    }

    override fun onDestroy(activity: Activity) {
        scope.cancel()
    }

    /** 重新加载页面内容 */
    private fun reload() {
        content.removeAllViews()
        content.addView(ui.section("Shell 增强", "命令历史统计、别名管理、收藏夹与快速模板"))
        content.addView(ui.muted("正在加载..."))
        scope.launch(Dispatchers.IO) {
            val stats = parseBashHistory()
            val aliases = parseAliases()
            val oneliners = loadOneliners()
            withContext(Dispatchers.Main) {
                content.removeAllViews()
                content.addView(ui.section("Shell 增强", "命令历史统计、别名管理、收藏夹与快速模板"))
                content.addView(buildCommandHistorySection(stats))
                content.addView(ui.divider())
                content.addView(buildAliasSection(aliases))
                content.addView(ui.divider())
                content.addView(buildOnelinersSection(oneliners))
                content.addView(ui.divider())
                content.addView(buildTemplatesSection())
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────
    //  1. 命令历史统计
    // ─────────────────────────────────────────────────────────────────

    /** 构建"命令历史统计"区域 */
    private fun buildCommandHistorySection(stats: List<Pair<String, Int>>): View {
        val section = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }
        section.addView(ui.text("命令历史 Top 20", DesignTokens.TEXT_H2, ui.palette.text, bold = true).apply {
            setPadding(0, ui.dp(DesignTokens.SPACE_8), 0, ui.dp(DesignTokens.SPACE_4))
        })

        if (stats.isEmpty()) {
            section.addView(ui.emptyState("暂无历史记录", "bash_history 文件不存在或为空"))
        } else {
            val list = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
            }
            stats.take(20).forEach { (cmd, count) ->
                list.addView(historyItem(cmd, count))
            }
            section.addView(list)
        }
        return section
    }

    /** 读取并统计 bash_history，返回按次数降序排列的 (命令名, 次数) 列表 */
    private fun parseBashHistory(): List<Pair<String, Int>> {
        val file = bashHistoryFile
        if (!file.isFile) return emptyList()
        return runCatching {
            file.readLines()
                .filter { it.isNotBlank() }
                .map { it.trim().split(Regex("\\s+")).firstOrNull().orEmpty() }
                .filter { it.isNotBlank() }
                .groupingBy { it }
                .eachCount()
                .entries
                .sortedByDescending { it.value }
                .map { it.key to it.value }
        }.getOrDefault(emptyList())
    }

    /** 单条历史命令行：显示命令名 + 次数，点击复制 */
    private fun historyItem(cmd: String, count: Int): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ui.dp(DesignTokens.SPACE_8), ui.dp(DesignTokens.SPACE_4), ui.dp(DesignTokens.SPACE_8), ui.dp(DesignTokens.SPACE_4))
            isClickable = true
            isFocusable = true
            // 点击复制命令
            setOnClickListener {
                ui.pulse()
                ClipboardHelper.copy(activity, "Shell History", cmd)
                toast("已复制：$cmd")
            }
            // 命令名
            addView(TextView(activity).apply {
                text = cmd
                textSize = DesignTokens.TEXT_BODY
                setTextColor(ui.palette.text)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                typeface = Typeface.DEFAULT_BOLD
            }, LinearLayout.LayoutParams(0, -2, 1f))
            // 使用次数
            addView(TextView(activity).apply {
                text = "${count}x"
                textSize = DesignTokens.TEXT_CAPTION
                setTextColor(ui.palette.muted)
            })
        }

    // ─────────────────────────────────────────────────────────────────
    //  2. 别名管理
    // ─────────────────────────────────────────────────────────────────

    /** 构建"别名管理"区域 */
    private fun buildAliasSection(aliases: List<Pair<String, String>>): View {
        val section = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }
        section.addView(ui.text("别名管理", DesignTokens.TEXT_H2, ui.palette.text, bold = true).apply {
            setPadding(0, ui.dp(DesignTokens.SPACE_8), 0, ui.dp(DesignTokens.SPACE_4))
        })

        if (aliases.isEmpty()) {
            section.addView(ui.emptyState("暂无别名", "点击下方按钮添加新别名"))
        } else {
            val list = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
            }
            aliases.forEach { (name, command) ->
                list.addView(aliasItem(name, command))
            }
            section.addView(list)
        }

        // 添加别名按钮
        section.addView(addButton("添加别名") { showAddAliasDialog(aliases) })
        return section
    }

    /** 从 .bashrc 中解析 alias 定义，返回 (别名名, 命令) 列表 */
    private fun parseAliases(): List<Pair<String, String>> {
        val file = bashrcFile
        if (!file.isFile) return emptyList()
        return runCatching {
            file.readLines()
                .filter { it.trimStart().startsWith("alias ") }
                .mapNotNull { line ->
                    // alias name='command' 或 alias name="command" 或 alias name=command
                    val trimmed = line.trimStart().removePrefix("alias ").trim()
                    val eqIdx = trimmed.indexOf('=')
                    if (eqIdx < 0) return@mapNotNull null
                    val name = trimmed.substring(0, eqIdx).trim()
                    var value = trimmed.substring(eqIdx + 1).trim()
                    // 去除引号
                    if (value.startsWith("'") && value.endsWith("'") ||
                        value.startsWith("\"") && value.endsWith("\"")
                    ) {
                        value = value.substring(1, value.length - 1)
                    }
                    name to value
                }
        }.getOrDefault(emptyList())
    }

    /** 单个别名行：显示名称=命令，长按删除 */
    private fun aliasItem(name: String, command: String): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(DesignTokens.SPACE_8), ui.dp(DesignTokens.SPACE_4), ui.dp(DesignTokens.SPACE_8), ui.dp(DesignTokens.SPACE_4))
            // 点击复制展开后的命令
            isClickable = true
            isFocusable = true
            setOnClickListener {
                ui.pulse()
                ClipboardHelper.copy(activity, "Alias", "$name $command")
                toast("已复制：$name")
            }
            setOnLongClickListener {
                ui.pulse()
                showDeleteAliasDialog(name)
                true
            }
            // 别名名称
            addView(TextView(activity).apply {
                text = "alias $name"
                textSize = DesignTokens.TEXT_BODY
                setTextColor(ui.palette.accent)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                typeface = Typeface.DEFAULT_BOLD
            })
            // 对应命令
            addView(TextView(activity).apply {
                text = command
                textSize = DesignTokens.TEXT_CAPTION
                setTextColor(ui.palette.muted)
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, ui.dp(DesignTokens.SPACE_2), 0, 0)
            })
        }

    /** 显示添加别名的对话框 */
    private fun showAddAliasDialog(existingAliases: List<Pair<String, String>>) {
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(20), ui.dp(10), ui.dp(20), 0)
        }
        val nameInput = EditText(activity).apply {
            hint = "别名名称，例如 ll"
        }
        val cmdInput = EditText(activity).apply {
            hint = "对应命令，例如 ls -lah"
            setSingleLine(false)
        }
        box.addView(nameInput)
        box.addView(cmdInput)
        AlertDialog.Builder(activity)
            .setTitle("添加别名")
            .setView(box)
            .setPositiveButton("保存") { _, _ ->
                val name = nameInput.text.toString().trim()
                val cmd = cmdInput.text.toString().trim()
                if (name.isBlank() || cmd.isBlank()) {
                    toast("名称和命令不能为空")
                    return@setPositiveButton
                }
                // 检查是否已存在同名别名
                if (existingAliases.any { it.first == name }) {
                    toast("别名 $name 已存在，请先删除再添加")
                    return@setPositiveButton
                }
                // 追加到 .bashrc
                scope.launch(Dispatchers.IO) {
                    runCatching {
                        bashrcFile.appendText("\nalias $name='$cmd'")
                        withContext(Dispatchers.Main) {
                            toast("别名 $name 已添加，新会话生效")
                            reload()
                        }
                    }.onFailure {
                        withContext(Dispatchers.Main) { toast("写入失败：${it.message}") }
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /** 显示删除别名确认对话框 */
    private fun showDeleteAliasDialog(name: String) {
        AlertDialog.Builder(activity)
            .setTitle("删除别名")
            .setMessage("确定要删除别名 $name 吗？")
            .setPositiveButton("删除") { _, _ ->
                removeAlias(name)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /** 从 .bashrc 中删除指定别名 */
    private fun removeAlias(name: String) {
        val file = bashrcFile
        if (!file.isFile) {
            toast(".bashrc 文件不存在")
            return
        }
        scope.launch(Dispatchers.IO) {
            runCatching {
                val lines = file.readLines().filter { line ->
                    val trimmed = line.trimStart()
                    !trimmed.startsWith("alias ") || !trimmed.removePrefix("alias ").trim().startsWith("$name=")
                }
                file.writeText(lines.joinToString("\n"))
                withContext(Dispatchers.Main) {
                    toast("别名 $name 已删除")
                    reload()
                }
            }.onFailure {
                withContext(Dispatchers.Main) { toast("删除失败：${it.message}") }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────
    //  3. One-liners 收藏夹
    // ─────────────────────────────────────────────────────────────────

    /** 预置的常用 One-liners */
    private val defaultOneliners: List<String> = listOf(
        "find . -name '*.log' -mtime +7 -delete",
        "du -sh * | sort -rh | head -10",
        "grep -rn 'TODO' --include='*.kt' --include='*.java' .",
        "ps aux --sort=-%mem | head -10",
        "for f in *.jpg; do mv \"\$f\" \"\${f,,}\"; done",
        "curl -s ip.sb && echo",
        "history | awk '{print \\$2}' | sort | uniq -c | sort -rn | head"
    )

    /** 构建"One-liners 收藏夹"区域 */
    private fun buildOnelinersSection(saved: List<String>): View {
        val section = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }
        section.addView(ui.text("One-liners 收藏夹", DesignTokens.TEXT_H2, ui.palette.text, bold = true).apply {
            setPadding(0, ui.dp(DesignTokens.SPACE_8), 0, ui.dp(DesignTokens.SPACE_4))
        })

        if (saved.isEmpty()) {
            section.addView(ui.emptyState("暂无收藏", "点击下方按钮添加常用命令"))
        } else {
            val list = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
            }
            saved.forEachIndexed { index, cmd ->
                list.addView(onelinerItem(cmd, index))
            }
            section.addView(list)
        }

        // 添加按钮行
        val btnRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        btnRow.addView(addButton("添加") { showAddOnelinerDialog() }, LinearLayout.LayoutParams(0, -2, 1f).apply {
            setMargins(0, 0, ui.dp(4), 0)
        })
        btnRow.addView(addButton("加载预置") {
            loadDefaultOneliners()
        }, LinearLayout.LayoutParams(0, -2, 1f).apply {
            setMargins(ui.dp(4), 0, 0, 0)
        })
        section.addView(btnRow)
        return section
    }

    /** 从 SharedPreferences 加载 One-liners */
    private fun loadOneliners(): List<String> {
        val raw = shellPrefs.getString("oneliners", "") ?: ""
        return raw.lines().filter { it.isNotBlank() }
    }

    /** 保存 One-liners 到 SharedPreferences */
    private fun saveOneliners(list: List<String>) {
        shellPrefs.edit().putString("oneliners", list.joinToString("\n")).apply()
    }

    /** 单条 One-liner 行：点击执行，长按删除 */
    private fun onelinerItem(cmd: String, index: Int): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ui.dp(DesignTokens.SPACE_8), ui.dp(DesignTokens.SPACE_4), ui.dp(DesignTokens.SPACE_8), ui.dp(DesignTokens.SPACE_4))
            isClickable = true
            isFocusable = true
            // 点击执行命令
            setOnClickListener {
                ui.pulse()
                host.openTerminal(cmd)
            }
            // 长按删除
            setOnLongClickListener {
                ui.pulse()
                showDeleteOnelinerDialog(index)
                true
            }
            addView(TextView(activity).apply {
                text = cmd
                textSize = DesignTokens.TEXT_BODY
                setTextColor(ui.palette.text)
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(0, -2, 1f))
            // 执行图标提示
            addView(TextView(activity).apply {
                text = "RUN"
                textSize = DesignTokens.TEXT_LABEL
                setTextColor(ui.palette.accent)
                gravity = Gravity.CENTER
                setPadding(ui.dp(4), ui.dp(2), ui.dp(4), ui.dp(2))
                background = GradientDrawable().apply {
                    setColor((ui.palette.accent and 0x00FFFFFF) or 0x1A000000)
                    cornerRadius = ui.dp(4).toFloat()
                }
            })
        }

    /** 显示添加 One-liner 的对话框 */
    private fun showAddOnelinerDialog() {
        val input = EditText(activity).apply {
            hint = "输入命令，例如 find . -name '*.log' -delete"
            setSingleLine(false)
        }
        AlertDialog.Builder(activity)
            .setTitle("添加 One-liner")
            .setView(input)
            .setPositiveButton("保存") { _, _ ->
                val cmd = input.text.toString().trim()
                if (cmd.isBlank()) {
                    toast("命令不能为空")
                    return@setPositiveButton
                }
                val list = loadOneliners().toMutableList()
                list.add(cmd)
                saveOneliners(list)
                toast("已收藏")
                reload()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /** 显示删除 One-liner 确认对话框 */
    private fun showDeleteOnelinerDialog(index: Int) {
        val list = loadOneliners().toMutableList()
        val cmd = list.getOrElse(index) { return }
        AlertDialog.Builder(activity)
            .setTitle("删除收藏")
            .setMessage("确定要删除这条命令吗？\n$cmd")
            .setPositiveButton("删除") { _, _ ->
                list.removeAt(index)
                saveOneliners(list)
                toast("已删除")
                reload()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /** 加载预置 One-liners */
    private fun loadDefaultOneliners() {
        val existing = loadOneliners().toMutableList()
        var added = 0
        for (cmd in defaultOneliners) {
            if (cmd !in existing) {
                existing.add(cmd)
                added++
            }
        }
        if (added > 0) {
            saveOneliners(existing)
            toast("已加载 $added 条预置命令")
            reload()
        } else {
            toast("所有预置命令已存在")
        }
    }

    // ─────────────────────────────────────────────────────────────────
    //  4. 快速模板
    // ─────────────────────────────────────────────────────────────────

    /** 预置项目模板：(名称, 描述, 终端执行命令) */
    private val quickTemplates: List<Triple<String, String, String>> = listOf(
        Triple(".gitignore", "通用 Git 忽略文件", "cat > .gitignore << 'EOF'\n# IDE\n.idea/\n*.iml\n.vscode/\n\n# Build\nbuild/\ndist/\n*.apk\n\n# OS\n.DS_Store\nThumbs.db\n\n# Env\n.env\n.env.local\n\n# Dependencies\nnode_modules/\n.venv/\n__pycache__/\nEOF"),
        Triple("package.json", "Node.js 初始化", "npm init -y"),
        Triple("Dockerfile", "Node.js Docker 镜像", "cat > Dockerfile << 'EOF'\nFROM node:20-alpine\nWORKDIR /app\nCOPY package*.json ./\nRUN npm install\nCOPY . .\nEXPOSE 3000\nCMD [\"npm\", \"start\"]\nEOF"),
        Triple("README.md", "项目 README", "cat > README.md << 'EOF'\n# Project Name\n\n## Description\n\n## Getting Started\n\n### Prerequisites\n\n### Installation\n\n## Usage\n\n## License\nEOF"),
        Triple("docker-compose.yml", "Docker Compose 模板", "cat > docker-compose.yml << 'EOF'\nversion: '3.8'\nservices:\n  app:\n    build: .\n    ports:\n      - \"3000:3000\"\n    volumes:\n      - .:/app\n    environment:\n      - NODE_ENV=development\nEOF"),
        Triple("Makefile", "通用 Makefile", "cat > Makefile << 'EOF'\n.PHONY: build test clean install\n\ninstall:\n\tnpm install\n\nbuild:\n\tnpm run build\n\ntest:\n\tnpm test\n\nclean:\n\trm -rf build/ dist/ node_modules/\nEOF"),
        Triple(".env.example", "环境变量模板", "cat > .env.example << 'EOF'\n# Server\nPORT=3000\nHOST=0.0.0.0\n\n# Database\nDB_HOST=localhost\nDB_PORT=5432\nDB_NAME=myapp\n\n# Auth\nJWT_SECRET=your-secret-here\nEOF"),
        Triple("Python venv", "创建 Python 虚拟环境", "python3 -m venv .venv && echo 'source .venv/bin/activate' >> ~/.bashrc && echo '已创建虚拟环境，新会话自动激活'")
    )

    /** 构建"快速模板"区域 */
    private fun buildTemplatesSection(): View {
        val section = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }
        section.addView(ui.text("快速模板", DesignTokens.TEXT_H2, ui.palette.text, bold = true).apply {
            setPadding(0, ui.dp(DesignTokens.SPACE_8), 0, ui.dp(DesignTokens.SPACE_4))
        })

        val list = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }
        quickTemplates.forEach { (name, desc, command) ->
            list.addView(templateItem(name, desc, command))
        }
        section.addView(list)
        return section
    }

    /** 单个模板行：显示名称和描述，点击在终端执行 */
    private fun templateItem(name: String, desc: String, command: String): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(DesignTokens.SPACE_8), ui.dp(DesignTokens.SPACE_4), ui.dp(DesignTokens.SPACE_8), ui.dp(DesignTokens.SPACE_4))
            isClickable = true
            isFocusable = true
            background = ui.subtleButtonBackground()
            setOnClickListener {
                ui.pulse()
                host.openTerminal(command)
            }
            // 模板名称
            addView(TextView(activity).apply {
                text = name
                textSize = DesignTokens.TEXT_BODY
                setTextColor(ui.palette.text)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                typeface = Typeface.DEFAULT_BOLD
            })
            // 模板描述
            addView(TextView(activity).apply {
                text = desc
                textSize = DesignTokens.TEXT_CAPTION
                setTextColor(ui.palette.muted)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, ui.dp(DesignTokens.SPACE_2), 0, 0)
            })
        }

    // ─────────────────────────────────────────────────────────────────
    //  通用辅助
    // ─────────────────────────────────────────────────────────────────

    /** 创建一个带主题样式的添加按钮 */
    private fun addButton(label: String, onClick: () -> Unit): TextView =
        TextView(activity).apply {
            text = "+ $label"
            textSize = DesignTokens.TEXT_BODY
            setTextColor(ui.palette.accent)
            gravity = Gravity.CENTER
            setPadding(ui.dp(DesignTokens.SPACE_8), ui.dp(DesignTokens.SPACE_8), ui.dp(DesignTokens.SPACE_8), ui.dp(DesignTokens.SPACE_8))
            background = GradientDrawable().apply {
                setColor((ui.palette.accent and 0x00FFFFFF) or 0x15000000)
                cornerRadius = ui.dp(DesignTokens.RADIUS_MD).toFloat()
                setStroke(ui.dp(1), (ui.palette.accent and 0x00FFFFFF) or 0x30000000)
            }
            isClickable = true
            isFocusable = true
            setOnClickListener {
                ui.pulse()
                onClick()
            }
        }

    /** 显示 Toast 提示 */
    private fun toast(text: String) = Toast.makeText(activity, text, Toast.LENGTH_SHORT).show()
}
