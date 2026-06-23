package com.aidev.terminal.domain

data class BackupItem(
    val id: String,
    val name: String,
    val desc: String,
    val isLarge: Boolean,
    val defaultSelected: Boolean,
    val paths: () -> List<String>,
    val enabled: Boolean
)
