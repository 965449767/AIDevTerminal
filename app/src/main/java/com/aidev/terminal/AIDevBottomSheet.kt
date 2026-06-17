package com.aidev.terminal

import android.app.Activity
import android.app.Dialog
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.abs

/**
 * 自定义 BottomSheetDialog：从底部滑出的全屏对话框
 * 不使用 AndroidX Material，纯自定义实现
 */
class AIDevBottomSheet(private val activity: Activity, private val ui: AIDevUi) {
    private var dialog: Dialog? = null
    private var contentView: LinearLayout? = null
    
    fun show(title: String, builder: (LinearLayout) -> Unit) {
        dismiss()
        
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ui.palette.surface)
            setPadding(ui.dp(DesignTokens.SPACE_16), ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_16), ui.dp(DesignTokens.SPACE_24))
        }
        
        // 顶部拖拽指示条
        root.addView(View(activity).apply {
            val width = ui.dp(36)
            val height = ui.dp(4)
            layoutParams = LinearLayout.LayoutParams(width, height).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setMargins(0, 0, 0, ui.dp(DesignTokens.SPACE_12))
            }
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = height / 2f
                setColor(ui.palette.outline)
            }
        })
        
        // 标题栏
        val titleRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, ui.dp(DesignTokens.SPACE_12))
        }
        titleRow.addView(ui.text(title, DesignTokens.TEXT_H2, ui.palette.text, bold = true).apply {
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        })
        // 关闭按钮
        titleRow.addView(ui.text("✕", DesignTokens.TEXT_H2, ui.palette.muted).apply {
            setPadding(ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_4), ui.dp(DesignTokens.SPACE_4), ui.dp(DesignTokens.SPACE_4))
            setOnClickListener { dismiss() }
        })
        root.addView(titleRow)
        
        // 分割线
        root.addView(ui.divider())
        
        // 内容区（ScrollView 包裹）
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, ui.dp(DesignTokens.SPACE_4), 0, 0)
        }
        builder(content)
        root.addView(ScrollView(activity).apply { addView(content) }, LinearLayout.LayoutParams(-1, -1))
        
        contentView = root
        
        dialog = Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar).apply {
            setContentView(root, ViewGroup.LayoutParams(-1, -1))
            window?.apply {
                setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                setGravity(Gravity.BOTTOM)
                // 背景半透明遮罩
                setBackgroundDrawable(android.graphics.drawable.ColorDrawable(0xCC000000.toInt()))
                // 点击背景关闭
                decorView.setOnClickListener { dismiss() }
            }
            // 内容区点击不关闭
            root.setOnClickListener { /* consume */ }
            // 下滑手势关闭
            attachSwipeDismiss(root)
            show()
        }
    }
    
    fun dismiss() {
        dialog?.dismiss()
        dialog = null
        contentView = null
    }
    
    private fun attachSwipeDismiss(view: View) {
        var downY = 0f
        var isDragging = false
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downY = event.rawY
                    isDragging = false
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = event.rawY - downY
                    if (dy > ui.dp(24)) {
                        isDragging = true
                    }
                    false
                }
                MotionEvent.ACTION_UP -> {
                    val dy = event.rawY - downY
                    if (isDragging && dy > ui.dp(80)) {
                        dismiss()
                        true
                    } else {
                        false
                    }
                }
                else -> false
            }
        }
    }
}
