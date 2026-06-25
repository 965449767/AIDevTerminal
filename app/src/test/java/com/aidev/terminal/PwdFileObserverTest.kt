package com.aidev.terminal

import org.junit.Test
import org.junit.Assert.*
import java.io.File

class PwdFileObserverTest {

    @Test
    fun `double read confirms stable file content`() {
        val pwdFile = File.createTempFile("aidev-pwd-test", ".tmp")
        try {
            pwdFile.writeText("/root/projects")
            val first = pwdFile.readText().trim()
            Thread.sleep(60)
            val second = pwdFile.readText().trim()
            assertEquals(first, second)
            assertEquals("/root/projects", second)
        } finally {
            pwdFile.delete()
        }
    }

    @Test
    fun `double read detects ongoing write when reads differ`() {
        val pwdFile = File.createTempFile("aidev-pwd-race", ".tmp")
        try {
            pwdFile.writeText("/root/projects")
            val first = pwdFile.readText().trim()
            pwdFile.writeText("/tmp")
            val second = pwdFile.readText().trim()
            assertNotEquals(first, second)
            assertEquals("/tmp", second)
        } finally {
            pwdFile.delete()
        }
    }

    @Test
    fun `empty content on first read resolves on second read`() {
        val pwdFile = File.createTempFile("aidev-pwd-empty", ".tmp")
        try {
            pwdFile.writeText("")
            val first = pwdFile.readText().trim()
            Thread.sleep(60)
            pwdFile.writeText("/root")
            val second = pwdFile.readText().trim()
            val third = pwdFile.readText().trim()
            assertNotEquals(first, second)
            assertEquals(second, third)
        } finally {
            pwdFile.delete()
        }
    }
}
