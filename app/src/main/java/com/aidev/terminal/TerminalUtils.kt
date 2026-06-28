package com.aidev.terminal

import java.io.File

internal fun completionRank(prefix: String, item: TerminalCompletion): Int {
    val p = prefix.lowercase()
    val text = item.insertText.lowercase()
    val kindBase = when (item.kind) {
        "PIN" -> 0
        "PATH" -> 10
        "ENV" -> 20
        else -> 40
    }
    return when {
        text == p -> kindBase
        text.startsWith(p) -> kindBase + 1
        text.split(Regex("[^\\p{L}\\p{N}]+")).any { it.startsWith(p) } -> kindBase + 4
        else -> kindBase + 9
    }
}

internal fun fuzzyCompletionMatch(prefix: String, item: TerminalCompletion): Boolean {
    if (prefix.length < 2) return false
    val normalizedPrefix = prefix.lowercase().filter { it.isLetterOrDigit() }
    if (normalizedPrefix.length < 2) return false
    return item.insertText
        .lowercase()
        .split(Regex("[^\\p{L}\\p{N}]+"))
        .filter { it.isNotBlank() }
        .any { it.startsWith(normalizedPrefix) }
}

internal fun builtinCompletions(): List<TerminalCompletion> =
    listOf(
        "aidev-doctor",
        "aidev-agent-context",
        "aidev-agent-context-file",

        "ubuntu",
        "help",
        "history",
        "pwd",
        "clear",
        "alias",
        "alias ll='ls -lah'",
        "ll",
        "ls",
        "ls -la",
        "git status",
        "git status --short",
        "git add .",
        "git commit -m \"\"",
        "git diff --stat",
        "git log --oneline -10",
        "git pull",
        "git switch ",
        "git checkout ",
        "grep -R ",
        "find . -maxdepth 2 -type f",
        "df -h",
        "ps aux",
        "env | sort",
        "whoami",
        "cat /etc/os-release",
        "task-list",
        "list-listen-ports",

        "install-ubuntu",
        "setup-dev-env",
        "check-dev-env",
        "repair-dev-env",
        "opencode-check",
        "setup-opencode",
        "aidev-logcat",
        "aidev-build",
        "aidev-apk-info",
        "aidev-create-android-project",
        "aidev-gen",
        "aidev-error-why",
        "aidev-index",
        "aidev-install",
        "aidev-opencode",

        "aidev-current-project",
        "aidev-agent-summary",
        "aidev-agent-log",
        "aidev-agent-tail",

        "task-run",

        "sysnotify",
        "screencap",
        "volume",
        "brightness",
        "startapp",
        "stopapp",
        "installapk",
        "uninstallapp",

        "android-sh",
        "aidev-clean",
        "aidev-shizuku",
        "aidev-proxy",
        "sysclip",
        "pmx",
        "amx",
        "getpropx",
        "logcatx"
    ).map { TerminalCompletion(it) }

internal fun decodeKeyInput(input: String): String =
    input.replace("\\n", "\n").replace("\\t", "\t").replace("\\e", "\u001b")

internal fun encodeKeyInput(input: String): String =
    input.replace("\u001b", "\\e").replace("\n", "\\n").replace("\t", "\\t")

internal fun parseCustomKeys(raw: String): List<EmbeddedVirtualKey> =
    raw.lines().mapNotNull { line ->
        val parts = line.split("\t")
        val label = parts.getOrNull(0)?.trim().orEmpty()
        val input = parts.getOrNull(1).orEmpty()
        val swipe = parts.getOrNull(2).orEmpty()
        if (label.isEmpty() || input.isEmpty()) null else EmbeddedVirtualKey(label.take(8), decodeKeyInput(input), decodeKeyInput(swipe), "custom_$label")
    }.take(8)

internal fun parseKeyOverrides(raw: String): Map<String, EmbeddedVirtualKey> =
    raw.lines().mapNotNull { line ->
        val parts = line.split("\t")
        val id = parts.getOrNull(0)?.trim().orEmpty()
        val label = parts.getOrNull(1)?.trim().orEmpty()
        val input = parts.getOrNull(2).orEmpty()
        val swipe = parts.getOrNull(3).orEmpty()
        if (id.isEmpty() || label.isEmpty()) null else id to EmbeddedVirtualKey(label.take(8), decodeKeyInput(input), decodeKeyInput(swipe), id)
    }.toMap()

internal fun parseKeyAliases(raw: String): List<KeyAlias> =
    raw.lines().mapNotNull { line ->
        val parts = line.split("\t")
        val name = parts.getOrNull(0)?.trim().orEmpty()
        val value = parts.getOrNull(1).orEmpty()
        if (name.isEmpty()) null else KeyAlias(name, value)
    }

internal fun terminalDp(activity: android.app.Activity, v: Int): Int =
    (activity.resources.displayMetrics.density * v + 0.5f).toInt()

internal fun tapCmdHint() = "例如 c、\\n 自动回车、\\t、\\e[A"

internal fun pathParts(relative: String, displayRoot: String, hostRoot: File): Triple<File, String, String> {
    val slash = relative.lastIndexOf('/')
    val dirPart = if (slash >= 0) relative.substring(0, slash) else ""
    val namePart = if (slash >= 0) relative.substring(slash + 1) else relative
    return Triple(File(hostRoot, dirPart), namePart, displayRoot + dirPart.let { if (it.isBlank()) "" else "$it/" })
}
