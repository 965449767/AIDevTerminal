package com.aidev.terminal

object UbuntuBootstrapScripts {
    fun agentHostScripts(): Map<String, String> = mapOf(
        "aidev-current-project" to """#!/system/bin/sh
            pwd
            [ -d .git ] && git status --short --branch 2>/dev/null
            [ -f package.json ] && node -e "const p=require('./package.json'); console.log('package:',p.name||'-'); console.log('scripts:',Object.keys(p.scripts||{}).join(','))" 2>/dev/null
            [ -f pyproject.toml ] && echo "python: pyproject.toml"
            [ -f requirements.txt ] && echo "python: requirements.txt"
            [ -f build.gradle ] || [ -f build.gradle.kts ] && echo "gradle project"
            [ -f go.mod ] && echo "go module"
            [ -f Cargo.toml ] && echo "rust cargo"
        """.trimIndent(),
        "aidev-agent-context" to """#!/system/bin/sh
            echo "== AIDev Agent Context =="
            echo "time: $(date '+%F %T')"
            echo "pwd: $(pwd)"
            echo "version: ${'$'}{AIDEV_VERSION:-unknown}"
            echo
            echo "== project =="
            /system/bin/sh "${'$'}AIDEV_BIN/aidev-current-project"
            echo
            echo "== files =="
            find . -maxdepth 2 -type f 2>/dev/null | sed 's#^\./##' | head -120
            echo
            echo "== recent git =="
            git status --short --branch 2>/dev/null || true
            git diff --stat 2>/dev/null | head -80 || true
            echo
            echo "== tasks =="
            /system/bin/sh "${'$'}AIDEV_BIN/task-list" 2>/dev/null || true
        """.trimIndent(),
        "aidev-agent-context-file" to """#!/system/bin/sh
            out="aidev-agent-context.txt"
            /system/bin/sh "${'$'}AIDEV_BIN/aidev-agent-context" > "${'$'}{out}"
            echo >> "${'$'}{out}"
            echo "== recent errors ==" >> "${'$'}{out}"
            /system/bin/sh "${'$'}AIDEV_BIN/aidev-agent-summary" >> "${'$'}{out}" 2>/dev/null || true
            echo "已导出上下文文件：$(pwd)/${'$'}{out}"
        """.trimIndent(),
        "aidev-agent-summary" to """#!/system/bin/sh
            log="$(ls -t "${'$'}{AIDEV_HOME}/tasks"/*.log 2>/dev/null | head -1)"
            [ -n "${'$'}{log}" ] || { echo "暂无任务日志"; exit 0; }
            echo "== log =="
            echo "${'$'}{log}"
            echo
            echo "== recent commands =="
            grep -iE "command|running|exec|npm |python|gradle|go |cargo|git " "${'$'}{log}" 2>/dev/null | tail -20 || true
            echo
            echo "== recent errors =="
            grep -iE "error|failed|exception|traceback|cannot|not found|denied" "${'$'}{log}" 2>/dev/null | tail -40 || true
            echo
            echo "== possible modified files =="
            grep -iE "modified|created|updated|wrote|write|saved|changed" "${'$'}{log}" 2>/dev/null | tail -30 || true
        """.trimIndent(),
        "aidev-agent-log" to """#!/system/bin/sh
            ls -lt "${'$'}{AIDEV_HOME}/tasks"/*.log 2>/dev/null | head -20
        """.trimIndent(),
        "aidev-agent-tail" to """#!/system/bin/sh
            log="$(ls -t "${'$'}{AIDEV_HOME}/tasks"/*.log 2>/dev/null | head -1)"
            [ -n "${'$'}{log}" ] || { echo "暂无任务日志"; exit 1; }
            tail -f "${'$'}{log}"
        """.trimIndent(),
        "list-listen-ports" to """#!/system/bin/sh
            if command -v ss >/dev/null 2>&1; then
              ss -tlnp 2>/dev/null
            elif command -v netstat >/dev/null 2>&1; then
              netstat -tlnp 2>/dev/null
            else
              echo "=== /proc/net/tcp ==="
              cat /proc/net/tcp 2>/dev/null | head -40 || echo "(无法读取)"
              echo "=== /proc/net/tcp6 ==="
              cat /proc/net/tcp6 2>/dev/null | head -40 || true
            fi
        """.trimIndent(),
        "task-list" to """#!/system/bin/sh
            dir="${'$'}{AIDEV_HOME}/tasks"
            [ -d "${'$'}{dir}" ] || { echo "暂无任务"; exit 0; }
            for meta in "${'$'}{dir}"/*.meta; do
              [ -f "${'$'}{meta}" ] || continue
              id="${'$'}{meta##*/}"; id="${'$'}{id%.meta}"
              name=""; pid=""; cmd=""
              while IFS='=' read -r k v; do
                case "${'$'}{k}" in
                  name) name="${'$'}{v}" ;;
                  pid) pid="${'$'}{v}" ;;
                  cmd) cmd="${'$'}{v}" ;;
                esac
              done < "${'$'}{meta}"
              if [ -n "${'$'}{pid}" ]; then
                kill -0 "${'$'}{pid}" 2>/dev/null && status="运行中" || status="已结束"
              else
                status="未知"
              fi
              echo "${'$'}{id}  ${'$'}{status}  ${'$'}{name:-${'$'}{cmd:0:40}}"
            done
        """.trimIndent(),
        "task-run" to """#!/system/bin/sh
            [ $# -ge 2 ] || { echo "用法: task-run <name> <command>"; exit 1; }
            name="${'$'}{1}"; shift
            dir="${'$'}{AIDEV_HOME}/tasks"
            mkdir -p "${'$'}{dir}"
            id="$(date +%s)_${'$'}{name}"
            log="${'$'}{dir}/${'$'}{id}.log"
            {
              echo "name=${'$'}{name}"
              echo "id=${'$'}{id}"
              echo "started=$(date '+%F %T')"
              echo "cmd=${'$'}{*}"
            } > "${'$'}{dir}/${'$'}{id}.meta"
            nohup /system/bin/sh -c "${'$'}{*}" > "${'$'}{log}" 2>&1 &
            pid=$!
            echo "pid=${'$'}{pid}" >> "${'$'}{dir}/${'$'}{id}.meta"
            echo "任务已启动: ${'$'}{name} (PID ${'$'}{pid})"
        """.trimIndent(),
    )

