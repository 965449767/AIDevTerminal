package com.aidev.terminal

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.webkit.WebView
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.NestedScrollView

class PreviewManager(
    private val activity: Activity,
    private val ui: AIDevUi,
    private val container: FrameLayout
) {
    lateinit var panel: LinearLayout
    lateinit var nameView: TextView
    lateinit var infoView: TextView
    lateinit var contentFrame: FrameLayout
    lateinit var editText: EditText
    lateinit var scrollView: NestedScrollView
    lateinit var plainText: TextView
    lateinit var webView: WebView
    lateinit var imageView: ImageView
    lateinit var navPrev: View
    lateinit var navNext: View

    var tabRow: LinearLayout? = null
    var infoPanel: LinearLayout? = null
    var permsPanel: LinearLayout? = null
    var dirtyDot: View? = null
    var htmlToggle: View? = null
    var editToggle: View? = null
    var saveBtn: View? = null
    var tabIndex: Int = 0

    var imageBitmap: Bitmap? = null
    var imageWidth: Int = 0
    var imageHeight: Int = 0
    var fileList: List<java.io.File>? = null

    var onNavigate: ((Int) -> Unit)? = null
    var onClose: (() -> Unit)? = null

    private fun paneHeaderBg(): GradientDrawable =
        GradientDrawable().apply {
            setColor(0x1A7C3AED)
            cornerRadius = ui.dp(10).toFloat()
            setStroke(ui.dp(1), ui.palette.outline)
        }

    private fun actionBtn(label: String, click: () -> Unit): View =
        TextView(activity).apply {
            text = label
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(ui.palette.text)
            includeFontPadding = false
            setPadding(ui.dp(12), 0, ui.dp(12), 0)
            background = GradientDrawable().apply {
                setColor(0xFF1C2430.toInt())
                cornerRadius = ui.dp(9).toFloat()
                setStroke(ui.dp(1), 0xFF334155.toInt())
            }
            layoutParams = LinearLayout.LayoutParams(-2, ui.dp(34)).apply {
                setMargins(ui.dp(3), 0, ui.dp(3), 0)
            }
            setOnClickListener { ui.pulse(); click() }
        }

    fun build(
        editTextWatcher: TextWatcher,
        onSwitchTab: (Int) -> Unit
    ) {
        panel = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(4), 0, ui.dp(4), 0)
            visibility = View.GONE
            val c = (ui.palette.surface and 0x00FFFFFF) or 0xEE000000.toInt()
            setBackgroundColor(c)
            setOnTouchListener { _, _ -> true }
        }
        container.addView(panel, FrameLayout.LayoutParams(-1, -1).apply {
            leftMargin = ui.dp(60)
        })

        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(ui.dp(6), ui.dp(6), ui.dp(6), ui.dp(6))
            background = paneHeaderBg()
        }
        navPrev = ui.text("\u25C0", 16f, ui.palette.accent, bold = true).apply {
            gravity = Gravity.CENTER
            setPadding(ui.dp(8), ui.dp(4), ui.dp(8), ui.dp(4))
            setOnClickListener { onNavigate?.invoke(-1) }
        }
        navNext = ui.text("\u25B6", 16f, ui.palette.accent, bold = true).apply {
            gravity = Gravity.CENTER
            setPadding(ui.dp(8), ui.dp(4), ui.dp(8), ui.dp(4))
            setOnClickListener { onNavigate?.invoke(1) }
        }
        val closeBtn = ui.text("\u2715", 16f, ui.palette.accent).apply {
            gravity = Gravity.CENTER
            setPadding(ui.dp(8), ui.dp(4), ui.dp(8), ui.dp(4))
            setOnClickListener { onClose?.invoke() }
        }
        val nameLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, ui.dp(8), 0)
        }
        nameView = ui.text("", 13f, ui.palette.accent, bold = true).apply {
            setPadding(0, 0, 0, ui.dp(2))
        }
        infoView = ui.text("", 11f, ui.palette.muted)
        nameLayout.addView(nameView, LinearLayout.LayoutParams(-1, -2))
        nameLayout.addView(infoView, LinearLayout.LayoutParams(-1, -2))
        header.addView(navPrev, LinearLayout.LayoutParams(-2, -2))
        header.addView(nameLayout, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(navNext, LinearLayout.LayoutParams(-2, -2))
        header.addView(closeBtn, LinearLayout.LayoutParams(-2, -2))
        panel.addView(header, LinearLayout.LayoutParams(-1, -2))

        tabRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(ui.dp(4), 0, ui.dp(4), 0)
            val labels = listOf("内容", "信息", "权限")
            for (i in labels.indices) {
                addView(ui.text(labels[i], 12f, ui.palette.text).apply {
                    gravity = Gravity.CENTER
                    setPadding(ui.dp(12), ui.dp(6), ui.dp(12), ui.dp(6))
                    layoutParams = LinearLayout.LayoutParams(0, ui.dp(34), 1f).apply {
                        setMargins(ui.dp(2), 0, ui.dp(2), 0)
                    }
                    background = GradientDrawable().apply {
                        setColor(0x1A7C3AED.toInt())
                        cornerRadius = ui.dp(4).toFloat()
                    }
                    setOnClickListener { onSwitchTab(i) }
                })
            }
        }
        panel.addView(tabRow, LinearLayout.LayoutParams(-1, -2))

        contentFrame = FrameLayout(activity).apply {
            setPadding(0, ui.dp(4), 0, ui.dp(4))
        }
        editText = EditText(activity).apply {
            isFocusable = false
            isClickable = true
            setTextColor(ui.palette.text)
            setHintTextColor(ui.palette.muted)
            setBackgroundColor(ui.palette.surfaceAlt)
            setPadding(ui.dp(6), ui.dp(6), ui.dp(6), ui.dp(6))
            textSize = 12f
            typeface = android.graphics.Typeface.MONOSPACE
            gravity = Gravity.TOP
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            addTextChangedListener(editTextWatcher)
        }
        contentFrame.addView(editText)
        plainText = TextView(activity).apply {
            setTextColor(ui.palette.text)
            setBackgroundColor(ui.palette.surfaceAlt)
            setPadding(ui.dp(6), ui.dp(6), ui.dp(6), ui.dp(6))
            textSize = 12f
            typeface = android.graphics.Typeface.MONOSPACE
            gravity = Gravity.TOP
        }
        scrollView = NestedScrollView(activity).apply {
            addView(plainText)
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            visibility = View.GONE
        }
        contentFrame.addView(scrollView)
        webView = WebView(activity).apply {
            settings.javaScriptEnabled = true
            settings.allowContentAccess = false
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            settings.allowFileAccess = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.builtInZoomControls = true
            settings.setSupportZoom(true)
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            visibility = View.GONE
        }
        contentFrame.addView(webView)
        imageView = ImageView(activity).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            visibility = View.GONE
        }
        contentFrame.addView(imageView)
        panel.addView(contentFrame, LinearLayout.LayoutParams(-1, 0, 1f))

        infoPanel = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8))
            visibility = View.GONE
        }
        panel.addView(infoPanel, LinearLayout.LayoutParams(-1, 0, 1f))

        permsPanel = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8))
            visibility = View.GONE
        }
        panel.addView(permsPanel, LinearLayout.LayoutParams(-1, 0, 1f))

        val bottomBar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(ui.dp(4), ui.dp(2), ui.dp(4), ui.dp(2))
            setBackgroundColor(ui.palette.surface)
        }
        htmlToggle = actionBtn("源码") {}
        editToggle = actionBtn("编辑") {}
        saveBtn = actionBtn("保存") {}
        dirtyDot = View(activity).apply {
            setBackgroundColor(0xFFFFD700.toInt())
            val s = ui.dp(8)
            layoutParams = LinearLayout.LayoutParams(s, s)
            (layoutParams as LinearLayout.LayoutParams).setMargins(0, 0, ui.dp(2), 0)
            visibility = View.GONE
        }
        bottomBar.addView(htmlToggle)
        bottomBar.addView(editToggle)
        bottomBar.addView(dirtyDot)
        bottomBar.addView(saveBtn)
        panel.addView(bottomBar, LinearLayout.LayoutParams(-1, -2))
    }

    fun show(animate: Boolean) {
        panel.visibility = View.VISIBLE
        if (animate) {
            panel.translationX = (container.width.coerceAtLeast(2000)).toFloat()
            panel.animate().translationX(0f).setDuration(250)
                .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
        } else {
            panel.translationX = 0f
            panel.animate().cancel()
        }
    }

    fun hide(onHidden: () -> Unit) {
        panel.animate().cancel()
        panel.animate().translationX(container.width.coerceAtLeast(2000).toFloat()).setDuration(200)
            .setInterpolator(android.view.animation.AccelerateDecelerateInterpolator())
            .withEndAction {
                panel.visibility = View.GONE
                panel.translationX = 0f
                onHidden()
            }
            .start()
    }

    fun resetViews() {
        editText.visibility = View.GONE
        scrollView.visibility = View.GONE
        webView.visibility = View.GONE
        imageView.visibility = View.GONE
        infoPanel?.removeAllViews()
        permsPanel?.removeAllViews()
        imageBitmap?.let { it.recycle(); imageBitmap = null }
        imageView.setImageBitmap(null)
        imageWidth = 0
        imageHeight = 0
    }

    fun isVisible(): Boolean =
        panel.visibility == View.VISIBLE && panel.translationX == 0f

    fun switchTab(index: Int) {
        tabIndex = index
        val row = tabRow
        for (i in 0 until (row?.childCount ?: 0)) {
            val tab = row?.getChildAt(i) as? TextView ?: continue
            val active = i == index
            tab.setTextColor(if (active) ui.palette.accent else ui.palette.text)
            tab.setBackgroundColor(if (active) 0x337C3AED.toInt() else 0x1A7C3AED.toInt())
        }
        val showContent = index == 0
        val showInfo = index == 1
        val showPerms = index == 2
        contentFrame.visibility = if (showContent) View.VISIBLE else View.GONE
        infoPanel?.visibility = if (showInfo) View.VISIBLE else View.GONE
        permsPanel?.visibility = if (showPerms) View.VISIBLE else View.GONE
    }

    fun populateInfoTab(file: java.io.File, extra: String = "") {
        val panel = infoPanel ?: return
        panel.removeAllViews()
        val dims = if (imageWidth > 0 && imageHeight > 0) " · ${imageWidth}×${imageHeight}" else ""
        val info = listOf(
            "文件名" to file.name,
            "路径" to file.absolutePath,
            "大小" to com.aidev.terminal.FileUtils.formatSize(file.length()),
            "修改时间" to java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(file.lastModified()),
            "类型" to (file.extension.ifBlank { "未知" }) + dims
        )
        for ((k, v) in info) {
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, ui.dp(4), 0, ui.dp(4))
                addView(ui.text("$k：", 12f, ui.palette.muted).apply {
                    layoutParams = LinearLayout.LayoutParams(ui.dp(80), -2)
                })
                addView(ui.text(v, 12f, ui.palette.text).apply {
                    layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                })
            }
            panel.addView(row)
        }
        if (extra.isNotEmpty()) {
            panel.addView(ui.text(extra, 11f, ui.palette.muted).apply {
                setPadding(0, ui.dp(8), 0, 0)
            })
        }
        panel.visibility = View.VISIBLE
    }

    fun populatePermsTab(file: java.io.File) {
        val panel = permsPanel ?: return
        panel.removeAllViews()
        val rwx = StringBuilder()
        rwx.append(if (file.canRead()) "r" else "-")
        rwx.append(if (file.canWrite()) "w" else "-")
        rwx.append(if (file.canExecute()) "x" else "-")
        val type = if (file.isDirectory) "d" else "-"
        panel.addView(ui.text("权限：$type$rwx", 12f, ui.palette.text).apply {
            setPadding(0, ui.dp(4), 0, ui.dp(4))
        })
        panel.addView(ui.text("可读：${if (file.canRead()) "是" else "否"}", 12f, ui.palette.muted))
        panel.addView(ui.text("可写：${if (file.canWrite()) "是" else "否"}", 12f, ui.palette.muted))
        panel.addView(ui.text("可执行：${if (file.canExecute()) "是" else "否"}", 12f, ui.palette.muted))
        panel.visibility = View.VISIBLE
    }

    fun setNavAlpha(prev: Float, next: Float) {
        navPrev.alpha = prev
        navNext.alpha = next
    }
}
