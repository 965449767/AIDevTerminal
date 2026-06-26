package com.aidev.terminal

import java.io.File

internal object FileUtils {

    fun formatSize(n: Long): String = when {
        n < 1024 -> "${n}B"
        n < 1024 * 1024 -> "%.1fK".format(n / 1024.0)
        n < 1024L * 1024L * 1024L -> "%.1fM".format(n / 1024.0 / 1024.0)
        else -> "%.1fG".format(n / 1024.0 / 1024.0 / 1024.0)
    }

    fun isHtmlFile(file: File?): Boolean =
        file?.name?.let { it.endsWith(".html", ignoreCase = true) || it.endsWith(".htm", ignoreCase = true) } ?: false

    fun isEnhancedFile(file: File?): Boolean {
        if (file == null) return false
        val ext = file.extension.lowercase()
        return ext in setOf("md", "kt", "kts", "java", "js", "mjs", "ts", "py", "rs", "go", "sh", "bash", "cpp", "cc", "c", "h", "swift", "xml", "json", "yaml", "yml", "css", "scss", "sql", "gradle", "toml", "proto", "rb", "php", "pl", "lua", "r", "dart")
    }

    fun getHighlightLanguage(file: File?): String {
        val ext = file?.extension?.lowercase() ?: return ""
        return when (ext) {
            "kt", "kts" -> "kotlin"
            "js", "mjs" -> "javascript"
            "ts" -> "typescript"
            "py" -> "python"
            "sh", "bash" -> "bash"
            "cpp", "cc" -> "cpp"
            "md" -> "md"
            "yml" -> "yaml"
            "scss" -> "scss"
            "gradle" -> "gradle"
            "toml" -> "ini"
            else -> ext
        }
    }

    fun isImageFile(file: File): Boolean =
        file.extension.lowercase() in setOf("png", "jpg", "jpeg", "webp", "gif", "bmp")

    fun isLikelyText(file: File): Boolean =
        runCatching {
            file.inputStream().use { input ->
                val buffer = ByteArray(2048)
                val n = input.read(buffer)
                if (n <= 0) return@use true
                for (i in 0 until n) {
                    val b = buffer[i].toInt() and 0xFF
                    if (b == 0) return@use false
                }
                true
            }
        }.getOrDefault(false)
}
