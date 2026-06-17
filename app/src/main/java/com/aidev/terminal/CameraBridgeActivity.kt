package com.aidev.terminal

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File

/**
 * 摄像头桥接 Activity。
 * 透明背景，无 UI，通过 Intent 调用系统相机或相册，结果写回 IPC 结果文件。
 *
 * 启动参数（Intent extras）：
 * - request_file: 请求文件的绝对路径
 * - result_file: 结果文件的绝对路径
 */
class CameraBridgeActivity : Activity() {

    companion object {
        private const val TAG = "CameraBridge"
        private const val REQ_CAMERA = 1001
        private const val REQ_PICK = 1002

        /**
         * 启动拍照流程。
         * @param activity 当前 Activity（用于启动透明 Activity）
         * @param requestFile 请求文件路径
         * @param resultFile 结果文件路径
         */
        fun start(activity: Activity, requestFile: String, resultFile: String) {
            val intent = Intent(activity, CameraBridgeActivity::class.java).apply {
                putExtra("request_file", requestFile)
                putExtra("result_file", resultFile)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(intent)
        }
    }

    private lateinit var requestFile: String
    private lateinit var resultFile: String
    private var photoUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 透明背景，不显示任何 UI
        window.setBackgroundDrawableResource(android.R.color.transparent)

        requestFile = intent.getStringExtra("request_file") ?: ""
        resultFile = intent.getStringExtra("result_file") ?: ""

        if (requestFile.isBlank() || resultFile.isBlank()) {
            writeResult("error", "Missing request_file or result_file")
            finish()
            return
        }

        val request = parseRequest(File(requestFile))
        when (request.mode) {
            "photo" -> launchCamera(request.output)
            "pick" -> launchPicker()
            else -> {
                writeResult("error", "Unknown mode: ${request.mode}")
                finish()
            }
        }
    }

    private fun parseRequest(file: File): CameraRequest {
        val content = file.readText()
        // 支持两种格式：
        // 1. 旧版 key=value 格式（兼容 logcat 请求）
        // 2. JSON 格式
        return if (content.trim().startsWith("{")) {
            parseJson(content)
        } else {
            val lines = content.lines().associate {
                val parts = it.split("=", limit = 2)
                parts[0] to if (parts.size > 1) parts[1] else ""
            }
            CameraRequest(
                mode = lines["MODE"] ?: "photo",
                output = lines["OUTPUT"] ?: defaultOutputPath(),
                quality = lines["QUALITY"]?.toIntOrNull() ?: 90
            )
        }
    }

    private fun parseJson(content: String): CameraRequest {
        // 简单 JSON 解析，不引入外部库
        val mode = extractJsonField(content, "mode") ?: "photo"
        val output = extractJsonField(content, "output") ?: defaultOutputPath()
        val quality = extractJsonField(content, "quality")?.toIntOrNull() ?: 90
        return CameraRequest(mode, output, quality)
    }

    private fun extractJsonField(json: String, field: String): String? {
        val regex = """"$field"\s*:\s*"([^"]*)"""".toRegex()
        return regex.find(json)?.groupValues?.get(1)
    }

    private fun defaultOutputPath(): String {
        val dir = File("/sdcard/DCIM/AIDev")
        dir.mkdirs()
        return File(dir, "photo_${System.currentTimeMillis()}.jpg").absolutePath
    }

    private fun launchCamera(outputPath: String) {
        val file = File(outputPath)
        file.parentFile?.mkdirs()
        photoUri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            file
        )
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
        }
        if (intent.resolveActivity(packageManager) != null) {
            startActivityForResult(intent, REQ_CAMERA)
        } else {
            writeResult("error", "No camera app available")
            finish()
        }
    }

    private fun launchPicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
            type = "image/*"
        }
        if (intent.resolveActivity(packageManager) != null) {
            startActivityForResult(intent, REQ_PICK)
        } else {
            writeResult("error", "No gallery app available")
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQ_CAMERA -> {
                if (resultCode == RESULT_OK) {
                    val path = photoUri?.let { getRealPathFromUri(it) } ?: "unknown"
                    writeResult("success", path)
                } else {
                    writeResult("cancelled", "User cancelled")
                }
            }
            REQ_PICK -> {
                if (resultCode == RESULT_OK && data?.data != null) {
                    val path = getRealPathFromUri(data.data!!)
                    writeResult("success", path)
                } else {
                    writeResult("cancelled", "User cancelled")
                }
            }
        }
        finish()
    }

    private fun getRealPathFromUri(uri: Uri): String {
        // 优先尝试直接路径
        if (uri.scheme == "file") {
            return uri.path ?: uri.toString()
        }
        // content:// URI，尝试查询
        return try {
            contentResolver.query(uri, arrayOf(MediaStore.Images.Media.DATA), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                    cursor.getString(idx)
                } else null
            } ?: uri.toString()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve URI: $uri", e)
            uri.toString()
        }
    }

    private fun writeResult(status: String, pathOrError: String) {
        try {
            val output = when (status) {
                "success" -> """{"status":"success","path":"$pathOrError","timestamp":${System.currentTimeMillis()}}"""
                "cancelled" -> """{"status":"cancelled","error":"$pathOrError","timestamp":${System.currentTimeMillis()}}"""
                else -> """{"status":"error","error":"$pathOrError","timestamp":${System.currentTimeMillis()}}"""
            }
            File(resultFile).writeText(output)
            Log.d(TAG, "Result written to $resultFile: $status")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write result", e)
        }
    }

    data class CameraRequest(
        val mode: String,
        val output: String,
        val quality: Int
    )
}
