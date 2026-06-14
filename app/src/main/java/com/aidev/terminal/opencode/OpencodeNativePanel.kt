package com.aidev.terminal.opencode

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject

/**
 * OpenCode 原生面板。
 *
 * Phase 2 目标：先把“创建会话 / 多会话 / 发送 Prompt / 中止 / TODO / Diff / SSE 事件”
 * 从 PTY 文本解析路径中剥离出来，让 AIDev Terminal 具备一个真正的 OpenCode 客户端入口。
 *
 * 该对象故意只使用 Android 原生 View 和 AlertDialog，避免引入新依赖，也方便后续迁移成
 * 独立 Activity 或嵌入式页签。
 */
object OpencodeNativePanel {
    private const val PREFS = "aidev_opencode_native"
    private const val KEY_ACTIVE_SESSION = "active_session_id"
    private const val KEY_ACTIVE_TITLE = "active_session_title"
    private const val KEY_SELECTED_AGENT = "selected_agent"

    fun showHome(activity: Activity) {
        val actions = arrayOf(
            "会话 · 原生多会话列表",
            "会话 · 新建会话并提问",
            "会话 · 继续当前会话",
            "任务 · 当前会话 TODO",
            "任务 · 中止当前会话",
            "代码 · 当前会话 Diff",
            "智能体 · 选择 Agent",
            "命令 · 执行 Slash Command",
            "事件 · 打开 SSE 事件监听",
            "协议 · 重新探测健康"
        )
        AlertDialog.Builder(activity)
            .setTitle("OpenCode 原生面板")
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> showSessions(activity)
                    1 -> createSessionAndPrompt(activity)
                    2 -> showActiveSession(activity)
                    3 -> showActiveTodo(activity)
                    4 -> abortActive(activity)
                    5 -> showActiveDiff(activity)
                    6 -> showAgents(activity)
                    7 -> showCommands(activity)
                    8 -> showEventConsole(activity)
                    9 -> {
                        OpencodeManager.probeNow(activity.applicationContext)
                        Toast.makeText(activity, "已重新探测 OpenCode 健康状态", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    fun createSessionAndPrompt(activity: Activity) {
        val edit = EditText(activity).apply {
            hint = "输入要交给 OpenCode 的任务，例如：审计当前项目并修复一个明显问题"
            minLines = 4
            maxLines = 8
            gravity = Gravity.TOP
        }
        AlertDialog.Builder(activity)
            .setTitle("新建 OpenCode 会话")
            .setView(edit)
            .setPositiveButton("发送") { _, _ ->
                val prompt = edit.text.toString().trim()
                if (prompt.isBlank()) {
                    Toast.makeText(activity, "Prompt 不能为空", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                runClient(activity, "正在创建会话...") { client ->
                    when (val created = client.createSession(title = prompt.take(24))) {
                        is OpencodeClient.HttpResult.Success -> {
                            val sid = created.value.optString("id", created.value.optStringNested("info", "id") ?: "")
                            val title = created.value.optString("title", prompt.take(24))
                            if (sid.isBlank()) {
                                uiToast(activity, "创建成功但未返回 session id")
                                return@runClient
                            }
                            rememberActive(activity, sid, title)
                            when (val sent = client.promptAsync(sid, prompt, agent = selectedAgent(activity))) {
                                is OpencodeClient.HttpResult.Success -> ui(activity) {
                                    Toast.makeText(activity, "已异步提交，开始监听事件", Toast.LENGTH_SHORT).show()
                                    showLiveSession(activity, sid, title)
                                }
                                is OpencodeClient.HttpResult.Failure -> uiToast(activity, "提交失败：${sent.message}")
                            }
                        }
                        is OpencodeClient.HttpResult.Failure -> uiToast(activity, "创建失败：${created.message}")
                    }
                }
            }
            .setNeutralButton("只创建") { _, _ ->
                runClient(activity, "正在创建会话...") { client ->
                    when (val created = client.createSession(title = "AIDev 原生会话")) {
                        is OpencodeClient.HttpResult.Success -> {
                            val sid = created.value.optString("id", created.value.optStringNested("info", "id") ?: "")
                            val title = created.value.optString("title", "AIDev 原生会话")
                            rememberActive(activity, sid, title)
                            ui(activity) { showLiveSession(activity, sid, title) }
                        }
                        is OpencodeClient.HttpResult.Failure -> uiToast(activity, "创建失败：${created.message}")
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    fun showSessions(activity: Activity) {
        runClient(activity, "正在读取会话...") { client ->
            val sessions = client.listSessions()
            val statuses = client.getSessionStatus()
            if (sessions is OpencodeClient.HttpResult.Failure) {
                uiToast(activity, "读取会话失败：${sessions.message}")
                return@runClient
            }
            val arr = (sessions as OpencodeClient.HttpResult.Success<JSONArray>).value
            val statusObj = if (statuses is OpencodeClient.HttpResult.Success) statuses.value else JSONObject()
            val rows = (0 until arr.length()).mapNotNull { idx ->
                arr.optJSONObject(idx)?.let { obj ->
                    val id = obj.optString("id")
                    val title = obj.optString("title", id.take(8))
                    val status = statusObj.optString(id, "")
                    SessionRow(id, title, status)
                }
            }
            ui(activity) {
                if (rows.isEmpty()) {
                    AlertDialog.Builder(activity)
                        .setTitle("OpenCode 会话")
                        .setMessage("暂无会话。可以新建会话并发送 Prompt。")
                        .setPositiveButton("新建") { _, _ -> createSessionAndPrompt(activity) }
                        .setNegativeButton("关闭", null)
                        .show()
                    return@ui
                }
                AlertDialog.Builder(activity)
                    .setTitle("OpenCode 会话")
                    .setItems(rows.map { it.label() }.toTypedArray()) { _, which ->
                        val row = rows[which]
                        rememberActive(activity, row.id, row.title)
                        showSessionActions(activity, row.id, row.title)
                    }
                    .setPositiveButton("新建") { _, _ -> createSessionAndPrompt(activity) }
                    .setNegativeButton("关闭", null)
                    .show()
            }
        }
    }

    private fun showSessionActions(activity: Activity, sessionId: String, title: String) {
        val actions = arrayOf("实时查看 / 发送 Prompt", "查看消息", "查看 TODO", "查看 Diff", "选择 Agent", "执行 Slash Command", "中止会话", "Fork 会话", "删除会话")
        AlertDialog.Builder(activity)
            .setTitle(title.ifBlank { sessionId })
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> showLiveSession(activity, sessionId, title)
                    1 -> showMessages(activity, sessionId, title)
                    2 -> showTodo(activity, sessionId, title)
                    3 -> showDiff(activity, sessionId, title)
                    4 -> showAgents(activity)
                    5 -> showCommands(activity)
                    6 -> abortSession(activity, sessionId)
                    7 -> forkSession(activity, sessionId)
                    8 -> deleteSession(activity, sessionId)
                }
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    fun showActiveSession(activity: Activity) {
        val (id, title) = active(activity)
        if (id.isBlank()) {
            Toast.makeText(activity, "暂无当前 OpenCode 会话", Toast.LENGTH_SHORT).show()
            showSessions(activity)
            return
        }
        showLiveSession(activity, id, title.ifBlank { id })
    }

    fun showActiveTodo(activity: Activity) {
        val (id, title) = active(activity)
        if (id.isBlank()) return Toast.makeText(activity, "暂无当前 OpenCode 会话", Toast.LENGTH_SHORT).show()
        showTodo(activity, id, title)
    }

    fun showActiveDiff(activity: Activity) {
        val (id, title) = active(activity)
        if (id.isBlank()) return Toast.makeText(activity, "暂无当前 OpenCode 会话", Toast.LENGTH_SHORT).show()
        showDiff(activity, id, title)
    }

    fun abortActive(activity: Activity) {
        val (id, _) = active(activity)
        if (id.isBlank()) return Toast.makeText(activity, "暂无当前 OpenCode 会话", Toast.LENGTH_SHORT).show()
        abortSession(activity, id)
    }

    private fun showLiveSession(activity: Activity, sessionId: String, title: String) {
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 14), dp(activity, 10), dp(activity, 14), dp(activity, 4))
        }
        val status = TextView(activity).apply {
            text = "SSE：连接中..."
            textSize = 12f
            setTextColor(0xFF8A93A3.toInt())
            ellipsize = TextUtils.TruncateAt.END
            maxLines = 1
        }
        val output = TextView(activity).apply {
            textSize = 13f
            setTextColor(0xFFE8EAEE.toInt())
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            text = "正在加载消息...\n"
        }
        val scroll = ScrollView(activity).apply { addView(output) }
        val input = EditText(activity).apply {
            hint = "继续追问 / 下达任务"
            minLines = 2
            maxLines = 5
            gravity = Gravity.TOP
        }
        root.addView(status, LinearLayout.LayoutParams(-1, dp(activity, 22)))
        root.addView(scroll, LinearLayout.LayoutParams(-1, dp(activity, 340)))
        root.addView(input, LinearLayout.LayoutParams(-1, -2))

        var sub: OpencodeClient.Subscription? = null
        val dialog = AlertDialog.Builder(activity)
            .setTitle("原生 OpenCode · ${title.ifBlank { sessionId.take(8) }}")
            .setView(root)
            .setPositiveButton("发送", null)
            .setNeutralButton("中止", null)
            .setNegativeButton("关闭", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val text = input.text.toString().trim()
                if (text.isBlank()) return@setOnClickListener
                input.setText("")
                runClient(activity, "正在发送 Prompt...") { client ->
                    when (val r = client.promptAsync(sessionId, text, agent = selectedAgent(activity))) {
                        is OpencodeClient.HttpResult.Success -> ui(activity) {
                            append(output, "\n\n> $text\n\n[已提交，等待 SSE 更新]\n")
                        }
                        is OpencodeClient.HttpResult.Failure -> uiToast(activity, "发送失败：${r.message}")
                    }
                }
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { abortSession(activity, sessionId) }
            loadMessagesInto(activity, sessionId, output)
            sub = OpencodeManager.client(activity).events(
                onEvent = { event ->
                    ui(activity) {
                        status.text = "SSE：${event.type}"
                        when (event) {
                            is OpencodeEvent.PermissionRequested -> showPermissionDialog(activity, event)
                            is OpencodeEvent.TodoUpdated -> append(output, "\n[TODO 已更新]\n")
                            is OpencodeEvent.FileEdited -> append(output, "\n[文件变更] ${event.path}\n")
                            is OpencodeEvent.MessageUpdated,
                            is OpencodeEvent.PartUpdated -> loadMessagesInto(activity, sessionId, output)
                            else -> append(output, "\n[event] ${event.type}\n")
                        }
                    }
                },
                onClosed = { err ->
                    ui(activity) { status.text = if (err == null) "SSE：已关闭" else "SSE：断开 ${err.message}" }
                }
            )
        }
        dialog.setOnDismissListener { sub?.close() }
        dialog.show()
    }

    private fun showMessages(activity: Activity, sessionId: String, title: String) {
        runClient(activity, "正在读取消息...") { client ->
            when (val r = client.listMessages(sessionId, 80)) {
                is OpencodeClient.HttpResult.Success -> ui(activity) {
                    showText(activity, "消息 · ${title.ifBlank { sessionId.take(8) }}", renderMessages(r.value))
                }
                is OpencodeClient.HttpResult.Failure -> uiToast(activity, "读取消息失败：${r.message}")
            }
        }
    }

    private fun loadMessagesInto(activity: Activity, sessionId: String, output: TextView) {
        runClient(activity, null) { client ->
            when (val r = client.listMessages(sessionId, 80)) {
                is OpencodeClient.HttpResult.Success -> ui(activity) { output.text = renderMessages(r.value) }
                is OpencodeClient.HttpResult.Failure -> ui(activity) { append(output, "\n[消息刷新失败] ${r.message}\n") }
            }
        }
    }

    private fun showTodo(activity: Activity, sessionId: String, title: String) {
        runClient(activity, "正在读取 TODO...") { client ->
            when (val r = client.sessionTodo(sessionId)) {
                is OpencodeClient.HttpResult.Success -> ui(activity) {
                    val text = if (r.value.length() == 0) "暂无 TODO" else renderArray(r.value)
                    showText(activity, "TODO · ${title.ifBlank { sessionId.take(8) }}", text)
                }
                is OpencodeClient.HttpResult.Failure -> uiToast(activity, "读取 TODO 失败：${r.message}")
            }
        }
    }

    private fun showDiff(activity: Activity, sessionId: String, title: String) {
        runClient(activity, "正在读取 Diff...") { client ->
            when (val r = client.sessionDiff(sessionId)) {
                is OpencodeClient.HttpResult.Success -> ui(activity) {
                    val text = if (r.value.length() == 0) "暂无 Diff" else renderArray(r.value)
                    showText(activity, "Diff · ${title.ifBlank { sessionId.take(8) }}", text)
                }
                is OpencodeClient.HttpResult.Failure -> uiToast(activity, "读取 Diff 失败：${r.message}")
            }
        }
    }

    private fun abortSession(activity: Activity, sessionId: String) {
        AlertDialog.Builder(activity)
            .setTitle("中止 OpenCode 会话？")
            .setMessage("将调用 POST /session/$sessionId/abort，中止当前运行中的 Agent。")
            .setPositiveButton("中止") { _, _ ->
                runClient(activity, "正在中止...") { client ->
                    when (val r = client.abortSession(sessionId)) {
                        is OpencodeClient.HttpResult.Success -> uiToast(activity, "已发送中止请求")
                        is OpencodeClient.HttpResult.Failure -> uiToast(activity, "中止失败：${r.message}")
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun forkSession(activity: Activity, sessionId: String) {
        runClient(activity, "正在 Fork 会话...") { client ->
            when (val r = client.forkSession(sessionId)) {
                is OpencodeClient.HttpResult.Success -> {
                    val newId = r.value.optString("id", r.value.optStringNested("info", "id") ?: "")
                    val title = r.value.optString("title", "Fork 会话")
                    rememberActive(activity, newId, title)
                    ui(activity) {
                        Toast.makeText(activity, "Fork 完成", Toast.LENGTH_SHORT).show()
                        showLiveSession(activity, newId, title)
                    }
                }
                is OpencodeClient.HttpResult.Failure -> uiToast(activity, "Fork 失败：${r.message}")
            }
        }
    }

    private fun deleteSession(activity: Activity, sessionId: String) {
        AlertDialog.Builder(activity)
            .setTitle("删除 OpenCode 会话？")
            .setMessage("该操作会删除会话及其数据，无法从 AIDev Terminal 内恢复。")
            .setPositiveButton("删除") { _, _ ->
                runClient(activity, "正在删除...") { client ->
                    when (val r = client.deleteSession(sessionId)) {
                        is OpencodeClient.HttpResult.Success -> uiToast(activity, "已删除会话")
                        is OpencodeClient.HttpResult.Failure -> uiToast(activity, "删除失败：${r.message}")
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    fun showAgents(activity: Activity) {
        runClient(activity, "正在读取 Agent...") { client ->
            when (val r = client.listAgents()) {
                is OpencodeClient.HttpResult.Success -> {
                    val rows = (0 until r.value.length()).mapNotNull { idx ->
                        r.value.optJSONObject(idx)?.let { obj ->
                            val id = obj.optString("id", obj.optString("name", ""))
                            val desc = obj.optString("description", "")
                            AgentRow(id, desc, obj)
                        }
                    }.filter { it.id.isNotBlank() }
                    ui(activity) {
                        if (rows.isEmpty()) {
                            showText(activity, "OpenCode Agent", "未读取到 Agent。请确认 opencode serve 正常运行。")
                            return@ui
                        }
                        val current = selectedAgent(activity).orEmpty()
                        val labels = rows.map { row ->
                            val mark = if (row.id == current) "● " else ""
                            mark + row.id + if (row.description.isBlank()) "" else " · ${row.description.take(36)}"
                        }.toTypedArray()
                        AlertDialog.Builder(activity)
                            .setTitle("选择 OpenCode Agent")
                            .setItems(labels) { _, which ->
                                val row = rows[which]
                                activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                                    .putString(KEY_SELECTED_AGENT, row.id)
                                    .apply()
                                Toast.makeText(activity, "已选择 Agent：${row.id}", Toast.LENGTH_SHORT).show()
                            }
                            .setNeutralButton("查看 JSON") { _, _ -> showText(activity, "Agent JSON", renderArray(r.value)) }
                            .setNegativeButton("关闭", null)
                            .show()
                    }
                }
                is OpencodeClient.HttpResult.Failure -> uiToast(activity, "读取 Agent 失败：${r.message}")
            }
        }
    }

    fun showCommands(activity: Activity) {
        val (sid, _) = active(activity)
        if (sid.isBlank()) {
            Toast.makeText(activity, "请先创建或选择一个 OpenCode 会话", Toast.LENGTH_SHORT).show()
            showSessions(activity)
            return
        }
        runClient(activity, "正在读取 Slash Command...") { client ->
            when (val r = client.listCommands()) {
                is OpencodeClient.HttpResult.Success -> {
                    val rows = (0 until r.value.length()).mapNotNull { idx ->
                        r.value.optJSONObject(idx)?.let { obj ->
                            val name = obj.optString("name", obj.optString("id", obj.optString("command", "")))
                            val desc = obj.optString("description", "")
                            CommandRow(name, desc, obj)
                        }
                    }.filter { it.name.isNotBlank() }
                    ui(activity) {
                        if (rows.isEmpty()) {
                            showText(activity, "Slash Command", "未读取到命令。可在 .opencode/commands 或 opencode.jsonc 中定义。")
                            return@ui
                        }
                        AlertDialog.Builder(activity)
                            .setTitle("执行 Slash Command")
                            .setItems(rows.map { it.label() }.toTypedArray()) { _, which ->
                                askCommandArguments(activity, sid, rows[which])
                            }
                            .setNeutralButton("查看 JSON") { _, _ -> showText(activity, "Command JSON", renderArray(r.value)) }
                            .setNegativeButton("关闭", null)
                            .show()
                    }
                }
                is OpencodeClient.HttpResult.Failure -> uiToast(activity, "读取命令失败：${r.message}")
            }
        }
    }

    private fun askCommandArguments(activity: Activity, sessionId: String, row: CommandRow) {
        val edit = EditText(activity).apply {
            hint = "可选参数，例如文件名、组件名、任务描述"
            minLines = 2
            maxLines = 5
            gravity = Gravity.TOP
        }
        AlertDialog.Builder(activity)
            .setTitle("/${row.name}")
            .setMessage(row.description.ifBlank { "执行 OpenCode Slash Command" })
            .setView(edit)
            .setPositiveButton("执行") { _, _ ->
                val args = edit.text.toString().trim()
                runClient(activity, "正在执行 /${row.name}...") { client ->
                    when (val r = client.executeCommand(sessionId, row.name, args, agent = selectedAgent(activity))) {
                        is OpencodeClient.HttpResult.Success -> ui(activity) {
                            Toast.makeText(activity, "命令已执行", Toast.LENGTH_SHORT).show()
                            showActiveSession(activity)
                        }
                        is OpencodeClient.HttpResult.Failure -> uiToast(activity, "执行失败：${r.message}")
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    fun showEventConsole(activity: Activity) {
        val output = TextView(activity).apply {
            textSize = 12.5f
            setTextColor(0xFFE8EAEE.toInt())
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            text = "正在连接 /event...\n"
        }
        val scroll = ScrollView(activity).apply {
            setPadding(dp(activity, 10), dp(activity, 8), dp(activity, 10), dp(activity, 8))
            addView(output)
        }
        var sub: OpencodeClient.Subscription? = null
        val dialog = AlertDialog.Builder(activity)
            .setTitle("OpenCode SSE 事件")
            .setView(scroll)
            .setNegativeButton("关闭", null)
            .create()
        dialog.setOnShowListener {
            sub = OpencodeManager.client(activity).events(
                onEvent = { event ->
                    ui(activity) {
                        append(output, "${event.type}\n")
                        if (event is OpencodeEvent.PermissionRequested) showPermissionDialog(activity, event)
                    }
                },
                onClosed = { err -> ui(activity) { append(output, "SSE closed: ${err?.message ?: "主动关闭"}\n") } }
            )
        }
        dialog.setOnDismissListener { sub?.close() }
        dialog.show()
    }

    private fun showPermissionDialog(activity: Activity, event: OpencodeEvent.PermissionRequested) {
        if (event.sessionId.isBlank() || event.permissionId.isBlank()) return
        AlertDialog.Builder(activity)
            .setTitle("OpenCode 权限请求")
            .setMessage("工具: ${event.tool ?: "未知"}\n会话: ${event.sessionId}\n权限: ${event.permissionId}\n\n是否允许本次操作？")
            .setPositiveButton("允许") { _, _ -> respondPermission(activity, event, true, false) }
            .setNeutralButton("允许并记住") { _, _ -> respondPermission(activity, event, true, true) }
            .setNegativeButton("拒绝") { _, _ -> respondPermission(activity, event, false, false) }
            .show()
    }

    private fun respondPermission(activity: Activity, event: OpencodeEvent.PermissionRequested, accept: Boolean, remember: Boolean) {
        runClient(activity, "正在回应权限...") { client ->
            when (val r = client.respondPermission(event.sessionId, event.permissionId, accept, remember)) {
                is OpencodeClient.HttpResult.Success -> uiToast(activity, if (accept) "已允许" else "已拒绝")
                is OpencodeClient.HttpResult.Failure -> uiToast(activity, "回应失败：${r.message}")
            }
        }
    }

    private fun renderMessages(arr: JSONArray): String {
        if (arr.length() == 0) return "暂无消息"
        val out = StringBuilder()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val info = item.optJSONObject("info") ?: JSONObject()
            val role = info.optString("role", info.optString("type", "message"))
            val id = info.optString("id", "")
            out.append("## ").append(role).append(if (id.isBlank()) "" else " · ${id.take(8)}").append("\n")
            val parts = item.optJSONArray("parts") ?: JSONArray()
            for (p in 0 until parts.length()) {
                val part = parts.optJSONObject(p) ?: continue
                val type = part.optString("type", "part")
                val text = collectText(part).trim()
                out.append("[").append(type).append("] ")
                out.append(if (text.isBlank()) part.toString(2) else text)
                out.append("\n\n")
            }
        }
        return out.toString().trim()
    }

    private fun renderArray(arr: JSONArray): String {
        val out = StringBuilder()
        for (i in 0 until arr.length()) {
            val v = arr.opt(i)
            out.append(i + 1).append(". ")
            out.append(prettyJson(v))
            out.append("\n\n")
        }
        return out.toString().trim()
    }

    private fun collectText(obj: JSONObject): String {
        val direct = listOf("text", "content", "message", "summary", "title", "output")
            .mapNotNull { obj.optString(it).takeIf { s -> s.isNotBlank() } }
        if (direct.isNotEmpty()) return direct.joinToString("\n")
        val out = StringBuilder()
        val keys = obj.keys()
        while (keys.hasNext()) {
            when (val v = obj.opt(keys.next())) {
                is JSONObject -> out.append(collectText(v)).append('\n')
                is JSONArray -> for (i in 0 until v.length()) {
                    val item = v.opt(i)
                    if (item is JSONObject) out.append(collectText(item)).append('\n')
                }
            }
        }
        return out.toString()
    }

    private fun prettyJson(v: Any?): String = when (v) {
        is JSONObject -> runCatching { v.toString(2) }.getOrElse { v.toString() }
        is JSONArray -> runCatching { v.toString(2) }.getOrElse { v.toString() }
        null -> "null"
        else -> v.toString()
    }

    private fun showText(activity: Activity, title: String, text: String) {
        val tv = TextView(activity).apply {
            textSize = 12.5f
            setTextColor(0xFFE8EAEE.toInt())
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            this.text = text
            setPadding(dp(activity, 12), dp(activity, 10), dp(activity, 12), dp(activity, 10))
        }
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setView(ScrollView(activity).apply { addView(tv) })
            .setPositiveButton("关闭", null)
            .show()
    }

    private fun runClient(activity: Activity, loading: String?, block: (OpencodeClient) -> Unit) {
        loading?.let { Toast.makeText(activity, it, Toast.LENGTH_SHORT).show() }
        Thread({
            block(OpencodeManager.client(activity.applicationContext))
        }, "opencode-native-panel").apply { isDaemon = true; start() }
    }

    private fun rememberActive(activity: Activity, id: String, title: String) {
        if (id.isBlank()) return
        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_ACTIVE_SESSION, id)
            .putString(KEY_ACTIVE_TITLE, title)
            .apply()
    }

    private fun active(activity: Activity): Pair<String, String> {
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return (prefs.getString(KEY_ACTIVE_SESSION, "") ?: "") to (prefs.getString(KEY_ACTIVE_TITLE, "") ?: "")
    }

    private fun selectedAgent(activity: Activity): String? =
        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SELECTED_AGENT, "")
            ?.takeIf { it.isNotBlank() }

    private fun ui(activity: Activity, block: () -> Unit) = activity.runOnUiThread(block)
    private fun uiToast(activity: Activity, text: String) = ui(activity) { Toast.makeText(activity, text, Toast.LENGTH_LONG).show() }
    private fun append(tv: TextView, text: String) { tv.append(text) }
    private fun dp(activity: Activity, v: Int): Int = (v * activity.resources.displayMetrics.density).toInt()

    private data class SessionRow(val id: String, val title: String, val status: String) {
        fun label(): String = "${if (status.isBlank()) "idle" else status} · ${title.ifBlank { id.take(8) }}"
    }

    private data class AgentRow(val id: String, val description: String, val raw: JSONObject)

    private data class CommandRow(val name: String, val description: String, val raw: JSONObject) {
        fun label(): String = "/$name" + if (description.isBlank()) "" else " · ${description.take(42)}"
    }

    private fun JSONObject.optStringNested(vararg path: String): String? {
        var cur: JSONObject? = this
        for ((i, k) in path.withIndex()) {
            if (cur == null) return null
            if (i == path.lastIndex) return cur.optString(k).takeIf { it.isNotBlank() }
            cur = cur.optJSONObject(k)
        }
        return null
    }
}
