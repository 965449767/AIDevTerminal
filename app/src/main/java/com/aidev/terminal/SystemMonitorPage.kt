package com.aidev.terminal
import com.google.android.material.dialog.MaterialAlertDialogBuilder

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.os.Build
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 系统监控页面：CPU、内存、磁盘、网络、进程列表、电池信息
 *
 * 实时刷新周期 3 秒，通过 Handler + postDelayed 实现。
 * onSelected() 启动刷新，onDestroy() 停止刷新。
 */
class SystemMonitorPage : ShellPage {
    private lateinit var activity: Activity
    private lateinit var ui: AIDevUi
    private lateinit var host: ShellHost
    private lateinit var list: LinearLayout
    private lateinit var scrollView: ScrollView

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var refreshJob: Job? = null
    private var isVisible = false
    private var paused = false

    // CPU 采样：保存上一次 /proc/stat 的数据用于计算差值
    private var prevCpuTotal: Long = 0L
    private var prevCpuIdle: Long = 0L
    private var cpuUsagePercent: Float = 0f

    // 网络流量采样：保存上一次 /proc/net/dev 的数据
    private var prevRxBytes: Long = 0L
    private var prevTxBytes: Long = 0L
    private var rxSpeed: Long = 0L
    private var txSpeed: Long = 0L

    // 电池信息
    private var batteryLevel: Int = -1
    private var batteryTemp: Float = 0f
    private var batteryStatus: String = "未知"
    private var batteryHealth: String = "未知"
    private var batteryVoltage: Int = 0
    private var batteryChargingType: String = "未知"

    // 电池广播接收器
    private var batteryReceiver: BroadcastReceiver? = null

    companion object {
        private const val REFRESH_INTERVAL_MS = 3000L
        private const val MAX_PROCESS_COUNT = 15
    }

