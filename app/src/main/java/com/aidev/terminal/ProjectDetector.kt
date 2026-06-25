package com.aidev.terminal

import java.io.File

object ProjectDetector {

    fun findProjectRoot(startDir: File): File? {
        var best: File? = null
        var current = startDir.absoluteFile
        while (true) {
            if (hasGradleFile(current)) {
                best = current
            }
            val parent = current.parentFile ?: break
            current = parent
        }
        if (best != null) return best
        current = startDir.absoluteFile
        while (true) {
            if (File(current, ".git").isDirectory) return current
            val parent = current.parentFile ?: break
            current = parent
        }
        return null
    }

    fun isProjectRoot(dir: File): Boolean = hasGradleFile(dir) || File(dir, ".git").isDirectory

    private fun hasGradleFile(dir: File): Boolean =
        File(dir, "settings.gradle").isFile || File(dir, "settings.gradle.kts").isFile
}
