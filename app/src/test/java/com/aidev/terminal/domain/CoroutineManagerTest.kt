package com.aidev.terminal.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

@RunWith(JUnit4::class)
class CoroutineManagerTest {

    private lateinit var manager: CoroutineManager

    @Before
    fun setUp() {
        manager = CoroutineManager()
    }

    @Test
    fun testExecuteIoTaskReturnsResult() = runTest {
        val result = manager.executeIoTask { "test" }.first()
        assertEquals("test", result)
    }

    @Test
    fun testExecuteIoTaskWithNumericResult() = runTest {
        val result = manager.executeIoTask { 42 }.first()
        assertEquals(42, result)
    }

    @Test
    fun testCancelAllCancelsJobs() = runTest {
        var executed = false
        val job = manager.launchCoroutine(Dispatchers.Unconfined) {
            delay(10000)
            executed = true
        }
        manager.cancelAll()
        delay(100)
        assertTrue(job.isCancelled)
    }

    @Test
    fun testLaunchCoroutineReturnsJob() {
        val job = manager.launchCoroutine(Dispatchers.Unconfined) {
            delay(100)
        }
        assertTrue(job.isActive)
        job.cancel()
    }
}
