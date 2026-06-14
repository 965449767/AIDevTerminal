package com.aidev.terminal

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

class ThemeCenterActivity : Activity() {
    private lateinit var ui: AIDevUi

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        rebuild()
    }

    override fun onResume() {
        super.onResume()
        rebuild()
    }

    @Deprecated("使用简单结果回调，保持当前项目结构轻量")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PICK_BG && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            runCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            prefs().edit()
                .putString("bg_mode", "image")
                .putString("bg_image_uri", uri.toString())
                .apply()
            Toast.makeText(this, "自定义背景已应用", Toast.LENGTH_SHORT).show()
            rebuild()
        }
    }

    private fun rebuild() {
        ui = AIDevUi(this, prefs())
        val root = ui.pageRoot()
        AppNav.attach(this, ui, root, ThemeCenterActivity::class.java)
        root.addView(ui.topBar("主题中心", "设置" to { startActivity(Intent(this, SettingsActivity::class.java)) }, "关闭" to { finish() }))

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(18), ui.dp(12), ui.dp(18), ui.dp(24))
            addView(ui.section("预览", "主题、背景、透明和模糊的关系必须明确可见"))
            addView(previewCard())
            addView(ui.section("主题", "选择整体色板，不再把颜色写死在各个页面里"))
            addView(ui.rowOf(
                ui.actionCard("主题预设", currentThemeLabel(), "THEME") { chooseTheme() },
                ui.actionCard("背景模式", currentBgLabel(), "BG") { chooseBgMode() }
            ))
            addView(ui.rowOf(
                ui.actionCard("选择背景图", "从系统文件选择图片", "IMAGE") { chooseImage() },
                ui.actionCard("清除背景图", "恢复主题背景", "CLEAR") { clearImage() }
            ))
            addView(ui.section("效果", "透明与模糊不是所有背景下都一样明显"))
            addView(ui.rowOf(
                ui.actionCard("界面透明度", "${prefs().getInt("ui_alpha", 94)}%", "ALPHA") {
                    percentDialog("界面透明度", "ui_alpha", 94, 72, 100)
                },
                ui.actionCard("背景模糊感", "${prefs().getInt("ui_blur", 18)}%", "BLUR") {
                    percentDialog("背景模糊感", "ui_blur", 18, 0, 40)
                }
            ))
            addView(ui.rowOf(
                ui.actionCard("界面尺寸", "${prefs().getInt("ui_density", 100)}%", "SIZE") {
                    percentDialog("界面尺寸", "ui_density", 100, 86, 116)
                },
                ui.actionCard("风格说明", "圆角、间距、导航高度统一由设计系统控制", "STYLE") {
                    Toast.makeText(this@ThemeCenterActivity, "界面尺寸会影响卡片间距、顶部栏、底部栏和内容留白", Toast.LENGTH_LONG).show()
                }
            ))
            addView(noticeCard())
            addView(ui.section("移动交互", "顶级页面支持左右滑动切换，点击仍保留触觉反馈"))
            addView(ui.actionCard("手势说明", "左滑进入下一个页面，右滑返回上一个页面；列表滚动区域优先保持滚动。", "GESTURE") {
                Toast.makeText(this@ThemeCenterActivity, "已启用顶级页面滑动导航", Toast.LENGTH_SHORT).show()
            })
        }

        root.addView(ScrollView(this).apply { addView(content) }, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(ui.bottomNav(AppNav.bottom(this)))
        setContentView(root)
    }

    private fun previewCard() =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(18), ui.dp(18), ui.dp(18), ui.dp(18))
            background = ui.card(hero = true)
            addView(ui.text("AIDev Workstation", 24f, android.graphics.Color.WHITE, bold = true))
            addView(ui.heroSubtitle("当前主题：${currentThemeLabel()} · 背景：${currentBgLabel()}"))
            addView(ui.heroMeta("透明度 ${prefs().getInt("ui_alpha", 94)}% · 模糊感 ${prefs().getInt("ui_blur", 18)}%"))
        }

    private fun noticeCard() =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12))
            background = ui.infoPanelBackground()
            addView(ui.text("效果说明", 16f, ui.palette.text, bold = true))
            addView(ui.muted(ui.effectNotice()).apply { setPadding(0, ui.dp(8), 0, 0) })
        }

    private fun chooseTheme() {
        val labels = arrayOf("深空蓝", "矩阵绿", "紫色专业", "亮色", "跟随系统动态")
        val values = arrayOf("midnight", "matrix", "violet", "light", "dynamic")
        val current = values.indexOf(prefs().getString("theme_preset", "midnight")).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("主题预设")
            .setSingleChoiceItems(labels, current) { d, which ->
                prefs().edit().putString("theme_preset", values[which]).apply()
                d.dismiss()
                rebuild()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun chooseBgMode() {
        val labels = arrayOf("纯色背景", "主题渐变", "自定义图片")
        val values = arrayOf("solid", "gradient", "image")
        val current = values.indexOf(prefs().getString("bg_mode", "solid")).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("背景模式")
            .setSingleChoiceItems(labels, current) { d, which ->
                prefs().edit().putString("bg_mode", values[which]).apply()
                d.dismiss()
                rebuild()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun chooseImage() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        runCatching { startActivityForResult(intent, REQ_PICK_BG) }
            .onFailure {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
    }

    private fun clearImage() {
        prefs().edit().remove("bg_image_uri").putString("bg_mode", "solid").apply()
        Toast.makeText(this, "已清除自定义背景", Toast.LENGTH_SHORT).show()
        rebuild()
    }

    private fun percentDialog(title: String, key: String, defaultValue: Int, min: Int, max: Int) {
        val current = prefs().getInt(key, defaultValue).coerceIn(min, max)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(20), ui.dp(8), ui.dp(20), 0)
        }
        val value = TextView(this).apply {
            text = "$current%"
            textSize = 18f
            setTextColor(ui.palette.text)
        }
        val seek = SeekBar(this).apply {
            this.max = max - min
            progress = current - min
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    value.text = "${progress + min}%"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        box.addView(value)
        box.addView(seek)
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(box)
            .setPositiveButton("保存") { _, _ ->
                prefs().edit().putInt(key, seek.progress + min).apply()
                rebuild()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun currentThemeLabel(): String =
        when (prefs().getString("theme_preset", "midnight")) {
            "matrix" -> "矩阵绿"
            "violet" -> "紫色专业"
            "light" -> "亮色"
            "dynamic" -> "跟随系统动态"
            else -> "深空蓝"
        }

    private fun currentBgLabel(): String =
        when (prefs().getString("bg_mode", "solid")) {
            "gradient" -> "主题渐变"
            "image" -> if (prefs().getString("bg_image_uri", null).isNullOrBlank()) "自定义图片（未选择）" else "自定义图片"
            else -> "纯色背景"
        }

    private fun prefs() = getSharedPreferences("aidev_ui", MODE_PRIVATE)

    companion object {
        private const val REQ_PICK_BG = 6101
    }
}
