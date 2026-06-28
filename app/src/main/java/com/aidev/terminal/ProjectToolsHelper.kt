package com.aidev.terminal

import android.app.Activity
import android.os.Build
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File

internal class ProjectToolsHelper(private val h: FilePageHost) {

    fun inspectProject() {
        val dir = h.hostSelectedFile()?.takeIf { it.isDirectory } ?: h.hostActiveDir()
        val markers = listOf(
            "package.json" to "Node / 前端项目",
            "build.gradle" to "Gradle 项目",
            "build.gradle.kts" to "Gradle Kotlin 项目",
            "settings.gradle" to "Gradle 多模块项目",
            "pyproject.toml" to "Python 项目",
            "requirements.txt" to "Python 依赖项目",
            "Cargo.toml" to "Rust 项目",
            "go.mod" to "Go 项目",
            ".git" to "Git 仓库",
            "README.md" to "README 文档"
        ).filter { File(dir, it.first).exists() }
        val body = if (markers.isEmpty()) {
            "未识别到常见项目标记。\n\n目录：${dir.absolutePath}"
        } else {
            markers.joinToString("\n") { "✓ ${it.second}：${it.first}" } + "\n\n目录：${dir.absolutePath}"
        }
        val up = ubuntuPath(dir)
        val runCmd = when {
            File(dir, "package.json").exists() -> "cd \"$up\" && npm install && npm run dev"
            File(dir, "build.gradle").exists() || File(dir, "build.gradle.kts").exists() -> "cd \"$up\" && ./gradlew assembleDebug"
            File(dir, "requirements.txt").exists() -> "cd \"$up\" && pip install -r requirements.txt --break-system-packages"
            File(dir, "pyproject.toml").exists() -> "cd \"$up\" && python3 -m pip install . --break-system-packages"
            else -> "cd \"$up\" && ls -la"
        }
        MaterialAlertDialogBuilder(h.hostActivity())
            .setTitle("项目识别")
            .setMessage(body)
            .setPositiveButton("运行建议") { _, _ ->
                if (h.hostActivity() is ShellActivity) {
                    TerminalCommandBus.post(runCmd)
                    (h.hostActivity() as? ShellActivity)?.switchTo(ShellActivity.TAB_TERMINAL)
                }
            }
            .setNeutralButton("复制路径") { _, _ ->
                h.hostCopyText("AIDev 项目路径", dir.absolutePath)
                h.hostToast("已复制项目路径")
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    fun projectWorkspace() {
        val dir = h.hostSelectedFile()?.takeIf { it.isDirectory } ?: h.hostActiveDir()
        val up = ubuntuPath(dir)
        val isGradle = File(dir, "build.gradle.kts").isFile || File(dir, "build.gradle").isFile
        val items = mutableListOf(
            "标记当前项目", "清除当前项目", "项目概览", "项目健康检查",
            "运行修复建议", "最近操作", "项目脚本", "复制诊断报告",
            "复制修复命令", "项目识别", "查看 README", "Git 状态",
            "Git Diff", "安装依赖", "运行开发服务", "运行测试",
            "构建项目"
        )
        if (isGradle) {
            items.add("安装APK")
            items.add("运行App")
        }
        items.addAll(listOf("终端进入目录", "复制项目命令", "导出项目摘要"))
        val builtApkRel = "app/build/outputs/apk/debug/app-debug.apk"
        MaterialAlertDialogBuilder(h.hostActivity())
            .setTitle("项目工作区")
            .setItems(items.toTypedArray()) { _, which ->
                when (items[which]) {
                    "标记当前项目" -> markCurrentProject(dir)
                    "清除当前项目" -> clearCurrentProject()
                    "项目概览" -> projectOverview(dir)
                    "项目健康检查" -> runInTerminal("cd \"$up\" && ${ProjectCommands.healthCommand(dir)}")
                    "运行修复建议" -> confirmProjectRepair(dir)
                    "最近操作" -> showProjectHistory()
                    "项目脚本" -> showProjectScripts(dir)
                    "复制诊断报告" -> copyProjectReport(dir)
                    "复制修复命令" -> copyRepairCommand(dir)
                    "项目识别" -> inspectProject()
                    "查看 README" -> showReadme(dir)
                    "Git 状态" -> runInTerminal("cd \"$up\" && git status --short --branch")
                    "Git Diff" -> runInTerminal("cd \"$up\" && git diff --stat")
                    "安装依赖" -> runInTerminal("cd \"$up\" && ${ProjectCommands.installCommand(dir)}")
                    "运行开发服务" -> runInTerminal("cd \"$up\" && ${ProjectCommands.devCommand(dir)}")
                    "运行测试" -> runInTerminal("cd \"$up\" && ${ProjectCommands.testCommand(dir)}")
                    "构建项目" -> runInTerminal("cd \"$up\" && ${ProjectCommands.buildCommand(dir)}")
                    "安装APK" -> runInTerminal("cd \"$up\" && cp \"$builtApkRel\" /sdcard/$(basename \"$up\")-debug.apk && installapk \"/sdcard/$(basename \"$up\")-debug.apk\"")
                    "运行App" -> runInTerminal("cd \"$up\" && PKG=\$(grep -oP 'namespace\\s*=\\s*\"\\K[^\"]+' app/build.gradle.kts) && am start -n \"\${PKG}/.MainActivity\"")
                    "终端进入目录" -> runInTerminal("cd \"$up\" && pwd && ls -la")
                    "复制项目命令" -> copyProjectCommands(dir)
                    "导出项目摘要" -> exportProjectSummary(dir)
                }
            }
            .show()
    }

    fun markCurrentProject(dir: File = h.hostSelectedFile()?.takeIf { it.isDirectory } ?: h.hostActiveDir()) {
        h.hostPm().currentProjectPath = dir.absolutePath
        h.hostRememberRecentDir(dir)
        h.hostToast("已标记当前项目")
    }

    fun jumpCurrentProject() {
        val path = h.hostPm().currentProjectPath
        val dir = File(path)
        if (path.isBlank() || !dir.isDirectory) {
            h.hostToast("未标记当前项目")
            return
        }
        h.hostClearSelection()
        h.hostNavigateTo(dir)
    }

    fun clearCurrentProject() {
        h.hostPm().currentProjectPath = ""
        h.hostToast("已清除当前项目")
    }

    fun projectOverview(dir: File) {
        val current = h.hostPm().currentProjectPath == dir.absolutePath
        val body = listOf(
            "名称：${dir.name}",
            "路径：${dir.absolutePath}",
            "当前项目：${if (current) "是" else "否"}",
            "README：${if (listOf("README.md", "README.txt", "readme.md").any { File(dir, it).isFile }) "有" else "无"}",
            "Git：${if (File(dir, ".git").exists()) "有" else "无"}",
            "健康摘要：${ProjectCommands.detectSummary(dir)}",
            "安装命令：cd \"${dir.absolutePath}\" && ${ProjectCommands.installCommand(dir)}",
            "开发命令：cd \"${dir.absolutePath}\" && ${ProjectCommands.devCommand(dir)}",
            "测试命令：cd \"${dir.absolutePath}\" && ${ProjectCommands.testCommand(dir)}",
            "构建命令：cd \"${dir.absolutePath}\" && ${ProjectCommands.buildCommand(dir)}",
            "修复命令：cd \"${dir.absolutePath}\" && ${ProjectCommands.repairCommand(dir)}"
        ).joinToString("\n")
        MaterialAlertDialogBuilder(h.hostActivity())
            .setTitle("项目概览")
            .setMessage(body)
            .setPositiveButton("复制") { _, _ ->
                h.hostCopyText("AIDev 项目概览", body)
                h.hostToast("已复制项目概览")
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    fun showApkInfo(file: File) {
        val info = h.hostActivity().packageManager.getPackageArchiveInfo(file.absolutePath, 0)
        val body = if (info == null) {
            "无法解析 APK 信息。\n\n路径：${file.absolutePath}\n大小：${h.hostFormatSize(file.length())}"
        } else {
            val versionName = info.versionName ?: "-"
            val versionCode = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode.toString() else info.versionCode.toString()
            "包名：${info.packageName}\n版本名：$versionName\n版本号：$versionCode\n大小：${h.hostFormatSize(file.length())}\n路径：${file.absolutePath}"
        }
        MaterialAlertDialogBuilder(h.hostActivity())
            .setTitle("APK 信息")
            .setMessage(body)
            .setPositiveButton("复制路径") { _, _ -> h.hostCopySelectedPath() }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun copyProjectCommands(dir: File) {
        val body = listOf(
            "cd \"${dir.absolutePath}\"",
            ProjectCommands.installCommand(dir),
            ProjectCommands.devCommand(dir),
            ProjectCommands.testCommand(dir),
            ProjectCommands.buildCommand(dir)
        ).joinToString("\n")
        h.hostCopyText("AIDev 项目命令", body)
        h.hostToast("已复制项目命令")
    }

    private fun copyProjectReport(dir: File) {
        val body = listOf(
            "项目：${dir.name}",
            "路径：${dir.absolutePath}",
            "健康摘要：${ProjectCommands.detectSummary(dir)}",
            "README：${if (listOf("README.md", "README.txt", "readme.md").any { File(dir, it).isFile }) "有" else "无"}",
            "Git：${if (File(dir, ".git").exists()) "有" else "无"}",
            "安装：${ProjectCommands.installCommand(dir)}",
            "开发：${ProjectCommands.devCommand(dir)}",
            "测试：${ProjectCommands.testCommand(dir)}",
            "构建：${ProjectCommands.buildCommand(dir)}",
            "诊断：${ProjectCommands.healthCommand(dir)}",
            "修复：${ProjectCommands.repairCommand(dir)}"
        ).joinToString("\n")
        h.hostCopyText("AIDev 项目诊断", body)
        h.hostToast("已复制诊断报告")
    }

    private fun copyRepairCommand(dir: File) {
        h.hostCopyText("AIDev 修复命令", ProjectCommands.repairCommand(dir))
        h.hostToast("已复制修复命令")
    }

    private fun confirmProjectRepair(dir: File) {
        val up = ubuntuPath(dir)
        val command = "cd \"$up\" && ${ProjectCommands.repairCommand(dir)}"
        MaterialAlertDialogBuilder(h.hostActivity())
            .setTitle("确认修复项目")
            .setMessage("项目：${dir.name}\n路径：${dir.absolutePath}\n\n执行：\n$command\n\n注意：某些修复会删除缓存、依赖目录或锁文件。")
            .setPositiveButton("确认执行") { _, _ ->
                rememberProjectAction("修复", dir, command)
                runInTerminal(command)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showProjectHistory() {
        val prefs = h.hostPm().sharedPreferences
        val rows = prefs.getString("project_action_history", "")?.lines()?.filter { it.isNotBlank() }.orEmpty().takeLast(20).reversed()
        if (rows.isEmpty()) return h.hostToast("暂无项目操作历史")
        MaterialAlertDialogBuilder(h.hostActivity())
            .setTitle("最近项目操作")
            .setItems(rows.map { row ->
                val parts = row.split("\t", limit = 4)
                "${parts.getOrNull(1) ?: "操作"}\n${parts.getOrNull(3) ?: row}"
            }.toTypedArray()) { _, which ->
                val parts = rows[which].split("\t", limit = 4)
                val path = parts.getOrNull(2).orEmpty()
                val command = parts.getOrNull(3).orEmpty()
                if (path.isNotBlank() && command.isNotBlank()) {
                    val upPath = ubuntuPath(File(path))
                    runInTerminal("cd \"$upPath\" && $command")
                }
            }
            .setPositiveButton("复制全部") { _, _ ->
                h.hostCopyText("AIDev 项目历史", rows.joinToString("\n"))
                h.hostToast("已复制项目历史")
            }
            .setNegativeButton("清空") { _, _ ->
                prefs.edit().remove("project_action_history").apply()
                h.hostToast("已清空历史")
            }
            .show()
    }

    private fun showProjectScripts(dir: File) {
        val packageJson = File(dir, "package.json")
        if (!packageJson.isFile) return showNonNodeProjectScripts(dir)
        val text = runCatching { packageJson.readText() }.getOrDefault("")
        val scriptsBlock = Regex("\"scripts\"\\s*:\\s*\\{([\\s\\S]*?)\\}").find(text)?.groupValues?.getOrNull(1).orEmpty()
        val scripts = Regex("\"([^\"]+)\"\\s*:\\s*\"([^\"]+)\"")
            .findAll(scriptsBlock)
            .map { it.groupValues[1] to it.groupValues[2] }
            .take(30)
            .toList()
        if (scripts.isEmpty()) return h.hostToast("未识别到 npm scripts")
        val up = ubuntuPath(dir)
        MaterialAlertDialogBuilder(h.hostActivity())
            .setTitle("项目脚本")
            .setItems(scripts.map { "${it.first}\n${it.second}" }.toTypedArray()) { _, which ->
                runInTerminal("cd \"$up\" && npm run ${scripts[which].first}")
            }
            .setPositiveButton("复制脚本") { _, _ ->
                h.hostCopyText("AIDev 项目脚本", scripts.joinToString("\n") { "${it.first}: ${it.second}" })
                h.hostToast("已复制项目脚本")
            }
            .show()
    }

    private fun showNonNodeProjectScripts(dir: File) {
        val scripts = when {
            File(dir, "build.gradle").exists() || File(dir, "build.gradle.kts").exists() -> listOf(
                "Gradle 任务列表" to "./gradlew tasks --all",
                "Gradle 测试" to "./gradlew test",
                "Gradle 构建 Debug" to "./gradlew assembleDebug",
                "Gradle 清理" to "./gradlew clean"
            )
            File(dir, "manage.py").exists() -> listOf(
                "Django 开发服务" to "python3 manage.py runserver 0.0.0.0:8000",
                "Django 迁移检查" to "python3 manage.py showmigrations",
                "Django 测试" to "python3 manage.py test"
            )
            File(dir, "requirements.txt").exists() || File(dir, "pyproject.toml").exists() -> listOf(
                "Python 测试" to "python3 -m pytest",
                "Python HTTP 服务" to "python3 -m http.server 8000",
                "Python 版本" to "python3 --version"
            )
            File(dir, "go.mod").exists() -> listOf(
                "Go 测试" to "go test ./...",
                "Go 构建" to "go build ./...",
                "Go 整理依赖" to "go mod tidy"
            )
            File(dir, "Cargo.toml").exists() -> listOf(
                "Cargo 测试" to "cargo test",
                "Cargo 构建" to "cargo build",
                "Cargo 元数据" to "cargo metadata --no-deps"
            )
            else -> emptyList()
        }
        if (scripts.isEmpty()) return h.hostToast("未识别到可用项目脚本")
        val up = ubuntuPath(dir)
        MaterialAlertDialogBuilder(h.hostActivity())
            .setTitle("项目脚本")
            .setItems(scripts.map { "${it.first}\n${it.second}" }.toTypedArray()) { _, which ->
                runInTerminal("cd \"$up\" && ${scripts[which].second}")
            }
            .setPositiveButton("复制脚本") { _, _ ->
                h.hostCopyText("AIDev 项目脚本", scripts.joinToString("\n") { "${it.first}: ${it.second}" })
                h.hostToast("已复制项目脚本")
            }
            .show()
    }

    private fun exportProjectSummary(dir: File) {
        val out = File(dir, "aidev-project-summary.txt")
        val body = listOf(
            "AIDev 项目摘要",
            "项目：${dir.name}",
            "路径：${dir.absolutePath}",
            "健康摘要：${ProjectCommands.detectSummary(dir)}",
            "安装：${ProjectCommands.installCommand(dir)}",
            "开发：${ProjectCommands.devCommand(dir)}",
            "测试：${ProjectCommands.testCommand(dir)}",
            "构建：${ProjectCommands.buildCommand(dir)}",
            "诊断：${ProjectCommands.healthCommand(dir)}",
            "修复：${ProjectCommands.repairCommand(dir)}"
        ).joinToString("\n")
        runCatching { out.writeText(body) }
            .onSuccess {
                h.hostReloadAll()
                h.hostToast("已导出项目摘要")
            }
            .onFailure { h.hostToast("导出失败：${it.message}") }
    }

    private fun showReadme(dir: File) {
        val readme = listOf("README.md", "README.txt", "readme.md").map { File(dir, it) }.firstOrNull { it.isFile }
        if (readme == null) {
            h.hostToast("未找到 README")
            return
        }
        val text = runCatching { readme.readText().take(16000) }.getOrElse { "读取失败：${it.message}" }
        MaterialAlertDialogBuilder(h.hostActivity())
            .setTitle(readme.name)
            .setMessage(text)
            .setPositiveButton("编辑") { _, _ ->
                h.hostSetSelectedFile(readme)
                h.hostEditSelected()
            }
            .setNeutralButton("复制") { _, _ ->
                h.hostCopyText("AIDev README", text)
                h.hostToast("已复制 README")
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun ubuntuPath(dir: File): String {
        val home = File(h.hostActivity().filesDir, "home")
        return SyncCoordinator.toUbuntuPath(dir, home) ?: dir.absolutePath
    }

    private fun runInTerminal(command: String) {
        val dir = h.hostSelectedFile()?.takeIf { it.isDirectory } ?: h.hostActiveDir()
        rememberProjectAction(commandLabel(command), dir, command)
        val act = h.hostActivity()
        if (act is ShellActivity) {
            TerminalCommandBus.post(command)
            act.switchTo(ShellActivity.TAB_TERMINAL)
        }
    }

    private fun commandLabel(command: String): String = when {
        command.contains("git status") -> "Git"
        command.contains("test") || command.contains("pytest") -> "测试"
        command.contains("build") || command.contains("assemble") -> "构建"
        command.contains("install") || command.contains("fetch") -> "依赖"
        command.contains("clean") || command.contains("tidy") -> "修复"
        command.contains("tasks --all") || command.contains("collect-only") -> "诊断"
        command.contains("installapk") || command.contains("pm install") -> "安装"
        command.contains("am start") -> "运行"
        else -> "命令"
    }

    private fun rememberProjectAction(label: String, dir: File, command: String) {
        val prefs = h.hostPm().sharedPreferences
        val line = "${System.currentTimeMillis()}\t$label\t${dir.absolutePath}\t$command"
        val old = prefs.getString("project_action_history", "") ?: ""
        val next = (old.lines().filter { it.isNotBlank() } + line).takeLast(20).joinToString("\n")
        prefs.edit().putString("project_action_history", next).apply()
    }
}
