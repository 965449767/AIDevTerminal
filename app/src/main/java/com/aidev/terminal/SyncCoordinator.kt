package com.aidev.terminal

import android.content.SharedPreferences
import java.io.File

object SyncCoordinator {

    private const val PREF_KEY = "sync_terminal_files"

    fun isEnabled(prefs: SharedPreferences): Boolean = prefs.getBoolean(PREF_KEY, false)

    fun setEnabled(prefs: SharedPreferences, enabled: Boolean) {
        prefs.edit().putBoolean(PREF_KEY, enabled).apply()
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
