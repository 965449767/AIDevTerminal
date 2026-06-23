package com.aidev.terminal

import android.app.Activity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * BottomSheet 菜单容器，基于 Material 3 BottomSheetDialog。
 */
class AIDevBottomSheet(private val activity: Activity, private val ui: AIDevUi) {
    private var dialog: BottomSheetDialog? = null

    fun show(title: String, builder: (LinearLayout) -> Unit) {
        dismiss()

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(DesignTokens.SPACE_16), ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_16), ui.dp(DesignTokens.SPACE_24))
        }

        val titleRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, ui.dp(DesignTokens.SPACE_12))
        }
        titleRow.addView(ui.text(title, DesignTokens.TEXT_H2, ui.palette.text, bold = true).apply {
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        })
        titleRow.addView(ui.text("✕", DesignTokens.TEXT_H2, ui.palette.muted).apply {
            setPadding(ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_4), ui.dp(DesignTokens.SPACE_4), ui.dp(DesignTokens.SPACE_4))
            setOnClickListener { dismiss() }
        })
        root.addView(titleRow)

        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, ui.dp(DesignTokens.SPACE_4), 0, 0)
        }
        builder(content)
        root.addView(ScrollView(activity).apply { addView(content) }, LinearLayout.LayoutParams(-1, -2))

        dialog = BottomSheetDialog(activity).apply {
            setContentView(root)
            show()
        }
    }

    fun dismiss() {
        dialog?.dismiss()
        dialog = null
    }
}
