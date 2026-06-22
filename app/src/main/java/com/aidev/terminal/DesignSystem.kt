package com.aidev.terminal

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.text.TextUtils
import android.widget.EditText
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

/**
 * 设计令牌：统一的间距、圆角、字体层级
 */
object DesignTokens {
    // 间距 8 级（4dp 网格）
    const val SPACE_2 = 2
    const val SPACE_4 = 4
    const val SPACE_8 = 8
    const val SPACE_12 = 12
    const val SPACE_16 = 16
    const val SPACE_20 = 20
    const val SPACE_24 = 24
    const val SPACE_32 = 32

    // 圆角（基于原值，后续视觉确认后统一升级）
    const val RADIUS_SM = 6
    const val RADIUS_MD = 8
    const val RADIUS_LG = 12

    // 尺寸
    const val TOP_BAR_HEIGHT = 48
    const val BOTTOM_NAV_HEIGHT = 52
    const val LIST_ITEM_HEIGHT = 48
    const val SWIPE_TRIGGER_DP = 72
    const val SWIPE_SLOP_DP = 32

    // 字体层级
    const val TEXT_H1 = 20f
    const val TEXT_H2 = 16f
    const val TEXT_BODY = 14f
    const val TEXT_CAPTION = 12f
    const val TEXT_LABEL = 10f

    // 统一强调色：Matrix 终端绿
    const val ACCENT = 0xFF00FF41.toInt()
    const val ACCENT_DARK = 0xFF008F11.toInt()
}

/**
 * 主题管理器：支持系统深色/浅色模式跟随
 */
