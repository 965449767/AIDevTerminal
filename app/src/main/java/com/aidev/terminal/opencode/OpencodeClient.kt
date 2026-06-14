package com.aidev.terminal.opencode

import android.util.Base64
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * AIDev Terminal 的原生 OpenCode HTTP / SSE 客户端。
 *
 * 协议覆盖（基于 opencode.ai/docs/server，2026-06）:
 *   - 健康     GET  /global/health
 *   - 项目     GET  /project, /project/current, /vcs, /path
 *   - 会话     GET  /session, /session/status, /session/:id
 *              POST /session, /session/:id/abort, /session/:id/fork
 *              POST /session/:id/summarize, /session/:id/init
 *              GET  /session/:id/diff, /session/:id/todo
 *              POST /session/:id/permissions/:pid
 *              DELETE /session/:id
 *   - 消息     POST /session/:id/message, /session/:id/prompt_async
 *              POST /session/:id/command, /session/:id/shell
 *              GET  /session/:id/message
 *   - 资源     GET  /agent, /command, /config, /provider, /mcp
 *   - 文件     GET  /file, /file/content, /file/status, /find, /find/file, /find/symbol
 *   - 事件     GET  /event (SSE), /global/event (SSE)
 *
 * 设计原则:
 *   1. 仅依赖 HttpURLConnection + org.json，零额外 Gradle 依赖；
 *   2. 同步/阻塞 API + 后台线程，与现有项目（无 kotlinx.coroutines）风格一致；
 *   3. Basic Auth 来自 OPENCODE_SERVER_PASSWORD，缺省允许匿名（本机回环）；
 *   4. SSE 通过持久 GET 长连 + 行解析器实现，支持手动 close。
 *
 * 不抛出受检异常 —— IO 失败时返回 [HttpResult.Failure]，调用方按需要处理。
 */
