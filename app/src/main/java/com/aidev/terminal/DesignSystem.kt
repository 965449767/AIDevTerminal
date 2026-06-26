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
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.text.TextUtils
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.appbar.MaterialToolbar
import kotlin.math.abs

object DesignTokens {
    const val SPACE_2 = 2
    const val SPACE_4 = 4
    const val SPACE_8 = 8
    const val SPACE_12 = 12
    const val SPACE_16 = 16
    const val SPACE_20 = 20
    const val SPACE_24 = 24
    const val SPACE_32 = 32

    const val RADIUS_SM = 8
    const val RADIUS_MD = 12
    const val RADIUS_LG = 16

    const val TOP_BAR_HEIGHT = 48
    const val BOTTOM_NAV_HEIGHT = 52
    const val LIST_ITEM_HEIGHT = 48
    const val SWIPE_TRIGGER_DP = 72
    const val SWIPE_SLOP_DP = 32

    const val TEXT_H1 = 20f
    const val TEXT_H2 = 16f
    const val TEXT_BODY = 14f
    const val TEXT_CAPTION = 12f
    const val TEXT_LABEL = 10f

    const val ACCENT = 0xFF7C3AED.toInt()
    const val ACCENT_DARK = 0xFF5B21B6.toInt()
}

object ThemeManager {
    fun isSystemDark(context: Context): Boolean {
        return (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    fun getPalette(context: Context): WorkbenchPalette = WorkbenchPalette(
        bg = resolveColor(context, android.R.attr.colorBackground, 0xFF0B0C0E.toInt()),
        surface = resolveColor(context, com.google.android.material.R.attr.colorSurface, 0xFF141619.toInt()),
        surfaceAlt = resolveColor(context, com.google.android.material.R.attr.colorSurfaceVariant, 0xFF1C1E22.toInt()),
        text = resolveColor(context, com.google.android.material.R.attr.colorOnSurface, 0xFFF0F0F0.toInt()),
        muted = resolveColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF9CA3AF.toInt()),
        outline = resolveColor(context, com.google.android.material.R.attr.colorOutline, 0xFF262A30.toInt()),
        accent = resolveColor(context, com.google.android.material.R.attr.colorPrimary, 0xFF7C3AED.toInt()),
        success = 0xFF34D399.toInt(),
        warning = 0xFFFBBF24.toInt(),
        danger = resolveColor(context, com.google.android.material.R.attr.colorError, 0xFFF87171.toInt())
    )

    private fun resolveColor(context: Context, attr: Int, fallback: Int): Int =
        MaterialColors.getColor(context, attr, fallback)
}

data class WorkbenchPalette(
    val bg: Int,
    val surface: Int,
    val surfaceAlt: Int,
    val text: Int,
    val muted: Int,
    val outline: Int,
    val accent: Int,
    val success: Int,
    val warning: Int,
    val danger: Int
)

fun View.divider(color: Int, height: Int = 1): View {
    return View(context).apply {
        setBackgroundColor(color)
        layoutParams = LinearLayout.LayoutParams(-1, height)
    }
}

class AIDevUi(private val activity: Activity, private val prefs: SharedPreferences) {
    val palette: WorkbenchPalette = ThemeManager.getPalette(activity)

    fun dp(value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()

    fun pageRoot(): LinearLayout =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            applyBackground(this)
        }

    fun topBar(title: String, vararg actions: Pair<String, () -> Unit>): MaterialToolbar =
        MaterialToolbar(activity).apply {
            setTitle(title)
            setTitleTextColor(palette.text)
            setBackgroundColor(palette.surface)
            layoutParams = LinearLayout.LayoutParams(-1, dp(DesignTokens.TOP_BAR_HEIGHT))
            if (actions.isNotEmpty()) {
                actions.forEachIndexed { index, (label, _) ->
                    menu.add(Menu.NONE, index, Menu.NONE, label).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                }
                setOnMenuItemClickListener { item ->
                    val idx = item.itemId
                    if (idx in actions.indices) {
                        pulse()
                        actions[idx].second()
                        true
                    } else false
                }
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

    fun statusDot(ok: Boolean): View =
        View(activity).apply {
            val size = dp(8)
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                setMargins(0, 0, dp(DesignTokens.SPACE_4), 0)
            }
            setBackgroundResource(android.R.drawable.btn_default)
            setBackgroundColor(if (ok) palette.success else palette.danger)
        }

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
                    target.performClick()
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

    fun smallButton(label: String, click: () -> Unit): MaterialButton =
        MaterialButton(activity, null, com.google.android.material.R.attr.materialButtonStyle).apply {
            text = label
            minimumWidth = 0
            minimumHeight = 0
            setOnClickListener {
                pulse()
                click()
            }
        }

    fun showAsDialog(contentView: View, onDismiss: (() -> Unit)? = null): Dialog {
        val dialog = Dialog(activity)
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

    private fun applyBackground(root: LinearLayout) {
        when (prefs.getString("bg_mode", "solid")) {
            "image" -> {
                val uri = prefs.getString("bg_image_uri", null)
                val drawable = uri?.let {
                    runCatching {
                        activity.contentResolver.openInputStream(android.net.Uri.parse(it)).use { input ->
                            val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                            BitmapDrawable(activity.resources, BitmapFactory.decodeStream(input, null, opts)).apply {
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
}
