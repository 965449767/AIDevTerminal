package com.aidev.terminal

import android.app.Activity
import kotlinx.coroutines.CoroutineScope
import java.io.File

internal interface FilePageHost {
    val hostScope: CoroutineScope
    fun hostActivity(): Activity
    fun hostPm(): PreferencesManager
    fun hostSelectedFile(): File?
    fun hostSetSelectedFile(file: File?)
    fun hostActiveDir(): File
    var hostActiveLeft: Boolean
    var hostLeftDir: File
    var hostRightDir: File
    var hostMultiMode: Boolean
    var hostMultiPaneSide: Boolean
    var hostMultiSelected: Set<String>
    var hostAnchorFile: String?
    fun hostGetPaneFiles(left: Boolean): List<File>
    fun hostClearSelection()
    fun hostReloadAll()
    fun hostLoadPane(left: Boolean)
    fun hostToast(msg: String)
    fun hostFormatSize(n: Long): String
    fun hostDragLog(msg: String)
    fun hostExitMultiMode()
    fun hostUpdateMultiInfo()
    fun hostInputAllowAny(title: String, hint: String, cb: (String) -> Unit)
    fun hostNavigateTo(dir: File)
    fun hostRememberRecentDir(dir: File)
    fun hostCopyText(label: String, text: String)
    fun hostEditSelected()
    fun hostCopySelectedPath()
}