    override fun create(activity: Activity, ui: AIDevUi, host: ShellHost): View {
        this.activity = activity
        this.ui = ui
        this.host = host
        list = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                ui.dp(DesignTokens.SPACE_12),
                ui.dp(DesignTokens.SPACE_8),
                ui.dp(DesignTokens.SPACE_12),
                ui.dp(DesignTokens.SPACE_24)
            )
        }
        scrollView = ScrollView(activity).apply { addView(list) }

        // 注册电池广播
        registerBatteryReceiver()

        // 初始加载
        refreshData()

        return scrollView
    }

    override fun onSelected(activity: Activity, view: View) {
        if (::list.isInitialized) {
            isVisible = true
            startRefreshing()
        }
    }

    override fun onDestroy(activity: Activity) {
        isVisible = false
        stopRefreshing()
        scope.cancel()
        unregisterBatteryReceiver()
    }

    // ==================== 刷新控制 ====================

    private fun startRefreshing() {
        stopRefreshing()
        refreshJob = scope.launch {
            while (isActive && isVisible) {
                if (!paused) refreshData()
                delay(REFRESH_INTERVAL_MS)
            }
        }
    }

    private fun stopRefreshing() {
        refreshJob?.cancel()
        refreshJob = null
    }

    // ==================== 数据采集 ====================

    private fun refreshData() {
        if (!::list.isInitialized) return
        scope.launch(Dispatchers.IO) {
            // IO 线程采集数据
            collectCpuUsage()
            collectNetworkTraffic()
            val diskInfo = getDiskInfo()
            val processList = getProcessList()
            // Main 线程更新 UI
            withContext(Dispatchers.Main) {
                collectBatteryInfo()
                rebuildUi(diskInfo, processList)
            }
        }
    }

    /** 解析 /proc/stat 计算 CPU 使用率（两次采样差值法） */
    private fun collectCpuUsage() {
        try {
            val lines = File("/proc/stat").readLines()
            val cpuLine = lines.firstOrNull { it.startsWith("cpu ") } ?: return
            val parts = cpuLine.split(Regex("\\s+")).filter { it.isNotBlank() }
            // cpu user nice system idle iowait irq softirq steal guest guest_nice
            if (parts.size < 5) return

            var total: Long = 0
            for (i in 1 until parts.size) {
                total += parts[i].toLongOrNull() ?: 0L
            }
            val idle = (parts[4].toLongOrNull() ?: 0L) + (parts[5].toLongOrNull() ?: 0L)

            if (prevCpuTotal > 0) {
                val diffTotal = total - prevCpuTotal
                val diffIdle = idle - prevCpuIdle
                if (diffTotal > 0) {
                    cpuUsagePercent = ((diffTotal - diffIdle).toFloat() / diffTotal.toFloat()) * 100f
                }
            }
            prevCpuTotal = total
            prevCpuIdle = idle
        } catch (e: Exception) {
            Log.w("SysMon", "collectCpuUsage failed", e)
        }
    }

    /** 解析 /proc/net/dev 计算网络收发速率 */
    private fun collectNetworkTraffic() {
        try {
            val lines = File("/proc/net/dev").readLines()
            var totalRx: Long = 0
            var totalTx: Long = 0
            for (line in lines) {
                if (!line.contains(":")) continue
                val iface = line.substringBefore(":").trim()
                // 跳过 lo 回环接口
                if (iface == "lo") continue
                val parts = line.substringAfter(":").trim().split(Regex("\\s+"))
                if (parts.size >= 10) {
                    totalRx += parts[1].toLongOrNull() ?: 0L
                    totalTx += parts[9].toLongOrNull() ?: 0L
                }
            }
            if (prevRxBytes > 0) {
                rxSpeed = totalRx - prevRxBytes
                txSpeed = totalTx - prevTxBytes
            }
            prevRxBytes = totalRx
            prevTxBytes = totalTx
        } catch (e: Exception) {
            Log.w("SysMon", "collectNetworkTraffic failed", e)
        }
    }

    /** 通过广播获取电池信息 */
    private fun registerBatteryReceiver() {
        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                collectBatteryFromIntent(intent)
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        activity.registerReceiver(batteryReceiver, filter)
    }

    private fun unregisterBatteryReceiver() {
        try {
            batteryReceiver?.let { activity.unregisterReceiver(it) }
        } catch (e: Exception) {
            Log.w("SysMon", "unregisterBatteryReceiver failed", e)
        }
        batteryReceiver = null
    }

    private fun collectBatteryFromIntent(intent: Intent) {
        batteryLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        batteryTemp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f
        batteryVoltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)

        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        batteryStatus = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "充电中"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "放电中"
            BatteryManager.BATTERY_STATUS_FULL -> "已充满"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "未充电"
            else -> "未知"
        }

        val health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
        batteryHealth = when (health) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "良好"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "过热"
            BatteryManager.BATTERY_HEALTH_COLD -> "过冷"
            BatteryManager.BATTERY_HEALTH_DEAD -> "耗尽"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "过压"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "故障"
            else -> "未知"
        }

        val plug = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        batteryChargingType = when {
            plug and BatteryManager.BATTERY_PLUGGED_AC != 0 -> "AC 电源"
            plug and BatteryManager.BATTERY_PLUGGED_USB != 0 -> "USB"
            plug and BatteryManager.BATTERY_PLUGGED_WIRELESS != 0 -> "无线充电"
            else -> "未连接"
        }
    }

    private fun collectBatteryInfo() {
        // 使用 BatteryManager API 获取基本电池信息（替代废弃的粘滞广播）
        try {
            val bm = activity.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return
            val cap = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            if (cap >= 0) batteryLevel = cap
        } catch (e: Exception) {
            Log.w("SysMon", "collectBatteryInfo failed", e)
        }
    }

    /** 解析 /proc/meminfo 获取内存信息 */
    private fun getMemoryInfo(): MemInfo {
        val info = MemInfo()
        try {
            val lines = File("/proc/meminfo").readLines()
            for (line in lines) {
                val pair = line.split(Regex(":"), limit = 2).let {
                    if (it.size == 2) it[0].trim() to it[1].trim() else null
                } ?: continue
                val key = pair.first
                val value = pair.second
                val kb = value.removeSuffix(" kB").trim().toLongOrNull() ?: continue
                when {
                    key == "MemTotal" -> info.memTotal = kb
                    key == "MemAvailable" -> info.memAvailable = kb
                    key == "MemFree" -> info.memFree = kb
                    key == "Buffers" -> info.buffers = kb
                    key == "Cached" -> info.cached = kb
                    key == "SwapTotal" -> info.swapTotal = kb
                    key == "SwapFree" -> info.swapFree = kb
                }
            }
        } catch (e: Exception) {
            Log.w("SysMon", "getMemoryInfo failed", e)
        }
        return info
    }

    /** 执行 df -h 获取磁盘使用信息 */
    private fun getDiskInfo(): List<DiskInfo> {
        val result = mutableListOf<DiskInfo>()
        try {
            val process = Runtime.getRuntime().exec(arrayOf("df", "-h"))
            process.inputStream.bufferedReader().use { reader ->
                var line: String?
                var first = true
                while (reader.readLine().also { line = it } != null) {
                    if (first) { first = false; continue }
                    val lineStr = line ?: continue
                    val parts = lineStr.trim().split(Regex("\\s+"))
                    if (parts.size >= 6) {
                        result.add(DiskInfo(
                            filesystem = parts[0],
                            size = parts[1],
                            used = parts[2],
                            available = parts[3],
                            usePercent = parts[4],
                            mountedOn = parts[5]
                        ))
                    }
                }
            }
            process.errorStream?.bufferedReader()?.use { it.readText() }
            process.waitFor()
        } catch (e: Exception) {
            Log.w("SysMon", "getDiskInfo failed", e)
        }
        return result
    }

    /** 执行 ps aux --sort=-%cpu 获取进程列表 */
    private fun getProcessList(): List<ProcessInfo> {
        val result = mutableListOf<ProcessInfo>()
        try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "ps aux --sort=-%cpu 2>/dev/null || ps aux"))
            process.inputStream.bufferedReader().use { reader ->
                var line: String?
                var first = true
                while (reader.readLine().also { line = it } != null) {
                    if (first) { first = false; continue }
                    val lineStr = line ?: continue
                    val parts = lineStr.trim().split(Regex("\\s+"))
                    if (parts.size >= 11) {
                        result.add(ProcessInfo(
                            user = parts[0],
                            pid = parts[1],
                            cpu = parts[2],
                            mem = parts[3],
                            vsz = parts[4],
                            rss = parts[5],
                            stat = parts[7].takeIf { parts.size > 7 } ?: "",
                            command = parts.drop(10).joinToString(" ")
                        ))
                    }
                }
            }
            process.errorStream?.bufferedReader()?.use { it.readText() }
            process.waitFor()
        } catch (e: Exception) {
            Log.w("SysMon", "getProcessList failed", e)
        }
        return result.take(MAX_PROCESS_COUNT)
    }

    // ==================== UI 构建 ====================

    private fun rebuildUi(diskInfo: List<DiskInfo>, processes: List<ProcessInfo>) {
        list.removeAllViews()

        // 暂停/恢复按钮行
        val titleRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_8), ui.dp(DesignTokens.SPACE_12), 0)
            addView(ui.text("系统监控", DesignTokens.TEXT_H2, ui.palette.text, bold = true).apply {
                setPadding(0, 0, ui.dp(8), 0)
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(TextView(activity).apply {
                text = if (paused) "▶ 恢复" else "⏸ 暂停"
                textSize = DesignTokens.TEXT_BODY
                setTextColor(if (paused) ui.palette.success else ui.palette.warning)
                setOnClickListener {
                    paused = !paused
                    if (!paused) refreshData()
                    rebuildUi(getDiskInfo(), getProcessList())
                }
            })
        }
        list.addView(titleRow)
        if (paused) {
            list.addView(ui.muted("已暂停，点击「恢复」继续监控").apply { setPadding(ui.dp(DesignTokens.SPACE_12), 0, 0, ui.dp(DesignTokens.SPACE_8)) })
        }

        // 资源概览卡片（2x2 网格）
        list.addView(buildResourceCards())

        list.addView(ui.divider())

        // 磁盘详情
        list.addView(ui.section("磁盘使用", "分区使用情况"))
        if (diskInfo.isEmpty()) {
            list.addView(ui.muted("无法获取磁盘信息"))
        } else {
            diskInfo.forEach { disk ->
                list.addView(buildDiskRow(disk))
            }
        }

        list.addView(ui.divider())

        // 进程列表
        list.addView(ui.section("进程列表 (Top $MAX_PROCESS_COUNT)", "按 CPU 使用率排序"))
        if (processes.isEmpty()) {
            list.addView(ui.muted("无法获取进程列表"))
        } else {
            processes.forEach { proc ->
                list.addView(buildProcessRow(proc))
            }
        }

        list.addView(ui.divider())

        // 电池信息
        list.addView(ui.section("电池与温度", "实时电池状态"))
        list.addView(buildBatterySection())

    }

    /** 构建 2x2 资源概览卡片 */
    private fun buildResourceCards(): View {
        val memInfo = getMemoryInfo()
        val memUsed = memInfo.memTotal - memInfo.memAvailable
        val memPercent = if (memInfo.memTotal > 0) {
            (memUsed.toFloat() / memInfo.memTotal.toFloat() * 100f)
        } else 0f

        val row1 = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            isBaselineAligned = false
            addView(buildMetricCard("CPU", String.format("%.1f%%", cpuUsagePercent), cpuUsagePercent), LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(0, 0, ui.dp(4), ui.dp(4)) })
            addView(buildMetricCard("内存", String.format("%.0f%%", memPercent), memPercent), LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(ui.dp(4), 0, 0, ui.dp(4)) })
        }
        val row2 = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            isBaselineAligned = false
            val diskPercent = parseDiskPercent()
            addView(buildMetricCard("磁盘", diskPercent, diskPercent.removeSuffix("%").toFloatOrNull() ?: 0f), LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(0, 0, ui.dp(4), ui.dp(4)) })
            addView(buildNetworkCard(), LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(ui.dp(4), 0, 0, ui.dp(4)) })
        }

        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(row1)
            addView(row2)
        }
    }

    /** 单个指标卡片 */
    private fun buildMetricCard(label: String, value: String, percent: Float): View {
        val color = when {
            percent < 60f -> ui.palette.success
            percent < 85f -> ui.palette.warning
            else -> ui.palette.danger
        }
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(ui.dp(DesignTokens.SPACE_8), ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_8), ui.dp(DesignTokens.SPACE_12))
            background = GradientDrawable().apply {
                setColor(ui.palette.surface)
                cornerRadius = ui.dp(DesignTokens.RADIUS_MD).toFloat()
            }
            addView(ui.text(label, DesignTokens.TEXT_CAPTION, ui.palette.muted))
            addView(ui.text(value, DesignTokens.TEXT_H2, color, bold = true).apply {
                setPadding(0, ui.dp(DesignTokens.SPACE_4), 0, 0)
            })
        }
    }

    /** 网络流量卡片 */
    private fun buildNetworkCard(): View {
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(ui.dp(DesignTokens.SPACE_8), ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_8), ui.dp(DesignTokens.SPACE_12))
            background = GradientDrawable().apply {
                setColor(ui.palette.surface)
                cornerRadius = ui.dp(DesignTokens.RADIUS_MD).toFloat()
            }
            addView(ui.text("网络", DesignTokens.TEXT_CAPTION, ui.palette.muted))
            addView(ui.text(formatSpeed(rxSpeed) + " / " + formatSpeed(txSpeed), DesignTokens.TEXT_BODY, ui.palette.accent, bold = true).apply {
                setPadding(0, ui.dp(DesignTokens.SPACE_4), 0, 0)
            })
            addView(ui.muted("收 / 发").apply {
                setPadding(0, ui.dp(DesignTokens.SPACE_2), 0, 0)
                gravity = Gravity.CENTER
            })
        }
    }

    /** 磁盘行 */
    private fun buildDiskRow(disk: DiskInfo): View {
        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_4), ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_4))
            addView(ui.text(disk.mountedOn, DesignTokens.TEXT_CAPTION, ui.palette.muted), LinearLayout.LayoutParams(0, -2, 1f).apply {
                setMargins(0, 0, ui.dp(DesignTokens.SPACE_8), 0)
            })
            addView(ui.text("${disk.used}/${disk.size}", DesignTokens.TEXT_CAPTION, ui.palette.text), LinearLayout.LayoutParams(-2, -2).apply {
                setMargins(0, 0, ui.dp(DesignTokens.SPACE_8), 0)
            })
            val pct = disk.usePercent.removeSuffix("%").toFloatOrNull() ?: 0f
            val pctColor = when {
                pct < 60f -> ui.palette.success
                pct < 85f -> ui.palette.warning
                else -> ui.palette.danger
            }
            addView(ui.text(disk.usePercent, DesignTokens.TEXT_BODY, pctColor, bold = true))
        }
    }

    /** 进程行：点击查看详情，长按 kill */
    private fun buildProcessRow(proc: ProcessInfo): View {
        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_4), ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_4))
            isClickable = true
            isFocusable = true

            // 点击查看详情
            setOnClickListener {
                ui.pulse()
                showProcessDetail(proc)
            }

            // 长按 kill 进程
            setOnLongClickListener {
                ui.pulse()
                confirmKillProcess(proc)
                true
            }

            addView(ui.text(proc.pid, DesignTokens.TEXT_CAPTION, ui.palette.muted), LinearLayout.LayoutParams(-2, -2).apply {
                setMargins(0, 0, ui.dp(DesignTokens.SPACE_8), 0)
            })
            addView(ui.text(proc.command.take(20), DesignTokens.TEXT_BODY, ui.palette.text, bold = true), LinearLayout.LayoutParams(0, -2, 1f).apply {
                setMargins(0, 0, ui.dp(DesignTokens.SPACE_8), 0)
            })
            addView(ui.text("${proc.cpu}%", DesignTokens.TEXT_CAPTION, ui.palette.accent), LinearLayout.LayoutParams(-2, -2).apply {
                setMargins(0, 0, ui.dp(DesignTokens.SPACE_4), 0)
            })
            addView(ui.text("${proc.mem}%", DesignTokens.TEXT_CAPTION, ui.palette.warning))
        }
    }

    /** 电池信息区域 */
    private fun buildBatterySection(): View {
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_4), ui.dp(DesignTokens.SPACE_12), ui.dp(DesignTokens.SPACE_4))
        }

        if (batteryLevel < 0) {
            container.addView(ui.muted("无法获取电池信息"))
            return container
        }

        // 第一行：电量 + 温度
        val row1 = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            isBaselineAligned = false
            addView(ui.listItem("电量", "${batteryLevel}%", batteryLevel > 20), LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(0, 0, ui.dp(6), ui.dp(4)) })
            addView(ui.listItem("温度", String.format("%.1f C", batteryTemp), batteryTemp < 40f), LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(ui.dp(6), 0, 0, ui.dp(4)) })
        }
        // 第二行：状态 + 充电方式
        val row2 = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            isBaselineAligned = false
            addView(ui.listItem("状态", batteryStatus, batteryStatus == "充电中" || batteryStatus == "已充满"), LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(0, 0, ui.dp(6), ui.dp(4)) })
            addView(ui.listItem("充电方式", batteryChargingType, batteryChargingType != "未连接"), LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(ui.dp(6), 0, 0, ui.dp(4)) })
        }
        // 第三行：健康 + 电压
        val row3 = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            isBaselineAligned = false
            addView(ui.listItem("电池健康", batteryHealth, batteryHealth == "良好"), LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(0, 0, ui.dp(6), ui.dp(4)) })
            addView(ui.listItem("电压", "${batteryVoltage}mV", true), LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(ui.dp(6), 0, 0, ui.dp(4)) })
        }

        container.addView(row1)
        container.addView(row2)
        container.addView(row3)
        return container
    }

    // ==================== 交互对话框 ====================

    /** 显示进程详情 */
    private fun showProcessDetail(proc: ProcessInfo) {
        val detail = """
            PID: ${proc.pid}
            用户: ${proc.user}
            CPU: ${proc.cpu}%
            内存: ${proc.mem}%
            虚拟内存: ${proc.vsz} KB
            物理内存: ${proc.rss} KB
            状态: ${proc.stat}
            命令: ${proc.command}
        """.trimIndent()

        MaterialAlertDialogBuilder(activity)
            .setTitle("进程详情")
            .setMessage(detail)
            .setPositiveButton("复制") { _, _ ->
                ClipboardHelper.copy(activity, "进程详情", detail)
                Toast.makeText(activity, "已复制", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("Kill") { _, _ ->
                confirmKillProcess(proc)
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    /** 确认 kill 进程 */
    private fun confirmKillProcess(proc: ProcessInfo) {
        MaterialAlertDialogBuilder(activity)
            .setTitle("终止进程")
            .setMessage("确定要终止进程 ${proc.pid} (${proc.command.take(30)}) 吗？")
            .setPositiveButton("终止") { _, _ ->
                killProcess(proc.pid)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /** 执行 kill 命令 */
    private fun killProcess(pid: String) {
        scope.launch(Dispatchers.IO) {
            val result = try {
                val process = Runtime.getRuntime().exec(arrayOf("kill", pid))
                process.inputStream?.bufferedReader()?.use { it.readText() }
                process.errorStream?.bufferedReader()?.use { it.readText() }
                val exitCode = process.waitFor()
                if (exitCode == 0) "进程 $pid 已终止" else "终止失败 (exit=$exitCode)"
            } catch (e: Exception) {
                "终止失败: ${e.message}"
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(activity, result, Toast.LENGTH_SHORT).show()
                refreshData()
            }
        }
    }

    // ==================== 工具方法 ====================

    /** 格式化字节数为可读速度 */
    private fun formatSpeed(bytesPerSec: Long): String {
        return when {
            bytesPerSec < 1024 -> "${bytesPerSec} B/s"
            bytesPerSec < 1024 * 1024 -> String.format("%.1f KB/s", bytesPerSec / 1024.0)
            bytesPerSec < 1024L * 1024 * 1024 -> String.format("%.1f MB/s", bytesPerSec / (1024.0 * 1024.0))
            else -> String.format("%.2f GB/s", bytesPerSec / (1024.0 * 1024.0 * 1024.0))
        }
    }

    /** 解析根分区磁盘使用百分比 */
    private fun parseDiskPercent(): String {
        return try {
            val diskInfo = getDiskInfo()
            // 优先显示 / 分区
            val rootDisk = diskInfo.firstOrNull { it.mountedOn == "/" }
                ?: diskInfo.firstOrNull()
            rootDisk?.usePercent ?: "N/A"
        } catch (e: Exception) {
            Log.w("SysMon", "parseDiskPercent failed", e)
            "N/A"
        }
    }

    // ==================== 数据类 ====================

    private data class MemInfo(
        var memTotal: Long = 0L,
        var memAvailable: Long = 0L,
        var memFree: Long = 0L,
        var buffers: Long = 0L,
        var cached: Long = 0L,
        var swapTotal: Long = 0L,
        var swapFree: Long = 0L
    )

    private data class DiskInfo(
        val filesystem: String,
        val size: String,
        val used: String,
        val available: String,
        val usePercent: String,
        val mountedOn: String
    )

    private data class ProcessInfo(
        val user: String,
        val pid: String,
        val cpu: String,
        val mem: String,
        val vsz: String,
        val rss: String,
        val stat: String,
        val command: String
    )
}
