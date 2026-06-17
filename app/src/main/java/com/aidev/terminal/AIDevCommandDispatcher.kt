package com.aidev.terminal

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.util.Log

/**
 * AIDev 内部命令调度器。
 * 替代 exported BroadcastReceiver，所有命令通过此单例内部调用，不暴露给其他应用。
 */
object AIDevCommandDispatcher {

    private const val TAG = "AIDevCommandDispatcher"
    private const val CHANNEL_ID = "aidev_terminal"

    /** 发送系统通知 */
    fun notify(context: Context, title: String, msg: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(nm)

        if (Build.VERSION.SDK_INT >= 33) {
            if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "POST_NOTIFICATIONS permission not granted")
                return
            }
        }

        val builder = if (Build.VERSION.SDK_INT >= 26) {
            android.app.Notification.Builder(context, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            android.app.Notification.Builder(context)
        }
        val notification = builder
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle(title)
            .setContentText(msg)
            .setAutoCancel(true)
            .build()
        nm.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
    }

    /** 写入剪贴板 */
    fun setClipboard(context: Context, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("AIDev Terminal", text))
    }

    /** 设置音量 */
    fun setVolume(context: Context, stream: Int, volume: Int) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        am.setStreamVolume(stream, volume, 0)
    }

    /** 设置亮度 */
    fun setBrightness(context: Context, brightness: Int, auto: Boolean = false) {
        if (auto) {
            android.provider.Settings.System.putInt(context.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE, 1)
        } else {
            android.provider.Settings.System.putInt(context.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE, 0)
            android.provider.Settings.System.putInt(context.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS, brightness)
        }
    }

    /** 确保通知渠道存在 */
    private fun ensureChannel(nm: NotificationManager) {
        if (Build.VERSION.SDK_INT >= 26) {
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "AIDev Terminal", NotificationManager.IMPORTANCE_DEFAULT))
            }
        }
    }
}
