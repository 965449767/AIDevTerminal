package com.aidev.terminal

import org.junit.Test
import org.junit.Assert.*
import java.io.File

class SyncCoordinatorTest {

    @Test
    fun `toAndroidDir - blocked path returns null`() {
        val home = File("/tmp/test-home")
        assertNull(SyncCoordinator.toAndroidDir("/bin", home))
        assertNull(SyncCoordinator.toAndroidDir("/etc/passwd", home))
        assertNull(SyncCoordinator.toAndroidDir("/proc/1", home))
        assertNull(SyncCoordinator.toAndroidDir("/sys", home))
    }

    @Test
    fun `toAndroidDir - non existent directory returns null`() {
        val home = File("/tmp/nonexistent-test-home")
        assertNull(SyncCoordinator.toAndroidDir("/root", home))
    }

    @Test
    fun `toAndroidDir - existing browsable directory returns File`() {
        val home = File(System.getProperty("java.io.tmpdir"), "sync-test-home")
        val targetDir = File(home, "ubuntu-rootfs/root/projects")
        targetDir.mkdirs()
        try {
            val result = SyncCoordinator.toAndroidDir("/root/projects", home)
            assertEquals(targetDir, result)
        } finally {
            targetDir.deleteRecursively()
        }
    }

    @Test
    fun `toUbuntuPath - rootfs path returns ubuntu path`() {
        val home = File("/data/data/com.aidev.terminal/files/home")
        val file = File(home, "ubuntu-rootfs/root/.bashrc")
        assertEquals("/root/.bashrc", SyncCoordinator.toUbuntuPath(file, home))
    }

    @Test
    fun `toUbuntuPath - host-home root`() {
        val home = File("/data/data/com.aidev.terminal/files/home")
        assertEquals("/host-home", SyncCoordinator.toUbuntuPath(home, home))
    }

    @Test
    fun `toUbuntuPath - host-home subdirectory`() {
        val home = File("/data/data/com.aidev.terminal/files/home")
        val file = File(home, "dev-env/bin")
        assertEquals("/host-home/dev-env/bin", SyncCoordinator.toUbuntuPath(file, home))
    }

    @Test
    fun `toUbuntuPath - path outside home returns null`() {
        val home = File("/data/data/com.aidev.terminal/files/home")
        val sdcard = File("/sdcard/Download")
        assertNull(SyncCoordinator.toUbuntuPath(sdcard, home))
    }
}
