package com.aidev.terminal

import android.content.SharedPreferences
import java.io.File

object SyncCoordinator {

    fun isEnabled(prefs: SharedPreferences): Boolean = prefs.getBoolean(Constants.PrefKeys.SYNC_TERMINAL_FILES, false)

    fun setEnabled(prefs: SharedPreferences, enabled: Boolean) {
        prefs.edit().putBoolean(Constants.PrefKeys.SYNC_TERMINAL_FILES, enabled).apply()
    }

    fun onTerminalPwdChanged(
        ubuntuPwd: String,
        home: File,
        prefs: SharedPreferences,
        callback: (targetDir: File) -> Unit
    ) {
        if (!isEnabled(prefs)) return
        if (!PathBridge.isBrowsable(ubuntuPwd)) return
        val target = PathBridge.ubuntuToAndroid(home, ubuntuPwd) ?: return
        if (!target.isDirectory) return
        callback(target)
    }

    fun onBrowserDirChanged(
        androidDir: File,
        home: File,
        prefs: SharedPreferences,
        callback: (ubuntuCdPath: String) -> Unit
    ) {
        if (!isEnabled(prefs)) return
        val ubuntuPath = PathBridge.androidToUbuntu(home, androidDir) ?: return
        callback(ubuntuPath)
    }
}