    fun agentPrivotScripts(): Map<String, String> = mapOf(
        "aidev-current-project" to """#!/bin/sh
            pwd
            [ -d .git ] && git status --short --branch 2>/dev/null
            [ -f package.json ] && node -e "const p=require('./package.json'); console.log('package:',p.name||'-'); console.log('scripts:',Object.keys(p.scripts||{}).join(','))" 2>/dev/null
            [ -f pyproject.toml ] && echo "python: pyproject.toml"
            [ -f requirements.txt ] && echo "python: requirements.txt"
            [ -f build.gradle ] || [ -f build.gradle.kts ] && echo "gradle project"
            [ -f go.mod ] && echo "go module"
            [ -f Cargo.toml ] && echo "rust cargo"
        """.trimIndent(),
        "aidev-agent-context" to """#!/bin/sh
            echo "== AIDev Agent Context =="
            echo "time: $(date '+%F %T')"
            echo "pwd: $(pwd)"
            echo "version: ${'$'}{AIDEV_VERSION:-unknown}"
            echo
            echo "== project =="
            aidev-current-project
            echo
            echo "== files =="
            find . -maxdepth 2 -type f 2>/dev/null | sed 's#^\./##' | head -120
            echo
            echo "== recent git =="
            git status --short --branch 2>/dev/null || true
            git diff --stat 2>/dev/null | head -80 || true
            echo
            echo "== tasks =="
            task-list 2>/dev/null || true
        """.trimIndent(),
        "aidev-agent-context-file" to """#!/bin/sh
            out="aidev-agent-context.txt"
            aidev-agent-context > "${'$'}{out}"
            echo >> "${'$'}{out}"
            echo "== recent errors ==" >> "${'$'}{out}"
            aidev-agent-summary >> "${'$'}{out}" 2>/dev/null || true
            echo "已导出上下文文件：$(pwd)/${'$'}{out}"
        """.trimIndent(),
        "aidev-agent-summary" to """#!/bin/sh
            log="$(ls -t "${'$'}{AIDEV_HOME}/tasks"/*.log 2>/dev/null | head -1)"
            [ -n "${'$'}{log}" ] || { echo "暂无任务日志"; exit 0; }
            echo "== log =="
            echo "${'$'}{log}"
            echo
            echo "== recent commands =="
            grep -iE "command|running|exec|npm |python|gradle|go |cargo|git " "${'$'}{log}" 2>/dev/null | tail -20 || true
            echo
            echo "== recent errors =="
            grep -iE "error|failed|exception|traceback|cannot|not found|denied" "${'$'}{log}" 2>/dev/null | tail -40 || true
            echo
            echo "== possible modified files =="
            grep -iE "modified|created|updated|wrote|write|saved|changed" "${'$'}{log}" 2>/dev/null | tail -30 || true
        """.trimIndent(),
        "aidev-agent-log" to """#!/bin/sh
            ls -lt "${'$'}{AIDEV_HOME}/tasks"/*.log 2>/dev/null | head -20
        """.trimIndent(),
        "aidev-agent-tail" to """#!/bin/sh
            log="$(ls -t "${'$'}{AIDEV_HOME}/tasks"/*.log 2>/dev/null | head -1)"
            [ -n "${'$'}{log}" ] || { echo "暂无任务日志"; exit 1; }
            tail -f "${'$'}{log}"
        """.trimIndent(),
        "list-listen-ports" to """#!/bin/sh
            if command -v ss >/dev/null 2>&1; then
              ss -tlnp 2>/dev/null
            elif command -v netstat >/dev/null 2>&1; then
              netstat -tlnp 2>/dev/null
            else
              echo "=== /proc/net/tcp ==="
              cat /proc/net/tcp 2>/dev/null | head -40 || echo "(无法读取)"
              echo "=== /proc/net/tcp6 ==="
              cat /proc/net/tcp6 2>/dev/null | head -40 || true
            fi
        """.trimIndent(),
        "task-list" to """#!/bin/sh
            dir="${'$'}{AIDEV_HOME}/tasks"
            [ -d "${'$'}{dir}" ] || { echo "暂无任务"; exit 0; }
            for meta in "${'$'}{dir}"/*.meta; do
              [ -f "${'$'}{meta}" ] || continue
              id="${'$'}{meta##*/}"; id="${'$'}{id%.meta}"
              name=""; pid=""; cmd=""
              while IFS='=' read -r k v; do
                case "${'$'}{k}" in
                  name) name="${'$'}{v}" ;;
                  pid) pid="${'$'}{v}" ;;
                  cmd) cmd="${'$'}{v}" ;;
                esac
              done < "${'$'}{meta}"
              if [ -n "${'$'}{pid}" ]; then
                kill -0 "${'$'}{pid}" 2>/dev/null && status="运行中" || status="已结束"
              else
                status="未知"
              fi
              echo "${'$'}{id}  ${'$'}{status}  ${'$'}{name:-${'$'}{cmd:0:40}}"
            done
        """.trimIndent(),
        "task-run" to """#!/bin/sh
            [ $# -ge 2 ] || { echo "用法: task-run <name> <command>"; exit 1; }
            name="${'$'}{1}"; shift
            dir="${'$'}{AIDEV_HOME}/tasks"
            mkdir -p "${'$'}{dir}"
            id="$(date +%s)_${'$'}{name}"
            log="${'$'}{dir}/${'$'}{id}.log"
            {
              echo "name=${'$'}{name}"
              echo "id=${'$'}{id}"
              echo "started=$(date '+%F %T')"
              echo "cmd=${'$'}{*}"
            } > "${'$'}{dir}/${'$'}{id}.meta"
            nohup /bin/sh -c "${'$'}{*}" > "${'$'}{log}" 2>&1 &
            pid=$!
            echo "pid=${'$'}{pid}" >> "${'$'}{dir}/${'$'}{id}.meta"
            echo "任务已启动: ${'$'}{name} (PID ${'$'}{pid})"
        """.trimIndent(),
    )



