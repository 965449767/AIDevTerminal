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
import androidx.appcompat.widget.AppCompatEditText

class TerminalImeProxyEditText(context: Context) : AppCompatEditText(context) {
    var onComposingChanged: (String) -> Unit = {}
    var onCommittedText: (String) -> Unit = {}
    var onBackspace: () -> Unit = {}
    var onEnter: () -> Unit = {}
    var tuiMode = false
    var tuiKeyHandler: ((KeyEvent) -> Boolean)? = null
    private var clearing = false
    private var currentComposing = ""

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
                    tuiComposing = text?.toString().orEmpty()
                    tuiComposingSent = false
                    clearProxyText()
                    return super.setComposingText("", 1)
                }
                currentComposing = text?.toString().orEmpty()
                onComposingChanged(currentComposing)
                return super.setComposingText(text, newCursorPosition)
            }

            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                if (tuiMode) {
                    val str = text?.toString().orEmpty()
                    if (str.isNotEmpty()) onCommittedText(str)
                    tuiComposingSent = true
                    tuiComposing = ""
                    clearProxyText()
                    return super.commitText("", 1)
                }
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
                    if (!tuiComposingSent && tuiComposing.isNotEmpty()) {
                        onCommittedText(tuiComposing)
                        tuiComposingSent = true
                    }
                    tuiComposing = ""
                    clearProxyText()
                    return super.finishComposingText()
                }
                currentComposing = ""
                onComposingChanged("")
                return super.finishComposingText()
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                if (tuiMode) {
                    return super.deleteSurroundingText(beforeLength, afterLength)
                }
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
                    val handler = tuiKeyHandler
                    if (handler != null) {
                        return handler(event)
                    }
                    return super.sendKeyEvent(event)
                }
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
