package com.aidev.terminal.opencode

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.aidev.terminal.AIDevUi

/**
 * 顶栏右侧的"OpenCode 协议状态指示灯"。
 *
 * 视觉规范（与 v0.11.5 引入的 InfoPanel 区分原则一致）:
 *   - 是<strong>信息</strong>类控件，不可点击执行；点击只展示详情面板，不跳转/不变状态。
 *   - 圆点颜色:
 *       绿  = healthy 且 5 分钟内有探测；
 *       黄  = 上次探测距今 > 5 分钟（陈旧）；
 *       红  = 探测失败 或 从未连上；
 *       灰  = 尚未探测（启动瞬间）。
 *   - 圆点旁带极简文字 "v 0.x.y" 或 "未连接"，宽度有限时省略。
 */
object OpencodeStatusBadge {

    fun create(activity: Activity, ui: AIDevUi): View {
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ui.dp(8), ui.dp(2), ui.dp(8), ui.dp(2))
            background = GradientDrawable().apply {
                cornerRadius = ui.dp(12).toFloat()
                setColor(0xFF1A1F26.toInt())
                setStroke(ui.dp(1), 0xFF262C36.toInt())
            }
        }
        val dot = View(activity).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFF7A8390.toInt())
            }
        }
        val label = TextView(activity).apply {
            textSize = 10.5f
            setTextColor(0xFFB7BCC6.toInt())
            text = "OC ?"
            includeFontPadding = false
        }
        container.addView(dot, LinearLayout.LayoutParams(ui.dp(8), ui.dp(8)).apply {
            rightMargin = ui.dp(6)
        })
        container.addView(label)

        val listener = object : OpencodeManager.HealthListener {
            override fun onHealth(status: OpencodeManager.HealthStatus) {
                activity.runOnUiThread { applyStatus(dot, label, status) }
            }
        }
        OpencodeManager.addListener(listener)
        container.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) { OpencodeManager.removeListener(listener) }
        })
        container.setOnClickListener { showDetail(activity, OpencodeManager.lastStatus) }

        // 主动触发一次：界面打开即看到当前状态
        OpencodeManager.probeNow(activity.applicationContext)
        return container
    }

    private fun applyStatus(dot: View, label: TextView, status: OpencodeManager.HealthStatus) {
        val now = System.currentTimeMillis()
        val ageOk = status.timestampMs > 0 && (now - status.timestampMs) < 5 * 60_000L
        val color = when {
            status.timestampMs == 0L -> 0xFF7A8390.toInt()
            status.healthy && ageOk -> 0xFF6CD28A.toInt()
            status.healthy && !ageOk -> 0xFFF3C969.toInt()
            else -> 0xFFFF6B6B.toInt()
        }
        (dot.background as GradientDrawable).setColor(color)
        label.text = when {
            status.timestampMs == 0L -> "OC ?"
            status.healthy -> "OC " + (if (status.version.isNotBlank()) status.version else "on")
            else -> "OC 未连接"
        }
        label.setTextColor(if (status.healthy) 0xFFC8CCD4.toInt() else 0xFFFF9686.toInt())
    }

    fun showDetail(activity: Activity, status: OpencodeManager.HealthStatus) {
        val time = if (status.timestampMs == 0L) "—"
        else DateFormat.format("HH:mm:ss", status.timestampMs).toString()
        val message = buildString {
            append("协议端点: ").append(status.baseUrl).append('\n')
            append("健康状态: ").append(if (status.healthy) "healthy" else "unhealthy").append('\n')
            append("版本号  : ").append(status.version.ifBlank { "—" }).append('\n')
            append("最近探测: ").append(time).append('\n')
            status.errorMessage?.let { append("错误: ").append(it).append('\n') }
            append('\n')
            append("Phase 1 · 在 PRoot Ubuntu 中执行 `opencode serve --port 4096 --hostname 127.0.0.1` 即可与本端建立连接。")
        }
        AlertDialog.Builder(activity)
            .setTitle("OpenCode 协议状态")
            .setMessage(message)
            .setPositiveButton("立即重新探测") { _, _ -> OpencodeManager.probeNow(activity.applicationContext) }
            .setNegativeButton("关闭", null)
            .show()
    }

    @Suppress("unused")
    fun unusedColorReference(): Int = Color.WHITE
}
