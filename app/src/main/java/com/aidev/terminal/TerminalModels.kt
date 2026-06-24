package com.aidev.terminal

import com.termux.terminal.TerminalSession

internal data class EmbeddedTermSession(
    val id: Int,
    var title: String,
    val session: TerminalSession,
    var aiSession: Boolean = false
)

internal data class EmbeddedVirtualKey(
    val label: String,
    val input: String,
    val swipeCommand: String = "",
    val id: String = label
)

internal data class KeyAlias(
    val name: String,
    val value: String
)

internal data class TerminalCompletion(
    val label: String,
    val insertText: String = label,
    val kind: String = "CMD"
)
