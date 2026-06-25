package com.aidev.terminal

import java.io.File

object FileIconMapper {

    data class IconEntry(val res: Int, val bgColor: Int)

    private val map = mapOf(
        "kt" to IconEntry(R.drawable.ic_doc_codes, 0xFF607D8B.toInt()),
        "kts" to IconEntry(R.drawable.ic_doc_codes, 0xFF607D8B.toInt()),
        "java" to IconEntry(R.drawable.ic_doc_codes, 0xFF607D8B.toInt()),
        "py" to IconEntry(R.drawable.ic_doc_codes, 0xFF607D8B.toInt()),
        "js" to IconEntry(R.drawable.ic_doc_codes, 0xFF607D8B.toInt()),
        "jsx" to IconEntry(R.drawable.ic_doc_codes, 0xFF607D8B.toInt()),
        "ts" to IconEntry(R.drawable.ic_doc_codes, 0xFF607D8B.toInt()),
        "tsx" to IconEntry(R.drawable.ic_doc_codes, 0xFF607D8B.toInt()),
        "html" to IconEntry(R.drawable.ic_doc_codes, 0xFF607D8B.toInt()),
        "htm" to IconEntry(R.drawable.ic_doc_codes, 0xFF607D8B.toInt()),
        "css" to IconEntry(R.drawable.ic_doc_codes, 0xFF607D8B.toInt()),
        "scss" to IconEntry(R.drawable.ic_doc_codes, 0xFF607D8B.toInt()),
        "sass" to IconEntry(R.drawable.ic_doc_codes, 0xFF607D8B.toInt()),
        "less" to IconEntry(R.drawable.ic_doc_codes, 0xFF607D8B.toInt()),
        "xml" to IconEntry(R.drawable.ic_doc_codes, 0xFF607D8B.toInt()),
        "json" to IconEntry(R.drawable.ic_doc_codes, 0xFF607D8B.toInt()),
        "jsonc" to IconEntry(R.drawable.ic_doc_codes, 0xFF607D8B.toInt()),
        "yml" to IconEntry(R.drawable.ic_doc_codes, 0xFF607D8B.toInt()),
        "yaml" to IconEntry(R.drawable.ic_doc_codes, 0xFF607D8B.toInt()),
        "sh" to IconEntry(R.drawable.ic_doc_codes, 0xFF607D8B.toInt()),
        "bash" to IconEntry(R.drawable.ic_doc_codes, 0xFF607D8B.toInt()),
        "gradle" to IconEntry(R.drawable.ic_doc_codes, 0xFF607D8B.toInt()),
        "cpp" to IconEntry(R.drawable.ic_doc_codes, 0xFF607D8B.toInt()),
        "c" to IconEntry(R.drawable.ic_doc_codes, 0xFF607D8B.toInt()),
        "h" to IconEntry(R.drawable.ic_doc_codes, 0xFF607D8B.toInt()),
        "hpp" to IconEntry(R.drawable.ic_doc_codes, 0xFF607D8B.toInt()),
        "rs" to IconEntry(R.drawable.ic_doc_codes, 0xFF607D8B.toInt()),
        "go" to IconEntry(R.drawable.ic_doc_codes, 0xFF607D8B.toInt()),
        "rb" to IconEntry(R.drawable.ic_doc_codes, 0xFF607D8B.toInt()),
        "php" to IconEntry(R.drawable.ic_doc_codes, 0xFF607D8B.toInt()),
        "swift" to IconEntry(R.drawable.ic_doc_codes, 0xFF607D8B.toInt()),
        "dart" to IconEntry(R.drawable.ic_doc_codes, 0xFF607D8B.toInt()),
        "vue" to IconEntry(R.drawable.ic_doc_codes, 0xFF607D8B.toInt()),
        "pl" to IconEntry(R.drawable.ic_doc_codes, 0xFF607D8B.toInt()),
        "lua" to IconEntry(R.drawable.ic_doc_codes, 0xFF607D8B.toInt()),
        "bat" to IconEntry(R.drawable.ic_doc_codes, 0xFF607D8B.toInt()),
        "cmd" to IconEntry(R.drawable.ic_doc_codes, 0xFF607D8B.toInt()),
        "toml" to IconEntry(R.drawable.ic_doc_codes, 0xFF607D8B.toInt()),

        "txt" to IconEntry(R.drawable.ic_doc_text, 0xFF808080.toInt()),
        "md" to IconEntry(R.drawable.ic_doc_text, 0xFF808080.toInt()),
        "properties" to IconEntry(R.drawable.ic_doc_text, 0xFF808080.toInt()),
        "cfg" to IconEntry(R.drawable.ic_doc_text, 0xFF808080.toInt()),
        "conf" to IconEntry(R.drawable.ic_doc_text, 0xFF808080.toInt()),
        "ini" to IconEntry(R.drawable.ic_doc_text, 0xFF808080.toInt()),
        "log" to IconEntry(R.drawable.ic_doc_text, 0xFF808080.toInt()),

        "png" to IconEntry(R.drawable.ic_doc_image, 0xFF00838F.toInt()),
        "jpg" to IconEntry(R.drawable.ic_doc_image, 0xFF00838F.toInt()),
        "jpeg" to IconEntry(R.drawable.ic_doc_image, 0xFF00838F.toInt()),
        "gif" to IconEntry(R.drawable.ic_doc_image, 0xFF00838F.toInt()),
        "svg" to IconEntry(R.drawable.ic_doc_image, 0xFF00838F.toInt()),
        "webp" to IconEntry(R.drawable.ic_doc_image, 0xFF00838F.toInt()),
        "ico" to IconEntry(R.drawable.ic_doc_image, 0xFF00838F.toInt()),

        "mp3" to IconEntry(R.drawable.ic_doc_audio, 0xFFFF9800.toInt()),
        "wav" to IconEntry(R.drawable.ic_doc_audio, 0xFFFF9800.toInt()),
        "ogg" to IconEntry(R.drawable.ic_doc_audio, 0xFFFF9800.toInt()),
        "flac" to IconEntry(R.drawable.ic_doc_audio, 0xFFFF9800.toInt()),
        "aac" to IconEntry(R.drawable.ic_doc_audio, 0xFFFF9800.toInt()),

        "mp4" to IconEntry(R.drawable.ic_doc_video, 0xFFC74141.toInt()),
        "mkv" to IconEntry(R.drawable.ic_doc_video, 0xFFC74141.toInt()),
        "flv" to IconEntry(R.drawable.ic_doc_video, 0xFFC74141.toInt()),
        "avi" to IconEntry(R.drawable.ic_doc_video, 0xFFC74141.toInt()),

        "zip" to IconEntry(R.drawable.ic_doc_archive, 0xFF795548.toInt()),
        "tar" to IconEntry(R.drawable.ic_doc_archive, 0xFF795548.toInt()),
        "gz" to IconEntry(R.drawable.ic_doc_archive, 0xFF795548.toInt()),
        "7z" to IconEntry(R.drawable.ic_doc_archive, 0xFF795548.toInt()),
        "rar" to IconEntry(R.drawable.ic_doc_archive, 0xFF795548.toInt()),
        "bz2" to IconEntry(R.drawable.ic_doc_archive, 0xFF795548.toInt()),

        "pdf" to IconEntry(R.drawable.ic_doc_pdf, 0xFFFFDB4437.toInt()),
        "apk" to IconEntry(R.drawable.ic_doc_apk, 0xFF8BC34A.toInt()),

        "sql" to IconEntry(R.drawable.ic_doc_spreadsheet, 0xFF16A765.toInt()),
        "doc" to IconEntry(R.drawable.ic_doc_word, 0xFF4883F3.toInt()),
        "docx" to IconEntry(R.drawable.ic_doc_word, 0xFF4883F3.toInt()),
        "xls" to IconEntry(R.drawable.ic_doc_excel, 0xFF16A765.toInt()),
        "xlsx" to IconEntry(R.drawable.ic_doc_excel, 0xFF16A765.toInt()),
        "ppt" to IconEntry(R.drawable.ic_doc_powerpoint, 0xFFFF7537.toInt()),
        "pptx" to IconEntry(R.drawable.ic_doc_powerpoint, 0xFFFF7537.toInt()),

        "jar" to IconEntry(R.drawable.ic_doc_apk, 0xFF8BC34A.toInt()),
        "ttf" to IconEntry(R.drawable.ic_doc_font, 0xFF455A64.toInt()),
        "otf" to IconEntry(R.drawable.ic_doc_font, 0xFF455A64.toInt()),
    )

