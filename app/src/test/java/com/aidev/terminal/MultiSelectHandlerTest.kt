package com.aidev.terminal

import org.junit.Test
import org.junit.Assert.*
import org.junit.Before
import java.io.File

class MultiSelectHandlerTest {

    private lateinit var host: TestFilePageHost
    private lateinit var handler: MultiSelectHandler

    @Before
    fun setUp() {
        host = TestFilePageHost()
        handler = MultiSelectHandler(host)
    }

    @Test
    fun `enterMultiMode - sets state and returns`() {
        handler.enterMultiMode(File("/sdcard/test.txt"))

        assertTrue(host.hostMultiMode)
        assertEquals(setOf("/sdcard/test.txt"), host.hostMultiSelected)
        assertEquals("/sdcard/test.txt", host.hostAnchorFile)
    }

    @Test
    fun `enterMultiMode - no-op when already in multi mode`() {
        host.hostMultiMode = true
        host.hostMultiSelected = setOf("/sdcard/a.txt")
        host.hostAnchorFile = "/sdcard/a.txt"

        handler.enterMultiMode(File("/sdcard/b.txt"))

        assertEquals(setOf("/sdcard/a.txt"), host.hostMultiSelected)
        assertEquals("/sdcard/a.txt", host.hostAnchorFile)
    }

    @Test
    fun `exitMultiMode - clears all state`() {
        host.hostMultiMode = true
        host.hostMultiSelected = setOf("/sdcard/test.txt")
        host.hostAnchorFile = "/sdcard/test.txt"
        host.hostSetSelectedFile(File("/sdcard/test.txt"))

        handler.exitMultiMode()

        assertFalse(host.hostMultiMode)
        assertTrue(host.hostMultiSelected.isEmpty())
        assertNull(host.hostAnchorFile)
        assertNull(host.selectedFile)
    }

    @Test
    fun `exitMultiMode - no-op when not in multi mode`() {
        host.hostMultiMode = false
        host.hostMultiSelected = emptySet()

        handler.exitMultiMode()

        assertFalse(host.hostMultiMode)
    }

    @Test
    fun `toggleMultiSelect - adds file to selection`() {
        host.hostMultiMode = true
        host.hostMultiPaneSide = true
        host.hostMultiSelected = setOf("/sdcard/a.txt")

        val result = handler.toggleMultiSelect(File("/sdcard/b.txt"), true)

        assertEquals(MultiSelectEvent.TOGGLED, result)
        assertEquals(setOf("/sdcard/a.txt", "/sdcard/b.txt"), host.hostMultiSelected)
    }

    @Test
    fun `toggleMultiSelect - removes file from selection`() {
        host.hostMultiMode = true
        host.hostMultiPaneSide = true
        host.hostMultiSelected = setOf("/sdcard/a.txt", "/sdcard/b.txt")

        val result = handler.toggleMultiSelect(File("/sdcard/a.txt"), true)

        assertEquals(MultiSelectEvent.TOGGLED, result)
        assertEquals(setOf("/sdcard/b.txt"), host.hostMultiSelected)
    }

    @Test
    fun `toggleMultiSelect - exits mode when last item removed`() {
        host.hostMultiMode = true
        host.hostMultiPaneSide = true
        host.hostMultiSelected = setOf("/sdcard/only.txt")

        val result = handler.toggleMultiSelect(File("/sdcard/only.txt"), true)

        assertEquals(MultiSelectEvent.EXITED, result)
        assertFalse(host.hostMultiMode)
        assertTrue(host.hostMultiSelected.isEmpty())
    }

    @Test
    fun `toggleMultiSelect - no-op on wrong pane side`() {
        host.hostMultiMode = true
        host.hostMultiPaneSide = true
        host.hostMultiSelected = setOf("/sdcard/a.txt")

        val result = handler.toggleMultiSelect(File("/sdcard/b.txt"), false)

        assertEquals(MultiSelectEvent.NO_CHANGE, result)
        assertEquals(setOf("/sdcard/a.txt"), host.hostMultiSelected)
    }

    @Test
    fun `toggleSelectAll - selects all files`() {
        host.files["/sdcard"] = listOf(File("/sdcard/a.txt"), File("/sdcard/b.txt"), File("/sdcard/c.txt"))

        val result = handler.toggleSelectAll()

        assertEquals(MultiSelectEvent.ENTERED, result)
        assertTrue(host.hostMultiMode)
        assertEquals(setOf("/sdcard/a.txt", "/sdcard/b.txt", "/sdcard/c.txt"), host.hostMultiSelected)
    }

    @Test
    fun `toggleSelectAll - deselects all when already all selected`() {
        host.hostMultiMode = true
        host.hostMultiPaneSide = true
        host.hostActiveLeft = true
        host.files["/sdcard"] = listOf(File("/sdcard/a.txt"), File("/sdcard/b.txt"))
        host.hostMultiSelected = setOf("/sdcard/a.txt", "/sdcard/b.txt")

        val result = handler.toggleSelectAll()

        assertEquals(MultiSelectEvent.EXITED, result)
        assertFalse(host.hostMultiMode)
    }

