package com.aidev.terminal

import java.io.File

data class FilePageState(
    val activeLeft: Boolean,
    val leftDir: File,
    val rightDir: File,
    val selectedFile: File?,
    val multiMode: Boolean,
    val multiPaneSide: Boolean,
    val isPreviewViewMode: Boolean = false,
    val isHtmlSourceMode: Boolean = false,
    val isPreviewEditMode: Boolean = false,
    val previewFile: File? = null,
    val previewOriginalText: String = "",
    val isPreviewDirty: Boolean = false,
    val previewLineCount: Int = 0,
    val lastOpenTime: Long = 0L,
    val lastOpenFile: File? = null,
    val pendingSyncPath: String? = null,
    val multiSelected: Set<String> = emptySet(),
    val anchorFile: String? = null
)
