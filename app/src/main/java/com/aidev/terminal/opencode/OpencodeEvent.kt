package com.aidev.terminal.opencode

import org.json.JSONObject

/**
 * OpenCode SSE 事件总线 (`GET /event`) 投递的所有事件类型。
 *
 * 协议参考: https://opencode.ai/docs/server (2026-06)
 *  - server.connected               首条事件，仅表示连接成功
 *  - session.created/updated/...    会话生命周期
 *  - session.status                 idle / busy
 *  - message.updated                助手消息流式增量
 *  - part.updated                   消息片段（文本 / 工具调用 / 思考块）
 *  - todo.updated                   Agent 自维护的 TODO 列表
 *  - permission.requested           edit/bash 等权限请求
 *  - file.edited                    文件被修改
 *
 * 这里使用 sealed class 而非 enum，使每个分支可携带不同 payload。
 * 未识别的事件用 [Unknown] 兜底，避免协议升级时崩溃。
 */
sealed class OpencodeEvent {
    /** 原始事件 type 与 properties JSON。便于上层调试或自定义解析。 */
    abstract val type: String
    abstract val raw: JSONObject

    data class ServerConnected(override val type: String, override val raw: JSONObject) : OpencodeEvent()

    data class SessionCreated(override val type: String, override val raw: JSONObject, val sessionId: String) : OpencodeEvent()
    data class SessionUpdated(override val type: String, override val raw: JSONObject, val sessionId: String) : OpencodeEvent()
    data class SessionDeleted(override val type: String, override val raw: JSONObject, val sessionId: String) : OpencodeEvent()
    data class SessionStatus(override val type: String, override val raw: JSONObject, val sessionId: String, val status: String) : OpencodeEvent()

    data class MessageUpdated(override val type: String, override val raw: JSONObject, val sessionId: String, val messageId: String) : OpencodeEvent()
    data class PartUpdated(override val type: String, override val raw: JSONObject, val sessionId: String, val messageId: String, val partId: String?) : OpencodeEvent()

    data class TodoUpdated(override val type: String, override val raw: JSONObject, val sessionId: String) : OpencodeEvent()
    data class PermissionRequested(override val type: String, override val raw: JSONObject, val sessionId: String, val permissionId: String, val tool: String?) : OpencodeEvent()
    data class FileEdited(override val type: String, override val raw: JSONObject, val path: String) : OpencodeEvent()

    data class Unknown(override val type: String, override val raw: JSONObject) : OpencodeEvent()

    companion object {
        /** 将 SSE 行解析后的 JSON 对象映射为强类型事件。 */
        fun parse(envelope: JSONObject): OpencodeEvent {
            val type = envelope.optString("type", "")
            val props = envelope.optJSONObject("properties") ?: JSONObject()
            return when (type) {
                "server.connected" -> ServerConnected(type, envelope)
                "session.created" -> SessionCreated(type, envelope, props.optStringOrNull("info.id") ?: props.optStringNested("info", "id") ?: "")
                "session.updated" -> SessionUpdated(type, envelope, props.optStringNested("info", "id") ?: "")
                "session.deleted" -> SessionDeleted(type, envelope, props.optStringNested("info", "id") ?: props.optString("sessionID", ""))
                "session.status" -> SessionStatus(type, envelope, props.optString("sessionID", ""), props.optString("status", ""))
                "message.updated" -> MessageUpdated(type, envelope, props.optStringNested("info", "sessionID") ?: "", props.optStringNested("info", "id") ?: "")
                "message.part.updated", "part.updated" -> PartUpdated(
                    type, envelope,
                    props.optStringNested("info", "sessionID") ?: props.optString("sessionID", ""),
                    props.optStringNested("info", "messageID") ?: props.optString("messageID", ""),
                    props.optStringNested("info", "id")
                )
                "session.todo.updated", "todo.updated" -> TodoUpdated(type, envelope, props.optString("sessionID", ""))
                "permission.updated", "permission.requested" -> PermissionRequested(
                    type, envelope,
                    props.optStringNested("info", "sessionID") ?: props.optString("sessionID", ""),
                    props.optStringNested("info", "id") ?: props.optString("permissionID", ""),
                    props.optStringNested("info", "tool")
                )
                "file.edited", "file.updated" -> FileEdited(type, envelope, props.optString("file", props.optString("path", "")))
                else -> Unknown(type, envelope)
            }
        }

        private fun JSONObject.optStringOrNull(key: String): String? {
            if (!has(key) || isNull(key)) return null
            return optString(key, "").takeIf { it.isNotEmpty() }
        }

        private fun JSONObject.optStringNested(vararg path: String): String? {
            var cur: JSONObject? = this
            for ((i, k) in path.withIndex()) {
                if (cur == null) return null
                if (i == path.lastIndex) return cur.optStringOrNull(k)
                cur = cur.optJSONObject(k)
            }
            return null
        }
    }
}
