package com.aidev.terminal

import android.app.Activity
import android.os.Build
import java.io.File

data class TerminalShellAssetPaths(
    val home: File,
    val entry: File
)

@Suppress("SetWorldReadable")
object TerminalShellAssets {

    fun ensure(activity: Activity): TerminalShellAssetPaths {
        val home = File(activity.filesDir, "home").apply { mkdirs() }
        val rc = File(home, ".aidevrc")
        val entry = File(home, ".aidev_shell_entry")
        val rootfs = File(home, "ubuntu-rootfs")

        // 第一类：生成脚本无条件写，永不过期
        installAidevCommandScripts(home)
        writeCanonicalRc(activity, home, rc)
        writeShellEntry(home, rc, entry)
        writeGradleInitScripts(home, rootfs)

        // 第二类：大文件用 versionCode 门控
        val deployMarker = File(home, ".asset-deploy-code")
        val currentCode = getVersionCode(activity)
        val needsDeploy = !deployMarker.exists() || deployMarker.readText().trim() != currentCode.toString()
        if (needsDeploy) {
            installProotSupportLibraries(activity)
            if (rootfs.isDirectory) {
                deployTools(activity, rootfs)
            }
            deployMarker.writeText("$currentCode\n")
        }

        // rootfs 辅助脚本每次检查（rootfs 可能被重装）
        if (rootfs.isDirectory) {
            UbuntuBootstrapScripts.copyAssetScripts(activity, rootfs)
        }

        return TerminalShellAssetPaths(home, entry)
    }

    private fun getVersionCode(activity: Activity): Long {
        val pkgInfo = activity.packageManager.getPackageInfo(activity.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) {
            pkgInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION") pkgInfo.versionCode.toLong()
        }
    }

