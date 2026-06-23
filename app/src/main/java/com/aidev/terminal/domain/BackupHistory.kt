package com.aidev.terminal.domain

data class BackupHistory(
    val id: String,
    val timestamp: Long,
    val items: List<String>,
    val status: Status,
    val size: Long
) {
    enum class Status {
        SUCCESS,
        FAILED,
        IN_PROGRESS
    }
}
