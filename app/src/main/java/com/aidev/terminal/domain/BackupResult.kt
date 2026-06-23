package com.aidev.terminal.domain

data class BackupResult(
    val type: ResultType,
    val message: String,
    val data: Any? = null
) {
    enum class ResultType {
        PROGRESS,
        SUCCESS,
        ERROR
    }
}
