package com.aidev.terminal

import java.io.File

data class FilePageState(
    val activeLeft: Boolean,
    val leftDir: File,
    val rightDir: File,
    val selectedFile: File?,
    val multiMode: Boolean,
    val multiPaneSide: Boolean
)
