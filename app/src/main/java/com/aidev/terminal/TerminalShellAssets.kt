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
        // camera-photo 和 camera-pick 是可执行脚本，不依赖 .aidevrc 函数
        writeCameraScript(bin, "camera-photo", "photo")
        writeCameraScript(bin, "camera-pick", "pick")
        // 系统控制脚本（通知、截图、音量、亮度、应用管理）
        writeSystemScript(bin, "sysnotify", "send notification")
        writeSystemScript(bin, "screencap", "take screenshot")
        writeSystemScript(bin, "volume", "control volume")
        writeSystemScript(bin, "brightness", "control brightness")
        writeSystemScript(bin, "startapp", "start app")
        writeSystemScript(bin, "stopapp", "stop app")
        writeSystemScript(bin, "installapk", "install apk")
        writeSystemScript(bin, "uninstallapp", "uninstall app")
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
            camera-photo() {
              local out="${'$'}{1:-/sdcard/DCIM/AIDev/photo_$(date +%Y%m%d_%H%M%S).jpg}"
              local req="${'$'}AIDEV_HOME/.aidev-shizuku-bridge/request/camera_$(date +%s)_${'$'}PPID"
              local res="${'$'}AIDEV_HOME/.aidev-shizuku-bridge/result/camera_$(date +%s)_${'$'}PPID"
              mkdir -p "${'$'}AIDEV_HOME/.aidev-shizuku-bridge/request" "${'$'}AIDEV_HOME/.aidev-shizuku-bridge/result"
              echo "MODE=photo" > "${'$'}req"
              echo "OUTPUT=${'$'}out" >> "${'$'}req"
              echo "[CAMERA] 请求已发送，等待拍照..."
              local count=0
              while [ ! -f "${'$'}res" ] && [ ${'$'}count -lt 60 ]; do
                sleep 1
                count=$((count + 1))
              done
              if [ -f "${'$'}res" ]; then
                cat "${'$'}res"
                rm -f "${'$'}req" "${'$'}res"
              else
                echo '{"status":"error","error":"拍照超时"}'
                rm -f "${'$'}req"
              fi
            }
            camera-pick() {
              local req="${'$'}AIDEV_HOME/.aidev-shizuku-bridge/request/camera_$(date +%s)_${'$'}PPID"
              local res="${'$'}AIDEV_HOME/.aidev-shizuku-bridge/result/camera_$(date +%s)_${'$'}PPID"
              mkdir -p "${'$'}AIDEV_HOME/.aidev-shizuku-bridge/request" "${'$'}AIDEV_HOME/.aidev-shizuku-bridge/result"
              echo "MODE=pick" > "${'$'}req"
              echo "[CAMERA] 请求已发送，等待选择图片..."
              local count=0
              while [ ! -f "${'$'}res" ] && [ ${'$'}count -lt 60 ]; do
                sleep 1
                count=$((count + 1))
              done
              if [ -f "${'$'}res" ]; then
                cat "${'$'}res"
                rm -f "${'$'}req" "${'$'}res"
              else
                echo '{"status":"error","error":"选择超时"}'
                rm -f "${'$'}req"
              fi
            }
            ${UbuntuBootstrapScripts.agentShellFunctions()}
            """.trimIndent() + "\n"
        )
    }

    private fun writeCameraScript(binDir: File, name: String, mode: String) {
        val script = File(binDir, name)
        script.writeText(
            """#!/system/bin/sh
            # AIDev camera bridge script. Works in both Android shell and Ubuntu proot.
            # Usage: $name [output_path]

            # Detect AIDEV_HOME
            if [ -z "${'$'}AIDEV_HOME" ]; then
                # Fallback: derive from script location
                AIDEV_HOME="$(dirname "$(dirname "$(dirname "${'$'}0")")")"
            fi

            BRIDGE_DIR="${'$'}AIDEV_HOME/.aidev-shizuku-bridge"
            REQ_DIR="${'$'}BRIDGE_DIR/request"
            RES_DIR="${'$'}BRIDGE_DIR/result"
            mkdir -p "${'$'}REQ_DIR" "${'$'}RES_DIR"

            TS=$(date +%s)
            PID=${'$'}PPID
            REQ="${'$'}REQ_DIR/camera_${'$'}TS_${'$'}PID"
            RES="${'$'}RES_DIR/camera_${'$'}TS_${'$'}PID"

            if [ "$mode" = "photo" ]; then
                OUT="${'$'}{1:-/sdcard/DCIM/AIDev/photo_$(date +%Y%m%d_%H%M%S).jpg}"
                echo "MODE=photo" > "${'$'}REQ"
                echo "OUTPUT=${'$'}OUT" >> "${'$'}REQ"
                echo "[CAMERA] Sending photo request..."
            else
                echo "MODE=pick" > "${'$'}REQ"
                echo "[CAMERA] Sending pick request..."
            fi

            COUNT=0
            while [ ! -f "${'$'}RES" ] && [ ${'$'}COUNT -lt 60 ]; do
                sleep 1
                COUNT=$((COUNT + 1))
            done

            if [ -f "${'$'}RES" ]; then
                cat "${'$'}RES"
                rm -f "${'$'}REQ" "${'$'}RES"
            else
                echo '{"status":"error","error":"${'$'}{mode} timeout"}'
                rm -f "${'$'}REQ"
                exit 1
            fi
            """.trimIndent() + "\n"
        )
        script.setExecutable(true, false)
        script.setReadable(true, false)
    }

    private fun writeSystemScript(binDir: File, name: String, desc: String) {
        val script = File(binDir, name)
        val content = when (name) {
            "sysnotify" -> """#!/system/bin/sh
                # AIDev system notification script
                # Usage: sysnotify <title> <message>
                if [ $# -lt 2 ]; then
                    echo "Usage: sysnotify <title> <message>"
                    exit 1
                fi
                TITLE="${'$'}1"; shift
                MSG="${'$'}*"
                am broadcast -p com.aidev.terminal -a com.aidev.terminal.SYSNOTIFY \
                    --es title "${'$'}TITLE" --es msg "${'$'}MSG" >/dev/null
                echo '{"status":"success","action":"notification sent"}'
                """.trimIndent()

            "screencap" -> """#!/system/bin/sh
                # AIDev screenshot script
                # Usage: screencap [output_path]
                OUT="${'$'}{1:-/sdcard/screenshot_$(date +%Y%m%d_%H%M%S).png}"
                /system/bin/screencap -p "${'$'}OUT"
                if [ -f "${'$'}OUT" ]; then
                    echo "{\"status\":\"success\",\"path\":\"${'$'}OUT\"}"
                else
                    echo '{"status":"error","error":"screenshot failed"}'
                    exit 1
                fi
                """.trimIndent()

            "volume" -> """#!/system/bin/sh
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
                    # Get current volume
                    CUR=$(/system/bin/sh -c "service call audio 15 i32 ${'$'}CODE" 2>/dev/null | grep -o '0x[0-9a-f]*' | head -1)
                    echo "{\"status\":\"success\",\"stream\":\"${'$'}STREAM\",\"volume\":\"${'$'}CUR\"}"
                elif [ "${'$'}VAL" = "+" ] || [ "${'$'}VAL" = "-" ]; then
                    KEY=$(if [ "${'$'}VAL" = "+" ]; then echo 24; else echo 25; fi)
                    input keyevent "${'$'}KEY"
                    echo "{\"status\":\"success\",\"stream\":\"${'$'}STREAM\",\"action\":\"${'$'}VAL\"}"
                else
                    am broadcast -p com.aidev.terminal -a com.aidev.terminal.SYSVOLUME \
                        --ei stream "${'$'}CODE" --ei volume "${'$'}VAL" >/dev/null
                    echo "{\"status\":\"success\",\"stream\":\"${'$'}STREAM\",\"volume\":${'$'}VAL}"
                fi
                """.trimIndent()

            "brightness" -> """#!/system/bin/sh
                # AIDev brightness control script
                # Usage: brightness [0-255|auto]
                VAL="${'$'}1"
                if [ -z "${'$'}VAL" ]; then
                    CUR=$(settings get system screen_brightness 2>/dev/null || echo "unknown")
                    echo "{\"status\":\"success\",\"brightness\":${'$'}CUR}"
                elif [ "${'$'}VAL" = "auto" ]; then
                    settings put system screen_brightness_mode 1
                    echo '{"status":"success","mode":"auto"}'
                else
                    settings put system screen_brightness_mode 0
                    settings put system screen_brightness "${'$'}VAL"
                    echo "{\"status\":\"success\",\"brightness\":${'$'}VAL}"
                fi
                """.trimIndent()

            "startapp" -> """#!/system/bin/sh
                # AIDev start app script
                # Usage: startapp <package_name>
                if [ -z "${'$'}1" ]; then
                    echo "Usage: startapp <package_name>"
                    exit 1
                fi
                monkey -p "${'$'}1" 1 >/dev/null 2>&1
                echo "{\"status\":\"success\",\"action\":\"started\",\"package\":\"${'$'}1\"}"
                """.trimIndent()

            "stopapp" -> """#!/system/bin/sh
                # AIDev stop app script
                # Usage: stopapp <package_name>
                if [ -z "${'$'}1" ]; then
                    echo "Usage: stopapp <package_name>"
                    exit 1
                fi
                am force-stop "${'$'}1"
                echo "{\"status\":\"success\",\"action\":\"stopped\",\"package\":\"${'$'}1\"}"
                """.trimIndent()

            "installapk" -> """#!/system/bin/sh
                # AIDev install APK script
                # Usage: installapk <apk_path>
                if [ -z "${'$'}1" ] || [ ! -f "${'$'}1" ]; then
                    echo "Usage: installapk <apk_path>"
                    exit 1
                fi
                pm install -r "${'$'}1"
                echo "{\"status\":\"success\",\"action\":\"installed\",\"path\":\"${'$'}1\"}"
                """.trimIndent()

            "uninstallapp" -> """#!/system/bin/sh
                # AIDev uninstall app script
                # Usage: uninstallapp <package_name>
                if [ -z "${'$'}1" ]; then
                    echo "Usage: uninstallapp <package_name>"
                    exit 1
                fi
                pm uninstall "${'$'}1"
                echo "{\"status\":\"success\",\"action\":\"uninstalled\",\"package\":\"${'$'}1\"}"
                """.trimIndent()

            else -> "#!/system/bin/sh\necho 'Unknown command: $name'\n"
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
