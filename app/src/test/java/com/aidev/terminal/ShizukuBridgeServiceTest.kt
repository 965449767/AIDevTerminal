package com.aidev.terminal

import android.content.Context
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(JUnit4::class)
class ShizukuBridgeServiceTest {

    @Before
    fun setUp() {
        AIDevLogger.enabled = false
        ShizukuBridgeService.stop()
    }

    @After
    fun tearDown() {
        ShizukuBridgeService.stop()
    }

    @Test
    fun testStartStopWithInvalidDir() {
        val ctx = mock(Context::class.java)
        whenever(ctx.applicationContext).thenReturn(ctx)
        val tempDir = java.io.File.createTempFile("test", "bridge")
        tempDir.delete()
        ShizukuBridgeService.start(ctx, tempDir)
        assertTrue(ShizukuBridgeService.isRunning)
        ShizukuBridgeService.stop()
        assertFalse(ShizukuBridgeService.isRunning)
    }
}
