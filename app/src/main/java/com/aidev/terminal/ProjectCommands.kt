package com.aidev.terminal

import java.io.File

/**
 * 项目命令统一工具类。
 * 所有项目类型检测和命令生成都通过此类完成，
 * 避免在 ShellPages、FilesPage、TasksPage、ShellActivity 中重复定义。
 */
object ProjectCommands {

    /** 检测项目类型标记 */
    fun detectMarkers(dir: File): List<String> {
        val markers = mutableListOf<String>()
        if (File(dir, "package.json").exists()) markers.add("Node")
        if (File(dir, "build.gradle").exists() || File(dir, "build.gradle.kts").exists()) markers.add("Gradle")
        if (File(dir, "requirements.txt").exists() || File(dir, "pyproject.toml").exists()) markers.add("Python")
        if (File(dir, "go.mod").exists()) markers.add("Go")
        if (File(dir, "Cargo.toml").exists()) markers.add("Rust")
        return markers
    }

    fun detectSummary(dir: File): String {
        val markers = detectMarkers(dir)
        return "识别：${if (markers.isEmpty()) "未发现常见项目标记" else markers.joinToString(" · ")}"
    }

    fun testCommand(dir: File?): String = dir?.let { d ->
        when {
            File(d, "package.json").exists() -> "npm test"
            File(d, "build.gradle").exists() || File(d, "build.gradle.kts").exists() -> "./gradlew test"
            File(d, "requirements.txt").exists() || File(d, "pyproject.toml").exists() -> "python3 -m pytest"
            File(d, "Cargo.toml").exists() -> "cargo test"
            File(d, "go.mod").exists() -> "go test ./..."
            else -> "ls -la"
        }
    } ?: "ls -la"

    fun buildCommand(dir: File?): String = dir?.let { d ->
        when {
            File(d, "package.json").exists() -> "npm run build"
            File(d, "build.gradle").exists() || File(d, "build.gradle.kts").exists() -> "./gradlew assembleDebug"
            File(d, "Cargo.toml").exists() -> "cargo build"
            File(d, "go.mod").exists() -> "go build ./..."
            else -> "ls -la"
        }
    } ?: "ls -la"

    fun installCommand(dir: File?): String = dir?.let { d ->
        when {
            File(d, "package.json").exists() -> "npm install"
            File(d, "requirements.txt").exists() -> "pip install -r requirements.txt --break-system-packages"
            File(d, "pyproject.toml").exists() -> "python3 -m pip install . --break-system-packages"
            File(d, "Cargo.toml").exists() -> "cargo fetch"
            File(d, "go.mod").exists() -> "go mod download"
            else -> "ls -la"
        }
    } ?: "ls -la"

    fun repairCommand(dir: File?): String = dir?.let { d ->
        when {
            File(d, "package.json").exists() -> "rm -rf node_modules package-lock.json && npm install"
            File(d, "build.gradle").exists() || File(d, "build.gradle.kts").exists() -> "./gradlew --stop; ./gradlew clean"
            File(d, "requirements.txt").exists() -> "python3 -m pip install -r requirements.txt --break-system-packages"
            File(d, "pyproject.toml").exists() -> "python3 -m pip install . --break-system-packages"
            File(d, "Cargo.toml").exists() -> "cargo clean && cargo fetch"
            File(d, "go.mod").exists() -> "go clean -cache && go mod tidy"
            else -> "pwd && ls -la"
        }
    } ?: "pwd && ls -la"

    fun healthCommand(dir: File?): String = dir?.let { d ->
        when {
            File(d, "package.json").exists() -> "node -e \"const p=require('./package.json'); console.log('name:',p.name||'-'); console.log('scripts:', Object.keys(p.scripts||{}).join(','))\" && npm pkg get scripts"
            File(d, "build.gradle").exists() || File(d, "build.gradle.kts").exists() -> "./gradlew tasks --all | head -80"
            File(d, "requirements.txt").exists() || File(d, "pyproject.toml").exists() -> "python3 --version && python3 -m pip --version && python3 -m pytest --collect-only"
            File(d, "Cargo.toml").exists() -> "cargo metadata --no-deps"
            File(d, "go.mod").exists() -> "go list ./..."
            else -> "pwd && ls -la"
        }
    } ?: "pwd && ls -la"

    fun devCommand(dir: File?): String = dir?.let { d ->
        when {
            File(d, "package.json").exists() -> "npm run dev"
            File(d, "build.gradle").exists() || File(d, "build.gradle.kts").exists() -> "./gradlew assembleDebug"
            File(d, "requirements.txt").exists() || File(d, "pyproject.toml").exists() -> "python3 -m http.server 8000"
            else -> "ls -la"
        }
    } ?: "ls -la"
}
