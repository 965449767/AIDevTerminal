package com.aidev.terminal

data class ShellResult(
    val stdout: String,
    val stderr: String,
    val exitCode: Int
) {
    val isSuccess get() = exitCode == 0
}