    private val nameMap = mapOf(
        "Dockerfile" to IconEntry(R.drawable.ic_doc_codes, 0xFF607D8B.toInt()),
        "Makefile" to IconEntry(R.drawable.ic_doc_codes, 0xFF607D8B.toInt()),
        ".gitignore" to IconEntry(R.drawable.ic_doc_codes, 0xFF607D8B.toInt()),
        ".gitattributes" to IconEntry(R.drawable.ic_doc_codes, 0xFF607D8B.toInt()),
        ".env" to IconEntry(R.drawable.ic_doc_text, 0xFF808080.toInt()),
        ".editorconfig" to IconEntry(R.drawable.ic_doc_codes, 0xFF607D8B.toInt()),
    )

    private val fallback = IconEntry(R.drawable.ic_doc_generic, 0xFFDDDDDD.toInt())

    fun iconFor(file: File): Pair<Int, Int> {
        if (file.isDirectory) return Pair(R.drawable.ic_doc_folder, 0xFFFFC107.toInt())
        nameMap.entries.firstOrNull { file.name == it.key }?.let { return Pair(it.value.res, it.value.bgColor) }
        val ext = file.extension.lowercase()
        val entry = map[ext] ?: fallback
        return Pair(entry.res, entry.bgColor)
    }
}
