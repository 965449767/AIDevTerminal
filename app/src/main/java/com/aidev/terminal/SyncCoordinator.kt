package com.aidev.terminal

import java.io.File

object SyncCoordinator {

    fun toAndroidDir(ubuntuPwd: String, home: File): File? {
        if (!PathBridge.isBrowsable(ubuntuPwd)) return null
        val target = PathBridge.ubuntuToAndroid(home, ubuntuPwd) ?: return null
        if (!target.isDirectory) return null
        return target
    }

    fun toUbuntuPath(androidDir: File, home: File): String? =
        PathBridge.androidToUbuntu(home, androidDir)
}
