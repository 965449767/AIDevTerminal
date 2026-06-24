package com.aidev.terminal

import android.app.Activity
import java.io.File

data class TerminalShellAssetPaths(
    val home: File,
    val entry: File
)

object TerminalShellAssets {
    private const val ASSET_VERSION = Constants.ASSET_VERSION

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
        // 每次 ensure 都检查 rootfs 中的辅助脚本（rootfs 可能被重装）
        val rootfs = File(home, "ubuntu-rootfs")
        if (rootfs.isDirectory) {
            UbuntuBootstrapScripts.copyAssetScripts(activity, rootfs)
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
        listOf("ubuntu", "install-ubuntu", "aidev-auto-bootstrap", "aidev-doctor").forEach { name ->
            val out = File(bin, name)
            out.writeText("# AIDev command marker. Android 私有目录禁止直接执行脚本；实际入口由 .aidevrc 函数转发。\n")
            out.setReadable(true, false)
        }
        // 系统控制脚本（通知、截图、音量、亮度、应用管理）
        writeSystemScript(bin, "sysnotify", "send notification")
        writeSystemScript(bin, "screencap", "take screenshot")
        writeSystemScript(bin, "volume", "control volume")
        writeSystemScript(bin, "brightness", "control brightness")
        writeSystemScript(bin, "startapp", "start app")
        writeSystemScript(bin, "stopapp", "stop app")
        writeSystemScript(bin, "installapk", "install apk")
        writeSystemScript(bin, "uninstallapp", "uninstall app")
        // 两端共用脚本（agent 辅助 + 系统工具）
        UbuntuBootstrapScripts.agentHostScripts().forEach { (name, content) ->
            val out = File(bin, name)
            out.writeText(content + "\n")
            out.setExecutable(true, false)
            out.setReadable(true, false)
        }
        val prootBin = File(bin, ".privot").apply { mkdirs() }
        UbuntuBootstrapScripts.agentPrivotScripts().forEach { (name, content) ->
            val out = File(prootBin, name)
            out.writeText(content + "\n")
            out.setExecutable(true, false)
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
            setup-dev-env() { /system/bin/sh "${'$'}AIDEV_BIN/aidev-ubuntu-core" setup-dev-env "${'$'}@"; }
            opencode-install() { /system/bin/sh "${'$'}AIDEV_BIN/aidev-ubuntu-core" opencode-install "${'$'}@"; }
            aidev-current-project() { /system/bin/sh "${'$'}AIDEV_BIN/aidev-current-project" "${'$'}@"; }
            aidev-agent-context() { /system/bin/sh "${'$'}AIDEV_BIN/aidev-agent-context" "${'$'}@"; }
            aidev-agent-context-file() { /system/bin/sh "${'$'}AIDEV_BIN/aidev-agent-context-file" "${'$'}@"; }
            aidev-agent-summary() { /system/bin/sh "${'$'}AIDEV_BIN/aidev-agent-summary" "${'$'}@"; }
            aidev-agent-log() { /system/bin/sh "${'$'}AIDEV_BIN/aidev-agent-log" "${'$'}@"; }
            aidev-agent-tail() { /system/bin/sh "${'$'}AIDEV_BIN/aidev-agent-tail" "${'$'}@"; }
            list-listen-ports() { /system/bin/sh "${'$'}AIDEV_BIN/list-listen-ports" "${'$'}@"; }
            task-list() { /system/bin/sh "${'$'}AIDEV_BIN/task-list" "${'$'}@"; }
            task-run() { /system/bin/sh "${'$'}AIDEV_BIN/task-run" "${'$'}@"; }
            """.trimIndent() + "\n"
        )
    }

    private fun writeSystemScript(binDir: File, name: String, desc: String) {
        val script = File(binDir, name)
        val content = when (name) {
            "sysnotify" -> """#!/bin/sh
                # AIDev system notification script
                # Usage: sysnotify <title> <message>
                if [ $# -lt 2 ]; then
                    echo "Usage: sysnotify <title> <message>"
                    exit 1
                fi
                TITLE="${'$'}1"; shift
                MSG="${'$'}*"
                am broadcast -p com.aidev.terminal -a com.aidev.terminal.internal.NOTIFY \
                    --es title "${'$'}TITLE" --es msg "${'$'}MSG" >/dev/null
                echo '{"status":"success","action":"notification sent"}'
                """.trimIndent()

            "screencap" -> """#!/bin/sh
                # AIDev screenshot script
                # Usage: screencap [output_path]
                OUT="${'$'}{1:-/sdcard/screenshot_$(date +%Y%m%d_%H%M%S).png}"
                screencap -p "${'$'}OUT"
                if [ -f "${'$'}OUT" ]; then
                    echo "{\"status\":\"success\",\"path\":\"${'$'}OUT\"}"
                else
                    echo '{"status":"error","error":"screenshot failed"}'
                    exit 1
                fi
                """.trimIndent()

            "volume" -> """#!/bin/sh
                # AIDev volume control script
                # Usage: volume [media|ring|alarm|call] [0-15|+|-]
                STREAM="${'$'}{1:-media}"
                VAL="${'$'}2"
                case "${'$'}STREAM" in
                    media)  CODE=3 ;;
                    ring)   CODE=2 ;;
                    alarm)  CODE=4 ;;
                    call)   CODE=0 ;;
                    *) echo '{"status":"error","error":"stream must be media|ring|alarm|call"}'; exit 1 ;;
                esac
                if [ -z "${'$'}VAL" ]; then
                    CUR=$(service call audio 15 i32 ${'$'}CODE 2>/dev/null | grep -o '0x[0-9a-f]*' | head -1)
                    echo "{\"status\":\"success\",\"stream\":\"${'$'}STREAM\",\"volume\":\"${'$'}CUR\"}"
                elif [ "${'$'}VAL" = "+" ] || [ "${'$'}VAL" = "-" ]; then
                    KEY=$(if [ "${'$'}VAL" = "+" ]; then echo 24; else echo 25; fi)
                    input keyevent "${'$'}KEY"
                    echo "{\"status\":\"success\",\"stream\":\"${'$'}STREAM\",\"action\":\"${'$'}VAL\"}"
                else
                    am broadcast -p com.aidev.terminal -a com.aidev.terminal.internal.VOLUME \
                        --ei stream "${'$'}CODE" --ei volume "${'$'}VAL" >/dev/null
                    echo "{\"status\":\"success\",\"stream\":\"${'$'}STREAM\",\"volume\":${'$'}VAL}"
                fi
                """.trimIndent()

            "brightness" -> """#!/bin/sh
                # AIDev brightness control script
                # Usage: brightness [0-255|auto]
                VAL="${'$'}1"
                if [ -z "${'$'}VAL" ]; then
                    CUR=$(settings get system screen_brightness 2>/dev/null || echo "unknown")
                    echo "{\"status\":\"success\",\"brightness\":${'$'}CUR}"
                elif [ "${'$'}VAL" = "auto" ]; then
                    am broadcast -p com.aidev.terminal -a com.aidev.terminal.internal.BRIGHTNESS \
                        --ez auto true >/dev/null
                    echo '{"status":"success","mode":"auto"}'
                else
                    am broadcast -p com.aidev.terminal -a com.aidev.terminal.internal.BRIGHTNESS \
                        --ei brightness "${'$'}VAL" >/dev/null
                    echo "{\"status\":\"success\",\"brightness\":${'$'}VAL}"
                fi
                """.trimIndent()

            "startapp" -> """#!/bin/sh
                # AIDev start app script
                # Usage: startapp <package_name>
                if [ -z "${'$'}1" ]; then
                    echo "Usage: startapp <package_name>"
                    exit 1
                fi
                monkey -p "${'$'}1" 1 >/dev/null 2>&1
                echo "{\"status\":\"success\",\"action\":\"started\",\"package\":\"${'$'}1\"}"
                """.trimIndent()

            "stopapp" -> """#!/bin/sh
                # AIDev stop app script
                # Usage: stopapp <package_name>
                if [ -z "${'$'}1" ]; then
                    echo "Usage: stopapp <package_name>"
                    exit 1
                fi
                am force-stop "${'$'}1"
                echo "{\"status\":\"success\",\"action\":\"stopped\",\"package\":\"${'$'}1\"}"
                """.trimIndent()

            "installapk" -> """#!/bin/sh
                # AIDev install APK script
                # Usage: installapk <apk_path>
                if [ -z "${'$'}1" ] || [ ! -f "${'$'}1" ]; then
                    echo "Usage: installapk <apk_path>"
                    exit 1
                fi
                pm install -r "${'$'}1"
                echo "{\"status\":\"success\",\"action\":\"installed\",\"path\":\"${'$'}1\"}"
                """.trimIndent()

            "uninstallapp" -> """#!/bin/sh
                # AIDev uninstall app script
                # Usage: uninstallapp <package_name>
                if [ -z "${'$'}1" ]; then
                    echo "Usage: uninstallapp <package_name>"
                    exit 1
                fi
                pm uninstall "${'$'}1"
                echo "{\"status\":\"success\",\"action\":\"uninstalled\",\"package\":\"${'$'}1\"}"
                """.trimIndent()

            else -> "#!/bin/sh\necho 'Unknown command: $name'\n"
        }
        script.writeText(content + "\n")
        script.setExecutable(true, false)
        script.setReadable(true, false)
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
