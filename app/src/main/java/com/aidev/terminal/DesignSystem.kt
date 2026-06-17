package com.aidev.terminal

import android.app.Activity
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
import android.text.TextUtils
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

object DesignTokens {
    const val SPACE_XS = 4
    const val SPACE_SM = 8
    const val SPACE_MD = 12
    const val SPACE_LG = 16
    const val SPACE_XL = 20
    const val RADIUS_SM = 6
    const val RADIUS_MD = 8
    const val RADIUS_LG = 12
    const val TOP_BAR_HEIGHT = 48
    const val BOTTOM_NAV_HEIGHT = 52
    const val SWIPE_TRIGGER_DP = 72
    const val SWIPE_SLOP_DP = 32

    // 统一强调色：青绿色
    const val ACCENT = 0xFF22D3A7.toInt()
    const val ACCENT_DARK = 0xFF0D9488.toInt()
}

data class WorkbenchPalette(
    val bg: Int,
    val nav: Int,
    val surface: Int,
    val surfaceAlt: Int,
    val text: Int,
    val muted: Int,
    val outline: Int,
    val primary: Int,
    val secondary: Int,
    val success: Int,
    val warning: Int,
    val danger: Int
) {
    companion object {
        fun from(prefs: SharedPreferences, uiMode: Int): WorkbenchPalette {
            val preset = prefs.getString("theme_preset", "midnight") ?: "midnight"
            val systemDark = (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            return when (preset) {
                "light" -> WorkbenchPalette(
                    bg = 0xFFF7F9FC.toInt(),
                    nav = 0xFFFFFFFF.toInt(),
                    surface = 0xFFFFFFFF.toInt(),
                    surfaceAlt = 0xFFF1F5F9.toInt(),
                    text = 0xFF111827.toInt(),
                    muted = 0xFF6B7280.toInt(),
                    outline = 0xFFE5E7EB.toInt(),
                    primary = 0xFF2563EB.toInt(),
                    secondary = 0xFF06B6D4.toInt(),
                    success = 0xFF059669.toInt(),
                    warning = 0xFFD97706.toInt(),
                    danger = 0xFFDC2626.toInt()
                )
                "dynamic" -> if (systemDark) dark(0xFF7C3AED.toInt(), 0xFF06B6D4.toInt()) else WorkbenchPalette(
                    bg = 0xFFF8FAFF.toInt(),
                    nav = 0xFFFFFFFF.toInt(),
                    surface = 0xFFFFFFFF.toInt(),
                    surfaceAlt = 0xFFF3F0FF.toInt(),
                    text = 0xFF111827.toInt(),
                    muted = 0xFF64748B.toInt(),
                    outline = 0xFFE2E8F0.toInt(),
                    primary = 0xFF6750A4.toInt(),
                    secondary = 0xFF006A6A.toInt(),
                    success = 0xFF0F9F6E.toInt(),
                    warning = 0xFFD48A00.toInt(),
                    danger = 0xFFBA1A1A.toInt()
                )
                "matrix" -> dark(0xFF22C55E.toInt(), 0xFF14B8A6.toInt())
                "violet" -> dark(0xFF8B5CF6.toInt(), 0xFFEC4899.toInt())
                else -> dark(0xFF60A5FA.toInt(), 0xFF22D3EE.toInt())
            }
        }

        private fun dark(primary: Int, secondary: Int) = WorkbenchPalette(
            bg = 0xFF080B10.toInt(),
            nav = 0xFF0E131B.toInt(),
            surface = 0xFF151B24.toInt(),
            surfaceAlt = 0xFF101620.toInt(),
            text = 0xFFE5E7EB.toInt(),
            muted = 0xFF9CA3AF.toInt(),
            outline = 0xFF243244.toInt(),
            primary = primary,
            secondary = secondary,
            success = 0xFF34D399.toInt(),
            warning = 0xFFFBBF24.toInt(),
            danger = 0xFFF87171.toInt()
        )
    }
}

class AIDevUi(private val activity: Activity, private val prefs: SharedPreferences) {
    val palette: WorkbenchPalette = WorkbenchPalette.from(prefs, activity.resources.configuration.uiMode)

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
            setPadding(dp(DesignTokens.SPACE_MD), 0, dp(DesignTokens.SPACE_SM), 0)
            setBackgroundColor(palette.nav)
            layoutParams = LinearLayout.LayoutParams(-1, dp(DesignTokens.TOP_BAR_HEIGHT))
            addView(text(title, 18f, palette.text, bold = true), LinearLayout.LayoutParams(0, -1, 1f))
            actions.forEach { (label, action) ->
                addView(navItem(label, action), LinearLayout.LayoutParams(dp(58), -1))
            }
        }

    fun section(title: String, desc: String): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(DesignTokens.SPACE_LG), 0, dp(DesignTokens.SPACE_SM))
            addView(text(title, 16f, palette.text, bold = true).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
            addView(text(desc, 12f, palette.muted).apply {
                setPadding(0, dp(2), 0, 0)
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

    fun effectNotice(): String {
        val bgMode = prefs.getString("bg_mode", "solid") ?: "solid"
        val blur = prefs.getInt("ui_blur", 18)
        val alpha = prefs.getInt("ui_alpha", 94)
        return when (bgMode) {
            "image" -> "当前为自定义背景：透明度会影响卡片通透感；模糊强度用于背景遮罩和层次感，后续会继续升级为真实背景模糊。当前透明度 ${alpha}%，模糊感 ${blur}%。"
            "gradient" -> "当前为主题渐变背景：透明度会影响卡片通透感；模糊感主要影响层次提示，不会像图片背景那样明显。当前透明度 ${alpha}%，模糊感 ${blur}%。"
            else -> "当前为纯色背景：透明度只影响卡片与面板，模糊设置不会产生明显视觉差异。若希望看到模糊和通透效果，请切换为自定义背景或主题渐变背景。"
        }
    }

    fun listItem(title: String, value: String, ok: Boolean): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(DesignTokens.SPACE_MD), dp(DesignTokens.SPACE_SM), dp(DesignTokens.SPACE_MD), dp(DesignTokens.SPACE_SM))
            addView(text(title, 13f, palette.muted), LinearLayout.LayoutParams(0, -2, 1f))
            addView(text(value, 14f, if (ok) palette.success else palette.warning, bold = true), LinearLayout.LayoutParams(-2, -2))
        }

    fun actionRow(title: String, desc: String, action: () -> Unit): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(DesignTokens.SPACE_MD), dp(DesignTokens.SPACE_SM), dp(DesignTokens.SPACE_MD), dp(DesignTokens.SPACE_SM))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                pulse()
                action()
            }
            addView(text(title, 15f, palette.text, bold = true).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
            addView(text(desc, 12f, palette.muted).apply {
                setPadding(0, dp(2), 0, 0)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
        }

    fun infoRow(title: String, body: String): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(DesignTokens.SPACE_MD), dp(DesignTokens.SPACE_SM), dp(DesignTokens.SPACE_MD), dp(DesignTokens.SPACE_SM))
            addView(text(title, 14f, palette.text, bold = true).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
            addView(text(body, 12f, palette.muted).apply {
                setPadding(0, dp(2), 0, 0)
            })
        }

    fun bottomNav(items: List<Pair<String, () -> Unit>>): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(DesignTokens.SPACE_MD), 0, dp(DesignTokens.SPACE_MD), 0)
            setBackgroundColor(palette.nav)
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

    fun muted(value: String): TextView = text(value, 12f, palette.muted)

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
            activity.window.decorView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
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
        text(label, 12f, palette.muted).apply {
            gravity = Gravity.CENTER
            setOnClickListener {
                pulse()
                action()
            }
        }

    fun heroSubtitle(value: String): TextView =
        text(value, 13f, Color.argb(221, 255, 255, 255)).apply {
            setPadding(0, dp(DesignTokens.SPACE_SM), 0, 0)
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
                    intArrayOf(palette.bg, palette.surfaceAlt, palette.nav)
                )
            }
            else -> root.setBackgroundColor(palette.bg)
        }
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
