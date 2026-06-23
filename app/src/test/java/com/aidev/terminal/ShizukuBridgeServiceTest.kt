package com.aidev.terminal

import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.test.assertFalse

@RunWith(JUnit4::class)
class ShizukuBridgeServiceTest {

    @Before
    fun setUp() {
        AIDevLogger.enabled = false
    }

    @Test
    fun testStartStopWithInvalidDir() {
        val tempDir = java.io.File.createTempFile("test", "bridge")
        tempDir.delete()
        try {
            ShizukuBridgeService.start(tempDir)
        } catch (_: Exception) {
        }
        assertFalse(ShizukuBridgeService.isRunning)
    }
}
