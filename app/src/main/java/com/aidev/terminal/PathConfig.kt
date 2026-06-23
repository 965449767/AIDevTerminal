package com.aidev.terminal

import android.content.Context
import android.os.Environment
import java.io.File

object PathConfig {

    fun aidevHome(ctx: Context) = File(ctx.filesDir, "home")
    fun rootfs(ctx: Context) = File(aidevHome(ctx), "ubuntu-rootfs")
    fun tasksDir(ctx: Context) = File(aidevHome(ctx), "tasks")
    fun prootLibDir(ctx: Context) = File(aidevHome(ctx), "proot-lib")
    fun devEnvBin(ctx: Context) = File(aidevHome(ctx), "dev-env/bin")

    fun backupDir(ctx: Context): File {
        val p = PreferencesManager(ctx).backupDir
        return if (p.isNotBlank()) File(p) else File("/sdcard/AIDev/backups/")
    }

    fun projectsDir(ctx: Context): File {
        val rel = PreferencesManager(ctx).projectsDirRel
        return if (rel.isNotBlank()) File(rootfs(ctx), rel) else File(rootfs(ctx), "root/projects")
    }

    fun externalAidevDir(ctx: Context): File {
        val p = PreferencesManager(ctx).externalAidevDir
        return if (p.isNotBlank()) File(p) else File("/sdcard/AIDev")
    }

    fun downloadDir(ctx: Context) = File(Environment.getExternalStorageDirectory(), "Download")
}
