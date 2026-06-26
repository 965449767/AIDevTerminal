package com.aidev.terminal

import android.os.Environment
import java.io.File

internal class NavigationHandler(private val host: FilePageHost) {

    fun activeDir(): File = host.hostActiveDir()

    fun otherDir(): File = if (host.hostActiveLeft) host.hostRightDir else host.hostLeftDir

    fun syncPanes() {
        val src = activeDir()
        if (host.hostActiveLeft) host.hostRightDir = src else host.hostLeftDir = src
        host.hostReloadAll()
    }

    fun navigateTo(dir: File) {
        if (host.hostActiveLeft) host.hostLeftDir = dir else host.hostRightDir = dir
        host.hostSetSelectedFile(null)
        host.hostRememberRecentDir(dir)
        host.hostLoadPane(true)
        host.hostLoadPane(false)
        notifyTerminalCd(dir)
    }

    fun onBackPressed(sdRoot: File = Environment.getExternalStorageDirectory()): Boolean {
        val dir = activeDir()
        val parent = dir.parentFile
        if (dir != sdRoot && parent != null) {
            navigateTo(parent)
            return true
        }
        return false
    }

    fun syncNavigateToSplit(targetDir: File): Boolean {
        if (!targetDir.isDirectory) return false
        if (host.hostPm().fileLayoutMode != "split") return false
        if (host.hostActiveLeft) host.hostLeftDir = targetDir else host.hostRightDir = targetDir
        host.hostSetSelectedFile(null)
        host.hostLoadPane(host.hostActiveLeft)
        return true
    }

    private fun notifyTerminalCd(dir: File) {
        val act = host.hostActivity()
        val home = File(act.filesDir, "home")
        val ubuntuPath = SyncCoordinator.toUbuntuPath(dir, home) ?: return
        if (act is ShellActivity) act.syncTerminalCd(ubuntuPath)
    }
}
