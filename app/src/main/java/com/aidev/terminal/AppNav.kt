package com.aidev.terminal

import android.app.Activity
import android.content.Intent

object AppNav {
    private val fullOrder = listOf(
        DashboardActivity::class.java,
        ShellActivity::class.java,
        FileManagerActivity::class.java,
        TaskCenterActivity::class.java,
        AIAgentActivity::class.java,
        ServerCenterActivity::class.java,
        ThemeCenterActivity::class.java,
        SettingsActivity::class.java
    )

    private val bottomOrder = listOf(
        DashboardActivity::class.java,
        ShellActivity::class.java,
        FileManagerActivity::class.java,
        TaskCenterActivity::class.java,
        SettingsActivity::class.java
    )

    fun bottom(activity: Activity): List<Pair<String, () -> Unit>> = listOf(
        "工作台" to { open(activity, DashboardActivity::class.java) },
        "终端" to { openTerminal(activity, "") },
        "文件" to { open(activity, FileManagerActivity::class.java) },
        "任务" to { open(activity, TaskCenterActivity::class.java) },
        "设置" to { open(activity, SettingsActivity::class.java) }
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
        activity.overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }

    private fun applyTransition(activity: Activity, target: Class<out Activity>, forceForward: Boolean?) {
        val forward = forceForward ?: isForward(activity::class.java, target)
        if (forward) {
            activity.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        } else {
            activity.overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
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
