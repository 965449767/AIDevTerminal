package com.aidev.terminal

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.graphics.drawable.GradientDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.BackgroundColorSpan
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject

class KnowledgeBasePage : ShellPage {
    private lateinit var activity: Activity
    private lateinit var ui: AIDevUi
    private lateinit var host: ShellHost
    private lateinit var list: LinearLayout
    private lateinit var searchEdit: EditText
    private lateinit var chipContainer: LinearLayout
    private lateinit var chipsRow: LinearLayout
    private var allCategories: List<KnowledgeCategory> = emptyList()
    private var currentFilter: String = ""
    private var selectedCategoryId: String? = null
    private val searchHandler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null

    override fun create(activity: Activity, ui: AIDevUi, host: ShellHost): View {
        this.activity = activity
        this.ui = ui
        this.host = host

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }

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
                    searchRunnable?.let { searchHandler.removeCallbacks(it) }
                    searchRunnable = Runnable { render() }
                    searchHandler.postDelayed(searchRunnable!!, 300)
                }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
        }
        root.addView(searchEdit, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_8), ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_4))
        })

        chipContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        root.addView(chipContainer, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(ui.dp(DesignTokens.SPACE_12), 0, ui.dp(DesignTokens.SPACE_12), ui.dp(6))
        })

        chipContainer.addView(categoryChip("全部", true) {
            selectedCategoryId = null
            render()
        })

        chipsRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        chipContainer.addView(HorizontalScrollView(activity).apply {
            addView(chipsRow)
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
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
                        KnowledgeItem(
                            item.getString("title"),
                            item.getString("cmd"),
                            item.getString("desc"),
                            tags = item.optJSONArray("tags")?.let { arr ->
                                (0 until arr.length()).map { arr.getString(it) }
                            } ?: emptyList(),
                            usage = item.optString("usage", ""),
                            permissions = item.optString("permissions", ""),
                            notes = item.optString("notes", "")
                        )
                    }
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun render() {
        buildChips()
        list.removeAllViews()

        if (allCategories.isEmpty()) {
            list.addView(ui.text("知识库加载失败", DesignTokens.TEXT_H2, ui.palette.text, bold = true))
            return
        }

        val filter = currentFilter.lowercase()
        var totalItems = 0

        allCategories.forEach { category ->
            if (selectedCategoryId != null && category.id != selectedCategoryId) return@forEach

            val items = if (filter.isEmpty()) {
                category.items
            } else {
                category.items.filter {
                    it.title.lowercase().contains(filter) ||
                        it.cmd.lowercase().contains(filter) ||
                        it.desc.lowercase().contains(filter) ||
                        it.tags.any { tag -> tag.lowercase().contains(filter) }
                }
            }

            if (items.isEmpty()) return@forEach
            totalItems += items.size

            list.addView(ui.text(category.name, DesignTokens.TEXT_H2, ui.palette.accent, bold = true).apply {
                setPadding(0, ui.dp(6), 0, ui.dp(DesignTokens.SPACE_4))
            })
            list.addView(ui.divider())

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

    private fun buildChips() {
        val allChip = chipContainer.getChildAt(0) as? TextView
        if (allChip != null) {
            val selected = selectedCategoryId == null
            allChip.setTextColor(if (selected) ui.palette.bg else ui.palette.muted)
            (allChip.background as? GradientDrawable)?.setColor(
                if (selected) ui.palette.accent else ui.palette.surfaceAlt
            )
            allChip.setTypeface(allChip.typeface, if (selected) Typeface.BOLD else Typeface.NORMAL)
            allChip.setOnClickListener {
                selectedCategoryId = null
                render()
            }
        }

        chipsRow.removeAllViews()
        allCategories.forEach { cat ->
            chipsRow.addView(categoryChip(cat.name, selectedCategoryId == cat.id) {
                selectedCategoryId = cat.id
                render()
            })
        }
    }

    private fun categoryChip(label: String, selected: Boolean, onClick: () -> Unit): TextView =
        TextView(activity).apply {
            text = label
            textSize = DesignTokens.TEXT_CAPTION
            setTextColor(if (selected) ui.palette.bg else ui.palette.muted)
            setSingleLine(true)
            val bg = GradientDrawable().apply {
                setColor(if (selected) ui.palette.accent else ui.palette.surfaceAlt)
                cornerRadius = ui.dp(DesignTokens.RADIUS_SM).toFloat()
            }
            background = bg
            setPadding(ui.dp(DesignTokens.SPACE_8), ui.dp(DesignTokens.SPACE_4), ui.dp(DesignTokens.SPACE_8), ui.dp(DesignTokens.SPACE_4))
            setTypeface(typeface, if (selected) Typeface.BOLD else Typeface.NORMAL)
            setOnClickListener {
                ui.pulse()
                onClick()
            }
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply {
                setMargins(0, 0, ui.dp(6), 0)
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

            val titleView = ui.text(item.title, DesignTokens.TEXT_BODY, ui.palette.text, bold = true).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            if (currentFilter.isNotEmpty()) titleView.text = highlightText(item.title, currentFilter)
            addView(titleView)

            val cmdRow = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, ui.dp(DesignTokens.SPACE_2), 0, 0)
            }
            val cmdView = ui.text(item.cmd, DesignTokens.TEXT_CAPTION, ui.palette.accent).apply {
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setTypeface(typeface, Typeface.BOLD)
            }
            if (currentFilter.isNotEmpty()) cmdView.text = highlightText(item.cmd, currentFilter)
            cmdRow.addView(cmdView)
            cmdRow.addView(TextView(activity).apply {
                text = "复制"
                textSize = DesignTokens.TEXT_LABEL
                setTextColor(ui.palette.muted)
                setPadding(ui.dp(DesignTokens.SPACE_4), 0, 0, 0)
                gravity = Gravity.CENTER_VERTICAL
                setOnClickListener {
                    copyText(item.cmd)
                    Toast.makeText(activity, "已复制", Toast.LENGTH_SHORT).show()
                }
            })
            addView(cmdRow)

            val descView = ui.text(item.desc, DesignTokens.TEXT_CAPTION, ui.palette.muted).apply {
                setPadding(0, ui.dp(DesignTokens.SPACE_2), 0, 0)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            if (currentFilter.isNotEmpty()) descView.text = highlightText(item.desc, currentFilter)
            addView(descView)

            if (item.tags.isNotEmpty()) {
                addView(tagRow(item.tags))
            }
        }

    private fun tagRow(tags: List<String>): LinearLayout =
        LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, ui.dp(DesignTokens.SPACE_4), 0, 0)
            tags.forEach { tag ->
                addView(TextView(activity).apply {
                    text = tag
                    textSize = DesignTokens.TEXT_CAPTION
                    setTextColor(ui.palette.muted)
                    val bg = GradientDrawable().apply {
                        setColor(ui.palette.surfaceAlt)
                        cornerRadius = ui.dp(6).toFloat()
                    }
                    background = bg
                    setPadding(ui.dp(6), ui.dp(1), ui.dp(6), ui.dp(1))
                    setOnClickListener {
                        ui.pulse()
                        searchEdit.setText(tag)
                        searchEdit.setSelection(tag.length)
                    }
                }, LinearLayout.LayoutParams(-2, -2).apply {
                    setMargins(0, 0, ui.dp(DesignTokens.SPACE_4), 0)
                })
            }
        }

    private fun highlightText(text: String, query: String): SpannableString {
        val ss = SpannableString(text)
        if (query.isEmpty()) return ss
        val lowerText = text.lowercase()
        val lowerQuery = query.lowercase()
        var start = 0
        val highlightColor = Color.argb(
            48, Color.red(ui.palette.accent), Color.green(ui.palette.accent), Color.blue(ui.palette.accent)
        )
        while (true) {
            val index = lowerText.indexOf(lowerQuery, start)
            if (index < 0) break
            ss.setSpan(BackgroundColorSpan(highlightColor), index, index + query.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            start = index + query.length
        }
        return ss
    }

    private fun showItemMenu(item: KnowledgeItem) {
        val sheet = AIDevBottomSheet(activity, ui)
        sheet.show(item.title) { content ->
            val cmdBg = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_8), ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_8))
                background = GradientDrawable().apply {
                    setColor(ui.palette.surfaceAlt)
                    cornerRadius = ui.dp(DesignTokens.RADIUS_SM).toFloat()
                }
            }
            cmdBg.addView(ui.text(item.cmd, DesignTokens.TEXT_BODY, ui.palette.accent, bold = true).apply {
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                typeface = Typeface.MONOSPACE
            })
            cmdBg.addView(TextView(activity).apply {
                text = "复制"
                textSize = DesignTokens.TEXT_LABEL
                setTextColor(ui.palette.accent)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(ui.dp(DesignTokens.SPACE_8), 0, 0, 0)
                setOnClickListener {
                    copyText(item.cmd)
                    Toast.makeText(activity, "已复制", Toast.LENGTH_SHORT).show()
                }
            })
            content.addView(cmdBg)

            if (item.tags.isNotEmpty()) {
                val tagsLayout = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, ui.dp(DesignTokens.SPACE_8), 0, 0)
                }
                item.tags.forEach { tag ->
                    tagsLayout.addView(TextView(activity).apply {
                        text = tag
                        textSize = DesignTokens.TEXT_CAPTION
                        setTextColor(ui.palette.muted)
                        val bg = GradientDrawable().apply {
                            setColor(ui.palette.surfaceAlt)
                            cornerRadius = ui.dp(6).toFloat()
                        }
                        background = bg
                        setPadding(ui.dp(DesignTokens.SPACE_8), ui.dp(DesignTokens.SPACE_2), ui.dp(DesignTokens.SPACE_8), ui.dp(DesignTokens.SPACE_2))
                        setOnClickListener {
                            ui.pulse()
                            searchEdit.setText(tag)
                            searchEdit.setSelection(tag.length)
                            sheet.dismiss()
                        }
                    }, LinearLayout.LayoutParams(-2, -2).apply {
                        setMargins(0, 0, ui.dp(DesignTokens.SPACE_4), 0)
                    })
                }
                content.addView(tagsLayout)
            }

            content.addView(ui.text(item.desc, DesignTokens.TEXT_BODY, ui.palette.text).apply {
                setPadding(0, ui.dp(DesignTokens.SPACE_8), 0, 0)
            })

            if (item.usage.isNotEmpty()) {
                content.addView(sectionHeader("用法示例"))
                item.usage.split("\n").forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isEmpty()) return@forEach
                    val commentIdx = trimmed.indexOf("  # ")
                    val cmdPart = if (commentIdx >= 0) trimmed.substring(0, commentIdx).trim() else trimmed
                    val explPart = if (commentIdx >= 0) trimmed.substring(commentIdx + 4).trim() else ""

                    val usageRow = LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(0, ui.dp(6), 0, 0)
                    }

                    val codeLine = LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(ui.dp(DesignTokens.SPACE_8), ui.dp(6), ui.dp(DesignTokens.SPACE_8), ui.dp(6))
                        background = GradientDrawable().apply {
                            setColor(ui.palette.surfaceAlt)
                            cornerRadius = ui.dp(DesignTokens.RADIUS_SM).toFloat()
                        }
                    }
                    codeLine.addView(TextView(activity).apply {
                        text = cmdPart
                        textSize = DesignTokens.TEXT_CAPTION
                        setTextColor(ui.palette.accent)
                        typeface = Typeface.MONOSPACE
                        layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                    })
                    codeLine.addView(TextView(activity).apply {
                        text = "复制"
                        textSize = DesignTokens.TEXT_LABEL
                        setTextColor(ui.palette.muted)
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(ui.dp(DesignTokens.SPACE_4), 0, 0, 0)
                        setOnClickListener {
                            copyText(cmdPart)
                            Toast.makeText(activity, "已复制", Toast.LENGTH_SHORT).show()
                        }
                    })
                    usageRow.addView(codeLine)

                    if (explPart.isNotEmpty()) {
                        usageRow.addView(ui.text(explPart, DesignTokens.TEXT_CAPTION, ui.palette.muted).apply {
                            setPadding(ui.dp(DesignTokens.SPACE_4), ui.dp(DesignTokens.SPACE_2), 0, 0)
                        })
                    }

                    content.addView(usageRow)
                }
            }

            if (item.permissions.isNotEmpty()) {
                content.addView(sectionHeader("所需权限"))
                content.addView(ui.text(item.permissions, DesignTokens.TEXT_CAPTION, ui.palette.text).apply {
                    setPadding(ui.dp(DesignTokens.SPACE_8), ui.dp(DesignTokens.SPACE_4), 0, 0)
                })
            }

            if (item.notes.isNotEmpty()) {
                content.addView(sectionHeader("注意事项"))
                content.addView(ui.text(item.notes, DesignTokens.TEXT_CAPTION, ui.palette.text).apply {
                    setPadding(ui.dp(DesignTokens.SPACE_8), ui.dp(DesignTokens.SPACE_4), 0, 0)
                })
            }

            val btnRow = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, ui.dp(DesignTokens.SPACE_16), 0, 0)
            }
            btnRow.addView(ui.smallButton("在终端执行") {
                host.openTerminal(item.cmd)
                sheet.dismiss()
            }.apply {
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply {
                    setMargins(0, 0, ui.dp(6), 0)
                }
            })
            btnRow.addView(ui.smallButton("复制命令") {
                copyText(item.cmd)
                Toast.makeText(activity, "已复制", Toast.LENGTH_SHORT).show()
            }.apply {
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply {
                    setMargins(ui.dp(6), 0, 0, 0)
                }
            })
            content.addView(btnRow)
        }
    }

    private fun sectionHeader(text: String): TextView =
        TextView(activity).apply {
            this.text = text
            textSize = DesignTokens.TEXT_CAPTION
            setTextColor(ui.palette.accent)
            setPadding(0, ui.dp(DesignTokens.SPACE_12), 0, ui.dp(DesignTokens.SPACE_2))
            setTypeface(typeface, Typeface.BOLD)
        }

    private fun copyText(text: String) {
        ClipboardHelper.copy(activity, "AIDev 命令", text)
    }
}

private data class KnowledgeCategory(val id: String, val name: String, val items: List<KnowledgeItem>)
private data class KnowledgeItem(
    val title: String,
    val cmd: String,
    val desc: String,
    val tags: List<String> = emptyList(),
    val usage: String = "",
    val permissions: String = "",
    val notes: String = ""
)
