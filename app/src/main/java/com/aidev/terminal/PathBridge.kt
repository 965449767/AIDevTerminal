package com.aidev.terminal

import java.io.File

object PathBridge {

    private val BLOCKED_PREFIXES = listOf(
        "/bin", "/sbin", "/lib", "/usr/bin", "/usr/sbin", "/usr/lib",
        "/etc", "/boot", "/dev", "/proc", "/sys"
    )

    fun ubuntuToAndroid(home: File, ubuntuPath: String): File? {
        val clean = ubuntuPath.trimEnd('/')
        return when {
            clean == "/host-home" || clean.startsWith("/host-home/") -> {
                val rest = if (clean == "/host-home") "" else clean.removePrefix("/host-home/")
                File(home, rest.ifBlank { "." })
            }
            clean == "/root" || clean.startsWith("/root/") -> {
                val rest = if (clean == "/root") "" else clean.removePrefix("/root/")
                File(home, "ubuntu-rootfs/root/$rest".trimEnd('/'))
            }
            clean == "/" -> File(home, "ubuntu-rootfs")
            clean.startsWith("/") -> File(home, "ubuntu-rootfs${clean}")
            else -> null
        }
    }

    fun androidToUbuntu(home: File, file: File): String? {
        val abs = file.absolutePath
        val homeAbs = home.absolutePath.trimEnd('/')
        val rootfs = "$homeAbs/ubuntu-rootfs"
        return when {
            abs == homeAbs -> "/host-home"
            abs.startsWith("$homeAbs/") -> "/host-home/${abs.removePrefix("$homeAbs/")}"
            abs.startsWith("$rootfs/root/") -> "/root/${abs.removePrefix("$rootfs/root/")}"
            abs == "$rootfs/root" -> "/root"
            abs.startsWith("$rootfs/") -> abs.removePrefix(rootfs)
            else -> null
        }
    }

    fun isBrowsable(ubuntuPath: String): Boolean {
        val clean = ubuntuPath.trimEnd('/')
        return BLOCKED_PREFIXES.none { prefix ->
            clean == prefix || clean.startsWith("$prefix/")
        }
    }
}
