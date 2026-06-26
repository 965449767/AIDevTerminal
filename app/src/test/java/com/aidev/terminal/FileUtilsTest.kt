package com.aidev.terminal

import org.junit.Test
import org.junit.Assert.*
import java.io.File

class FileUtilsTest {

    @Test
    fun `formatSize - bytes`() {
        assertEquals("0B", FileUtils.formatSize(0))
        assertEquals("511B", FileUtils.formatSize(511))
        assertEquals("1.0K", FileUtils.formatSize(1024))
    }

    @Test
    fun `formatSize - kilobytes`() {
        assertEquals("1.0K", FileUtils.formatSize(1024))
        assertEquals("1.5K", FileUtils.formatSize(1536))
        assertEquals("1024.0K", FileUtils.formatSize(1024 * 1024 - 1))
    }

    @Test
    fun `formatSize - megabytes`() {
        assertEquals("1.0M", FileUtils.formatSize(1024 * 1024))
        assertEquals("1000.0M", FileUtils.formatSize(1024L * 1024 * 1000))
    }

    @Test
    fun `formatSize - gigabytes`() {
        assertEquals("1.0G", FileUtils.formatSize(1024L * 1024L * 1024L))
        assertEquals("2.0G", FileUtils.formatSize(2L * 1024 * 1024 * 1024))
        assertEquals("2.5G", FileUtils.formatSize((2.5 * 1024 * 1024 * 1024).toLong()))
    }

    @Test
    fun `isHtmlFile - true for html files`() {
        assertTrue(FileUtils.isHtmlFile(File("index.html")))
        assertTrue(FileUtils.isHtmlFile(File("page.htm")))
        assertTrue(FileUtils.isHtmlFile(File("PAGE.HTML")))
    }

    @Test
    fun `isHtmlFile - false for non-html`() {
        assertFalse(FileUtils.isHtmlFile(File("file.txt")))
        assertFalse(FileUtils.isHtmlFile(File("file.html5")))
        assertFalse(FileUtils.isHtmlFile(null))
    }

    @Test
    fun `isEnhancedFile - known extensions`() {
        assertTrue(FileUtils.isEnhancedFile(File("main.kt")))
        assertTrue(FileUtils.isEnhancedFile(File("README.md")))
        assertTrue(FileUtils.isEnhancedFile(File("script.py")))
        assertTrue(FileUtils.isEnhancedFile(File("build.gradle")))
    }

    @Test
    fun `isEnhancedFile - unknown extensions`() {
        assertFalse(FileUtils.isEnhancedFile(File("file.txt")))
        assertFalse(FileUtils.isEnhancedFile(File("data.bin")))
        assertFalse(FileUtils.isEnhancedFile(null))
    }

    @Test
    fun `getHighlightLanguage - known mappings`() {
        assertEquals("kotlin", FileUtils.getHighlightLanguage(File("main.kt")))
        assertEquals("python", FileUtils.getHighlightLanguage(File("script.py")))
        assertEquals("javascript", FileUtils.getHighlightLanguage(File("app.js")))
        assertEquals("cpp", FileUtils.getHighlightLanguage(File("main.cpp")))
    }

    @Test
    fun `getHighlightLanguage - unknown extension returns as-is`() {
        assertEquals("txt", FileUtils.getHighlightLanguage(File("file.txt")))
        assertEquals("", FileUtils.getHighlightLanguage(null))
    }

    @Test
    fun `isImageFile - known image extensions`() {
        assertTrue(FileUtils.isImageFile(File("photo.png")))
        assertTrue(FileUtils.isImageFile(File("photo.jpg")))
        assertTrue(FileUtils.isImageFile(File("photo.webp")))
        assertTrue(FileUtils.isImageFile(File("photo.GIF")))
    }

    @Test
    fun `isImageFile - non-image`() {
        assertFalse(FileUtils.isImageFile(File("document.pdf")))
        assertFalse(FileUtils.isImageFile(File("archive.zip")))
    }

    @Test
    fun `isLikelyText - simple text file`() {
        val f = File.createTempFile("test", ".txt").apply { writeText("hello world") }
        assertTrue(FileUtils.isLikelyText(f))
        f.delete()
    }

    @Test
    fun `isLikelyText - binary file with null byte`() {
        val f = File.createTempFile("test", ".bin").apply {
            writeBytes(byteArrayOf(0x48, 0x65, 0x00, 0x6C, 0x6C, 0x6F))
        }
        assertFalse(FileUtils.isLikelyText(f))
        f.delete()
    }
}
