package com.aidev.terminal

import android.app.Activity
import java.io.File

data class TerminalShellAssetPaths(
    val home: File,
    val entry: File
)

@Suppress("SetWorldReadable")
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
            deployTools(activity, rootfs)
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
        // 系统控制脚本（通知、截图、音量、亮度、剪贴板、应用管理）
        writeSystemScript(bin, "sysnotify", "send notification")
        writeSystemScript(bin, "screencap", "take screenshot")
        writeSystemScript(bin, "volume", "control volume")
        writeSystemScript(bin, "brightness", "control brightness")
        writeSystemScript(bin, "sysclip", "clipboard get/set")
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

    private fun deployTools(activity: Activity, rootfs: File) {
        val targetDir = File(rootfs, "usr/local/bin").apply { mkdirs() }
        val curlBin = File(targetDir, "curl")

        if (!curlBin.exists()) {
            runCatching {
                activity.assets.open("tools/curl").use { input ->
                    curlBin.outputStream().use { output -> input.copyTo(output) }
                }
                curlBin.setExecutable(true)
            }
        }

        val caCertsDir = File(rootfs, "etc/ssl/certs").apply { mkdirs() }
        val caCertFile = File(caCertsDir, "ca-certificates.crt")
        if (!caCertFile.exists()) {
            runCatching {
                activity.assets.open("tools/ca-certificates.crt").use { input ->
                    caCertFile.outputStream().use { output -> input.copyTo(output) }
                }
                caCertFile.setReadable(true)
            }
        }

        val cmdsDir = File(rootfs, "root/.config/opencode/commands").apply { mkdirs() }
        listOf(
            "aidev-build", "aidev-apk-info", "aidev-create-project",
            "aidev-gen", "aidev-error-why", "aidev-logcat", "aidev-index"
        ).forEach { name ->
            val out = File(cmdsDir, "$name.md")
            if (!out.exists()) {
                runCatching {
                    activity.assets.open("config/opencode/commands/$name.md").use { input ->
                        out.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            }
        }
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
            export PATH="/usr/local/bin:${'$'}AIDEV_BIN:/system/bin:/system/xbin:${'$'}PATH"
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
            setup-opencode() { /system/bin/sh "${'$'}AIDEV_BIN/aidev-ubuntu-core" setup-opencode "${'$'}@"; }
            aidev-current-project() { /system/bin/sh "${'$'}AIDEV_BIN/aidev-current-project" "${'$'}@"; }
            aidev-agent-context() { /system/bin/sh "${'$'}AIDEV_BIN/aidev-agent-context" "${'$'}@"; }
            aidev-agent-context-file() { /system/bin/sh "${'$'}AIDEV_BIN/aidev-agent-context-file" "${'$'}@"; }
            aidev-agent-summary() { /system/bin/sh "${'$'}AIDEV_BIN/aidev-agent-summary" "${'$'}@"; }
            aidev-agent-log() { /system/bin/sh "${'$'}AIDEV_BIN/aidev-agent-log" "${'$'}@"; }
            aidev-agent-tail() { /system/bin/sh "${'$'}AIDEV_BIN/aidev-agent-tail" "${'$'}@"; }
            aidev-shizuku() { /system/bin/sh "${'$'}AIDEV_ROOTFS/usr/local/bin/aidev-shizuku" "${'$'}@"; }
            aidev-apk-info() { /system/bin/sh "${'$'}AIDEV_BIN/aidev-ubuntu-core" aidev-apk-info "${'$'}@"; }
            aidev-build() { /system/bin/sh "${'$'}AIDEV_BIN/aidev-ubuntu-core" aidev-build "${'$'}@"; }
            aidev-create-android-project() { /system/bin/sh "${'$'}AIDEV_BIN/aidev-ubuntu-core" aidev-create-android-project "${'$'}@"; }
            aidev-gen() { /system/bin/sh "${'$'}AIDEV_BIN/aidev-ubuntu-core" aidev-gen "${'$'}@"; }
            aidev-error-why() { /system/bin/sh "${'$'}AIDEV_BIN/aidev-ubuntu-core" aidev-error-why "${'$'}@"; }
            aidev-index() { /system/bin/sh "${'$'}AIDEV_BIN/aidev-ubuntu-core" aidev-index "${'$'}@"; }
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
                # Usage: sysnotify [--priority min|low|default|high|max] [--ongoing] [--alert-once] <title> <message>
                PRIORITY=""
                ONGOING="false"
                ALERT_ONCE="false"
                while [ ${'$'}# -gt 0 ]; do
                    case "${'$'}1" in
                        --priority) PRIORITY="${'$'}2"; shift 2 ;;
                        --ongoing)  ONGOING="true"; shift ;;
                        --alert-once) ALERT_ONCE="true"; shift ;;
                        --help|-h)
                            echo "Usage: sysnotify [--priority min|low|default|high|max] [--ongoing] [--alert-once] <title> <message>"
                            exit 0 ;;
                        *) break ;;
                    esac
                done
                if [ ${'$'}# -lt 2 ]; then
                    echo "Usage: sysnotify [options] <title> <message>"
                    exit 1
                fi
                TITLE="${'$'}1"; shift
                MSG="${'$'}*"
                REQ_DIR="${'$'}{AIDEV_HOME}/.aidev-notify"
                mkdir -p "${'$'}REQ_DIR"
                echo "{\"title\":\"${'$'}TITLE\",\"message\":\"${'$'}MSG\",\"priority\":\"${'$'}PRIORITY\",\"ongoing\":${'$'}ONGOING,\"alert_only_once\":${'$'}ALERT_ONCE}" > "${'$'}REQ_DIR/req-$(date +%s%N).json"
                echo '{"status":"success","action":"notification sent"}'
                """.trimIndent()

            "screencap" -> """#!/bin/sh
                # AIDev screenshot script
                # Usage: screencap [output_path]
                OUT="${'$'}{1:-/sdcard/screenshot_$(date +%Y%m%d_%H%M%S).png}"
                REQ_DIR="${'$'}{AIDEV_HOME}/.aidev-cmd"
                mkdir -p "${'$'}REQ_DIR"
                echo "{\"action\":\"screencap\",\"path\":\"${'$'}OUT\"}" > "${'$'}REQ_DIR/req-$(date +%s%N).json"
                echo "{\"status\":\"success\",\"action\":\"screencap requested\",\"path\":\"${'$'}OUT\"}"
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
                    CUR=$(/system/bin/dumpsys audio 2>/dev/null | grep -i "${'$'}STREAM" | head -1)
                    echo "{\"status\":\"success\",\"stream\":\"${'$'}STREAM\",\"volume\":\"${'$'}CUR\"}"
                elif [ "${'$'}VAL" = "+" ] || [ "${'$'}VAL" = "-" ]; then
                    KEY=$(if [ "${'$'}VAL" = "+" ]; then echo 24; else echo 25; fi)
                    /system/bin/input keyevent "${'$'}KEY"
                    echo "{\"status\":\"success\",\"stream\":\"${'$'}STREAM\",\"action\":\"${'$'}VAL\"}"
                else
                    REQ_DIR="${'$'}{AIDEV_HOME}/.aidev-cmd"
                    mkdir -p "${'$'}REQ_DIR"
                    echo "{\"action\":\"volume\",\"stream\":${'$'}CODE,\"volume\":${'$'}VAL}" > "${'$'}REQ_DIR/req-$(date +%s%N).json"
                    echo "{\"status\":\"success\",\"stream\":\"${'$'}STREAM\",\"volume\":${'$'}VAL}"
                fi
                """.trimIndent()

            "brightness" -> """#!/bin/sh
                # AIDev brightness control script
                # Usage: brightness [0-255|auto]
                VAL="${'$'}1"
                REQ_DIR="${'$'}{AIDEV_HOME}/.aidev-cmd"
                mkdir -p "${'$'}REQ_DIR"
                if [ -z "${'$'}VAL" ]; then
                    CUR=$(/system/bin/settings get system screen_brightness 2>/dev/null || echo "unknown")
                    echo "{\"status\":\"success\",\"brightness\":${'$'}CUR}"
                elif [ "${'$'}VAL" = "auto" ]; then
                    echo "{\"action\":\"brightness\",\"auto\":true}" > "${'$'}REQ_DIR/req-$(date +%s%N).json"
                    echo '{"status":"success","mode":"auto"}'
                else
                    echo "{\"action\":\"brightness\",\"brightness\":${'$'}VAL}" > "${'$'}REQ_DIR/req-$(date +%s%N).json"
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
                REQ_DIR="${'$'}{AIDEV_HOME}/.aidev-cmd"
                mkdir -p "${'$'}REQ_DIR"
                echo "{\"action\":\"startapp\",\"package\":\"${'$'}1\"}" > "${'$'}REQ_DIR/req-$(date +%s%N).json"
                echo "{\"status\":\"success\",\"action\":\"started\",\"package\":\"${'$'}1\"}"
                """.trimIndent()

            "stopapp" -> """#!/bin/sh
                # AIDev stop app script
                # Usage: stopapp <package_name>
                if [ -z "${'$'}1" ]; then
                    echo "Usage: stopapp <package_name>"
                    exit 1
                fi
                REQ_DIR="${'$'}{AIDEV_HOME}/.aidev-cmd"
                mkdir -p "${'$'}REQ_DIR"
                echo "{\"action\":\"stopapp\",\"package\":\"${'$'}1\"}" > "${'$'}REQ_DIR/req-$(date +%s%N).json"
                echo "{\"status\":\"success\",\"action\":\"stopped\",\"package\":\"${'$'}1\"}"
                """.trimIndent()

            "installapk" -> """#!/bin/sh
                # AIDev install APK script
                # Usage: installapk <apk_path>
                if [ -z "${'$'}1" ] || [ ! -f "${'$'}1" ]; then
                    echo "Usage: installapk <apk_path>"
                    exit 1
                fi
                # resolve /host-home/ to real Android path
                APK="${'$'}1"
                case "${'$'}APK" in /host-home/*)
                    APK="${'$'}{AIDEV_HOME}${'$'}{APK#/host-home}"
                esac
                REQ_DIR="${'$'}{AIDEV_HOME}/.aidev-cmd"
                mkdir -p "${'$'}REQ_DIR"
                echo "{\"action\":\"installapk\",\"path\":\"${'$'}APK\"}" > "${'$'}REQ_DIR/req-$(date +%s%N).json"
                echo "{\"status\":\"success\",\"action\":\"install launched\",\"path\":\"${'$'}1\"}"
                """.trimIndent()

            "uninstallapp" -> """#!/bin/sh
                # AIDev uninstall app script
                # Usage: uninstallapp <package_name>
                if [ -z "${'$'}1" ]; then
                    echo "Usage: uninstallapp <package_name>"
                    exit 1
                fi
                REQ_DIR="${'$'}{AIDEV_HOME}/.aidev-cmd"
                mkdir -p "${'$'}REQ_DIR"
                echo "{\"action\":\"uninstallapp\",\"package\":\"${'$'}1\"}" > "${'$'}REQ_DIR/req-$(date +%s%N).json"
                echo "{\"status\":\"success\",\"action\":\"uninstall launched\",\"package\":\"${'$'}1\"}"
                """.trimIndent()

            "sysclip" -> """#!/bin/sh
                # AIDev clipboard script
                # Usage: sysclip set <text>
                #        sysclip get
                CMD="${'$'}{1:-}"
                if [ "${'$'}CMD" = "get" ]; then
                    /system/bin/service call clipboard 2 2>/dev/null || echo '{"status":"error","error":"clipboard read not supported"}'
                    exit 0
                fi
                shift 2>/dev/null
                TEXT="${'$'}*"
                to_json() {
                    python3 -c "import json,sys; print(json.dumps(sys.stdin.read()))" 2>/dev/null || \
                    /system/bin/sh -c "printf '%s' \"${'$'}1\" | sed 's/\\\"/\\\\\"/g' | sed 's/^/\\\"/;s/$/\\\"/'"
                }
                REQ_DIR="${'$'}{AIDEV_HOME}/.aidev-cmd"
                mkdir -p "${'$'}REQ_DIR"
                ESCAPED=$(/system/bin/sh -c "printf '%s' \"${'$'}TEXT\" | python3 -c 'import json,sys; print(json.dumps(sys.stdin.read()))'" 2>/dev/null || echo "\"${'$'}TEXT\"")
                echo "{\"action\":\"clipboard\",\"text\":${'$'}ESCAPED}" > "${'$'}REQ_DIR/req-$(date +%s%N).json"
                echo "{\"status\":\"success\",\"action\":\"clipboard set\"}"
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
