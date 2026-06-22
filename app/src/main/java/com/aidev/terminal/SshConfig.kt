package com.aidev.terminal

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class SshConnection(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val host: String,
    val port: Int = 22,
    val user: String = "root",
    val lastConnected: Long = 0L
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("host", host)
        put("port", port)
        put("user", user)
        put("lastConnected", lastConnected)
    }

    fun connectCommand(): String = "ssh $user@$host -p $port"

    companion object {
        fun fromJson(obj: JSONObject): SshConnection = SshConnection(
            id = obj.optString("id", UUID.randomUUID().toString()),
            name = obj.optString("name", ""),
            host = obj.optString("host", ""),
            port = obj.optInt("port", 22),
            user = obj.optString("user", "root"),
            lastConnected = obj.optLong("lastConnected", 0L)
        )
    }
}

object SshConfigManager {
    private const val PREFS_NAME = "aidev_ssh"
    private const val KEY_CONNECTIONS = "connections"

    fun getAll(context: Context): List<SshConnection> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_CONNECTIONS, "[]") ?: "[]"
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { SshConnection.fromJson(arr.getJSONObject(it)) }
    }

    fun save(context: Context, connections: List<SshConnection>) {
        val arr = JSONArray(connections.map { it.toJson() })
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CONNECTIONS, arr.toString())
            .apply()
    }

    fun add(context: Context, conn: SshConnection) {
        val list = getAll(context).toMutableList()
        list.add(conn)
        save(context, list)
    }

    fun update(context: Context, conn: SshConnection) {
        val list = getAll(context).toMutableList()
        val idx = list.indexOfFirst { it.id == conn.id }
        if (idx >= 0) {
            list[idx] = conn
            save(context, list)
        }
    }

    fun delete(context: Context, id: String) {
        val list = getAll(context).filter { it.id != id }
        save(context, list)
    }

    fun touch(context: Context, id: String) {
        val list = getAll(context).toMutableList()
        val idx = list.indexOfFirst { it.id == id }
        if (idx >= 0) {
            list[idx] = list[idx].copy(lastConnected = System.currentTimeMillis())
            save(context, list)
        }
    }
}
