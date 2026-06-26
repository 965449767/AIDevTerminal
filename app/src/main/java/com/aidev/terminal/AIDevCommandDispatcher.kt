package com.aidev.terminal

import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

object AIDevCommandDispatcher {

    private const val CHANNEL_ID = Constants.NOTIFICATION_CHANNEL_ID

    fun notify(context: Context, title: String, msg: String, priority: String? = null, ongoing: Boolean = false, alertOnlyOnce: Boolean = false) {
        dragLog("notify: title=$title msg=$msg priority=$priority ongoing=$ongoing alertOnlyOnce=$alertOnlyOnce")
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 33) {
            if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                dragLog("notify: POST_NOTIFICATIONS not granted")
                Toast.makeText(context, "需要通知权限才能显示通知", Toast.LENGTH_LONG).show()
                val act = AIDevApp.getCurrentActivity()
                if (act != null && act.shouldShowRequestPermissionRationale(android.Manifest.permission.POST_NOTIFICATIONS)) {
                    dragLog("notify: showing rationale dialog")
                    AlertDialog.Builder(act)
                        .setTitle("需要通知权限")
                        .setMessage("AIDev Terminal 需要在终端命令中显示系统通知。请在接下来的系统设置中允许通知权限。")
                        .setPositiveButton("去授权") { _, _ ->
                            act.requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 0)
                        }
                        .setNegativeButton("取消", null)
                        .show()
                } else {
                    dragLog("notify: opening notification settings")
                    try {
                        context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            data = Uri.parse("package:${context.packageName}")
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            putExtra(Settings.EXTRA_CHANNEL_ID, channelId(priority))
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                    } catch (e: Exception) {
                        dragLog("notify: failed to open settings: ${e.message}")
                    }
                }
                return
            }
        }
        val chId = channelId(priority)
        ensureChannel(nm, chId, priority)
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            android.app.Notification.Builder(context, chId)
        } else {
            @Suppress("DEPRECATION")
            android.app.Notification.Builder(context)
        }
        if (ongoing) builder.setOngoing(true)
        if (Build.VERSION.SDK_INT >= 26 && alertOnlyOnce) {
            builder.setOnlyAlertOnce(true)
        }
        val notification = builder
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle(title)
            .setContentText(msg)
            .setAutoCancel(!ongoing)
            .build()
        nm.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
        dragLog("notify: notification sent")
    }

    fun setClipboard(context: Context, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("AIDev Terminal", text))
    }

    fun setVolume(context: Context, stream: Int, volume: Int) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        am.setStreamVolume(stream, volume, 0)
    }

    fun setBrightness(context: Context, brightness: Int, auto: Boolean = false) {
        if (auto) {
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 1)
        } else {
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0)
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, brightness)
        }
    }

    fun startApp(context: Context, packageName: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            dragLog("startApp: $packageName launched")
        } else {
            dragLog("startApp: no launch intent for $packageName")
        }
    }

    fun stopApp(context: Context, packageName: String) {
        runCatching {
            val proc = Runtime.getRuntime().exec(arrayOf("/system/bin/am", "force-stop", packageName))
            proc.waitFor()
            dragLog("stopApp: $packageName force-stopped")
        }.onFailure { dragLog("stopApp: ${it.message}") }
    }

    fun takeScreenshot(context: Context, path: String?) {
        val outPath = path ?: "/sdcard/screenshot_${java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())}.png"
        runCatching {
            val proc = Runtime.getRuntime().exec(arrayOf("/system/bin/screencap", "-p", outPath))
            proc.waitFor()
            dragLog("screencap: saved to $outPath")
        }.onFailure { dragLog("screencap: ${it.message}") }
    }

    fun installApk(context: Context, apkPath: String) {
        val file = resolvePath(context, apkPath) ?: run {
            dragLog("installApk: file not found $apkPath")
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            context.startActivity(intent)
            dragLog("installApk: launched installer for $apkPath")
        }.onFailure { dragLog("installApk: ${it.message}") }
    }

    fun uninstallApp(context: Context, packageName: String) {
        val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            context.startActivity(intent)
            dragLog("uninstallApp: launched uninstaller for $packageName")
        }.onFailure { dragLog("uninstallApp: ${it.message}") }
    }

    private fun resolvePath(context: Context, path: String): File? {
        val f = File(path)
        if (f.isFile) return f
        val alt = File(context.filesDir, "home/${path.removePrefix("/host-home/")}")
        if (alt.isFile) return alt
        return null
    }

    private fun ensureChannel(nm: NotificationManager, chId: String, priority: String?) {
        if (Build.VERSION.SDK_INT >= 26) {
            if (nm.getNotificationChannel(chId) == null) {
                val imp = when (priority) {
                    "max" -> NotificationManager.IMPORTANCE_MAX
                    "high" -> NotificationManager.IMPORTANCE_HIGH
                    "low" -> NotificationManager.IMPORTANCE_LOW
                    "min" -> NotificationManager.IMPORTANCE_MIN
                    else -> NotificationManager.IMPORTANCE_DEFAULT
                }
                nm.createNotificationChannel(NotificationChannel(chId, chId, imp))
            }
        }
    }

    private fun channelId(priority: String?): String = when (priority) {
        "min" -> "${CHANNEL_ID}_min"
        "low" -> "${CHANNEL_ID}_low"
        "high" -> "${CHANNEL_ID}_high"
        "max" -> "${CHANNEL_ID}_max"
        else -> CHANNEL_ID
    }

    private fun dragLog(msg: String) {
        runCatching { File("/storage/emulated/0/drag.log").appendText("$msg\n") }
    }
}
