package com.aidev.terminal

import android.app.Activity
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView

internal class VirtualKeyboardBuilder(
    private val pm: PreferencesManager,
    private val terminalView: TerminalView?,
    private val sessionRef: () -> TerminalSession?,
    private val onSendCommand: (String) -> Unit,
    private val onUpdateInputBuffer: (String) -> Unit,
    private val onHapticTap: () -> Unit,
    private val pendingRunnables: MutableList<Pair<View, Runnable>>,
    private val sharedHandler: android.os.Handler
) {
    var keyboardView: LinearLayout? = null
    var currentKeyOrder = mutableListOf<String>()
    var selectedKeyView: View? = null
    var wiggleAnimator: android.animation.ValueAnimator? = null
    var isRearranging = false
    var ctrlLatched = false

    companion object {
        private val SWIPE_THRESHOLD_DP = 72
    }

    fun keys(activity: Activity, ui: AIDevUi): LinearLayout =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF111418.toInt())
            setPadding(ui.dp(4), ui.dp(3), ui.dp(4), ui.dp(3))
            keyboardView = this
            currentKeyOrder = loadKeyOrder(activity)
            buildKeyboardRows(activity, ui, getOrderedKeys(activity))
        }

    fun buildKeyboardRows(activity: Activity, ui: AIDevUi, keys: List<EmbeddedVirtualKey>) {
        keyboardView?.removeAllViews()
        keys.chunked(6).forEach { rowKeys ->
            keyboardView?.addView(LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                rowKeys.forEachIndexed { index, key ->
                    addView(shortcutKey(activity, ui, key), LinearLayout.LayoutParams(0, ui.dp(30), 1f).apply {
                        setMargins(if (index == 0) 0 else ui.dp(2), ui.dp(1), ui.dp(2), ui.dp(1))
                    })
                }
            }, LinearLayout.LayoutParams(-1, 0, 1f))
        }
    }

    fun getOrderedKeys(activity: Activity): List<EmbeddedVirtualKey> {
        val all = embeddedKeys(activity)
        val keyMap = all.associateBy { it.id }
        val ordered = currentKeyOrder.mapNotNull { keyMap[it] }
        val rest = all.filter { it.id !in currentKeyOrder }
        return ordered + rest
    }

    private fun loadKeyOrder(activity: Activity): MutableList<String> {
        val raw = pm.terminalKeyOrder
        if (raw.isBlank()) return embeddedKeys(activity).map { it.id }.toMutableList()
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
    }

    private fun saveKeyOrder(activity: Activity) {
        val raw = currentKeyOrder.joinToString(",")
        pm.terminalKeyOrder = raw
    }

    fun enterRearrangeMode(activity: Activity, ui: AIDevUi) {
        isRearranging = true
        onHapticTap()
        setButtonsRearrangeStyle(ui, true)
    }

    fun exitRearrangeMode(activity: Activity, ui: AIDevUi, save: Boolean) {
        if (save) saveKeyOrder(activity)
        isRearranging = false
        ctrlLatched = false
        onHapticTap()
        setButtonsRearrangeStyle(ui, false)
        refreshKeyboard(activity, ui)
    }

    private fun setButtonsRearrangeStyle(ui: AIDevUi, rearranging: Boolean) {
        keyboardView?.let { kb ->
            if (rearranging) {
                kb.setBackgroundColor(0xFF1A2240.toInt())
            } else {
                kb.setBackgroundColor(0xFF111418.toInt())
                selectedKeyView = null
                stopWiggle()
            }
            val strokeWidth = ui.dp(1)
            val accent = ui.palette.accent
            for (ri in 0 until kb.childCount) {
                val row = kb.getChildAt(ri) as? ViewGroup ?: continue
                for (ci in 0 until row.childCount) {
                    val btn = row.getChildAt(ci)
                    val gd = btn.background as? android.graphics.drawable.GradientDrawable
                    if (rearranging) {
                        gd?.setColor(0xFF2D3748.toInt())
                        gd?.setStroke(strokeWidth, 0xFF5A6A8A.toInt())
                    } else {
                        gd?.setStroke(0, 0)
                        val id = btn.tag as? String ?: continue
                        val isC = id == "ctrl"
                        gd?.setColor(if (isC && ctrlLatched) 0xFF374151.toInt() else 0xFF1F2937.toInt())
                    }
                }
            }
            if (rearranging) startWiggle()
        }
    }

    private fun startWiggle() {
        val kb = keyboardView ?: return
        val buttons = mutableListOf<View>()
        for (ri in 0 until kb.childCount) {
            val row = kb.getChildAt(ri) as? ViewGroup ?: continue
            for (ci in 0 until row.childCount) buttons.add(row.getChildAt(ci))
        }
        wiggleAnimator?.cancel()
        wiggleAnimator = android.animation.ValueAnimator.ofFloat(-2.5f, 2.5f).apply {
            duration = 300
            repeatMode = android.animation.ValueAnimator.REVERSE
            repeatCount = android.animation.ValueAnimator.INFINITE
            addUpdateListener { anim ->
                val a = anim.animatedValue as Float
                buttons.forEach { it.rotation = a }
            }
            start()
        }
    }

    private fun stopWiggle(animate: Boolean = true) {
        wiggleAnimator?.cancel()
        wiggleAnimator = null
        val kb = keyboardView ?: return
        val buttons = mutableListOf<View>()
        for (ri in 0 until kb.childCount) {
            val row = kb.getChildAt(ri) as? ViewGroup ?: continue
            for (ci in 0 until row.childCount) buttons.add(row.getChildAt(ci))
        }
        if (animate && buttons.isNotEmpty()) {
            val startRots = buttons.map { it.rotation }
            android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 200
                addUpdateListener { anim ->
                    val t = anim.animatedFraction
                    val ease = if (t < 0.5f) 2f * t * t else 1f - (-2f * t * t + 4f * t - 1f) / 2f
                    buttons.forEachIndexed { i, btn ->
                        btn.rotation = startRots[i] * (1f - ease)
                    }
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) { buttons.forEach { it.rotation = 0f } }
                })
                start()
            }
        } else {
            buttons.forEach { it.rotation = 0f }
        }
    }

    private fun applySelectedVisual(btn: View, ui: AIDevUi) {
        val gd = btn.background as? android.graphics.drawable.GradientDrawable
        gd?.setStroke(ui.dp(2), ui.palette.accent)
        btn.scaleX = 1.06f
        btn.scaleY = 1.06f
        btn.alpha = 0.7f
    }

    private fun applyDefaultVisual(btn: View, ui: AIDevUi) {
        val gd = btn.background as? android.graphics.drawable.GradientDrawable
        gd?.setStroke(ui.dp(1), 0xFF5A6A8A.toInt())
        btn.scaleX = 1f
        btn.scaleY = 1f
        btn.alpha = 1f
    }

    private fun animateSwap(v1: View, v2: View, activity: Activity, ui: AIDevUi) {
        val loc1 = IntArray(2)
        val loc2 = IntArray(2)
        v1.getLocationOnScreen(loc1)
        v2.getLocationOnScreen(loc2)
        val dx = loc2[0] - loc1[0]
        val dy = loc2[1] - loc1[1]
        android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 160
            addUpdateListener { anim ->
                val t = anim.animatedFraction
                v1.translationX = dx * t
                v1.translationY = dy * t
                v2.translationX = -dx * t
                v2.translationY = -dy * t
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    v1.translationX = 0f; v1.translationY = 0f
                    v2.translationX = 0f; v2.translationY = 0f
                    swapButtonViews(v1, v2, activity, ui)
                }
            })
            start()
        }
    }

    private fun swapButtonViews(v1: View, v2: View, activity: Activity, ui: AIDevUi) {
        val row1 = v1.parent as ViewGroup
        val row2 = v2.parent as ViewGroup
        val idx1 = row1.indexOfChild(v1)
        val idx2 = row2.indexOfChild(v2)
        row1.removeView(v1)
        row2.removeView(v2)
        row1.addView(v2, idx1)
        row2.addView(v1, idx2)
        fun fixMargin(v: View, idx: Int) {
            val lp = v.layoutParams as? LinearLayout.LayoutParams ?: return
            lp.setMargins(if (idx == 0) 0 else ui.dp(2), ui.dp(1), ui.dp(2), ui.dp(1))
            v.layoutParams = lp
        }
        fixMargin(v2, idx1)
        fixMargin(v1, idx2)
        val id1 = v1.tag as? String
        val id2 = v2.tag as? String
        if (id1 != null && id2 != null) {
            val i1 = currentKeyOrder.indexOf(id1)
            val i2 = currentKeyOrder.indexOf(id2)
            if (i1 >= 0 && i2 >= 0) {
                currentKeyOrder[i1] = id2
                currentKeyOrder[i2] = id1
            }
        }
        android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 250
            addUpdateListener { anim ->
                val t = anim.animatedFraction
                val blink = if (t < 0.5f) 1f - t * 2f else (t - 0.5f) * 2f
                v1.alpha = 1f - blink * 0.4f
                v2.alpha = 1f - blink * 0.4f
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) { v1.alpha = 1f; v2.alpha = 1f; applyDefaultVisual(v1, ui); applyDefaultVisual(v2, ui) }
            })
            start()
        }
        stopWiggle(false)
        startWiggle()
    }

    private fun embeddedKeys(activity: Activity): List<EmbeddedVirtualKey> {
        val defaults = listOf(
            EmbeddedVirtualKey("ESC", "\u001B", "clear", "esc"),
            EmbeddedVirtualKey("CTRL", "__CTRL__", "", "ctrl"),
            EmbeddedVirtualKey("↑", "\u001B[A", "history", "up"),
            EmbeddedVirtualKey("TAB", "\t", "help", "tab"),
            EmbeddedVirtualKey("/", "/", "cd /", "slash"),
            EmbeddedVirtualKey("-", "-", "cd -", "dash"),
            EmbeddedVirtualKey("C", "c", "clear", "c"),
            EmbeddedVirtualKey("←", "\u001B[D", "\u001B[H", "left"),
            EmbeddedVirtualKey("↓", "\u001B[B", "ls", "down"),
            EmbeddedVirtualKey("→", "\u001B[C", "\u001B[F", "right"),
            EmbeddedVirtualKey("|", "|", "grep ", "pipe"),
            EmbeddedVirtualKey("SPC", " ", "pwd", "space")
        )
        val overrides = parseKeyOverrides(pm.terminalKeyOverrides)
        val customizedDefaults = defaults.map { key -> overrides[key.id] ?: key }
        val custom = parseCustomKeys(pm.terminalCustomKeys)
        return (customizedDefaults + custom).take(12)
    }

    private fun shortcutKey(activity: Activity, ui: AIDevUi, key: EmbeddedVirtualKey): TextView =
        TextView(activity).apply {
            text = key.label
            textSize = 11f
            setTextColor(0xFFD1D5DB.toInt())
            gravity = Gravity.CENTER
            includeFontPadding = false
            tag = key.id
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(if (key.input == "__CTRL__" && ctrlLatched) 0xFF374151.toInt() else 0xFF1F2937.toInt())
                cornerRadius = ui.dp(8).toFloat()
            }
            var downX = 0f
            var downY = 0f
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX
                        downY = event.rawY
                        if (isRearranging) {
                            wiggleAnimator?.pause()
                            applySelectedVisual(this@apply, ui)
                            true
                        } else false
                    }
                    MotionEvent.ACTION_UP -> {
                        if (isRearranging) {
                            val dx = event.rawX - downX
                            if (dx > ui.dp(SWIPE_THRESHOLD_DP) && kotlin.math.abs(event.rawY - downY) < ui.dp(32)) {
                                applyDefaultVisual(this@apply, ui)
                                selectedKeyView = null
                                wiggleAnimator?.resume()
                                exitRearrangeMode(activity, ui, true)
                                true
                            } else {
                                val tapped = this@apply
                                if (tapped == selectedKeyView) {
                                    applyDefaultVisual(tapped, ui)
                                    selectedKeyView = null
                                    wiggleAnimator?.resume()
                                } else if (selectedKeyView == null) {
                                    selectedKeyView = tapped
                                } else {
                                    selectedKeyView?.let { source ->
                                        selectedKeyView = null
                                        applyDefaultVisual(source, ui)
                                        applyDefaultVisual(tapped, ui)
                                        onHapticTap()
                                        animateSwap(source, tapped, activity, ui)
                                    }
                                }
                                true
                            }
                        } else {
                            val dx = event.rawX - downX
                            val dy = downY - event.rawY
                            if (dx < -ui.dp(SWIPE_THRESHOLD_DP) && kotlin.math.abs(dy) < ui.dp(32)) {
                                enterRearrangeMode(activity, ui)
                                true
                            } else if (dy > ui.dp(24) && key.swipeCommand.isNotBlank()) {
                                onHapticTap()
                                sendSwipeAction(key.swipeCommand)
                                true
                            } else false
                        }
                    }
                    else -> false
                }
            }
            setOnClickListener {
                if (!isRearranging) handleVirtualKeyTap(activity, key, ui)
            }
            setOnLongClickListener {
                if (!isRearranging) {
                    onShowVirtualKeyMenu(activity, key)
                    true
                } else true
            }
        }

    var onShowVirtualKeyMenu: (Activity, EmbeddedVirtualKey) -> Unit = { _, _ -> }

    private fun handleVirtualKeyTap(activity: Activity, key: EmbeddedVirtualKey, ui: AIDevUi) {
        onHapticTap()
        if (key.input == "__CTRL__") {
            ctrlLatched = !ctrlLatched
            refreshKeyboard(activity, ui)
            return
        }
        val input = key.input
        if (input.isEmpty()) return
        val session = sessionRef()
        if (ctrlLatched && input.length == 1) {
            val ch = input[0].uppercaseChar()
            if (ch in '@'..'_') {
                val b = byteArrayOf((ch.code and 0x1F).toByte())
                session?.write(b, 0, 1)
                ctrlLatched = false
                refreshKeyboard(activity, ui)
                return
            }
        }
        session?.write(input)
        onUpdateInputBuffer(input)
        if (ctrlLatched) {
            ctrlLatched = false
            refreshKeyboard(activity, ui)
        }
    }

    fun trackPostDelayed(view: View, delayMs: Long, action: () -> Unit) {
        val r = Runnable { action() }
        pendingRunnables.add(view to r)
        view.postDelayed(r, delayMs)
    }

    private fun sendSwipeAction(action: String) {
        if (action.isEmpty()) return
        if (action.startsWith("\u001B")) {
            sessionRef()?.write(action)
        } else {
            onSendCommand(action)
        }
    }

    fun refreshKeyboard(activity: Activity, ui: AIDevUi) {
        val kb = keyboardView ?: return
        for (i in 0 until kb.childCount) {
            val row = kb.getChildAt(i) as? ViewGroup ?: continue
            for (j in 0 until row.childCount) {
                val key = row.getChildAt(j)
                if (key.tag == "ctrl") {
                    key.background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(if (ctrlLatched) 0xFF374151.toInt() else 0xFF1F2937.toInt())
                        cornerRadius = (ui.dp(8)).toFloat()
                    }
                    return
                }
            }
        }
    }

    fun clearPostDelayed() {
        pendingRunnables.forEach { (v, r) -> v.removeCallbacks(r) }
        pendingRunnables.clear()
    }
}
