package com.aidev.terminal

import android.app.Activity
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

internal class VirtualKeyEditor(
    private val onKeysChanged: () -> Unit
) {

    fun show(activity: Activity, key: EmbeddedVirtualKey) {
        val aliases = parseKeyAliases(
            activity.getSharedPreferences("aidev_ui", Activity.MODE_PRIVATE)
                .getString("terminal_key_aliases", "") ?: ""
        )

        val dp = { v: Int -> terminalDp(activity, v) }
        val pill = { label: String, onClick: () -> Unit ->
            fillPill(activity, label, onClick)
        }

        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(10), dp(20), 0)
        }

        val nameInput = EditText(activity).apply {
            hint = "按钮名称"
            setText(key.label)
        }
        content.addView(nameInput)

        val divider = { ->
            View(activity).apply {
                setBackgroundColor(0xFF374151.toInt())
                layoutParams = LinearLayout.LayoutParams(-1, 1).apply {
                    topMargin = dp(12); bottomMargin = dp(8)
                }
            }
        }

        // ── 点击输入 section ──
        content.addView(divider())
        content.addView(TextView(activity).apply {
            text = "点击输入"
            setTextColor(0xFF9CA3AF.toInt())
            textSize = 13f
        })
        val currentTap = if (key.input.isNotEmpty()) "  当前: ${encodeKeyInput(key.input)}" else "  （未设置）"
        content.addView(TextView(activity).apply {
            text = currentTap
            setTextColor(0xFF6B7280.toInt())
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(4)
            }
        })
        val tapInput = EditText(activity).apply {
            hint = tapCmdHint()
            setText(encodeKeyInput(key.input))
        }
        content.addView(tapInput)

        val tapPresets = HorizontalScrollView(activity).apply {
            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                for (p in listOf("c", "l", "s", "p", "d")) {
                    addView(pill(p) { tapInput.setText(p); tapInput.setSelection(p.length) })
                }
                for (cmd in listOf("clear", "exit", "ssh", "ls -la")) {
                    val v = "${cmd}\\n"
                    addView(pill(cmd) { tapInput.setText(v); tapInput.setSelection(v.length) })
                }
                for ((lbl, raw) in listOf("↹" to "\\t", "↑" to "\\e[A", "↓" to "\\e[B", "←" to "\\e[D", "→" to "\\e[C", "Home" to "\\e[H", "End" to "\\e[F")) {
                    addView(pill(lbl) { tapInput.setText(raw); tapInput.setSelection(raw.length) })
                }
            })
            isHorizontalScrollBarEnabled = false
        }
        content.addView(tapPresets)

        var tapAliasRow: View? = null
        var swipeAliasRow: View? = null

        var refreshAliasesFn: () -> Unit = {}

        tapAliasRow = aliasSection(activity, aliases,
            onFill = { tapInput.setText(encodeKeyInput(it.value)); tapInput.setSelection(encodeKeyInput(it.value).length) },
            onDelete = { removeKeyAlias(activity, it.name); refreshAliasesFn() },
            onNew = { showAliasDialog(activity, null) { a -> saveKeyAlias(activity, a.name, a.value); refreshAliasesFn() } }
        )
        tapAliasRow?.let { content.addView(it) }

        // ── 上滑命令 section ──
        content.addView(divider())
        content.addView(TextView(activity).apply {
            text = "上滑命令"
            setTextColor(0xFF9CA3AF.toInt())
            textSize = 13f
        })
        val currentSwipe = if (key.swipeCommand.isNotEmpty()) "  当前: ${encodeKeyInput(key.swipeCommand)}" else "  （未设置）"
        content.addView(TextView(activity).apply {
            text = currentSwipe
            setTextColor(0xFF6B7280.toInt())
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(4)
            }
        })
        val swipeInput = EditText(activity).apply {
            hint = "例如 clear、pwd、grep "
            setText(encodeKeyInput(key.swipeCommand))
        }
        content.addView(swipeInput)

        val swipePresets = HorizontalScrollView(activity).apply {
            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                for (p in listOf("clear", "ls", "pwd", "grep ", "history", "cd /", "cd -", "help")) {
                    addView(pill(p) { swipeInput.setText(p); swipeInput.setSelection(p.length) })
                }
            })
            isHorizontalScrollBarEnabled = false
        }
        content.addView(swipePresets)

        swipeAliasRow = aliasSection(activity, aliases,
            onFill = { swipeInput.setText(encodeKeyInput(it.value)); swipeInput.setSelection(encodeKeyInput(it.value).length) },
            onDelete = { removeKeyAlias(activity, it.name); refreshAliasesFn() },
            onNew = { showAliasDialog(activity, null) { a -> saveKeyAlias(activity, a.name, a.value); refreshAliasesFn() } }
        )
        swipeAliasRow?.let { content.addView(it) }

        refreshAliasesFn = {
            val raw = activity.getSharedPreferences("aidev_ui", Activity.MODE_PRIVATE)
                .getString("terminal_key_aliases", "") ?: ""
            val updated = parseKeyAliases(raw)
            val tapIdx = tapAliasRow?.let { content.indexOfChild(it) } ?: -1
            val swipeIdx = swipeAliasRow?.let { content.indexOfChild(it) } ?: -1
            tapAliasRow?.let { content.removeView(it) }
            swipeAliasRow?.let { content.removeView(it) }
            tapAliasRow = aliasSection(activity, updated,
                onFill = { tapInput.setText(encodeKeyInput(it.value)); tapInput.setSelection(encodeKeyInput(it.value).length) },
                onDelete = { removeKeyAlias(activity, it.name); refreshAliasesFn() },
                onNew = { showAliasDialog(activity, null) { a -> saveKeyAlias(activity, a.name, a.value); refreshAliasesFn() } }
            )
            swipeAliasRow = aliasSection(activity, updated,
                onFill = { swipeInput.setText(encodeKeyInput(it.value)); swipeInput.setSelection(encodeKeyInput(it.value).length) },
                onDelete = { removeKeyAlias(activity, it.name); refreshAliasesFn() },
                onNew = { showAliasDialog(activity, null) { a -> saveKeyAlias(activity, a.name, a.value); refreshAliasesFn() } }
            )
            tapAliasRow?.let { content.addView(it, tapIdx.coerceAtLeast(0).coerceAtMost(content.childCount)) }
            swipeAliasRow?.let { content.addView(it, swipeIdx.coerceAtLeast(0).coerceAtMost(content.childCount)) }
        }

        val scroll = ScrollView(activity).apply { addView(content) }

        MaterialAlertDialogBuilder(activity)
            .setTitle("自定义虚拟键: ${key.label}")
            .setView(scroll)
            .setPositiveButton("保存") { _, _ ->
                saveKeyOverride(
                    activity,
                    key.id,
                    EmbeddedVirtualKey(
                        nameInput.text.toString().trim().ifBlank { key.label }.take(8),
                        decodeKeyInput(tapInput.text.toString()),
                        decodeKeyInput(swipeInput.text.toString()),
                        key.id
                    )
                )
                onKeysChanged()
            }
            .setNeutralButton("恢复默认") { _, _ ->
                removeKeyOverride(activity, key.id)
                onKeysChanged()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun saveKeyOverride(activity: Activity, id: String, key: EmbeddedVirtualKey) {
        val prefs = activity.getSharedPreferences("aidev_ui", Activity.MODE_PRIVATE)
        val old = prefs.getString("terminal_key_overrides", "") ?: ""
        val lines = old.lines().filter { it.isNotBlank() && it.substringBefore("\t") != id }
        val line = listOf(id, key.label, encodeKeyInput(key.input), encodeKeyInput(key.swipeCommand)).joinToString("\t")
        prefs.edit().putString("terminal_key_overrides", (lines + line).joinToString("\n")).apply()
    }

    private fun removeKeyOverride(activity: Activity, id: String) {
        val prefs = activity.getSharedPreferences("aidev_ui", Activity.MODE_PRIVATE)
        val old = prefs.getString("terminal_key_overrides", "") ?: ""
        val lines = old.lines().filter { it.isNotBlank() && it.substringBefore("\t") != id }
        prefs.edit().putString("terminal_key_overrides", lines.joinToString("\n")).apply()
    }

    private fun saveKeyAlias(activity: Activity, name: String, value: String) {
        val prefs = activity.getSharedPreferences("aidev_ui", Activity.MODE_PRIVATE)
        val old = prefs.getString("terminal_key_aliases", "") ?: ""
        val lines = old.lines().filter { it.isNotBlank() && it.substringBefore("\t") != name }
        val line = listOf(name, value).joinToString("\t")
        prefs.edit().putString("terminal_key_aliases", (lines + line).joinToString("\n")).apply()
    }

    private fun removeKeyAlias(activity: Activity, name: String) {
        val prefs = activity.getSharedPreferences("aidev_ui", Activity.MODE_PRIVATE)
        val old = prefs.getString("terminal_key_aliases", "") ?: ""
        val lines = old.lines().filter { it.isNotBlank() && it.substringBefore("\t") != name }
        prefs.edit().putString("terminal_key_aliases", lines.joinToString("\n")).apply()
    }

    private fun fillPill(activity: Activity, label: String, onClick: () -> Unit): TextView =
        TextView(activity).apply {
            text = label
            textSize = 12f
            setTextColor(0xFFD1D5DB.toInt())
            gravity = Gravity.CENTER
            setPadding(terminalDp(activity, 10), terminalDp(activity, 4), terminalDp(activity, 10), terminalDp(activity, 4))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFF1F2937.toInt())
                cornerRadius = terminalDp(activity, 8).toFloat()
            }
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply {
                setMargins(0, terminalDp(activity, 2), terminalDp(activity, 6), 0)
            }
        }

    private fun aliasSection(
        activity: Activity,
        aliases: List<KeyAlias>,
        onFill: (KeyAlias) -> Unit,
        onDelete: (KeyAlias) -> Unit,
        onNew: () -> Unit
    ): View {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        for (a in aliases) {
            row.addView(fillPill(activity, a.name) { onFill(a) }.apply {
                setOnLongClickListener {
                    MaterialAlertDialogBuilder(activity)
                        .setTitle("删除别名")
                        .setMessage("删除「${a.name}」？")
                        .setPositiveButton("删除") { _, _ -> onDelete(a) }
                        .setNegativeButton("取消", null)
                        .show()
                    true
                }
            })
        }
        row.addView(fillPill(activity, "+ 新建别名") { onNew() }.apply {
            setTextColor(0xFF60A5FA.toInt())
        })
        return HorizontalScrollView(activity).apply {
            addView(row)
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = terminalDp(activity, 4)
            }
        }
    }

    private fun showAliasDialog(activity: Activity, existing: KeyAlias?, onSave: (KeyAlias) -> Unit) {
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(terminalDp(activity, 20), terminalDp(activity, 10), terminalDp(activity, 20), 0)
        }
        val nameInput = EditText(activity).apply {
            hint = "别名名称"
            setText(existing?.name ?: "")
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val valueInput = EditText(activity).apply {
            hint = tapCmdHint()
            setText(encodeKeyInput(existing?.value ?: ""))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setLines(3)
            isVerticalScrollBarEnabled = true
        }
        box.addView(nameInput)
        box.addView(valueInput)
        MaterialAlertDialogBuilder(activity)
            .setTitle(if (existing != null) "编辑别名" else "新建别名")
            .setView(box)
            .setPositiveButton("保存") { _, _ ->
                val name = nameInput.text.toString().trim()
                val value = decodeKeyInput(valueInput.text.toString())
                if (name.isNotEmpty()) onSave(KeyAlias(name, value))
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
