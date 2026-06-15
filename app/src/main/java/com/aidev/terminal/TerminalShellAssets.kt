package com.aidev.terminal

import android.app.Activity
import java.io.File

data class TerminalShellAssetPaths(
    val home: File,
    val entry: File
)

object TerminalShellAssets {
    private const val ASSET_VERSION = "0.12.32-terminal-file-sync-debug"

    fun ensure(activity: Activity): TerminalShellAssetPaths {
        val home = File(activity.filesDir, "home").apply { mkdirs() }
        val rc = File(home, ".aidevrc")
        val entry = File(home, ".aidev_shell_entry")
        val core = File(home, "dev-env/bin/aidev-ubuntu-core")
        val marker = File(home, ".aidev-shell-assets-version")
        installProotSupportLibraries(activity)
        val assetsReady = marker.exists() &&
            marker.readText().trim() == ASSET_VERSION &&
            rc.exists() &&
            entry.exists() &&
            core.exists()
        if (!assetsReady) {
            installAidevCommandScripts(home)
            writeCanonicalRc(activity, home, rc)
            writeShellEntry(home, rc, entry)
            marker.writeText("$ASSET_VERSION\n")
        }
        return TerminalShellAssetPaths(home, entry)
    }

    private fun installProotSupportLibraries(activity: Activity) {
        val outDir = File(activity.filesDir, "home/proot-lib").apply { mkdirs() }
        listOf("libtalloc.so.2", "libandroid-shmem.so").forEach { name ->
            val out = File(outDir, name)
            val marker = File(outDir, "$name.v2")
            if (out.exists() && marker.exists()) return@forEach
            runCatching {
                activity.assets.open("proot-libs/arm64-v8a/$name").use { input ->
                    out.outputStream().use { output -> input.copyTo(output) }
                }
                out.setReadable(true, false)
                marker.writeText("ok\n")
            }
        }
    }

    private fun installAidevCommandScripts(home: File) {
        val bin = File(home, "dev-env/bin").apply { mkdirs() }
        val core = File(bin, "aidev-ubuntu-core")
        core.writeText(UbuntuBootstrapScripts.aidevUbuntuCommandScript(home.absolutePath))
        core.setReadable(true, false)
        listOf("ubuntu", "install-ubuntu", "aidev-auto-bootstrap", "aidev-doctor", "aidev-index-commands").forEach { name ->
            val out = File(bin, name)
            out.writeText("# AIDev command marker. Android 私有目录禁止直接执行脚本；实际入口由 .aidevrc 函数转发。\n")
            out.setReadable(true, false)
        }
        File(home, ".aidev_shell_fallback").delete()
    }

    private fun writeCanonicalRc(activity: Activity, home: File, rc: File) {
        val nativeDir = activity.applicationInfo.nativeLibraryDir
        rc.writeText(
            """
            # AIDev canonical shell rc. 自动生成，请不要在这里保存个人配置。
            AIDEV_VERSION="$ASSET_VERSION"
            AIDEV_HOME="${home.absolutePath}"
            AIDEV_BIN="${'$'}AIDEV_HOME/dev-env/bin"
            AIDEV_ROOTFS="${'$'}AIDEV_HOME/ubuntu-rootfs"
            AIDEV_NATIVE="$nativeDir"
            AIDEV_PROOT="${'$'}AIDEV_NATIVE/libproot.so"
            AIDEV_PROOT_LOADER="${'$'}AIDEV_NATIVE/libproot_loader.so"
            PROOT_LOADER="${'$'}AIDEV_PROOT_LOADER"
            PROOT_TMP_DIR="${'$'}AIDEV_HOME/proot-tmp"
            export AIDEV_VERSION AIDEV_HOME AIDEV_BIN AIDEV_ROOTFS AIDEV_NATIVE AIDEV_PROOT AIDEV_PROOT_LOADER PROOT_LOADER PROOT_TMP_DIR
            export LANG=C.UTF-8
            export LC_ALL=C.UTF-8
            export PATH="${'$'}AIDEV_BIN:/system/bin:/system/xbin:${'$'}PATH"
            export PS1='aidev:${'$'}{PWD##*/}# '
            alias ll='ls -lah'
            android-sh() { /system/bin/sh -lc "${'$'}*"; }
            pmx() { android-sh "pm ${'$'}*"; }
            amx() { android-sh "am ${'$'}*"; }
            getpropx() { android-sh "getprop ${'$'}*"; }
            logcatx() { android-sh "logcat ${'$'}*"; }
            ubuntu() { /system/bin/sh "${'$'}AIDEV_BIN/aidev-ubuntu-core" ubuntu "${'$'}@"; }
            install-ubuntu() { /system/bin/sh "${'$'}AIDEV_BIN/aidev-ubuntu-core" install-ubuntu "${'$'}@"; }
            aidev-auto-bootstrap() { /system/bin/sh "${'$'}AIDEV_BIN/aidev-ubuntu-core" aidev-auto-bootstrap "${'$'}@"; }
            aidev-doctor() { /system/bin/sh "${'$'}AIDEV_BIN/aidev-ubuntu-core" aidev-doctor "${'$'}@"; }
            aidev-index-commands() { /system/bin/sh "${'$'}AIDEV_BIN/aidev-ubuntu-core" aidev-index-commands "${'$'}@"; }
            ${UbuntuBootstrapScripts.agentShellFunctions()}
            """.trimIndent() + "\n"
        )
    }

    private fun writeShellEntry(home: File, rc: File, entry: File) {
        val core = File(home, "dev-env/bin/aidev-ubuntu-core")
        val ready = File(home, "ubuntu-rootfs/.aidev-rootfs-ready")
        entry.writeText(
            """
            export ENV="${rc.absolutePath}"
            if [ -f "${ready.absolutePath}" ] && [ -f "${core.absolutePath}" ]; then
              /system/bin/sh "${core.absolutePath}" aidev-auto-bootstrap
            fi
            exec sh -i
            """.trimIndent() + "\n"
        )
    }
}
