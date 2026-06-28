package com.aidev.terminal
import com.google.android.material.dialog.MaterialAlertDialogBuilder

import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.HttpURLConnection
import java.net.URL

/**
 * 网络诊断页面：ping、HTTP 请求、端口检查、DNS、网络信息
 */
class NetworkDiagnosticsPage : ShellPage {
    private lateinit var activity: Activity
    private lateinit var ui: AIDevUi
    private lateinit var host: ShellHost
    private lateinit var list: LinearLayout
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun create(activity: Activity, ui: AIDevUi, host: ShellHost): View {
        this.activity = activity
        this.ui = ui
        this.host = host
        list = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_8), ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_24))
        }
        reload()
        return ScrollView(activity).apply { addView(list) }
    }

    override fun onSelected(activity: Activity, view: View) {
        if (::list.isInitialized) reload()
    }

    override fun onDestroy(activity: Activity) {
        scope.cancel()
    }

    private fun reload() {
        list.removeAllViews()

        list.addView(ui.section("网络诊断", "ping、HTTP 测试、端口检查、DNS 查询"))

        // 网络状态概览
        list.addView(ui.rowOf(
            ui.listItem("网络", if (isNetworkAvailable()) "已连接" else "未连接", isNetworkAvailable()),
            ui.listItem("WiFi", if (isWifiConnected()) "已连接" else "未连接", isWifiConnected())
        ))

        list.addView(ui.divider())

        // 快捷诊断
        list.addView(ui.section("快捷诊断", "常用网络测试工具"))
        list.addView(ui.actionRow("Ping 测试", "测试到目标主机的连通性") { showPingDialog() })
        list.addView(ui.actionRow("HTTP 请求", "发送 GET/POST 请求并查看响应") { showHttpDialog() })
        list.addView(ui.actionRow("端口检查", "检查本地或远程端口是否开放") { showPortCheckDialog() })
        list.addView(ui.actionRow("DNS 查询", "解析域名到 IP 地址") { showDnsDialog() })
        list.addView(ui.actionRow("本机网络信息", "查看 IP、接口、路由等信息") { showNetworkInfo() })

        list.addView(ui.divider())

        // 常用端口状态（后台检查，避免阻塞主线程）
        list.addView(ui.section("常用端口", "本地常用服务端口状态"))
        val commonPorts = listOf(22, 80, 443, 3000, 8080, 8000, 5000, 9000)
        val portViews = commonPorts.map { port ->
            portStatusItem(port, checking = true)
        }
        portViews.chunked(2).forEach { pair ->
            list.addView(ui.rowOf(
                pair[0],
                if (pair.size > 1) pair[1] else View(activity)
            ))
        }
        // 后台检查端口状态并更新 UI
        scope.launch(Dispatchers.IO) {
            val results = commonPorts.map { port -> port to isLocalPortOpen(port) }
            withContext(Dispatchers.Main) {
                results.forEachIndexed { index, (port, isOpen) ->
                    if (index < portViews.size) {
                        val updated = portStatusItem(port, checking = false, isOpen = isOpen)
                        val parent = portViews[index].parent as? android.view.ViewGroup
                        if (parent != null) {
                            val pos = parent.indexOfChild(portViews[index])
                            if (pos >= 0) {
                                parent.removeViewAt(pos)
                                parent.addView(updated, pos)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun portStatusItem(port: Int, checking: Boolean = false, isOpen: Boolean = false): View {
        val status = when {
            checking -> "检查中..."
            isOpen -> "开放"
            else -> "关闭"
        }
        return ui.listItem("端口 $port", status, isOpen && !checking)
    }

    private fun showPingDialog() {
        val edit = EditText(activity).apply { hint = "输入主机名或IP，如 google.com"; setText("google.com") }
        MaterialAlertDialogBuilder(activity)
            .setTitle("Ping 测试")
            .setView(edit)
            .setPositiveButton("执行") { _, _ ->
                val host = edit.text.toString().trim()
                if (host.isEmpty()) return@setPositiveButton
                if (!isValidHostName(host)) {
                    Toast.makeText(activity, "无效的主机名或 IP", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                executePing(host)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun executePing(host: String) {
        scope.launch(Dispatchers.IO) {
            val result = try {
                val process = Runtime.getRuntime().exec(arrayOf("ping", "-c", "4", "-W", "3", host))
                val output = process.inputStream.bufferedReader().use { it.readText() }
                val error = process.errorStream.bufferedReader().use { it.readText() }
                val exitCode = process.waitFor()
                if (exitCode == 0) output else "Ping 失败 (exit=$exitCode):\n$error\n$output"
            } catch (e: Exception) {
                "执行失败: ${e.message}"
            }
            withContext(Dispatchers.Main) {
                MaterialAlertDialogBuilder(activity)
                    .setTitle("Ping: $host")
                    .setMessage(result.take(4000))
                    .setPositiveButton("复制") { _, _ -> copyText("Ping 结果", result) }
                    .setNeutralButton("再试") { _, _ -> executePing(host) }
                    .setNegativeButton("关闭", null)
                    .show()
            }
        }
    }

    private fun showHttpDialog() {
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(20), ui.dp(10), ui.dp(20), 0)
        }
        val urlEdit = EditText(activity).apply { hint = "URL，如 https://api.github.com"; setText("https://api.github.com") }
        val methodEdit = EditText(activity).apply { hint = "GET / POST / HEAD"; setText("GET") }
        layout.addView(urlEdit)
        layout.addView(methodEdit)

        MaterialAlertDialogBuilder(activity)
            .setTitle("HTTP 请求")
            .setView(layout)
            .setPositiveButton("发送") { _, _ ->
                val url = urlEdit.text.toString().trim()
                val method = methodEdit.text.toString().trim().uppercase()
                if (url.isEmpty()) return@setPositiveButton
                if (!isValidUrl(url)) {
                    Toast.makeText(activity, "URL 必须以 http:// 或 https:// 开头", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                executeHttp(url, method)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun executeHttp(urlStr: String, method: String) {
        scope.launch(Dispatchers.IO) {
            val result = try {
                val url = URL(urlStr)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = method
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                conn.setRequestProperty("User-Agent", "AIDev-Terminal/1.0")
                conn.connect()

                val sb = StringBuilder()
                sb.append("Status: ${conn.responseCode} ${conn.responseMessage}\n")
                sb.append("Time: ${conn.date}\n\n")
                sb.append("Headers:\n")
                conn.headerFields.forEach { (k, v) ->
                    if (k != null) sb.append("$k: ${v.joinToString(", ")}\n")
                }

                if (method == "GET") {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    sb.append("\nBody (first 2000 chars):\n${body.take(2000)}")
                }
                conn.disconnect()
                sb.toString()
            } catch (e: Exception) {
                "请求失败: ${e.message}"
            }
            withContext(Dispatchers.Main) {
                MaterialAlertDialogBuilder(activity)
                    .setTitle("HTTP $method")
                    .setMessage(result.take(4000))
                    .setPositiveButton("复制") { _, _ -> copyText("HTTP 响应", result) }
                    .setNegativeButton("关闭", null)
                    .show()
            }
        }
    }

    private fun showPortCheckDialog() {
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(20), ui.dp(10), ui.dp(20), 0)
        }
        val hostEdit = EditText(activity).apply { hint = "主机，如 127.0.0.1"; setText("127.0.0.1") }
        val portEdit = EditText(activity).apply { hint = "端口，如 8080"; setText("8080") }
        layout.addView(hostEdit)
        layout.addView(portEdit)

        MaterialAlertDialogBuilder(activity)
            .setTitle("端口检查")
            .setView(layout)
            .setPositiveButton("检查") { _, _ ->
                val host = hostEdit.text.toString().trim()
                val port = portEdit.text.toString().trim().toIntOrNull()
                if (host.isEmpty() || port == null) return@setPositiveButton
                if (!isValidHostName(host)) {
                    Toast.makeText(activity, "无效的主机名或 IP", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                checkPort(host, port)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun checkPort(host: String, port: Int) {
        scope.launch(Dispatchers.IO) {
            val (isOpen, timeMs) = try {
                val start = System.currentTimeMillis()
                Socket().use { socket ->
                    socket.connect(java.net.InetSocketAddress(host, port), 3000)
                }
                Pair(true, System.currentTimeMillis() - start)
            } catch (e: Exception) {
                Pair(false, 0L)
            }
            val result = if (isOpen) {
                "端口 $port 在 $host 上开放\n响应时间: ${timeMs}ms"
            } else {
                "端口 $port 在 $host 上关闭或不可达"
            }
            withContext(Dispatchers.Main) {
                MaterialAlertDialogBuilder(activity)
                    .setTitle("端口检查结果")
                    .setMessage(result)
                    .setPositiveButton("复制") { _, _ -> copyText("端口检查", result) }
                    .setNegativeButton("关闭", null)
                    .show()
            }
        }
    }

    private fun showDnsDialog() {
        val edit = EditText(activity).apply { hint = "输入域名，如 google.com"; setText("google.com") }
        MaterialAlertDialogBuilder(activity)
            .setTitle("DNS 查询")
            .setView(edit)
            .setPositiveButton("查询") { _, _ ->
                val domain = edit.text.toString().trim()
                if (domain.isEmpty()) return@setPositiveButton
                if (!isValidDomain(domain)) {
                    Toast.makeText(activity, "无效的域名格式", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                executeDns(domain)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun executeDns(domain: String) {
        scope.launch(Dispatchers.IO) {
            val result = try {
                val addresses = InetAddress.getAllByName(domain)
                addresses.joinToString("\n") { addr ->
                    "${addr.hostAddress}  ${if (addr is java.net.Inet6Address) "IPv6" else "IPv4"}"
                }
            } catch (e: Exception) {
                "解析失败: ${e.message}"
            }
            withContext(Dispatchers.Main) {
                MaterialAlertDialogBuilder(activity)
                    .setTitle("DNS: $domain")
                    .setMessage(result)
                    .setPositiveButton("复制") { _, _ -> copyText("DNS 结果", result) }
                    .setNegativeButton("关闭", null)
                    .show()
            }
        }
    }

    private fun showNetworkInfo() {
        val sb = StringBuilder()
        try {
            sb.append("=== 网络接口 ===\n")
            NetworkInterface.getNetworkInterfaces().toList().forEach { ni ->
                if (ni.isUp) {
                    sb.append("${ni.displayName} (${ni.name})\n")
                    ni.inetAddresses.toList().forEach { addr ->
                        if (!addr.isLoopbackAddress) {
                            sb.append("  ${addr.hostAddress}\n")
                        }
                    }
                }
            }

            sb.append("\n=== 连接信息 ===\n")
            val cm = activity.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            if (Build.VERSION.SDK_INT >= 23) {
                cm.activeNetwork?.let { network ->
                    cm.getNetworkCapabilities(network)?.let { caps ->
                        sb.append("WiFi: ${caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)}\n")
                        sb.append("Cellular: ${caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)}\n")
                        sb.append("VPN: ${caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)}\n")
                    }
                }
            }

            sb.append("\n=== 路由 ===\n")
            val routeProcess = Runtime.getRuntime().exec(arrayOf("sh", "-c", "ip route 2>/dev/null || route -n"))
            val route = routeProcess.inputStream.bufferedReader().use { it.readText() }
            routeProcess.errorStream?.bufferedReader()?.use { it.readText() }
            routeProcess.waitFor()
            sb.append(route.take(1000))
        } catch (e: Exception) {
            sb.append("获取信息失败: ${e.message}")
        }

        MaterialAlertDialogBuilder(activity)
            .setTitle("网络信息")
            .setMessage(sb.toString())
            .setPositiveButton("复制") { _, _ -> copyText("网络信息", sb.toString()) }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun isNetworkAvailable(): Boolean {
        return try {
            val cm = activity.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            if (Build.VERSION.SDK_INT >= 23) {
                cm.activeNetwork?.let { network ->
                    cm.getNetworkCapabilities(network)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                } ?: false
            } else {
                @Suppress("DEPRECATION")
                cm.activeNetworkInfo?.isConnected == true
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun isWifiConnected(): Boolean {
        return try {
            val cm = activity.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            if (Build.VERSION.SDK_INT >= 23) {
                cm.activeNetwork?.let { network ->
                    cm.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                } ?: false
            } else {
                @Suppress("DEPRECATION")
                cm.activeNetworkInfo?.type == ConnectivityManager.TYPE_WIFI
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun isLocalPortOpen(port: Int): Boolean {
        return try {
            Socket().use { it.connect(java.net.InetSocketAddress("127.0.0.1", port), 500) }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun isValidHostName(host: String): Boolean {
        if (host.length > 253) return false
        // IPv4: digits and dots; IPv6: hex and colons; hostname: alphanum, dots, hyphens
        return host.matches(Regex("^([\\w\\-.]+|\\d{1,3}(\\.\\d{1,3}){3}|[\\da-fA-F:]+)$"))
    }

    private fun isValidDomain(domain: String): Boolean {
        if (domain.length > 253) return false
        return domain.matches(Regex("^[\\w][\\w\\-]*([.][\\w][\\w\\-]*)*$"))
    }

    private fun isValidUrl(url: String): Boolean {
        return url.startsWith("http://") || url.startsWith("https://")
    }

    private fun copyText(label: String, text: String) {
        ClipboardHelper.copy(activity, label, text)
        Toast.makeText(activity, "已复制", Toast.LENGTH_SHORT).show()
    }
}
