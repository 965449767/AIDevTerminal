package com.aidev.terminal

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL

class OpenCodeActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_ABORT -> {
                val sessionId = intent.getStringExtra("session_id") ?: return
                CoroutineScope(Dispatchers.IO).launch {
                    runCatching {
                        val url = URL("${Constants.OPENCODE_BASE_URL}/session/$sessionId/abort")
                        val conn = url.openConnection() as HttpURLConnection
                        conn.requestMethod = "POST"
                        conn.connectTimeout = 5000
                        conn.readTimeout = 5000
                        conn.doOutput = true
                        conn.outputStream.write("{}".toByteArray())
                        conn.outputStream.flush()
                        conn.outputStream.close()
                        val code = conn.responseCode
                        conn.disconnect()
                        AIDevLogger.d("OCReceiver", "abort $sessionId -> $code")
                    }.onFailure {
                        AIDevLogger.w("OCReceiver", "abort failed: ${it.message}")
                    }
                }
            }
        }
    }

    companion object {
        private const val ACTION_ABORT = "com.aidev.terminal.OC_ABORT"
    }
}