    @Test
    fun `toggleSelectAll - toast when directory empty`() {
        host.files["/sdcard"] = emptyList()

        val result = handler.toggleSelectAll()

        assertEquals(MultiSelectEvent.TOAST_EMPTY, result)
    }

    @Test
    fun `invertSelection - inverts selection`() {
        host.hostMultiMode = true
        host.hostMultiPaneSide = true
        host.hostActiveLeft = true
        host.files["/sdcard"] = listOf(File("/sdcard/a.txt"), File("/sdcard/b.txt"), File("/sdcard/c.txt"))
        host.hostMultiSelected = setOf("/sdcard/a.txt")

        val result = handler.invertSelection()

        assertEquals(MultiSelectEvent.ENTERED, result)
        assertEquals(setOf("/sdcard/b.txt", "/sdcard/c.txt"), host.hostMultiSelected)
    }

    @Test
    fun `invertSelection - exits when all inverted`() {
        host.hostMultiMode = true
        host.hostMultiPaneSide = true
        host.hostActiveLeft = true
        host.files["/sdcard"] = listOf(File("/sdcard/a.txt"), File("/sdcard/b.txt"))
        host.hostMultiSelected = setOf("/sdcard/a.txt", "/sdcard/b.txt")

        val result = handler.invertSelection()

        assertEquals(MultiSelectEvent.EXITED, result)
        assertFalse(host.hostMultiMode)
    }

    @Test
    fun `rangeSelect - selects range from anchor to target`() {
        host.hostMultiPaneSide = true
        host.hostAnchorFile = "/sdcard/file_a.txt"
        host.hostMultiSelected = emptySet()
        host.files["/sdcard"] = listOf(
            File("/sdcard/file_a.txt"),
            File("/sdcard/file_b.txt"),
            File("/sdcard/file_c.txt")
        )

        val result = handler.rangeSelect(File("/sdcard/file_c.txt"), true)

        assertEquals(MultiSelectEvent.TOGGLED, result)
        assertEquals(setOf("/sdcard/file_a.txt", "/sdcard/file_b.txt", "/sdcard/file_c.txt"), host.hostMultiSelected)
    }

    @Test
    fun `rangeSelect - no-op with no anchor`() {
        host.hostMultiPaneSide = true
        host.hostAnchorFile = null

        val result = handler.rangeSelect(File("/sdcard/file.txt"), true)

        assertEquals(MultiSelectEvent.NO_CHANGE, result)
    }

    /**
     * A fake FilePageHost for testing MultiSelectHandler state transitions.
     */
    private class TestFilePageHost : FilePageHost {
        override val hostScope: kotlinx.coroutines.CoroutineScope
            get() = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob())
        override var hostActiveLeft: Boolean = true
        override var hostLeftDir: File = File("/sdcard")
        override var hostRightDir: File = File("/")
        override var hostMultiMode: Boolean = false
        override var hostMultiPaneSide: Boolean = true
        override var hostMultiSelected: Set<String> = emptySet()
        override var hostAnchorFile: String? = null

        var selectedFile: File? = null

        /** Maps directory -> list of files for getPaneFiles simulation */
        val files: MutableMap<String, List<File>> = mutableMapOf()

        override fun hostActivity(): android.app.Activity = throw UnsupportedOperationException()
        override fun hostPm(): PreferencesManager = throw UnsupportedOperationException()
        override fun hostSelectedFile(): File? = selectedFile
        override fun hostSetSelectedFile(file: File?) { selectedFile = file }
        override fun hostActiveDir(): File = if (hostActiveLeft) hostLeftDir else hostRightDir
        override fun hostClearSelection() { selectedFile = null; hostMultiSelected = emptySet() }
        override fun hostReloadAll() {}
        override fun hostLoadPane(left: Boolean) {}
        override fun hostToast(msg: String) {}
        override fun hostFormatSize(n: Long): String = FileUtils.formatSize(n)
        override fun hostDragLog(msg: String) {}
        override fun hostExitMultiMode() = exitMultiMode()
        override fun hostUpdateMultiInfo() {}
        override fun hostInputAllowAny(title: String, hint: String, cb: (String) -> Unit) {}
        override fun hostNavigateTo(dir: File) {}
        override fun hostRememberRecentDir(dir: File) {}
        override fun hostCopyText(label: String, text: String) {}
        override fun hostEditSelected() {}
        override fun hostCopySelectedPath() {}
        override fun hostGetPaneFiles(left: Boolean): List<File> {
            val dir = if (left) hostLeftDir else hostRightDir
            return files[dir.absolutePath] ?: emptyList()
        }

        private fun exitMultiMode() {
            hostMultiMode = false
            hostMultiPaneSide = true
            hostMultiSelected = emptySet()
            hostAnchorFile = null
            selectedFile = null
        }
    }
}
