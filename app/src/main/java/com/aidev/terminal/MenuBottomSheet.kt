package com.aidev.terminal

import android.app.Activity
import android.widget.LinearLayout

/**
 * 菜单项 BottomSheet：带标题、描述、点击回调的选项列表
 */
class MenuBottomSheet(private val activity: Activity, private val ui: AIDevUi) {
    
    data class MenuItem(val title: String, val desc: String, val action: () -> Unit)
    
    fun show(title: String, items: List<MenuItem>) {
        val sheet = AIDevBottomSheet(activity, ui)
        sheet.show(title) { content ->
            items.forEach { item ->
                content.addView(menuItemView(item) {
                    item.action()
                })
            }
        }
    }
    
    private fun menuItemView(item: MenuItem, onClick: () -> Unit): android.view.View {
        return ui.actionRow(item.title, item.desc) { onClick() }.apply {
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                setMargins(0, 0, 0, ui.dp(DesignTokens.SPACE_4))
            }
        }
    }
}