class OpencodeClient(
    val baseUrl: String = "http://127.0.0.1:4096",
    val username: String = "opencode",
    val password: String? = null,
    private val connectTimeoutMs: Int = 4_000,
    private val readTimeoutMs: Int = 30_000
) {

    // -----------------------------------------------------------------
    //  传输层
    // -----------------------------------------------------------------

    sealed class HttpResult<out T> {
        data class Success<T>(val value: T, val status: Int) : HttpResult<T>()
        data class Failure(val status: Int, val message: String, val cause: Throwable? = null) : HttpResult<Nothing>()
    }

    private fun open(method: String, path: String, query: Map<String, String?> = emptyMap(), accept: String = "application/json"): HttpURLConnection {
        val sep = if (path.contains("?")) "&" else "?"
        val q = query.filterValues { !it.isNullOrEmpty() }
            .map { (k, v) -> URLEncoder.encode(k, "UTF-8") + "=" + URLEncoder.encode(v, "UTF-8") }
            .joinToString("&")
        val url = URL(baseUrl + path + if (q.isEmpty()) "" else sep + q)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = connectTimeoutMs
        conn.readTimeout = readTimeoutMs
        conn.setRequestProperty("Accept", accept)
        conn.setRequestProperty("User-Agent", "AIDevTerminal/0.12 (Android)")
        password?.takeIf { it.isNotEmpty() }?.let {
            val token = Base64.encodeToString("$username:$it".toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
            conn.setRequestProperty("Authorization", "Basic $token")
        }
        return conn
    }

    private fun readBody(conn: HttpURLConnection): String =
        (if (conn.responseCode in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream))
            .use { it.bufferedReader(StandardCharsets.UTF_8).readText() }

    private fun requestJson(method: String, path: String, body: JSONObject? = null, query: Map<String, String?> = emptyMap()): HttpResult<JSONObject> {
        return try {
            val conn = open(method, path, query)
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                val payload = body.toString().toByteArray(StandardCharsets.UTF_8)
                conn.setFixedLengthStreamingMode(payload.size)
                conn.outputStream.use { it.write(payload); it.flush() }
            }
            val status = conn.responseCode
            val text = readBody(conn)
            conn.disconnect()
            if (status in 200..299) {
                val obj = if (text.isBlank()) JSONObject() else when (val parsed = parseLenient(text)) {
                    is JSONObject -> parsed
                    is JSONArray -> JSONObject().put("array", parsed)
                    else -> JSONObject().put("value", parsed)
                }
                HttpResult.Success(obj, status)
            } else {
                HttpResult.Failure(status, text.take(512))
            }
        } catch (e: IOException) {
            HttpResult.Failure(-1, e.message ?: "io error", e)
        } catch (e: Throwable) {
            HttpResult.Failure(-1, e.message ?: "unknown error", e)
        }
    }

    private fun requestArray(method: String, path: String, body: JSONObject? = null, query: Map<String, String?> = emptyMap()): HttpResult<JSONArray> {
        return when (val r = requestRaw(method, path, body, query)) {
            is HttpResult.Success -> {
                val parsed = parseLenient(r.value)
                val arr = if (parsed is JSONArray) parsed else if (parsed is JSONObject && parsed.optJSONArray("array") != null) parsed.getJSONArray("array") else JSONArray()
                HttpResult.Success(arr, r.status)
            }
            is HttpResult.Failure -> r
        }
    }

    private fun requestRaw(method: String, path: String, body: JSONObject? = null, query: Map<String, String?> = emptyMap()): HttpResult<String> {
        return try {
            val conn = open(method, path, query)
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                val payload = body.toString().toByteArray(StandardCharsets.UTF_8)
                conn.setFixedLengthStreamingMode(payload.size)
                conn.outputStream.use { it.write(payload) }
            }
            val status = conn.responseCode
            val text = readBody(conn)
            conn.disconnect()
            if (status in 200..299) HttpResult.Success(text, status)
            else HttpResult.Failure(status, text.take(512))
        } catch (e: IOException) {
            HttpResult.Failure(-1, e.message ?: "io error", e)
        } catch (e: Throwable) {
            HttpResult.Failure(-1, e.message ?: "unknown error", e)
        }
    }

    private fun parseLenient(text: String): Any? {
        val t = text.trim()
        return when {
            t.isEmpty() -> JSONObject()
            t.startsWith("{") -> try { JSONObject(t) } catch (_: Throwable) { t }
            t.startsWith("[") -> try { JSONArray(t) } catch (_: Throwable) { t }
            t == "true" -> true
            t == "false" -> false
            else -> t.toIntOrNull() ?: t.toDoubleOrNull() ?: t
        }
    }

    // -----------------------------------------------------------------
    //  Public API · Global
    // -----------------------------------------------------------------

    data class Health(val healthy: Boolean, val version: String)

    fun health(): HttpResult<Health> {
        return when (val r = requestJson("GET", "/global/health")) {
            is HttpResult.Success -> HttpResult.Success(
                Health(r.value.optBoolean("healthy", false), r.value.optString("version", "")),
                r.status
            )
            is HttpResult.Failure -> r
        }
    }

    // -----------------------------------------------------------------
    //  Public API · Sessions
    // -----------------------------------------------------------------

    /** 创建会话。返回会话 JSON（含 id, title, parentID...）。 */
    fun createSession(parentId: String? = null, title: String? = null): HttpResult<JSONObject> {
        val body = JSONObject().apply {
            parentId?.let { put("parentID", it) }
            title?.let { put("title", it) }
        }
        return requestJson("POST", "/session", body)
    }

    fun listSessions(): HttpResult<JSONArray> = requestArray("GET", "/session")
    fun getSession(id: String): HttpResult<JSONObject> = requestJson("GET", "/session/$id")
    fun getSessionStatus(): HttpResult<JSONObject> = requestJson("GET", "/session/status")
    fun deleteSession(id: String): HttpResult<JSONObject> = requestJson("DELETE", "/session/$id")
    fun abortSession(id: String): HttpResult<JSONObject> = requestJson("POST", "/session/$id/abort")
    fun forkSession(id: String, messageId: String? = null): HttpResult<JSONObject> {
        val body = JSONObject().apply { messageId?.let { put("messageID", it) } }
        return requestJson("POST", "/session/$id/fork", body)
    }
    fun summarizeSession(id: String, providerId: String, modelId: String): HttpResult<JSONObject> {
        val body = JSONObject().put("providerID", providerId).put("modelID", modelId)
        return requestJson("POST", "/session/$id/summarize", body)
    }
    fun sessionTodo(id: String): HttpResult<JSONArray> = requestArray("GET", "/session/$id/todo")
    fun sessionDiff(id: String, messageId: String? = null): HttpResult<JSONArray> =
        requestArray("GET", "/session/$id/diff", query = mapOf("messageID" to messageId))

    fun respondPermission(sessionId: String, permissionId: String, accept: Boolean, remember: Boolean = false): HttpResult<JSONObject> {
        val body = JSONObject()
            .put("response", if (accept) "accept" else "reject")
            .put("remember", remember)
        return requestJson("POST", "/session/$sessionId/permissions/$permissionId", body)
    }

    // -----------------------------------------------------------------
    //  Public API · Messages
    // -----------------------------------------------------------------

    /** 同步发送消息（阻塞至完整响应）。 */
    fun sendMessage(sessionId: String, text: String, agent: String? = null, model: String? = null): HttpResult<JSONObject> {
        val parts = JSONArray().put(JSONObject().put("type", "text").put("text", text))
        val body = JSONObject().put("parts", parts)
        agent?.let { body.put("agent", it) }
        model?.let { body.put("model", it) }
        return requestJson("POST", "/session/$sessionId/message", body)
    }

    /** 异步投递消息：立即返回 204，结果通过 SSE 接收。 */
    fun promptAsync(sessionId: String, text: String, agent: String? = null, model: String? = null): HttpResult<JSONObject> {
        val parts = JSONArray().put(JSONObject().put("type", "text").put("text", text))
        val body = JSONObject().put("parts", parts)
        agent?.let { body.put("agent", it) }
        model?.let { body.put("model", it) }
        return requestJson("POST", "/session/$sessionId/prompt_async", body)
    }

    fun listMessages(sessionId: String, limit: Int? = null): HttpResult<JSONArray> =
        requestArray("GET", "/session/$sessionId/message", query = mapOf("limit" to limit?.toString()))

    fun executeCommand(sessionId: String, command: String, arguments: String = "", agent: String? = null, model: String? = null): HttpResult<JSONObject> {
        val body = JSONObject().put("command", command).put("arguments", arguments)
        agent?.let { body.put("agent", it) }
        model?.let { body.put("model", it) }
        return requestJson("POST", "/session/$sessionId/command", body)
    }

    fun executeShell(sessionId: String, command: String, agent: String, model: String? = null): HttpResult<JSONObject> {
        val body = JSONObject().put("agent", agent).put("command", command)
        model?.let { body.put("model", it) }
        return requestJson("POST", "/session/$sessionId/shell", body)
    }

    // -----------------------------------------------------------------
    //  Public API · Resources
    // -----------------------------------------------------------------

    fun listAgents(): HttpResult<JSONArray> = requestArray("GET", "/agent")
    fun listCommands(): HttpResult<JSONArray> = requestArray("GET", "/command")
    fun getConfig(): HttpResult<JSONObject> = requestJson("GET", "/config")
    fun listProviders(): HttpResult<JSONObject> = requestJson("GET", "/provider")
    fun listMcp(): HttpResult<JSONObject> = requestJson("GET", "/mcp")

    // -----------------------------------------------------------------
    //  Public API · Files & Find
    // -----------------------------------------------------------------

    fun listFiles(path: String): HttpResult<JSONArray> = requestArray("GET", "/file", query = mapOf("path" to path))
    fun readFile(path: String): HttpResult<JSONObject> = requestJson("GET", "/file/content", query = mapOf("path" to path))
    fun fileStatus(): HttpResult<JSONArray> = requestArray("GET", "/file/status")
    fun findText(pattern: String): HttpResult<JSONArray> = requestArray("GET", "/find", query = mapOf("pattern" to pattern))
    fun findFile(query: String, type: String? = null, limit: Int? = null): HttpResult<JSONArray> =
        requestArray("GET", "/find/file", query = mapOf("query" to query, "type" to type, "limit" to limit?.toString()))
    fun findSymbol(query: String): HttpResult<JSONArray> = requestArray("GET", "/find/symbol", query = mapOf("query" to query))

    fun currentProject(): HttpResult<JSONObject> = requestJson("GET", "/project/current")
    fun vcsInfo(): HttpResult<JSONObject> = requestJson("GET", "/vcs")

    // -----------------------------------------------------------------
    //  SSE 事件流
    // -----------------------------------------------------------------

    /** 一个可主动关闭的 SSE 订阅。 */
    interface Subscription {
        fun close()
        val isClosed: Boolean
    }

    /**
     * 订阅服务器事件流。回调在后台线程；UI 处理需自行 post 到主线程。
     * 当连接异常断开时，[onClosed] 将被调用并附带最后一次错误（可能为 null = 主动关闭）。
     */
    fun events(
        global: Boolean = false,
        onEvent: (OpencodeEvent) -> Unit,
        onClosed: (Throwable?) -> Unit = {}
    ): Subscription {
        val closed = AtomicBoolean(false)
        val connRef = AtomicReference<HttpURLConnection?>(null)
        val sub = object : Subscription {
            override fun close() {
                closed.set(true)
                runCatching { connRef.get()?.disconnect() }
            }
            override val isClosed: Boolean get() = closed.get()
        }
        val path = if (global) "/global/event" else "/event"
        Thread({
            var lastError: Throwable? = null
            try {
                val conn = open("GET", path, accept = "text/event-stream")
                conn.readTimeout = 0 // SSE 长连，禁用 read timeout
                conn.setRequestProperty("Cache-Control", "no-cache")
                connRef.set(conn)
                val status = conn.responseCode
                if (status !in 200..299) {
                    lastError = IOException("SSE HTTP $status")
                    return@Thread
                }
                BufferedReader(InputStreamReader(conn.inputStream, StandardCharsets.UTF_8)).use { reader ->
                    val data = StringBuilder()
                    while (!closed.get()) {
                        val line = reader.readLine() ?: break
                        when {
                            line.isEmpty() -> {
                                if (data.isNotEmpty()) {
                                    val payload = data.toString()
                                    data.setLength(0)
                                    runCatching {
                                        val obj = JSONObject(payload)
                                        onEvent(OpencodeEvent.parse(obj))
                                    }.onFailure { Log.w(TAG, "bad sse json: ${'$'}{it.message}") }
                                }
                            }
                            line.startsWith("data:") -> data.append(line.removePrefix("data:").trimStart())
                            line.startsWith(":") -> { /* SSE comment / heartbeat */ }
                            else -> { /* event:/id:/retry: 暂忽略 */ }
                        }
                    }
                }
            } catch (e: Throwable) {
                if (!closed.get()) lastError = e
            } finally {
                runCatching { connRef.get()?.disconnect() }
                onClosed(lastError)
            }
        }, "opencode-sse").apply { isDaemon = true; start() }
        return sub
    }

    companion object {
        private const val TAG = "OpencodeClient"
    }
}
