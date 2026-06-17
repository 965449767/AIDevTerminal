package com.aidev.terminal

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 内部命令广播接收器。
 * 仅接收来自本应用的广播（exported="false"），转发到 AIDevCommandDispatcher。
 */
class SysCommandReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SysCommandReceiver"

        // 内部 Action，不暴露给外部应用
        const val ACTION_NOTIFY = Constants.Actions.NOTIFY
        const val ACTION_CLIP = Constants.Actions.CLIP
        const val ACTION_VOLUME = Constants.Actions.VOLUME
        const val ACTION_BRIGHTNESS = Constants.Actions.BRIGHTNESS

        /** 发送内部广播（仅本应用可用） */
        fun send(context: Context, action: String, extras: (Intent.() -> Unit)? = null) {
            val intent = Intent(action).setPackage(context.packageName)
            extras?.invoke(intent)
            context.sendBroadcast(intent)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_NOTIFY -> {
                val title = intent.getStringExtra("title") ?: "AIDev Terminal"
                val msg = intent.getStringExtra("msg") ?: ""
                AIDevCommandDispatcher.notify(context, title, msg)
            }
            ACTION_CLIP -> {
                val text = intent.getStringExtra("text") ?: ""
                AIDevCommandDispatcher.setClipboard(context, text)
            }
            ACTION_VOLUME -> {
                val stream = intent.getIntExtra("stream", 3)
                val volume = intent.getIntExtra("volume", -1)
                if (volume >= 0) {
                    AIDevCommandDispatcher.setVolume(context, stream, volume)
                }
            }
            ACTION_BRIGHTNESS -> {
                val brightness = intent.getIntExtra("brightness", -1)
                val auto = intent.getBooleanExtra("auto", false)
                AIDevCommandDispatcher.setBrightness(context, brightness, auto)
            }
            else -> Log.w(TAG, "Unknown action: ${intent.action}")
        }
    }
}
