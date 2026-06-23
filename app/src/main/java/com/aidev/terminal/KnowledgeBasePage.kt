package com.aidev.terminal
import com.google.android.material.dialog.MaterialAlertDialogBuilder

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.TextUtils
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject

/**
 * 知识库页面：内置命令速查手册，支持分类浏览和搜索
 */
class KnowledgeBasePage : ShellPage {
    private lateinit var activity: Activity
    private lateinit var ui: AIDevUi
    private lateinit var host: ShellHost
    private lateinit var list: LinearLayout
    private lateinit var searchEdit: EditText
    private var allCategories: List<KnowledgeCategory> = emptyList()
    private var currentFilter: String = ""

    override fun create(activity: Activity, ui: AIDevUi, host: ShellHost): View {
        this.activity = activity
        this.ui = ui
        this.host = host

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }

        // 搜索框
        searchEdit = EditText(activity).apply {
            hint = "搜索命令..."
            setTextColor(ui.palette.text)
            setHintTextColor(ui.palette.muted)
            setBackgroundColor(ui.palette.surfaceAlt)
            setPadding(ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_8), ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_8))
            textSize = DesignTokens.TEXT_BODY
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    currentFilter = s?.toString()?.trim() ?: ""
                    render()
                }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
        }
        root.addView(searchEdit, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_8), ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_4))
        })

        list = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(DesignTokens.SPACE_12), 0, ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_24))
        }

        root.addView(ScrollView(activity).apply { addView(list) }, LinearLayout.LayoutParams(-1, -1))

        loadData()
        render()
        return root
    }

    override fun onSelected(activity: Activity, view: View) {}

    private fun loadData() {
        allCategories = try {
            val json = activity.resources.openRawResource(R.raw.knowledge_base).bufferedReader().use { it.readText() }
            val root = JSONObject(json)
            val cats = root.getJSONArray("categories")
            (0 until cats.length()).map { i ->
                val cat = cats.getJSONObject(i)
                val items = cat.getJSONArray("items")
                KnowledgeCategory(
                    cat.getString("id"),
                    cat.getString("name"),
                    (0 until items.length()).map { j ->
                        val item = items.getJSONObject(j)
                        KnowledgeItem(item.getString("title"), item.getString("cmd"), item.getString("desc"))
                    }
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun render() {
        list.removeAllViews()

        if (allCategories.isEmpty()) {
            list.addView(ui.text("知识库加载失败", DesignTokens.TEXT_H2, ui.palette.text, bold = true))
            return
        }

        val filter = currentFilter.lowercase()
        var totalItems = 0

        allCategories.forEach { category ->
            val items = if (filter.isEmpty()) {
                category.items
            } else {
                category.items.filter {
                    it.title.lowercase().contains(filter) ||
                        it.cmd.lowercase().contains(filter) ||
                        it.desc.lowercase().contains(filter)
                }
            }

            if (items.isEmpty()) return@forEach

            totalItems += items.size

            // 分类标题
            list.addView(ui.text(category.name, DesignTokens.TEXT_H2, ui.palette.accent, bold = true).apply {
                setPadding(0, ui.dp(DesignTokens.SPACE_12), 0, ui.dp(DesignTokens.SPACE_4))
            })

            // 分割线
            list.addView(ui.divider())

            // 条目
            items.forEach { item ->
                list.addView(knowledgeRow(item))
            }
        }

        if (totalItems == 0) {
            list.addView(ui.text("无匹配结果", DesignTokens.TEXT_BODY, ui.palette.muted).apply {
                setPadding(0, ui.dp(DesignTokens.SPACE_16), 0, 0)
            })
        }
    }

    private fun knowledgeRow(item: KnowledgeItem): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(DesignTokens.SPACE_8), ui.dp(DesignTokens.SPACE_8), ui.dp(DesignTokens.SPACE_8), ui.dp(DesignTokens.SPACE_8))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                ui.pulse()
                showItemMenu(item)
            }

            addView(ui.text(item.title, DesignTokens.TEXT_BODY, ui.palette.text, bold = true).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
            addView(ui.text(item.cmd, DesignTokens.TEXT_CAPTION, ui.palette.accent).apply {
                setPadding(0, ui.dp(DesignTokens.SPACE_2), 0, 0)
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            addView(ui.text(item.desc, DesignTokens.TEXT_CAPTION, ui.palette.muted).apply {
                setPadding(0, ui.dp(DesignTokens.SPACE_2), 0, 0)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
        }

    private fun showItemMenu(item: KnowledgeItem) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(item.title)
            .setMessage("${item.cmd}\n\n${item.desc}")
            .setPositiveButton("复制命令") { _, _ ->
                copyText(item.cmd)
                Toast.makeText(activity, "已复制", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("在终端执行") { _, _ ->
                host.openTerminal(item.cmd)
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun copyText(text: String) {
        ClipboardHelper.copy(activity, "AIDev 命令", text)
    }
}

private data class KnowledgeCategory(val id: String, val name: String, val items: List<KnowledgeItem>)
private data class KnowledgeItem(val title: String, val cmd: String, val desc: String)
