package com.aidev.terminal.presentation
import com.google.android.material.dialog.MaterialAlertDialogBuilder

import android.app.Activity
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.aidev.terminal.AIDevUi
import com.aidev.terminal.DesignTokens
import com.aidev.terminal.R
import com.aidev.terminal.ShellHost
import com.aidev.terminal.PathConfig
import com.aidev.terminal.ShellPage
import com.aidev.terminal.data.BackupRepositoryImpl
import com.aidev.terminal.domain.BackupBusinessLogic
import com.aidev.terminal.domain.BackupItem
import com.aidev.terminal.domain.BackupRepository
import com.aidev.terminal.domain.BackupResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class BackupRestorePage(
    private val mode: Mode = Mode.BACKUP,
    repository: BackupRepository = BackupRepositoryImpl()
) : ShellPage {

    enum class Mode { BACKUP, RESTORE }

    private lateinit var activity: Activity
    private lateinit var ui: AIDevUi
    private lateinit var host: ShellHost
    private lateinit var content: LinearLayout
    private val selectedItems = mutableSetOf<String>()
    private var backupItemsCache: List<BackupItem> = emptyList()
    private val businessLogic = BackupBusinessLogic(repository)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun create(activity: Activity, ui: AIDevUi, host: ShellHost): View {
        this.activity = activity
        this.ui = ui
        this.host = host
        content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_8), ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_24))
        }
        if (mode == Mode.BACKUP) renderBackup() else renderRestore()
        return ScrollView(activity).apply { addView(content) }
    }

    override fun onSelected(activity: Activity, view: View) {}

    override fun onDestroy(activity: Activity) {
        scope.cancel()
    }

    private fun renderBackup() {
        content.addView(createHeader("备份"))
        content.addView(createDescription("选择要备份的数据项"))
        content.addView(createBackupItemsList())
        content.addView(createActionRow("开始备份", "打包选中的数据") {
            if (selectedItems.isEmpty()) {
                toast("请至少选择一项数据")
            } else {
                showBackupConfirm()
            }
        })
    }

    private fun renderRestore() {
        content.addView(createHeader("恢复"))
        content.addView(createDescription("选择要恢复的数据项"))
        content.addView(createRestoreItemsList())
        content.addView(createActionRow("开始恢复", "恢复选中的数据") {
            if (selectedItems.isEmpty()) {
                toast("请至少选择一项数据")
            } else {
                showRestoreConfirm()
            }
        })
    }

    private fun createHeader(title: String): View {
        return TextView(activity).apply {
            text = title
            setTextColor(ui.palette.text)
            textSize = DesignTokens.TEXT_H1
            setPadding(ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_8), ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_8))
        }
    }

    private fun createDescription(text: String): View {
        return TextView(activity).apply {
            this.text = text
            setTextColor(ui.palette.muted)
            textSize = DesignTokens.TEXT_BODY
            setPadding(ui.dp(DesignTokens.SPACE_12), 0, ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_12))
        }
    }

    private fun createBackupItemsList(): View {
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, ui.dp(DesignTokens.SPACE_8), 0, ui.dp(DesignTokens.SPACE_8))
        }

        scope.launch {
            businessLogic.getBackupItems().collect { backupItems ->
                backupItemsCache = backupItems
                container.removeAllViews()
                backupItems.forEach { item ->
                    container.addView(createBackupItemView(item))
                }
            }
        }

        return container
    }

    private fun createRestoreItemsList(): View {
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, ui.dp(DesignTokens.SPACE_8), 0, ui.dp(DesignTokens.SPACE_8))
        }

        scope.launch {
            businessLogic.getBackupItems().collect { backupItems ->
                backupItemsCache = backupItems
                container.removeAllViews()
                backupItems.forEach { item ->
                    container.addView(createBackupItemView(item))
                }
            }
        }

        return container
    }

    private fun createBackupItemView(item: BackupItem): View {
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_8), ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_8))
            background = ContextCompat.getDrawable(activity, R.drawable.bg_item)
        }

        val checkBox = CheckBox(activity).apply {
            isChecked = item.defaultSelected || selectedItems.contains(item.id)
            setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) selectedItems.add(item.id) else selectedItems.remove(item.id)
            }
        }

        val textView = TextView(activity).apply {
            text = "${item.name} - ${item.desc}"
            setTextColor(ui.palette.text)
            textSize = DesignTokens.TEXT_BODY
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        container.addView(checkBox)
        container.addView(textView)

        return container
    }

    private fun createActionRow(text: String, buttonText: String, onClick: () -> Unit): View {
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_12), 0)
        }

        val textView = TextView(activity).apply {
            this.text = text
            setTextColor(ui.palette.muted)
            textSize = DesignTokens.TEXT_BODY
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val button = TextView(activity).apply {
            this.text = buttonText
            setTextColor(ui.palette.accent)
            textSize = DesignTokens.TEXT_BODY
            setPadding(ui.dp(DesignTokens.SPACE_8), ui.dp(DesignTokens.SPACE_8), ui.dp(DesignTokens.SPACE_8), ui.dp(DesignTokens.SPACE_8))
            background = ContextCompat.getDrawable(activity, R.drawable.bg_button)
            setOnClickListener { onClick() }
        }

        container.addView(textView)
        container.addView(button)

        return container
    }

    private fun showBackupConfirm() {
        scope.launch {
            val hasLarge = selectedItems.any { id -> getBackupItemById(id)?.isLarge == true }
            val estimate = if (hasLarge) {
                "包含大体积数据，预计耗时 3-10 分钟，请确保存储空间充足"
            } else {
                "预计很快完成（通常 10-30 秒）"
            }
            MaterialAlertDialogBuilder(activity)
                .setTitle("确认备份")
                .setMessage("将备份 ${selectedItems.size} 项数据\n\n$estimate\n\n备份路径: ${PathConfig.backupDir(activity).absolutePath}")
                .setPositiveButton("开始备份") { _, _ -> executeBackup() }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    private fun showRestoreConfirm() {
        MaterialAlertDialogBuilder(activity)
            .setTitle("确认恢复")
            .setMessage("将恢复 ${selectedItems.size} 项数据\n\n备份路径: ${PathConfig.backupDir(activity).absolutePath}")
            .setPositiveButton("开始恢复") { _, _ -> executeRestore() }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun executeBackup() {
        val dialogView = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(DesignTokens.SPACE_24), ui.dp(DesignTokens.SPACE_16), ui.dp(DesignTokens.SPACE_24), ui.dp(DesignTokens.SPACE_16))
        }
        val statusText = TextView(activity).apply {
            text = "准备中..."
            setTextColor(ui.palette.text)
            textSize = DesignTokens.TEXT_BODY
        }
        val progressBar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
        }
        val itemStatusLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, ui.dp(DesignTokens.SPACE_8), 0, 0)
        }
        selectedItems.forEach { id ->
            val name = getBackupItemById(id)?.name ?: id
            val tv = TextView(activity).apply {
                text = "  ○ $name"
                setTextColor(ui.palette.muted)
                textSize = DesignTokens.TEXT_CAPTION
            }
            itemStatusLayout.addView(tv)
        }

        dialogView.addView(progressBar)
        dialogView.addView(statusText)
        dialogView.addView(itemStatusLayout)

        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle("正在备份")
            .setView(dialogView)
            .setCancelable(false)
            .setNegativeButton("取消") { _, _ ->
                businessLogic.cancel()
            }
            .show()

        scope.launch {
            val backupResult = businessLogic.executeBackup(items = selectedItems.toList())
            backupResult.collect { result ->
                when (result.type) {
                    BackupResult.ResultType.PROGRESS -> {
                        progressBar.progress = result.data as? Int ?: 0
                        statusText.text = "正在打包... (${result.data as? Int ?: 0}%)"
                    }
                    BackupResult.ResultType.SUCCESS -> {
                        dialog.dismiss()
                        toast("备份完成")
                    }
                    BackupResult.ResultType.ERROR -> {
                        dialog.dismiss()
                        toast("备份失败: ${result.message}")
                    }
                }
            }
        }
    }

    private fun executeRestore() {
        toast("恢复功能开发中")
    }

    private fun getBackupItemById(id: String): BackupItem? {
        return backupItemsCache.firstOrNull { it.id == id }
    }

    private fun toast(message: String) {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
    }
}
