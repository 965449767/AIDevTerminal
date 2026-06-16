#!/bin/bash
# aidev-logcat: 在 Ubuntu 环境中通过 Shizuku 获取 Android 应用日志
# 用法:
#   aidev-logcat                          # 获取 AIDev Terminal 日志
#   aidev-logcat com.example.app           # 获取指定应用日志
#   aidev-logcat --follow com.example.app  # 持续监听日志
#   aidev-logcat --lines 100 com.example.app  # 获取最近100行

set -e
BRIDGE_DIR="/host-home/.aidev-shizuku-bridge"
REQUEST_DIR="$BRIDGE_DIR/request"
RESULT_DIR="$BRIDGE_DIR/result"

mkdir -p "$REQUEST_DIR" "$RESULT_DIR"

# 解析参数
LINES=200
FOLLOW=""
PACKAGE="com.aidev.terminal"

while [ $# -gt 0 ]; do
  case "$1" in
    --follow|-f) FOLLOW="--follow"; shift ;;
    --lines|-n) LINES="$2"; shift 2 ;;
    --help|-h)
      echo "用法: aidev-logcat [选项] [包名]"
      echo "  --lines N    获取最近N行 (默认200)"
      echo "  --follow     持续监听"
      echo "  包名         要查看的应用包名 (默认 com.aidev.terminal)"
      return 0 ;;
    *) PACKAGE="$1"; shift ;;
  esac
done

# 生成唯一请求 ID
REQ_ID="log_$(date +%s)_$$"
REQ_FILE="$REQUEST_DIR/$REQ_ID"
RES_FILE="$RESULT_DIR/$REQ_ID"

# 写入请求
cat > "$REQ_FILE" <<EOF
PACKAGE=$PACKAGE
LINES=$LINES
FOLLOW=$FOLLOW
EOF

echo "已发送日志请求: $PACKAGE (最近${LINES}行)"
echo "等待 Android 端 Shizuku 处理..."

# 等待结果（最多 30 秒）
TIMEOUT=30
WAITED=0
while [ ! -s "$RES_FILE" ] && [ "$WAITED" -lt "$TIMEOUT" ]; do
  sleep 1
  WAITED=$((WAITED + 1))
  # 检查请求是否被拒绝
  if [ -f "$RES_FILE" ] && grep -q "ERROR:" "$RES_FILE" 2>/dev/null; then
    cat "$RES_FILE"
    rm -f "$REQ_FILE" "$RES_FILE"
    exit 1
  fi
done

if [ ! -s "$RES_FILE" ]; then
  echo "ERROR: 请求超时。Shizuku 可能未运行或未授权。"
  echo "请在 AIDev Terminal 的「任务」页检查 Shizuku 状态。"
  rm -f "$REQ_FILE" "$RES_FILE"
  exit 1
fi

# 输出结果
cat "$RES_FILE"
rm -f "$REQ_FILE" "$RES_FILE"
