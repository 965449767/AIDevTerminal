package com.aidev.terminal

import org.junit.Test
import org.junit.Assert.*
import java.io.File

/**
 * PathBridge 单元测试。
 */
class PathBridgeTest {

    private val home = File("/data/data/com.aidev.terminal/files/home")

    @Test
    fun `ubuntuToAndroid - host-home root`() {
        val result = PathBridge.ubuntuToAndroid(home, "/host-home")
        assertEquals(File(home, "."), result)
    }

    @Test
    fun `ubuntuToAndroid - host-home subdirectory`() {
        val result = PathBridge.ubuntuToAndroid(home, "/host-home/dev-env/bin")
        assertEquals(File(home, "dev-env/bin"), result)
    }

    @Test
    fun `ubuntuToAndroid - root directory`() {
        val result = PathBridge.ubuntuToAndroid(home, "/root")
        assertEquals(File(home, "ubuntu-rootfs/root"), result)
    }

    @Test
    fun `ubuntuToAndroid - root subdirectory`() {
        val result = PathBridge.ubuntuToAndroid(home, "/root/.bashrc")
        assertEquals(File(home, "ubuntu-rootfs/root/.bashrc"), result)
    }

    @Test
    fun `ubuntuToAndroid - absolute path`() {
        val result = PathBridge.ubuntuToAndroid(home, "/usr/bin")
        assertEquals(File(home, "ubuntu-rootfs/usr/bin"), result)
    }

    @Test
    fun `ubuntuToAndroid - relative path returns null`() {
        val result = PathBridge.ubuntuToAndroid(home, "relative/path")
        assertNull(result)
    }

    @Test
    fun `androidToUbuntu - home root`() {
        val result = PathBridge.androidToUbuntu(home, home)
        assertEquals("/host-home", result)
    }

    @Test
    fun `androidToUbuntu - home subdirectory`() {
        val file = File(home, "dev-env/bin")
        val result = PathBridge.androidToUbuntu(home, file)
        assertEquals("/host-home/dev-env/bin", result)
    }

    @Test
    fun `androidToUbuntu - rootfs path`() {
        val file = File(home, "ubuntu-rootfs/root/.bashrc")
        val result = PathBridge.androidToUbuntu(home, file)
        assertEquals("/root/.bashrc", result)
    }

    @Test
    fun `isBrowsable - blocked paths`() {
        assertFalse(PathBridge.isBrowsable("/bin"))
        assertFalse(PathBridge.isBrowsable("/bin/sh"))
        assertFalse(PathBridge.isBrowsable("/etc/passwd"))
        assertFalse(PathBridge.isBrowsable("/proc/1"))
    }

    @Test
    fun `isBrowsable - allowed paths`() {
        assertTrue(PathBridge.isBrowsable("/host-home"))
        assertTrue(PathBridge.isBrowsable("/root"))
        assertTrue(PathBridge.isBrowsable("/sdcard"))
        assertTrue(PathBridge.isBrowsable("/tmp"))
    }
}
