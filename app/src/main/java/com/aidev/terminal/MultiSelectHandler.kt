package com.aidev.terminal

import java.io.File

internal enum class MultiSelectEvent {
    ENTERED, EXITED, TOGGLED, NO_CHANGE, TOAST_EMPTY
}

internal class MultiSelectHandler(private val host: FilePageHost) {

    fun enterMultiMode(file: File) {
        if (host.hostMultiMode) return
        host.hostMultiMode = true
        host.hostMultiPaneSide = host.hostActiveLeft
        host.hostMultiSelected = setOf(file.absolutePath)
        host.hostAnchorFile = file.absolutePath
    }

    fun exitMultiMode() {
        if (!host.hostMultiMode) return
        host.hostMultiMode = false
        host.hostMultiPaneSide = true
        host.hostMultiSelected = emptySet()
        host.hostAnchorFile = null
        host.hostSetSelectedFile(null)
    }

    fun toggleMultiSelect(file: File, isLeft: Boolean): MultiSelectEvent {
        if (host.hostMultiPaneSide != isLeft) return MultiSelectEvent.NO_CHANGE
        val path = file.absolutePath
        host.hostMultiSelected = if (path in host.hostMultiSelected) host.hostMultiSelected - path else host.hostMultiSelected + path
        if (host.hostMultiSelected.isEmpty()) {
            exitMultiMode()
            return MultiSelectEvent.EXITED
        }
        return MultiSelectEvent.TOGGLED
    }

    fun toggleSelectAll(): MultiSelectEvent {
        val files = host.hostGetPaneFiles(if (host.hostMultiMode) host.hostMultiPaneSide else host.hostActiveLeft)
            .map { it.absolutePath }.toSet()
        if (files.isEmpty()) return MultiSelectEvent.TOAST_EMPTY
        if (host.hostMultiMode && files.all { it in host.hostMultiSelected }) {
            exitMultiMode()
            return MultiSelectEvent.EXITED
        }
        if (!host.hostMultiMode) {
            host.hostMultiMode = true
            host.hostMultiPaneSide = host.hostActiveLeft
        }
        host.hostMultiSelected = files
        host.hostAnchorFile = null
        return MultiSelectEvent.ENTERED
    }

    fun invertSelection(): MultiSelectEvent {
        val files = host.hostGetPaneFiles(if (host.hostMultiMode) host.hostMultiPaneSide else host.hostActiveLeft)
            .map { it.absolutePath }.toSet()
        if (files.isEmpty()) return MultiSelectEvent.TOAST_EMPTY
        if (!host.hostMultiMode) {
            host.hostMultiMode = true
            host.hostMultiPaneSide = host.hostActiveLeft
        }
        val inverted = files - host.hostMultiSelected
        if (inverted.isEmpty()) {
            exitMultiMode()
            return MultiSelectEvent.EXITED
        }
        host.hostMultiSelected = inverted
        host.hostAnchorFile = null
        return MultiSelectEvent.ENTERED
    }

    fun rangeSelect(file: File, isLeft: Boolean): MultiSelectEvent {
        if (host.hostMultiPaneSide != isLeft) return MultiSelectEvent.NO_CHANGE
        val anchor = host.hostAnchorFile ?: return MultiSelectEvent.NO_CHANGE
        val files = host.hostGetPaneFiles(isLeft)
        val anchorIdx = files.indexOfFirst { it.absolutePath == anchor }
        val currentIdx = files.indexOfFirst { it.absolutePath == file.absolutePath }
        if (anchorIdx < 0 || currentIdx < 0) return MultiSelectEvent.NO_CHANGE
        val range = minOf(anchorIdx, currentIdx)..maxOf(anchorIdx, currentIdx)
        val paths = files.slice(range).map { it.absolutePath }.toSet()
        val newSelected = host.hostMultiSelected + paths
        host.hostDragLog("rangeSelect anchor=${File(anchor).name}[$anchorIdx] file=${file.name}[$currentIdx] range=$range selected=${newSelected.toList()}")
        if (newSelected != host.hostMultiSelected) {
            host.hostMultiSelected = newSelected
            return MultiSelectEvent.TOGGLED
        }
        return MultiSelectEvent.NO_CHANGE
    }
}
