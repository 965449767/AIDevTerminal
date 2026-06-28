#!/bin/bash
# aidev-install: 安装 APK，支持静默（Shizuku）/标准（系统安装器）模式
# 用法: aidev-install [options] [apk_path]
set -eo pipefail

INSTALL_MODE="auto"
APK_PATH=""
CLEANUP=false

usage() {
    cat <<EOF
用法: aidev-install [选项] [APK路径]

选项:
  --silent, -s     强制静默安装（Shizuku，需授权）
  --gui, -g        强制使用系统安装界面
  --uninstall, -u  静默卸载应用: aidev-install --uninstall <包名>
  --status         检查 Shizuku 桥接状态
  -h, --help       显示帮助

示例:
  aidev-install                          # 自动发现 APK，优先静默安装
  aidev-install app-debug.apk            # 安装指定 APK
  aidev-install --silent ./app-debug.apk # 强制静默安装
  aidev-install --gui /sdcard/demo.apk   # 强制系统安装界面
  aidev-install --uninstall com.example  # 卸载应用
EOF
    exit 0
}

while [ $# -gt 0 ]; do
    case "$1" in
        --silent|-s) INSTALL_MODE="silent"; shift ;;
        --gui|-g) INSTALL_MODE="gui"; shift ;;
        --uninstall|-u) shift; exec aidev-shizuku exec "pm uninstall -k --user 0 $1" ;;
        --status) exec aidev-shizuku status ;;
        -h|--help) usage ;;
        *) APK_PATH="$1"; shift ;;
    esac
done

auto_discover_apk() {
    for pattern in \
        "/root/Workspace/"*"/app/build/outputs/apk/debug/"*.apk \
        "$PWD/app/build/outputs/apk/debug/"*.apk \
        /sdcard/Download/*.apk \
        /sdcard/*.apk; do
        for f in $pattern; do
            [ -f "$f" ] && { echo "$f"; return 0; }
        done
    done
    return 1
}

if [ -z "$APK_PATH" ]; then
    APK_PATH=$(auto_discover_apk) || true
fi

if [ -z "$APK_PATH" ] || [ ! -f "$APK_PATH" ]; then
    echo "错误: 找不到 APK 文件。请指定路径或先构建: aidev-build && aidev-install"
    exit 1
fi

APK_SIZE=$(du -h "$APK_PATH" 2>/dev/null | cut -f1)
echo "APK: $(basename "$APK_PATH") ($APK_SIZE)"

if [[ "$APK_PATH" != /sdcard/* ]]; then
    TMP_APK="/sdcard/aidev-install-tmp.apk"
    echo "→ 复制到 $TMP_APK"
    cp "$APK_PATH" "$TMP_APK"
    APK_PATH="$TMP_APK"
    CLEANUP=true
fi

cleanup() {
    [ "$CLEANUP" = true ] && rm -f "$TMP_APK" 2>/dev/null || true
}
trap cleanup EXIT

shizuku_heartbeat() {
    local hb_req="/host-home/.aidev-shizuku-bridge/request/hb_$$"
    local hb_res="/host-home/.aidev-shizuku-bridge/result/hb_$$"
    mkdir -p "$(dirname "$hb_req")" "$(dirname "$hb_res")"
    cat > "$hb_req" <<EOF
TYPE=exec
COMMAND=echo aidev-install-ok
EOF
    local waited=0
    while [ ! -s "$hb_res" ] && [ "$waited" -lt 5 ]; do
        sleep 1; waited=$((waited + 1))
    done
    if [ -s "$hb_res" ] && grep -q "aidev-install-ok" "$hb_res" 2>/dev/null; then
        rm -f "$hb_req" "$hb_res"
        return 0
    fi
    rm -f "$hb_req" "$hb_res" 2>/dev/null || true
    return 1
}

install_silent() {
    echo "→ 静默安装 (Shizuku)..."
    aidev-shizuku install "$APK_PATH"
    echo "✓ 静默安装完成"
}

install_gui() {
    echo "→ 系统安装界面..."
    local req_dir="/host-home/.aidev-cmd"
    mkdir -p "$req_dir"
    echo "{\"action\":\"installapk\",\"path\":\"$APK_PATH\"}" > "$req_dir/req-$(date +%s%N).json"
    echo "✓ 安装请求已发送"
}

case "$INSTALL_MODE" in
    silent)
        install_silent
        ;;
    gui)
        install_gui
        ;;
    auto)
        if shizuku_heartbeat; then
            install_silent
        else
            echo "Shizuku 未响应，使用系统安装界面"
            install_gui
        fi
        ;;
esac
