package com.aidev.terminal

import java.io.File

object ProjectDetector {

    fun findProjectRoot(startDir: File): File? {
        var best: File? = null
        var gitRoot: File? = null
        var current = startDir.absoluteFile
        while (true) {
            if (hasGradleFile(current)) best = current
            if (File(current, ".git").isDirectory && gitRoot == null) gitRoot = current
            val parent = current.parentFile ?: break
            current = parent
        }
        return best ?: gitRoot
    }

    fun isProjectRoot(dir: File): Boolean = hasGradleFile(dir) || File(dir, ".git").isDirectory

    private fun hasGradleFile(dir: File): Boolean =
        File(dir, "settings.gradle").isFile || File(dir, "settings.gradle.kts").isFile
}
