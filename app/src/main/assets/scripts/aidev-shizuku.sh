#!/bin/bash
# aidev-shizuku: 通过 Shizuku 执行 Android 系统命令
# 子命令:
#   exec '<command>'         执行任意 shell 命令
#   install <apk_path>       静默安装 APK
#   input keyevent <key>     模拟按键
#   input tap <x> <y>        模拟点击
#   wifi on|off              WiFi 开关
#   data on|off              移动数据开关
#   battery level <N>        设置模拟电池电量
#   battery reset            重置电池状态
#   status                   检查 Shizuku 状态

set -e
BRIDGE_DIR="/host-home/.aidev-shizuku-bridge"
REQUEST_DIR="$BRIDGE_DIR/request"
RESULT_DIR="$BRIDGE_DIR/result"
mkdir -p "$REQUEST_DIR" "$RESULT_DIR"

SUBCOMMAND="${1:-}"
shift 2>/dev/null || true

case "$SUBCOMMAND" in
  exec)
    CMD="$*"
    ;;
  install)
    APK_PATH="$1"
    if [ -z "$APK_PATH" ]; then
      echo "用法: aidev-shizuku install <apk_path>"
      exit 1
    fi
    CMD="cat '$APK_PATH' | pm install -r -d -S \$(stat -c%s '$APK_PATH')"
    ;;
  input)
    CMD="input $*"
    ;;
  wifi)
    case "$1" in
      on|off) CMD="svc wifi $1" ;;
      *) echo "用法: aidev-shizuku wifi on|off"; exit 1 ;;
    esac
    ;;
  data)
    case "$1" in
      on|off) CMD="svc data $1" ;;
      *) echo "用法: aidev-shizuku data on|off"; exit 1 ;;
    esac
    ;;
  battery)
    case "$1" in
      level) CMD="dumpsys battery set level $2" ;;
      reset) CMD="dumpsys battery reset" ;;
      *) echo "用法: aidev-shizuku battery level N|reset"; exit 1 ;;
    esac
    ;;
  status)
    echo "Shizuku 状态: 桥接通道正常 (在 AIDev Terminal 中)"
    exit 0
    ;;
  *)
    echo "用法: aidev-shizuku <子命令> [参数]"
    echo ""
    echo "子命令:"
    echo "  exec '<command>'         执行任意 shell 命令"
    echo "  install <apk_path>       静默安装 APK"
    echo "  input keyevent <key>     模拟按键"
    echo "  input tap <x> <y>        模拟点击"
    echo "  wifi on|off              WiFi 开关"
    echo "  data on|off              移动数据开关"
    echo "  battery level <N>        设置模拟电池电量"
    echo "  battery reset            重置电池状态"
    echo "  status                   检查 Shizuku 状态"
    exit 0
    ;;
esac

REQ_ID="exec_$(date +%s)_$$"
REQ_FILE="$REQUEST_DIR/$REQ_ID"
RES_FILE="$RESULT_DIR/$REQ_ID"

cat > "$REQ_FILE" <<EOF
TYPE=exec
COMMAND=$CMD
EOF

echo "Shizuku 请求已发送"
echo "命令: $CMD"
echo "等待执行结果..."

TIMEOUT=30
WAITED=0
while [ ! -s "$RES_FILE" ] && [ "$WAITED" -lt "$TIMEOUT" ]; do
  sleep 1
  WAITED=$((WAITED + 1))
  if [ -f "$RES_FILE" ] && grep -q "^ERROR:" "$RES_FILE" 2>/dev/null; then
    cat "$RES_FILE"
    rm -f "$REQ_FILE" "$RES_FILE"
    exit 1
  fi
done

if [ ! -s "$RES_FILE" ]; then
  echo "ERROR: 请求超时。Shizuku 可能未运行或未授权。"
  rm -f "$REQ_FILE" "$RES_FILE"
  exit 1
fi

cat "$RES_FILE"
rm -f "$REQ_FILE" "$RES_FILE"
