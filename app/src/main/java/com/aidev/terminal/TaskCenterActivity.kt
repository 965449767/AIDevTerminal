package com.aidev.terminal

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File

private data class TaskInfo(
    val id: String,
    val name: String,
    val pid: String,
    val started: String,
    val cmd: String,
    val logPath: String,
    val metaFile: File,
    val logFile: File
)

class TaskCenterActivity : Activity() {
    private lateinit var list: LinearLayout
    private lateinit var taskDir: File
    private lateinit var ui: AIDevUi

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        taskDir = File(filesDir, "home/tasks").apply { mkdirs() }
        ui = AIDevUi(this, getSharedPreferences("aidev_ui", MODE_PRIVATE))
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun buildUi() {
        val root = ui.pageRoot()
        AppNav.attach(this, ui, root, TaskCenterActivity::class.java)
        root.addView(ui.topBar("任务中心", "刷新" to { reload() }, "关闭" to { finish() }))
        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(18), ui.dp(12), ui.dp(18), ui.dp(24))
        }
        root.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(ui.bottomNav(AppNav.bottom(this)))
        setContentView(root)
    }

    private fun reload() {
        list.removeAllViews()
        val tasks = taskDir.listFiles { file -> file.name.endsWith(".meta") }
            ?.mapNotNull { parseTask(it) }
            ?.sortedByDescending { it.id }
            ?: emptyList()
        val running = tasks.count { isRunning(it.pid) }
        list.addView(ui.section("任务概览", "后台命令、服务和长任务日志集中在这里"))
        list.addView(ui.rowOf(
            ui.statusCard("运行中", "${running} 个", running > 0),
            ui.statusCard("任务记录", "${tasks.size} 个", tasks.isNotEmpty())
        ))
        list.addView(ui.section("任务列表", "点击任务查看命令、PID 和最近日志"))
        if (tasks.isEmpty()) {
            list.addView(emptyCard())
            return
        }
        tasks.forEach { task -> list.addView(taskRow(task)) }
    }

    private fun parseTask(meta: File): TaskInfo? {
        val map = meta.readLines().mapNotNull {
            val idx = it.indexOf('=')
            if (idx <= 0) null else it.substring(0, idx) to it.substring(idx + 1)
        }.toMap()
        val id = map["id"] ?: meta.name.removeSuffix(".meta")
        val logPath = map["log"] ?: File(taskDir, "$id.log").absolutePath
        return TaskInfo(
            id = id,
            name = map["name"] ?: "",
            pid = map["pid"] ?: "",
            started = map["started"] ?: "",
            cmd = map["cmd"] ?: "",
            logPath = logPath,
            metaFile = meta,
            logFile = File(taskDir, "$id.log")
        )
    }

    private fun taskRow(task: TaskInfo): View {
        val running = isRunning(task.pid)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12))
            background = ui.subtleCommandButtonBackground()
            val lp = LinearLayout.LayoutParams(-1, -2)
            lp.setMargins(0, 0, 0, ui.dp(8))
            layoutParams = lp

            addView(ui.text("${task.id}  ${if (running) "运行中" else "已结束"}", 13f, if (running) ui.palette.primary else ui.palette.text, bold = true))
            addView(ui.muted("名称：${task.name.ifBlank { "未命名" }}    PID：${task.pid.ifBlank { "-" }}").apply {
                setPadding(0, ui.dp(4), 0, 0)
            })
            addView(ui.muted("命令：${task.cmd.ifBlank { "-" }}").apply {
                setPadding(0, ui.dp(4), 0, 0)
                maxLines = 2
            })
            setOnClickListener {
                ui.pulse()
                showTask(task)
            }
        }
    }

    private fun emptyCard(): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12))
            background = ui.infoPanelBackground()
            addView(ui.text("暂无后台任务", 16f, ui.palette.text, bold = true))
            addView(ui.muted("可以在终端中运行：task-run opencode install-aitool").apply {
                setPadding(0, ui.dp(8), 0, 0)
            })
        }

    private fun showTask(task: TaskInfo) {
        val running = isRunning(task.pid)
        val log = if (task.logFile.exists()) {
            task.logFile.readLines().takeLast(80).joinToString("\n")
        } else {
            "日志不存在：${task.logPath}"
        }
        AlertDialog.Builder(this)
            .setTitle(task.id)
            .setMessage(
                """
                状态：${if (running) "运行中" else "已结束"}
                名称：${task.name}
                PID：${task.pid}
                开始：${task.started}
                命令：${task.cmd}
                日志：${task.logPath}

                最近日志：
                $log
                """.trimIndent()
            )
            .setPositiveButton("刷新") { _, _ -> reload() }
            .setNegativeButton(if (running) "停止任务" else "关闭") { _, _ ->
                if (running) stopTask(task)
            }
            .setNeutralButton("关闭", null)
            .show()
    }

    private fun isRunning(pid: String): Boolean {
        val n = pid.toIntOrNull() ?: return false
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("kill", "-0", n.toString()))
            p.waitFor() == 0
        } catch (_: Throwable) {
            false
        }
    }

    private fun stopTask(task: TaskInfo) {
        val n = task.pid.toIntOrNull()
        if (n == null) {
            Toast.makeText(this, "PID 无效", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val p = Runtime.getRuntime().exec(arrayOf("kill", n.toString()))
            val ok = p.waitFor() == 0
            Toast.makeText(this, if (ok) "已停止任务" else "停止失败或任务已结束", Toast.LENGTH_SHORT).show()
        } catch (_: Throwable) {
            Toast.makeText(this, "停止失败", Toast.LENGTH_SHORT).show()
        }
        reload()
    }

}
