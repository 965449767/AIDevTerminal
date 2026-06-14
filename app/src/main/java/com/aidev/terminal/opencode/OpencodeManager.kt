package com.aidev.terminal.opencode

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * OpenCode 客户端的进程内单例 + 健康轮询协调器。
 *
 * 职责:
 *   1. 提供全局唯一的 [OpencodeClient]（按 baseUrl/token 配置生成）；
 *   2. 周期探测 `GET /global/health`，把状态广播给 UI；
 *   3. 持久化最后一次健康状态，UI 可以"零网络首屏渲染"。
 *
 * 不负责进程拉起 —— `opencode serve` 由用户 (或后续阶段的 RootService) 在 PRoot 内启动。
 * Phase 1 阶段，客户端只读不写，因此即便 server 没起来也不会阻塞应用启动。
 */
object OpencodeManager {

    private const val TAG = "OpencodeManager"
    private const val PREFS = "aidev_opencode"
    private const val KEY_BASE = "base_url"
    private const val KEY_USER = "username"
    private const val KEY_TOKEN = "token"
    private const val KEY_LAST_VERSION = "last_version"
    private const val KEY_LAST_HEALTHY = "last_healthy"
    private const val KEY_LAST_AT = "last_at"

    private val main = Handler(Looper.getMainLooper())

    @Volatile private var clientCache: OpencodeClient? = null
    @Volatile private var pollerStarted = false
    private val listeners = mutableListOf<HealthListener>()

    @Volatile var lastStatus: HealthStatus = HealthStatus(false, "", 0L, "")
        private set

    interface HealthListener { fun onHealth(status: HealthStatus) }

    data class HealthStatus(
        val healthy: Boolean,
        val version: String,
        val timestampMs: Long,
        val baseUrl: String,
        val errorMessage: String? = null
    )

    fun client(context: Context): OpencodeClient {
        val cached = clientCache
        if (cached != null) return cached
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val base = prefs.getString(KEY_BASE, "http://127.0.0.1:4096")!!
        val user = prefs.getString(KEY_USER, "opencode")!!
        val token = prefs.getString(KEY_TOKEN, null)
        return OpencodeClient(base, user, token).also { clientCache = it }
    }

    fun configure(context: Context, baseUrl: String, username: String = "opencode", token: String? = null) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_BASE, baseUrl)
            .putString(KEY_USER, username)
            .apply {
                if (token == null) remove(KEY_TOKEN) else putString(KEY_TOKEN, token)
            }
            .apply()
        clientCache = null // 强制下次重建
    }

    fun addListener(l: HealthListener) {
        synchronized(listeners) { if (!listeners.contains(l)) listeners.add(l) }
        l.onHealth(lastStatus)
    }

    fun removeListener(l: HealthListener) {
        synchronized(listeners) { listeners.remove(l) }
    }

    /** 主动探测一次健康（异步，结果通过监听器回调）。 */
    fun probeNow(context: Context) {
        val client = client(context)
        Thread({
            val ts = System.currentTimeMillis()
            val status = when (val r = client.health()) {
                is OpencodeClient.HttpResult.Success ->
                    HealthStatus(r.value.healthy, r.value.version, ts, client.baseUrl)
                is OpencodeClient.HttpResult.Failure ->
                    HealthStatus(false, lastStatus.version, ts, client.baseUrl, "HTTP ${r.status}: ${r.message}")
            }
            persistAndDispatch(context, status)
        }, "opencode-health").apply { isDaemon = true; start() }
    }

    /** 启动定期健康探测；调用多次也只会启动一次。 */
    fun ensurePolling(context: Context, intervalMs: Long = 15_000L) {
        if (pollerStarted) return
        pollerStarted = true
        loadLast(context)
        val app = context.applicationContext
        val tick = object : Runnable {
            override fun run() {
                probeNow(app)
                main.postDelayed(this, intervalMs)
            }
        }
        main.post(tick)
    }

    private fun loadLast(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        lastStatus = HealthStatus(
            healthy = prefs.getBoolean(KEY_LAST_HEALTHY, false),
            version = prefs.getString(KEY_LAST_VERSION, "") ?: "",
            timestampMs = prefs.getLong(KEY_LAST_AT, 0L),
            baseUrl = prefs.getString(KEY_BASE, "http://127.0.0.1:4096") ?: "http://127.0.0.1:4096"
        )
    }

    private fun persistAndDispatch(context: Context, status: HealthStatus) {
        lastStatus = status
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_LAST_HEALTHY, status.healthy)
            .putString(KEY_LAST_VERSION, status.version)
            .putLong(KEY_LAST_AT, status.timestampMs)
            .apply()
        val snapshot = synchronized(listeners) { listeners.toList() }
        main.post {
            snapshot.forEach { runCatching { it.onHealth(status) }.onFailure { Log.w(TAG, "listener error", it) } }
        }
    }
}