object ThemeManager {
    fun isSystemDark(context: Context): Boolean {
        return (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    fun getPalette(context: Context): WorkbenchPalette {
        return if (isSystemDark(context)) DarkTheme else LightTheme
    }

    private val DarkTheme = WorkbenchPalette(
        bg = 0xFF0B0C0E.toInt(),
        surface = 0xFF141619.toInt(),
        surfaceAlt = 0xFF1C1E22.toInt(),
        surfaceHighlight = 0xFF23252A.toInt(),
        text = 0xFFF0F0F0.toInt(),
        muted = 0xFF9CA3AF.toInt(),
        outline = 0xFF262A30.toInt(),
        accent = DesignTokens.ACCENT,
        success = 0xFF34D399.toInt(),
        warning = 0xFFFBBF24.toInt(),
        danger = 0xFFF87171.toInt()
    )

    private val LightTheme = WorkbenchPalette(
        bg = 0xFFF5F5F7.toInt(),
        surface = 0xFFFFFFFF.toInt(),
        surfaceAlt = 0xFFF0F0F2.toInt(),
        surfaceHighlight = 0xFFE5E5E8.toInt(),
        text = 0xFF1A1A1A.toInt(),
        muted = 0xFF6B6B73.toInt(),
        outline = 0xFFE0E0E5.toInt(),
        accent = 0xFF00AA33.toInt(),
        success = 0xFF059669.toInt(),
        warning = 0xFFD97706.toInt(),
        danger = 0xFFDC2626.toInt()
    )
}

/**
 * 调色板：单强调色，移除 primary/secondary 双强调色
 */
data class WorkbenchPalette(
    val bg: Int,
    val surface: Int,
    val surfaceAlt: Int,
    val surfaceHighlight: Int,
    val text: Int,
    val muted: Int,
    val outline: Int,
    val accent: Int,
    val success: Int,
    val warning: Int,
    val danger: Int
)

/**
 * 分割线组件
 */
fun View.divider(color: Int, height: Int = 1): View {
    return View(context).apply {
        setBackgroundColor(color)
        layoutParams = LinearLayout.LayoutParams(-1, height)
    }
}

/**
 * AIDev UI 构建器
 */
class AIDevUi(private val activity: Activity, private val prefs: SharedPreferences) {
    val palette: WorkbenchPalette = ThemeManager.getPalette(activity)

    fun dp(value: Int): Int {
        val scale = prefs.getInt("ui_density", 100).coerceIn(86, 116) / 100f
        return (value * activity.resources.displayMetrics.density * scale).toInt()
    }

    fun pageRoot(): LinearLayout =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            applyBackground(this)
        }

    fun topBar(title: String, vararg actions: Pair<String, () -> Unit>): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(DesignTokens.SPACE_12), 0, dp(DesignTokens.SPACE_8), 0)
            setBackgroundColor(palette.bg)
            layoutParams = LinearLayout.LayoutParams(-1, dp(DesignTokens.TOP_BAR_HEIGHT))
            addView(text(title, DesignTokens.TEXT_H2, palette.text, bold = true), LinearLayout.LayoutParams(0, -1, 1f))
            actions.forEach { (label, action) ->
                addView(navItem(label, action), LinearLayout.LayoutParams(dp(58), -1))
            }
        }

    fun section(title: String, desc: String): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(DesignTokens.SPACE_16), 0, dp(DesignTokens.SPACE_8))
            addView(text(title, DesignTokens.TEXT_H2, palette.text, bold = true).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
            addView(text(desc, DesignTokens.TEXT_CAPTION, palette.muted).apply {
                setPadding(0, dp(DesignTokens.SPACE_2), 0, 0)
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
            })
        }

    fun surfaceBackground(): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(palette.surface, palette.surface)).apply {
            cornerRadius = dp(DesignTokens.RADIUS_MD).toFloat()
        }

    fun accentButtonBackground(): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(DesignTokens.ACCENT, DesignTokens.ACCENT_DARK)).apply {
            cornerRadius = dp(DesignTokens.RADIUS_MD).toFloat()
        }

    fun subtleButtonBackground(): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(palette.surfaceAlt, palette.surfaceAlt)).apply {
            cornerRadius = dp(DesignTokens.RADIUS_MD).toFloat()
        }

    fun roundedBackground(color: Int, radius: Int = DesignTokens.RADIUS_MD): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(color, color)).apply {
            cornerRadius = dp(radius).toFloat()
        }

    fun divider(): View =
        View(activity).apply {
            setBackgroundColor(palette.outline)
            layoutParams = LinearLayout.LayoutParams(-1, 1)
        }

    fun listItem(title: String, value: String, ok: Boolean): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(DesignTokens.SPACE_12), dp(DesignTokens.SPACE_8), dp(DesignTokens.SPACE_12), dp(DesignTokens.SPACE_8))
            addView(text(title, DesignTokens.TEXT_CAPTION, palette.muted), LinearLayout.LayoutParams(0, -2, 1f))
            addView(text(value, DesignTokens.TEXT_BODY, if (ok) palette.success else palette.warning, bold = true), LinearLayout.LayoutParams(-2, -2))
        }

    fun actionRow(title: String, desc: String, action: () -> Unit): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(DesignTokens.SPACE_12), dp(DesignTokens.SPACE_8), dp(DesignTokens.SPACE_12), dp(DesignTokens.SPACE_8))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                pulse()
                action()
            }
            addView(text(title, DesignTokens.TEXT_BODY, palette.text, bold = true).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
            addView(text(desc, DesignTokens.TEXT_CAPTION, palette.muted).apply {
                setPadding(0, dp(DesignTokens.SPACE_2), 0, 0)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
        }

    fun infoRow(title: String, body: String): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(DesignTokens.SPACE_12), dp(DesignTokens.SPACE_8), dp(DesignTokens.SPACE_12), dp(DesignTokens.SPACE_8))
            addView(text(title, DesignTokens.TEXT_BODY, palette.text, bold = true).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
            addView(text(body, DesignTokens.TEXT_CAPTION, palette.muted).apply {
                setPadding(0, dp(DesignTokens.SPACE_2), 0, 0)
            })
        }

    fun effectNotice(): String {
        val mode = prefs.getString("bg_mode", "solid")
        val alpha = prefs.getInt("ui_alpha", 94)
        val blur = prefs.getInt("ui_blur", 18)
        val density = prefs.getInt("ui_density", 100)
        return when (mode) {
            "image" -> "背景：自定义图片"
            "gradient" -> "背景：主题渐变"
            else -> "背景：纯色"
        } + " | 透明度：${alpha}% | 模糊：${blur} | 密度：${density}%"
    }

    fun bottomNav(items: List<Pair<String, () -> Unit>>): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(DesignTokens.SPACE_12), 0, dp(DesignTokens.SPACE_12), 0)
            setBackgroundColor(palette.bg)
            items.forEach { (label, action) ->
                addView(navItem(label, action), LinearLayout.LayoutParams(0, dp(DesignTokens.BOTTOM_NAV_HEIGHT), 1f))
            }
        }

    fun text(value: String, size: Float, color: Int, bold: Boolean = false): TextView =
        TextView(activity).apply {
            text = value
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER_VERTICAL
            includeFontPadding = false
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    fun muted(value: String): TextView = text(value, DesignTokens.TEXT_CAPTION, palette.muted)

    /** 空状态占位视图：居中显示标题和提示文字 */
    fun emptyState(message: String, hint: String): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(DesignTokens.SPACE_12), dp(DesignTokens.SPACE_24), dp(DesignTokens.SPACE_12), dp(DesignTokens.SPACE_24))
            addView(text(message, DesignTokens.TEXT_H2, palette.text, bold = true))
            addView(text(hint, DesignTokens.TEXT_CAPTION, palette.muted).apply {
                setPadding(0, dp(DesignTokens.SPACE_4), 0, 0)
            })
        }

    /** 状态指示圆点：绿色表示正常，红色表示异常 */
    fun statusDot(ok: Boolean): View =
        View(activity).apply {
            val size = dp(8)
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                setMargins(0, 0, dp(DesignTokens.SPACE_4), 0)
            }
            setBackgroundResource(android.R.drawable.btn_default)
            setBackgroundColor(if (ok) palette.success else palette.danger)
        }

    /** 带主题样式的输入框 */
    fun inputField(hint: String): EditText =
        EditText(activity).apply {
            setHint(hint)
            setTextColor(palette.text)
            setHintTextColor(palette.muted)
            setBackgroundColor(palette.surfaceAlt)
            setPadding(dp(DesignTokens.SPACE_12), dp(DesignTokens.SPACE_8), dp(DesignTokens.SPACE_12), dp(DesignTokens.SPACE_8))
            textSize = DesignTokens.TEXT_BODY
        }

    fun rowOf(left: View, right: View): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            isBaselineAligned = false
            addView(left, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(0, 0, dp(6), dp(8)) })
            addView(right, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(6), 0, 0, dp(8)) })
        }

    fun withBottomMargin(view: View, bottom: Int): View =
        view.apply {
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, dp(bottom)) }
        }

    fun pulse() {
        if (prefs.getBoolean("haptic_tap", true)) {
            activity.window?.decorView?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    fun attachSwipeNavigation(target: View, previous: (() -> Unit)? = null, next: (() -> Unit)? = null) {
        var downX = 0f
        var downY = 0f
        target.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    false
                }
                MotionEvent.ACTION_UP -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (abs(dx) > dp(DesignTokens.SWIPE_TRIGGER_DP) && abs(dy) < dp(DesignTokens.SWIPE_SLOP_DP)) {
                        pulse()
                        if (dx < 0) next?.invoke() else previous?.invoke()
                        true
                    } else {
                        false
                    }
                }
                else -> false
            }
        }
    }

    private fun navItem(label: String, action: () -> Unit): TextView =
        text(label, DesignTokens.TEXT_CAPTION, palette.muted).apply {
            gravity = Gravity.CENTER
            setOnClickListener {
                pulse()
                action()
            }
        }

    fun heroSubtitle(value: String): TextView =
        text(value, 13f, Color.argb(221, 255, 255, 255)).apply {
            setPadding(0, dp(DesignTokens.SPACE_8), 0, 0)
        }

    fun heroMeta(value: String): TextView =
        text(value, 12f, Color.argb(191, 255, 255, 255)).apply {
            setPadding(0, dp(10), 0, 0)
        }

    private fun applyBackground(root: LinearLayout) {
        when (prefs.getString("bg_mode", "solid")) {
            "image" -> {
                val uri = prefs.getString("bg_image_uri", null)
                val drawable = uri?.let {
                    runCatching {
                        activity.contentResolver.openInputStream(android.net.Uri.parse(it)).use { input ->
                            BitmapDrawable(activity.resources, BitmapFactory.decodeStream(input)).apply {
                                gravity = Gravity.CENTER
                                alpha = 255
                            }
                        }
                    }.getOrNull()
                }
                if (drawable != null) {
                    root.background = drawable
                } else {
                    root.setBackgroundColor(palette.bg)
                }
            }
            "gradient" -> {
                root.background = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(palette.bg, palette.surfaceAlt, palette.bg)
                )
            }
            else -> root.setBackgroundColor(palette.bg)
        }
    }

    fun showAsDialog(contentView: View, onDismiss: (() -> Unit)? = null): Dialog {
        val dialog = Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar)
        dialog.setContentView(contentView)
        dialog.window?.let { w ->
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            w.setGravity(Gravity.CENTER)
        }
        ViewCompat.setOnApplyWindowInsetsListener(contentView) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, v.paddingTop, bars.right, v.paddingBottom)
            insets
        }
        dialog.setOnDismissListener { onDismiss?.invoke() }
        dialog.show()
        return dialog
    }

    private fun pillBackground(stroke: Int, fill: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(DesignTokens.RADIUS_MD).toFloat()
            setStroke(dp(1), stroke)
        }

    private fun tint(foreground: Int, background: Int, amount: Float): Int {
        val a = amount.coerceIn(0f, 1f)
        val r = (Color.red(foreground) * a + Color.red(background) * (1f - a)).toInt()
        val g = (Color.green(foreground) * a + Color.green(background) * (1f - a)).toInt()
        val b = (Color.blue(foreground) * a + Color.blue(background) * (1f - a)).toInt()
        return Color.rgb(r, g, b)
    }
}
