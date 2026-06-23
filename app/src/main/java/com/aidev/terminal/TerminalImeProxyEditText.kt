package com.aidev.terminal

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.widget.EditText

/**
 * IME 代理 EditText，用于处理输入法相关功能
 * 
 * 功能：
 * - 处理 IME 的 composing 状态
 * - 转发 IME 事件给终端
 * - 支持 TUI 模式
 * 
 * @author Terminal Team
 */
class TerminalImeProxyEditText(context: Context) : EditText(context) {
    var onComposingChanged: (String) -> Unit = {}
    var onCommittedText: (String) -> Unit = {}
    var onBackspace: () -> Unit = {}
    var onEnter: () -> Unit = {}
    var tuiMode = false
    var tuiKeyHandler: ((KeyEvent) -> Boolean)? = null
    private var clearing = false
    private var currentComposing = ""
    // TUI 模式：缓存 composing 文字，等 commitText 或 finishComposingText 时发送
    private var tuiComposing = ""
    private var tuiComposingSent = false

    init {
        setSingleLine(true)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        imeOptions = EditorInfo.IME_ACTION_NONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        setTextColor(Color.TRANSPARENT)
        setBackgroundColor(Color.TRANSPARENT)
        isCursorVisible = false
        alpha = 0.01f
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        val base = super.onCreateInputConnection(outAttrs)
        return object : InputConnectionWrapper(base, true) {
            override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
                if (tuiMode) {
                    // TUI 模式：只缓存，不发送（避免重复）
                    tuiComposing = text?.toString().orEmpty()
                    tuiComposingSent = false
                    clearProxyText()
                    return super.setComposingText("", 1)
                }
                // 普通模式：更新 composing 状态
                currentComposing = text?.toString().orEmpty()
                onComposingChanged(currentComposing)
                return super.setComposingText(text, newCursorPosition)
            }

            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                if (tuiMode) {
                    // TUI 模式：发送最终文字
                    val str = text?.toString().orEmpty()
                    if (str.isNotEmpty()) onCommittedText(str)
                    tuiComposing = ""
                    tuiComposingSent = true
                    clearProxyText()
                    return super.commitText("", 1)
                }
                // 普通模式：发送文字
                val committed = text?.toString().orEmpty()
                if (committed.isNotEmpty()) onCommittedText(committed)
                currentComposing = ""
                onComposingChanged("")
                val result = super.commitText(text, newCursorPosition)
                clearProxyText()
                return result
            }

            override fun finishComposingText(): Boolean {
                if (tuiMode) {
                    // 语音输入可能只走 setComposingText + finishComposingText
                    // 如果 composing 文字未发送，在这里补发
                    if (!tuiComposingSent && tuiComposing.isNotEmpty()) {
                        onCommittedText(tuiComposing)
                    }
                    tuiComposing = ""
                    tuiComposingSent = false
                    clearProxyText()
                    return super.finishComposingText()
                }
                // 普通模式：清空 composing 状态
                currentComposing = ""
                onComposingChanged("")
                return super.finishComposingText()
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                if (tuiMode) {
                    // TUI 模式：IME 在预输入缓冲区中删除，不发给终端
                    return super.deleteSurroundingText(beforeLength, afterLength)
                }
                // 普通模式
                if (currentComposing.isNotEmpty()) {
                    currentComposing = currentComposing.dropLast(beforeLength.coerceAtLeast(1))
                    onComposingChanged(currentComposing)
                } else {
                    repeat(beforeLength.coerceAtLeast(1)) { onBackspace() }
                }
                return super.deleteSurroundingText(beforeLength, afterLength)
            }

            override fun sendKeyEvent(event: KeyEvent): Boolean {
                if (tuiMode) {
                    // TUI 模式：把按键转发给 TerminalView 处理
                    val handler = tuiKeyHandler
                    if (handler != null) {
                        return handler(event)
                    }
                    return super.sendKeyEvent(event)
                }
                // 普通模式
                if (event.action == KeyEvent.ACTION_DOWN) {
                    when (event.keyCode) {
                        KeyEvent.KEYCODE_DEL -> {
                            onBackspace()
                            return true
                        }
                        KeyEvent.KEYCODE_ENTER -> {
                            onEnter()
                            return true
                        }
                    }
                }
                return super.sendKeyEvent(event)
            }
        }
    }

    fun clearProxyText() {
        if (clearing) return
        clearing = true
        post {
            text?.clear()
            clearing = false
        }
    }
}