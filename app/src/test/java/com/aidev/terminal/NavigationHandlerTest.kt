package com.aidev.terminal

import org.junit.Test
import org.junit.Assert.*
import org.junit.Before
import org.mockito.Mockito
import org.mockito.kotlin.whenever
import java.io.File

class NavigationHandlerTest {

    private lateinit var host: TestFilePageHost
    private lateinit var handler: NavigationHandler

    @Before
    fun setUp() {
        host = TestFilePageHost()
        handler = NavigationHandler(host)
    }

    @Test
    fun `activeDir - returns left dir when activeLeft is true`() {
        host.hostActiveLeft = true
        host.hostLeftDir = File("/sdcard/Documents")
        assertEquals(File("/sdcard/Documents"), handler.activeDir())
    }

    @Test
    fun `activeDir - returns right dir when activeLeft is false`() {
        host.hostActiveLeft = false
        host.hostRightDir = File("/sdcard/Downloads")
        assertEquals(File("/sdcard/Downloads"), handler.activeDir())
    }

    @Test
    fun `otherDir - returns right dir when activeLeft is true`() {
        host.hostActiveLeft = true
        host.hostRightDir = File("/sdcard/Downloads")
        assertEquals(File("/sdcard/Downloads"), handler.otherDir())
    }

    @Test
    fun `syncPanes - copies active dir to other pane`() {
        host.hostActiveLeft = true
        host.hostLeftDir = File("/sdcard/Documents")
        host.hostRightDir = File("/sdcard/Downloads")

        handler.syncPanes()

        assertEquals(File("/sdcard/Documents"), host.hostRightDir)
        assertTrue(host.reloadCalled)
    }

    @Test
    fun `navigateTo - updates dir for active pane`() {
        host.hostActiveLeft = true
        host.hostSetSelectedFile(File("/sdcard/old.txt"))

        handler.navigateTo(File("/sdcard/NewFolder"))

        assertEquals(File("/sdcard/NewFolder"), host.hostLeftDir)
        assertNull(host.selectedFile)
        assertTrue(host.loadLeftCalled)
        assertTrue(host.loadRightCalled)
    }

    @Test
    fun `onBackPressed - navigates to parent and returns true`() {
        host.hostActiveLeft = true
        host.hostLeftDir = File("/tmp/Documents/Subdir")

        val result = handler.onBackPressed(File("/tmp"))

        assertTrue(result)
        assertEquals(File("/tmp/Documents"), host.hostLeftDir)
    }

    @Test
    fun `onBackPressed - returns false at root`() {
        host.hostActiveLeft = true
        host.hostLeftDir = File("/tmp")

        val result = handler.onBackPressed(File("/tmp"))

        assertFalse(result)
    }

    @Test
    fun `syncNavigateToSplit - navigates in split mode`() {
        host.hostActiveLeft = true
        host.hostLeftDir = File("/sdcard")
        host.layoutMode = "split"

        val result = handler.syncNavigateToSplit(File("/sdcard/Documents"))

        assertTrue(result)
        assertEquals(File("/sdcard/Documents"), host.hostLeftDir)
        assertTrue(host.loadLeftCalled)
    }

    @Test
    fun `syncNavigateToSplit - no-op in tree mode`() {
        host.layoutMode = "tree"

        val result = handler.syncNavigateToSplit(File("/sdcard/Documents"))

        assertFalse(result)
    }

    @Test
    fun `onBackPressed - navigates to parent with custom sdRoot`() {
        host.hostActiveLeft = true
        host.hostLeftDir = File("/tmp/subdir")

        val result = handler.onBackPressed(File("/tmp"))

        assertTrue(result)
        assertEquals(File("/tmp"), host.hostLeftDir)
    }

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
        var reloadCalled = false
        var loadLeftCalled = false
        var loadRightCalled = false
        var layoutMode = "split"

        private val tempHome = File(System.getProperty("java.io.tmpdir"), "navtest-home").also { it.mkdirs() }
        private val mockActivity = Mockito.mock(android.app.Activity::class.java).also { act ->
            Mockito.`when`(act.filesDir).thenReturn(tempHome)
        }
        private val mockPm = Mockito.mock(PreferencesManager::class.java).also {
            whenever(it.fileLayoutMode).thenReturn("split")
        }

        override fun hostActivity(): android.app.Activity = mockActivity
        override fun hostPm(): PreferencesManager {
            whenever(mockPm.fileLayoutMode).thenReturn(layoutMode)
            return mockPm
        }
        override fun hostSelectedFile(): File? = selectedFile
        override fun hostSetSelectedFile(file: File?) { selectedFile = file }
        override fun hostActiveDir(): File = if (hostActiveLeft) hostLeftDir else hostRightDir
        override fun hostClearSelection() { selectedFile = null; hostMultiSelected = emptySet() }
        override fun hostReloadAll() { reloadCalled = true }
        override fun hostLoadPane(left: Boolean) { if (left) loadLeftCalled = true else loadRightCalled = true }
        override fun hostToast(msg: String) {}
        override fun hostFormatSize(n: Long): String = FileUtils.formatSize(n)
        override fun hostDragLog(msg: String) {}
        override fun hostExitMultiMode() {}
        override fun hostUpdateMultiInfo() {}
        override fun hostInputAllowAny(title: String, hint: String, cb: (String) -> Unit) {}
        override fun hostNavigateTo(dir: File) {}
        override fun hostRememberRecentDir(dir: File) {}
        override fun hostCopyText(label: String, text: String) {}
        override fun hostEditSelected() {}
        override fun hostCopySelectedPath() {}
        override fun hostGetPaneFiles(left: Boolean): List<File> = emptyList()
    }
}
