package com.aidev.terminal

import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(JUnit4::class)
class ErrorHandlerTest {

    @Before
    fun setUp() {
        AIDevLogger.enabled = false
    }

    @Test
    fun testExecuteSuccess() = runTest {
        val result = ErrorHandler.execute { "test" }
        assertTrue(result.isSuccess)
        assertEquals("test", result.getOrNull())
    }

    @Test
    fun testExecuteFailure() = runTest {
        val result = ErrorHandler.execute { throw RuntimeException("test error") }
        assertTrue(result.isFailure)
        assertEquals("test error", result.exceptionOrNull()?.message)
    }
}
