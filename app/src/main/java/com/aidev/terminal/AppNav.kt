package com.aidev.terminal

import android.app.Activity
import android.content.Intent
import android.os.Build

object AppNav {
    private val fullOrder = listOf(
        ShellActivity::class.java,
        ServerCenterActivity::class.java
    )

    private val bottomOrder = listOf(
        ShellActivity::class.java
    )

    fun bottom(activity: Activity): List<Pair<String, () -> Unit>> = listOf(
        "终端" to { openTerminal(activity, "") },
        "文件" to { ShellActivity.open(activity, ShellActivity.TAB_FILES) },
        "设置" to { ShellActivity.open(activity, ShellActivity.TAB_SETTINGS) },
        "知识库" to { ShellActivity.open(activity, ShellActivity.TAB_KNOWLEDGE) }
    )

    fun attach(activity: Activity, ui: AIDevUi, root: android.view.View, current: Class<out Activity>) {
        val index = fullOrder.indexOf(current).coerceAtLeast(0)
        val previous = fullOrder[(index - 1 + fullOrder.size) % fullOrder.size]
        val next = fullOrder[(index + 1) % fullOrder.size]
        ui.attachSwipeNavigation(
            root,
            previous = { open(activity, previous, forceForward = false) },
            next = { open(activity, next, forceForward = true) }
        )
    }

    fun open(activity: Activity, cls: Class<out Activity>, forceForward: Boolean? = null) {
        if (activity::class.java == cls) return
        if (cls == ShellActivity::class.java) {
            ShellActivity.open(activity, ShellActivity.TAB_TERMINAL)
            return
        }
        activity.startActivity(Intent(activity, cls))
        applyTransition(activity, cls, forceForward)
    }

    fun openTerminal(activity: Activity, command: String) {
        TerminalCommandBus.post(command.trim())
        ShellActivity.open(activity, ShellActivity.TAB_TERMINAL)
    }

    fun finish(activity: Activity) {
        activity.finish()
        activity.overrideTransition(R.anim.slide_in_left, R.anim.slide_out_right, isFinish = true)
    }

    private fun applyTransition(activity: Activity, target: Class<out Activity>, forceForward: Boolean?) {
        val forward = forceForward ?: isForward(activity::class.java, target)
        if (forward) {
            activity.overrideTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        } else {
            activity.overrideTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
    }

    private fun Activity.overrideTransition(enterAnim: Int, exitAnim: Int, isFinish: Boolean = false) {
        if (Build.VERSION.SDK_INT >= 34) {
            val type = if (isFinish) Activity.OVERRIDE_TRANSITION_CLOSE else Activity.OVERRIDE_TRANSITION_OPEN
            overrideActivityTransition(type, enterAnim, exitAnim)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(enterAnim, exitAnim)
        }
    }

    private fun isForward(current: Class<out Activity>, target: Class<out Activity>): Boolean {
        val order = if (bottomOrder.contains(current) && bottomOrder.contains(target)) bottomOrder else fullOrder
        val from = order.indexOf(current)
        val to = order.indexOf(target)
        if (from < 0 || to < 0) return true
        return to > from
    }
}