    private fun installProotSupportLibraries(activity: Activity) {
        val outDir = File(activity.filesDir, "home/proot-lib").apply { mkdirs() }
        listOf("libtalloc.so.2", "libandroid-shmem.so").forEach { name ->
            runCatching {
                activity.assets.open("proot-libs/arm64-v8a/$name").use { input ->
                    File(outDir, name).outputStream().use { output -> input.copyTo(output) }
                }
                File(outDir, name).setReadable(true, false)
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
        writeSystemScript(bin, "aidev-proxy", "proxy manager")
        writeSystemScript(bin, "aidev-clean", "build cleaner")
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
        runCatching {
            activity.assets.open("tools/curl").use { input ->
                File(targetDir, "curl").outputStream().use { output -> input.copyTo(output) }
            }
            File(targetDir, "curl").setExecutable(true)
        }

        val caCertsDir = File(rootfs, "etc/ssl/certs").apply { mkdirs() }
        runCatching {
            activity.assets.open("tools/ca-certificates.crt").use { input ->
                File(caCertsDir, "ca-certificates.crt").outputStream().use { output -> input.copyTo(output) }
            }
            File(caCertsDir, "ca-certificates.crt").setReadable(true)
        }

        val cmdsDir = File(rootfs, "root/.config/opencode/commands").apply { mkdirs() }
        listOf(
            "aidev-build", "aidev-apk-info", "aidev-create-project",
            "aidev-gen", "aidev-error-why", "aidev-logcat", "aidev-index"
        ).forEach { name ->
            runCatching {
                activity.assets.open("config/opencode/commands/$name.md").use { input ->
                    File(cmdsDir, "$name.md").outputStream().use { output -> input.copyTo(output) }
                }
            }
        }
    }

    private fun writeCanonicalRc(activity: Activity, home: File, rc: File) {
        val nativeDir = activity.applicationInfo.nativeLibraryDir
        val v = getVersionCode(activity)
        rc.writeText(
            """
            # AIDev canonical shell rc. 自动生成，请不要在这里保存个人配置。
            AIDEV_VERSION="$v"
            AIDEV_HOME="${home.absolutePath}"
            AIDEV_BIN="${'$'}AIDEV_HOME/dev-env/bin"
            AIDEV_ROOTFS="${'$'}AIDEV_HOME/ubuntu-rootfs"
            AIDEV_NATIVE="$nativeDir"
            AIDEV_PROOT="${'$'}AIDEV_NATIVE/libproot.so"
            AIDEV_PROOT_LOADER="${'$'}AIDEV_NATIVE/libproot_loader.so"
            PROOT_LOADER="${'$'}AIDEV_PROOT_LOADER"
            PROOT_TMP_DIR="${'$'}AIDEV_HOME/proot-tmp"
            ANDROID_SDK_ROOT="${'$'}AIDEV_HOME/android-sdk"
            GRADLE_USER_HOME="${'$'}AIDEV_HOME/gradle-cache"
            export AIDEV_VERSION AIDEV_HOME AIDEV_BIN AIDEV_ROOTFS AIDEV_NATIVE AIDEV_PROOT AIDEV_PROOT_LOADER PROOT_LOADER PROOT_TMP_DIR
            export ANDROID_SDK_ROOT GRADLE_USER_HOME
            export LANG=C.UTF-8
            export LC_ALL=C.UTF-8
            export PATH="/usr/local/bin:${'$'}AIDEV_BIN:${'$'}ANDROID_SDK_ROOT/cmdline-tools/latest/bin:/system/bin:/system/xbin:${'$'}PATH"
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
            aidev-proxy() { /system/bin/sh "${'$'}AIDEV_BIN/aidev-proxy" "${'$'}@"; }
            aidev-clean() { /system/bin/sh "${'$'}AIDEV_BIN/aidev-clean" "${'$'}@"; }
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

            "aidev-proxy" -> """#!/bin/sh
                # AIDev proxy manager (tinyproxy)
                # Usage: aidev-proxy [start|stop|restart|status]
                CONF="${'$'}{AIDEV_HOME}/tinyproxy.conf"
                PIDFILE="/tmp/tinyproxy.pid"
                mkdir -p "${'$'}{AIDEV_HOME}/proxy-cache"
                case "${'$'}1" in
                    start)
                        if [ -f "${'$'}PIDFILE" ] && kill -0 $(cat "${'$'}PIDFILE") 2>/dev/null; then
                            echo "proxy already running (pid $(cat ${'$'}PIDFILE))"
                            exit 0
                        fi
                        cat > "${'$'}CONF" << EOF
Port 18080
Listen 127.0.0.1
Timeout 30
Syslog Off
LogLevel Warning
PidFile ${'$'}PIDFILE
XTinyProxy Off
CacheDir ${'$'}{AIDEV_HOME}/proxy-cache
CacheSize 500
CacheMaxExpire 1440
EOF
                        tinyproxy -c "${'$'}CONF" && echo "proxy started (port 18080)" && \
                        echo "export GRADLE_OPTS=\"${'$'}GRADLE_OPTS -Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=18080 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=18080\"" || \
                        echo "proxy start failed" ;;
                    stop)
                        [ -f "${'$'}PIDFILE" ] && kill $(cat "${'$'}PIDFILE") 2>/dev/null && rm -f "${'$'}PIDFILE" && echo "proxy stopped" || echo "proxy not running" ;;
                    restart) "${'$'}0" stop && "${'$'}0" start ;;
                    status)
                        if [ -f "${'$'}PIDFILE" ] && kill -0 $(cat "${'$'}PIDFILE") 2>/dev/null; then
                            echo "proxy running (pid $(cat ${'$'}PIDFILE), port 18080)"
                            echo "cache: ${'$'}{AIDEV_HOME}/proxy-cache"
                            echo "usage: GRADLE_OPTS with proxy settings"
                        else
                            echo "proxy not running"
                        fi ;;
                    *) echo "Usage: aidev-proxy [start|stop|restart|status]" ;;
                esac
                """.trimIndent()
            "aidev-clean" -> """#!/bin/sh
                # AIDev build cache cleaner
                # Usage: aidev-clean [--all|--gradle|--builds|--dry-run]
                echo "AIDev 存储清理"
                echo
                case "${'$'}1" in
                    --all|-a)
                        echo "=== Gradle 缓存 ==="
                        GCACHE="${'$'}{GRADLE_USER_HOME:-${'$'}AIDEV_HOME/gradle-cache}/caches"
                        if [ -d "${'$'}GCACHE" ]; then
                            SIZE=$(du -sh "${'$'}GCACHE" 2>/dev/null | cut -f1)
                            echo "  gradle caches: ${'$'}SIZE"
                            rm -rf "${'$'}GCACHE/modules-2/tmp" 2>/dev/null
                            find "${'$'}GCACHE" -name "*.lock" -delete 2>/dev/null
                            echo "  cleared tmp/locks"
                        fi
                        echo "=== 构建目录 ==="
                        for d in build app/build .gradle; do
                            [ -d "${'$'}d" ] && echo "  ${'$'}d: $(du -sh "${'$'}d" 2>/dev/null | cut -f1)" && rm -rf "${'$'}d" 2>/dev/null && echo "  -> removed"
                        done
                        echo "=== 系统缓存 ==="
                        apt-get clean 2>/dev/null && echo "  apt cache cleaned" || true
                        ;;
                    --gradle|-g)
                        GCACHE="${'$'}{GRADLE_USER_HOME:-${'$'}AIDEV_HOME/gradle-cache}/caches"
                        [ -d "${'$'}GCACHE" ] && du -sh "${'$'}GCACHE" && rm -rf "${'$'}GCACHE/modules-2/tmp" 2>/dev/null && echo "tmp cleaned" || echo "no gradle cache"
                        ;;
                    --builds|-b)
                        for d in build app/build; do
                            [ -d "${'$'}d" ] && du -sh "${'$'}d" && rm -rf "${'$'}d" && echo "removed" || true
                        done
                        ;;
                    --dry-run|-n)
                        echo "=== Gradle caches ==="
                        GCACHE="${'$'}{GRADLE_USER_HOME:-${'$'}AIDEV_HOME/gradle-cache}"
                        [ -d "${'$'}GCACHE" ] && du -sh "${'$'}GCACHE" || echo "  (none)"
                        echo "=== Build dirs ==="
                        for d in build app/build .gradle; do
                            [ -d "${'$'}d" ] && echo "  ${'$'}d: $(du -sh "${'$'}d" 2>/dev/null | cut -f1)" || true
                        done
                        echo "=== apt cache ==="
                        du -sh /var/cache/apt 2>/dev/null || echo "  (none)"
                        echo "=== proxy cache ==="
                        du -sh "${'$'}AIDEV_HOME/proxy-cache" 2>/dev/null || echo "  (none)"
                        ;;
                    *) echo "Usage: aidev-clean [--all|--gradle|--builds|--dry-run]" ;;
                esac
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

    private fun writeGradleInitScripts(home: File, rootfs: File) {
        val scripts = mapOf(
            "wrap-native.gradle" to """
                // AIDev: Auto-wrap aapt2 for ARM64 QEMU environment
                def arch = System.getProperty("os.arch", "")
                if (!arch.contains("aarch64")) return
                if (System.getProperty("android.aapt2DaemonMode") == null) {
                    System.setProperty("android.aapt2DaemonMode", "false")
                }
                def aapt2 = System.getProperty("android.aapt2FromMavenOverride")
                if (aapt2 == null) {
                    def sdkDir = System.getenv("ANDROID_SDK_ROOT") ?: "/Android"
                    def btDir = new File(sdkDir, "build-tools")
                    if (btDir.exists()) {
                        def dirs = btDir.listFiles().findAll { it.isDirectory() }.sort().reverse()
                        for (d in dirs) {
                            def f = new File(d, "aapt2")
                            if (f.canExecute()) {
                                System.setProperty("android.aapt2FromMavenOverride", f.absolutePath)
                                aapt2 = f.absolutePath
                                break
                            }
                        }
                    }
                }
                logger.lifecycle "AIDev: ARM64 QEMU wrapper" + (aapt2 ? " (aapt2=" + aapt2 + ")" : "")
            """.trimIndent(),
            "copy-apk.gradle" to """
                // AIDev: Copy built APKs to /sdcard/ for easy installation
                gradle.projectsLoaded {
                    rootProject.allprojects { p ->
                        p.afterEvaluate {
                            if (!p.plugins.hasPlugin("com.android.application")) return
                            p.android.applicationVariants.configureEach { variant ->
                                def caps = variant.name.substring(0,1).toUpperCase() + variant.name.substring(1)
                                def assemble = tasks.named("assemble" + caps)
                                assemble.configure {
                                    doLast {
                                        def apk = variant.outputs.first()?.outputFile
                                        if (apk != null && apk.exists()) {
                                            def target = new File("/sdcard/AIDev/", apk.name)
                                            target.parentFile.mkdirs()
                                            target.bytes = apk.bytes
                                            logger.lifecycle "AIDev: APK -> " + target
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            """.trimIndent(),
            "performance.gradle" to """
                // AIDev: Gradle performance tuning
                def workers = Runtime.runtime.availableProcessors().toString()
                System.setProperty("org.gradle.workers.max", workers)
                gradle.projectsLoaded {
                    logger.lifecycle "AIDev: workers=" + workers
                }
            """.trimIndent()
        )

        val androidDir = File(home, "gradle-init.d").apply { mkdirs() }
        scripts.forEach { (name, content) ->
            File(androidDir, name).writeText(content + "\n")
        }

        if (rootfs.isDirectory) {
            val ubuntuDir = File(rootfs, "root/.gradle/init.d").apply { mkdirs() }
            scripts.forEach { (name, content) ->
                File(ubuntuDir, name).writeText(content + "\n")
            }
        }
    }
}