    /**
     * 将 assets/scripts/ 中的脚本复制到 rootfs 的 /usr/local/bin/。
     * 必须在 Kotlin 层调用（不是 shell 层），因为需要访问 Android AssetManager。
     */
    fun copyAssetScripts(activity: android.app.Activity, rootfs: java.io.File) {
        if (!rootfs.isDirectory) return
        val binDir = java.io.File(rootfs, "usr/local/bin")
        binDir.mkdirs()
        val scripts = listOf("check-dev-env.sh", "repair-dev-env.sh", "setup-dev-env.sh", "opencode-install.sh", "aidev-logcat.sh")
        for (script in scripts) {
            val dstName = script.removeSuffix(".sh")
            val dst = java.io.File(binDir, dstName)
            try {
                activity.assets.open("scripts/$script").use { input ->
                    dst.outputStream().use { output -> input.copyTo(output) }
                }
                dst.setExecutable(true)
                android.util.Log.d("AIDev", "已复制脚本: $dstName -> ${dst.absolutePath}")
            } catch (e: Exception) {
                android.util.Log.w("AIDev", "无法复制脚本 $script: ${e.message}")
            }
        }
    }

    fun aidevUbuntuCommandScript(homePath: String): String =
        """
        #!/system/bin/sh
        set -u

        cmd="${'$'}{1:-ubuntu}"
        shift 2>/dev/null || true

        AIDEV_HOME="${'$'}{AIDEV_HOME:-$homePath}"
        AIDEV_BIN="${'$'}{AIDEV_BIN:-${'$'}AIDEV_HOME/dev-env/bin}"
        AIDEV_ROOTFS="${'$'}{AIDEV_ROOTFS:-${'$'}AIDEV_HOME/ubuntu-rootfs}"
        AIDEV_NATIVE="${'$'}{AIDEV_NATIVE:-}"
        AIDEV_PROOT="${'$'}{AIDEV_PROOT:-${'$'}AIDEV_NATIVE/libproot.so}"
        AIDEV_PROOT_LOADER="${'$'}{AIDEV_PROOT_LOADER:-${'$'}AIDEV_NATIVE/libproot_loader.so}"
        PROOT_LOADER="${'$'}AIDEV_PROOT_LOADER"
        PROOT_TMP_DIR="${'$'}{PROOT_TMP_DIR:-${'$'}AIDEV_HOME/proot-tmp}"
        export AIDEV_HOME AIDEV_BIN AIDEV_ROOTFS AIDEV_NATIVE AIDEV_PROOT AIDEV_PROOT_LOADER PROOT_LOADER PROOT_TMP_DIR

        ubuntu_url="${'$'}{AIDEV_UBUNTU_URL:-https://mirrors.tuna.tsinghua.edu.cn/ubuntu-cdimage/ubuntu-base/releases/24.04.4/release/ubuntu-base-24.04.4-base-arm64.tar.gz}"

        has_ubuntu() {
          [ -f "${'$'}AIDEV_ROOTFS/.aidev-rootfs-ready" ] &&
          [ -f "${'$'}AIDEV_ROOTFS/etc/os-release" ] &&
          { [ -x "${'$'}AIDEV_ROOTFS/bin/sh" ] || [ -x "${'$'}AIDEV_ROOTFS/bin/bash" ]; }
        }

        link_or_symlink() {
          left="${'$'}1"
          right="${'$'}2"
          if [ -e "${'$'}AIDEV_ROOTFS.tmp/${'$'}left" ] && [ ! -e "${'$'}AIDEV_ROOTFS.tmp/${'$'}right" ]; then
            ( cd "${'$'}AIDEV_ROOTFS.tmp/${'$'}(dirname "${'$'}right")" && ln -sf "${'$'}(basename "${'$'}left")" "${'$'}(basename "${'$'}right")" ) 2>/dev/null || true
          fi
          if [ -e "${'$'}AIDEV_ROOTFS.tmp/${'$'}right" ] && [ ! -e "${'$'}AIDEV_ROOTFS.tmp/${'$'}left" ]; then
            ( cd "${'$'}AIDEV_ROOTFS.tmp/${'$'}(dirname "${'$'}left")" && ln -sf "${'$'}(basename "${'$'}right")" "${'$'}(basename "${'$'}left")" ) 2>/dev/null || true
          fi
        }

        download_file() {
          url="${'$'}1"; out="${'$'}2"; part="${'$'}out.part"
          mkdir -p "${'$'}(dirname "${'$'}out")"
          echo "下载地址：${'$'}url"
          if command -v curl >/dev/null 2>&1; then
            curl -fsSL --retry 3 -C - -o "${'$'}part" "${'$'}url" || return ${'$'}?
          elif command -v wget >/dev/null 2>&1; then
            wget -q -c -O "${'$'}part" "${'$'}url" || return ${'$'}?
          elif /system/bin/toybox wget --help >/dev/null 2>&1; then
            /system/bin/toybox wget -O "${'$'}part" "${'$'}url" || return ${'$'}?
          else
            echo "没有可用下载器：curl/wget/toybox wget 都不可用。"
            echo "下一步将改为 APK assets 内置 rootfs 或 Kotlin 下载器，避免依赖系统命令。"
            return 1
          fi
          [ -s "${'$'}part" ] && mv "${'$'}part" "${'$'}out"
        }

        install_ubuntu() {
          case "${'$'}{1:-}" in
            --clean)
              rm -rf "${'$'}AIDEV_ROOTFS" "${'$'}AIDEV_ROOTFS.tmp" "${'$'}AIDEV_HOME/dev-env/tmp/ubuntu-base.tar.gz" "${'$'}AIDEV_HOME/dev-env/tmp/ubuntu-base.tar.gz.part"
              echo "已清理 Ubuntu rootfs 与下载缓存。"
              return 0
              ;;
          esac

          if has_ubuntu; then
            echo "Ubuntu 已就绪：${'$'}AIDEV_ROOTFS"
            return 0
          fi

          abi="${'$'}(getprop ro.product.cpu.abi 2>/dev/null || echo arm64-v8a)"
          case "${'$'}abi" in
            arm64-v8a|aarch64) ;;
            *) echo "当前自动 Ubuntu 仅支持 arm64，设备 ABI=${'$'}abi"; return 1 ;;
          esac

          [ -x "${'$'}AIDEV_PROOT" ] || { echo "proot 不存在：${'$'}AIDEV_PROOT"; return 1; }

          mkdir -p "${'$'}AIDEV_HOME/dev-env/tmp" "${'$'}PROOT_TMP_DIR"
          tar_file="${'$'}AIDEV_HOME/dev-env/tmp/ubuntu-base.tar.gz"
          if [ ! -s "${'$'}tar_file" ]; then
            echo "[1/4] 自动下载 Ubuntu Base 24.04 arm64..."
            download_file "${'$'}ubuntu_url" "${'$'}tar_file" || return ${'$'}?
          else
            echo "[1/4] 使用已下载缓存：${'$'}tar_file"
          fi

          echo "[2/4] 准备 rootfs 目录..."
          rm -rf "${'$'}AIDEV_ROOTFS.tmp" "${'$'}AIDEV_ROOTFS"
          mkdir -p "${'$'}AIDEV_ROOTFS.tmp"

          echo "[3/4] 解包 Ubuntu rootfs..."
          tar_log="${'$'}AIDEV_HOME/dev-env/tmp/tar-install.log"
          if command -v tar >/dev/null 2>&1; then
            ( cd "${'$'}AIDEV_ROOTFS.tmp" && tar --no-same-owner --no-same-permissions -xzf "${'$'}tar_file" ) 2>"${'$'}tar_log"
            tar_rc="${'$'}?"
          else
            echo "系统缺少 tar，无法解包 rootfs。"
            return 1
          fi

          link_or_symlink "usr/bin/perl" "usr/bin/perl5.38.2"
          link_or_symlink "usr/bin/gunzip" "usr/bin/uncompress"

          if [ "${'$'}tar_rc" -ne 0 ]; then
            if [ -f "${'$'}AIDEV_ROOTFS.tmp/etc/os-release" ] && { [ -x "${'$'}AIDEV_ROOTFS.tmp/bin/sh" ] || [ -x "${'$'}AIDEV_ROOTFS.tmp/bin/bash" ]; }; then
              echo "检测到 Android hardlink 限制，已自动转为 symlink 继续。"
            else
              echo "解包失败，请检查 tar 能力、存储空间或下载完整性。"
              [ -s "${'$'}tar_log" ] && tail -20 "${'$'}tar_log"
              rm -rf "${'$'}AIDEV_ROOTFS.tmp"
              return 1
            fi
          fi

          echo "[4/4] 初始化 apt 源、DNS 和完成标记..."
          mkdir -p "${'$'}AIDEV_ROOTFS.tmp/etc/apt" "${'$'}AIDEV_ROOTFS.tmp/root/projects"
          cat > "${'$'}AIDEV_ROOTFS.tmp/etc/apt/sources.list" <<'AIDEV_APT_EOF'
deb http://mirrors.tuna.tsinghua.edu.cn/ubuntu-ports/ noble main universe multiverse restricted
deb http://mirrors.tuna.tsinghua.edu.cn/ubuntu-ports/ noble-updates main universe multiverse restricted
deb http://mirrors.tuna.tsinghua.edu.cn/ubuntu-ports/ noble-security main universe multiverse restricted
AIDEV_APT_EOF
          echo "nameserver 223.5.5.5" > "${'$'}AIDEV_ROOTFS.tmp/etc/resolv.conf"
          ensure_android_groups "${'$'}AIDEV_ROOTFS.tmp"
          date '+%F %T' > "${'$'}AIDEV_ROOTFS.tmp/.aidev-rootfs-ready"
          mv "${'$'}AIDEV_ROOTFS.tmp" "${'$'}AIDEV_ROOTFS"
          echo "Ubuntu 初始化完成。"
        }

        ensure_android_groups() {
          target_root="${'$'}{1:-${'$'}AIDEV_ROOTFS}"
          group_file="${'$'}target_root/etc/group"
          [ -f "${'$'}group_file" ] || return 0
          for gid in ${'$'}(id -G 2>/dev/null); do
            case "${'$'}gid" in
              ''|*[!0-9]*) continue ;;
            esac
            grep -q "^[^:]*:[^:]*:${'$'}gid:" "${'$'}group_file" 2>/dev/null && continue
            echo "android_gid_${'$'}gid:x:${'$'}gid:" >> "${'$'}group_file"
          done
        }

        ubuntu_logo() {
          status="${'$'}1"
          printf '%s\n' \
            '╭────────────────────╮' \
            '│   AIDev Ubuntu     │' \
            "│   ${'$'}status" \
            '╰────────────────────╯'
        }

        aidev_doctor_android() {
          echo "== AIDev Doctor =="
          echo "mode: Android shell"
          echo "version: ${'$'}{AIDEV_VERSION:-unknown}"
          echo "time: ${'$'}(date '+%F %T' 2>/dev/null || echo unknown)"
          echo
          [ -d "${'$'}AIDEV_HOME" ] && echo "[OK] AIDEV_HOME: ${'$'}AIDEV_HOME" || echo "[FAIL] AIDEV_HOME missing: ${'$'}AIDEV_HOME"
          [ -f "${'$'}AIDEV_ROOTFS/.aidev-rootfs-ready" ] && echo "[OK] Ubuntu ready marker" || echo "[WARN] Ubuntu ready marker missing"
          [ -f "${'$'}AIDEV_ROOTFS/etc/os-release" ] && echo "[OK] Ubuntu os-release" || echo "[WARN] Ubuntu os-release missing"
          [ -x "${'$'}AIDEV_PROOT" ] && echo "[OK] PRoot: ${'$'}AIDEV_PROOT" || echo "[FAIL] PRoot missing: ${'$'}AIDEV_PROOT"
          [ -f "${'$'}AIDEV_PROOT_LOADER" ] && echo "[OK] PRoot loader" || echo "[WARN] PRoot loader missing"
          [ -f "${'$'}AIDEV_ROOTFS/etc/resolv.conf" ] && echo "[OK] DNS config" || echo "[WARN] DNS config missing"
          [ -f "${'$'}AIDEV_ROOTFS/etc/apt/sources.list" ] && echo "[OK] apt sources" || echo "[WARN] apt sources missing"
          command -v tar >/dev/null 2>&1 && echo "[OK] Android tar available" || echo "[WARN] Android tar missing"
          command -v curl >/dev/null 2>&1 && echo "[OK] curl available" || echo "[INFO] curl unavailable"
          command -v wget >/dev/null 2>&1 && echo "[OK] wget available" || echo "[INFO] wget unavailable"
          echo "groups: ${'$'}(id -G 2>/dev/null || echo unknown)"
          echo "disk:"
          df -h "${'$'}AIDEV_HOME" 2>/dev/null | tail -1 || true
          echo
          echo "如果已经在 Ubuntu 内，请直接运行：aidev-doctor"
        }

        ensure_ubuntu_helpers() {
          has_ubuntu || return 0
          mkdir -p "${'$'}AIDEV_ROOTFS/usr/local/bin"
          cat > "${'$'}AIDEV_ROOTFS/usr/local/bin/aidev-doctor" <<'AIDEV_DOCTOR_EOF'
#!/bin/sh
echo "== AIDev Doctor =="
echo "mode: Ubuntu PRoot"
echo "version: ${'$'}{AIDEV_VERSION:-unknown}"
echo "time: $(date '+%F %T' 2>/dev/null || echo unknown)"
echo
if [ -f /etc/os-release ]; then
  . /etc/os-release
  echo "[OK] Ubuntu: ${'$'}{PRETTY_NAME:-unknown}"
else
  echo "[FAIL] /etc/os-release missing"
fi
[ -f /.aidev-rootfs-ready ] && echo "[OK] rootfs ready marker" || echo "[WARN] rootfs ready marker missing"
[ -d /host-home ] && echo "[OK] host home mounted: /host-home" || echo "[WARN] /host-home missing"
[ -f /host-home/dev-env/bin/aidev-ubuntu-core ] && echo "[OK] host command core" || echo "[WARN] host command core missing"
[ -r /etc/resolv.conf ] && echo "[OK] DNS config" || echo "[WARN] DNS config missing"
[ -r /etc/apt/sources.list ] && echo "[OK] apt sources" || echo "[WARN] apt sources missing"
command -v apt-get >/dev/null 2>&1 && echo "[OK] apt-get available" || echo "[WARN] apt-get missing"
command -v bash >/dev/null 2>&1 && echo "[OK] bash available" || echo "[WARN] bash missing"
echo "user: $(id 2>/dev/null || echo unknown)"
echo "pwd: $(pwd)"
echo "disk:"
df -h / 2>/dev/null | tail -1 || true
AIDEV_DOCTOR_EOF
          chmod 755 "${'$'}AIDEV_ROOTFS/usr/local/bin/aidev-doctor" 2>/dev/null || true
          cat > "${'$'}AIDEV_ROOTFS/usr/local/bin/ubuntu" <<'AIDEV_UBUNTU_EOF'
#!/bin/sh
echo "已经在 AIDev Ubuntu 环境中。"
echo "当前目录：$(pwd)"
echo "诊断命令：aidev-doctor"
AIDEV_UBUNTU_EOF
          chmod 755 "${'$'}AIDEV_ROOTFS/usr/local/bin/ubuntu" 2>/dev/null || true
          cat > "${'$'}AIDEV_ROOTFS/usr/local/bin/install-ubuntu" <<'AIDEV_INSTALL_EOF'
#!/bin/sh
echo "当前已经在 Ubuntu 内。"
echo "如需清理或重装 rootfs，请先退出 Ubuntu，再在 AIDev Android shell 中运行：install-ubuntu --clean"
AIDEV_INSTALL_EOF
          chmod 755 "${'$'}AIDEV_ROOTFS/usr/local/bin/install-ubuntu" 2>/dev/null || true
          cat > "${'$'}AIDEV_ROOTFS/usr/local/bin/aidev-auto-bootstrap" <<'AIDEV_BOOTSTRAP_EOF'
#!/bin/sh
ubuntu "$@"
AIDEV_BOOTSTRAP_EOF
          chmod 755 "${'$'}AIDEV_ROOTFS/usr/local/bin/aidev-auto-bootstrap" 2>/dev/null || true

          # 将 dev-env/bin 中的两端共用脚本复制到 rootfs（单来源 → 双端可用）
          for script in aidev-current-project aidev-agent-context aidev-agent-context-file aidev-agent-summary aidev-agent-log aidev-agent-tail list-listen-ports task-list task-run; do
            if [ -f "${'$'}AIDEV_BIN/.privot/${'$'}script" ]; then
              cp "${'$'}AIDEV_BIN/.privot/${'$'}script" "${'$'}AIDEV_ROOTFS/usr/local/bin/${'$'}script"
              chmod 755 "${'$'}AIDEV_ROOTFS/usr/local/bin/${'$'}script" 2>/dev/null || true
            fi
          done

          mkdir -p "${'$'}AIDEV_ROOTFS/root"
          touch "${'$'}AIDEV_ROOTFS/root/.bashrc" 2>/dev/null || true
          if ! grep -q "AIDEV_PWD_HOOK_BEGIN" "${'$'}AIDEV_ROOTFS/root/.bashrc" 2>/dev/null; then
            cat >> "${'$'}AIDEV_ROOTFS/root/.bashrc" <<'AIDEV_PWD_HOOK_EOF'

# AIDEV_PWD_HOOK_BEGIN
aidev_write_pwd() {
  pwd > /host-home/.aidev-current-pwd 2>/dev/null || true
}
case "${'$'}{PROMPT_COMMAND:-}" in
  *aidev_write_pwd*) ;;
  *) PROMPT_COMMAND="aidev_write_pwd${'$'}{PROMPT_COMMAND:+;${'$'}PROMPT_COMMAND}" ;;
esac
aidev_write_pwd
# AIDEV_PWD_HOOK_END
AIDEV_PWD_HOOK_EOF
          fi
        }

        # 公共 proot 绑定参数（enter_ubuntu 和 run_ubuntu_command 共用）
        proot_common_binds() {
          echo "-b /dev -b /proc -b /sys -b /system/lib64 -b /system/lib -b /system/bin -b /system/etc -b /system/framework -b /sdcard -b /storage -b ${'$'}AIDEV_HOME:/host-home"
        }

        proot_common_env() {
          echo "HOME=/root AIDEV_HOME=/host-home AIDEV_VERSION=${'$'}{AIDEV_VERSION:-unknown} PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/system/bin:/system/xbin:/host-home/dev-env/bin TERM=${'$'}{TERM:-xterm-256color} LANG=C.UTF-8 LC_ALL=C.UTF-8"
        }

        enter_ubuntu() {
          has_ubuntu || install_ubuntu --fast || return ${'$'}?
          ensure_android_groups "${'$'}AIDEV_ROOTFS"
          ensure_ubuntu_helpers
          shell="/bin/bash"
          [ -x "${'$'}AIDEV_ROOTFS/bin/bash" ] || shell="/bin/sh"
          cd "${'$'}AIDEV_HOME" || exit 1
          mkdir -p "${'$'}{PROOT_TMP_DIR:-${'$'}AIDEV_HOME/proot-tmp}"
          eval exec "${'$'}AIDEV_PROOT" --link2symlink -0 -r "${'$'}AIDEV_ROOTFS" \
            $(proot_common_binds) \
            -w /root /usr/bin/env -i \
            $(proot_common_env) "${'$'}shell" -l
        }

        run_ubuntu_command() {
          has_ubuntu || install_ubuntu --fast || return ${'$'}?
          ensure_android_groups "${'$'}AIDEV_ROOTFS"
          ensure_ubuntu_helpers
          cd "${'$'}AIDEV_HOME" || exit 1
          eval exec "${'$'}AIDEV_PROOT" --link2symlink -0 -r "${'$'}AIDEV_ROOTFS" \
            $(proot_common_binds) \
            -w /root /usr/bin/env -i \
            $(proot_common_env) /bin/sh -lc "${'$'}*"
        }

        fix_bashrc() {
          local bashrc="${'$'}AIDEV_ROOTFS/root/.bashrc"
          if [ -f "${'$'}{bashrc}.bak" ]; then
            cp "${'$'}{bashrc}.bak" "${'$'}bashrc"
            echo "[OK] 已从 .bashrc.bak 恢复"
          elif [ -f "${'$'}bashrc" ]; then
            echo "[OK] 无备份文件，当前 .bashrc 存在，无需修复"
          else
            echo "[INFO] .bashrc 不存在，创建默认 .bashrc"
            mkdir -p "$(dirname "${'$'}bashrc")"
            cat > "${'$'}bashrc" << 'AIDEV_PWD_HOOK_EOF'
# AIDEV_PWD_HOOK_BEGIN
aidev_write_pwd() {
  pwd > /host-home/.aidev-current-pwd 2>/dev/null || true
}
case "${'$'}{PROMPT_COMMAND:-}" in
  *aidev_write_pwd*) ;;
  *) PROMPT_COMMAND="aidev_write_pwd${'$'}{PROMPT_COMMAND:+;${'$'}PROMPT_COMMAND}" ;;
esac
aidev_write_pwd
# AIDEV_PWD_HOOK_END
AIDEV_PWD_HOOK_EOF
            echo "[OK] 默认 .bashrc 已创建"
          fi
        }

        case "${'$'}cmd" in
          ubuntu) enter_ubuntu "${'$'}@" ;;
          install-ubuntu) install_ubuntu "${'$'}@" ;;
          aidev-doctor) aidev_doctor_android ;;
          setup-dev-env) run_ubuntu_command "/usr/local/bin/setup-dev-env" ;;
          opencode-install) run_ubuntu_command "/usr/local/bin/opencode-install" ;;
          fix-bashrc) fix_bashrc ;;
          aidev-auto-bootstrap)
            if has_ubuntu; then
              ubuntu_logo "自动进入环境     │"
            else
              ubuntu_logo "正在初始化环境   │"
            fi
            enter_ubuntu "${'$'}@"
            ;;
          *) echo "未知命令：${'$'}cmd"; exit 2 ;;
        esac
        """.trimIndent() + "\n"
}
