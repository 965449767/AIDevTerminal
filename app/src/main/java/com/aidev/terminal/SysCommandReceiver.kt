package com.aidev.terminal

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build

class SysCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "com.aidev.terminal.SYSNOTIFY" -> {
                val title = intent.getStringExtra("title") ?: "AIDev Terminal"
                val msg = intent.getStringExtra("msg") ?: ""
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val channelId = "aidev_terminal"
                if (Build.VERSION.SDK_INT >= 26) {
                    nm.createNotificationChannel(NotificationChannel(channelId, "AIDev Terminal", NotificationManager.IMPORTANCE_DEFAULT))
                }
                val builder = if (Build.VERSION.SDK_INT >= 26) {
                    android.app.Notification.Builder(context, channelId)
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
            "com.aidev.terminal.SYSCLIP" -> {
                val text = intent.getStringExtra("text") ?: ""
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("AIDev Terminal", text))
            }
            "com.aidev.terminal.SYSVOLUME" -> {
                val stream = intent.getIntExtra("stream", 3)
                val volume = intent.getIntExtra("volume", -1)
                if (volume >= 0) {
                    val am = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                    am.setStreamVolume(stream, volume, 0)
                }
            }
            "com.aidev.terminal.SYSBRIGHTNESS" -> {
                val brightness = intent.getIntExtra("brightness", -1)
                val auto = intent.getBooleanExtra("auto", false)
                if (auto) {
                    android.provider.Settings.System.putInt(context.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE, 1)
                } else if (brightness >= 0) {
                    android.provider.Settings.System.putInt(context.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE, 0)
                    android.provider.Settings.System.putInt(context.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS, brightness)
                }
            }
        }
    }
}
