#!/bin/bash
# aidev-logcat: 在 Ubuntu 环境中通过 Shizuku 获取 Android 应用日志
# 用法:
#   aidev-logcat                                    # 获取 AIDev Terminal 日志
#   aidev-logcat com.example.app                     # 获取指定应用日志
#   aidev-logcat --follow com.example.app            # 持续监听日志
#   aidev-logcat --lines 100                         # 获取最近100行
#   aidev-logcat --level ERROR                       # 只显示错误级别以上
#   aidev-logcat --tag ActivityManager               # 按标签过滤
#   aidev-logcat --tags Error,Exception,NativeCrash  # 只显示含这些关键词的行
#   aidev-logcat --watch-crash                       # 持续监听直到检测到崩溃
#   aidev-logcat --clear                             # 清空缓冲区

set -e
BRIDGE_DIR="/host-home/.aidev-shizuku-bridge"
REQUEST_DIR="$BRIDGE_DIR/request"
RESULT_DIR="$BRIDGE_DIR/result"

mkdir -p "$REQUEST_DIR" "$RESULT_DIR"

LINES=200
FOLLOW=""
LEVEL=""
TAG=""
TAGS=""
CLEAR=""
WATCH_CRASH=false
PACKAGE="com.aidev.terminal"

while [ $# -gt 0 ]; do
  case "$1" in
    --follow|-f) FOLLOW="--follow"; shift ;;
    --lines|-n) LINES="$2"; shift 2 ;;
    --level|-l) LEVEL="$2"; shift 2 ;;
    --tag|-t) TAG="$2"; shift 2 ;;
    --tags) TAGS="$2"; shift 2 ;;
    --watch-crash) WATCH_CRASH=true; FOLLOW="--follow"; shift ;;
    --clear|-c) CLEAR="--clear"; shift ;;
    --help|-h)
      echo "用法: aidev-logcat [选项] [包名]"
      echo "  --lines N         获取最近N行 (默认200)"
      echo "  --follow          持续监听 (Ctrl+C 停止)"
      echo "  --level L         日志级别 (VERBOSE/DEBUG/INFO/WARN/ERROR)"
      echo "  --tag T           按标签过滤 (如 ActivityManager)"
      echo "  --tags T1,T2,...  按关键词过滤 (如 Error,Exception)"
      echo "  --watch-crash     监听崩溃日志，检测到后停止"
      echo "  --clear           先清空缓冲区"
      echo "  包名              要查看的应用包名 (默认 com.aidev.terminal)"
      exit 0 ;;
    *) PACKAGE="$1"; shift ;;
  esac
done

REQ_ID="log_$(date +%s)_$$"
REQ_FILE="$REQUEST_DIR/$REQ_ID"
RES_FILE="$RESULT_DIR/$REQ_ID"

cat > "$REQ_FILE" <<EOF
PACKAGE=$PACKAGE
LINES=$LINES
FOLLOW=$FOLLOW
LEVEL=$LEVEL
TAG=$TAG
CLEAR=$CLEAR
EOF

echo "已发送日志请求: $PACKAGE (最近${LINES}行)"
[ -n "$LEVEL" ] && echo "  级别过滤: $LEVEL"
[ -n "$TAG" ] && echo "  标签过滤: $TAG"
[ -n "$TAGS" ] && echo "  关键词过滤: $TAGS"
[ -n "$CLEAR" ] && echo "  已清空缓冲区"
[ "$WATCH_CRASH" = true ] && echo "  崩溃监控模式: 检测到崩溃后自动停止"

if [ "$FOLLOW" = "--follow" ]; then
    echo "持续监听中... (Ctrl+C 停止)"
    echo ""

    CRASH_PATTERNS="FATAL EXCEPTION|CRASH|Native crash|kill|ANR|Process.*has died|android.util.Log"

    LAST_SIZE=0
    while true; do
        RUNNING_REQ="${REQ_FILE}.follow"
        cat > "$RUNNING_REQ" <<EOF
PACKAGE=$PACKAGE
LINES=$LINES
FOLLOW=$FOLLOW
LEVEL=$LEVEL
TAG=$TAG
EOF

        WAITED=0
        while [ "$WAITED" -lt 15 ]; do
            if [ -f "$RES_FILE" ] && [ -s "$RES_FILE" ]; then
                CUR_SIZE=$(stat --format=%s "$RES_FILE" 2>/dev/null || stat -f%z "$RES_FILE" 2>/dev/null)
                if [ "$CUR_SIZE" -gt "$LAST_SIZE" ]; then
                    NEW_DATA=$(dd if="$RES_FILE" bs=1 skip="$LAST_SIZE" 2>/dev/null || tail -c +$((LAST_SIZE + 1)) "$RES_FILE" 2>/dev/null)
                    [ -n "$NEW_DATA" ] && echo "$NEW_DATA"

                    LAST_SIZE=$CUR_SIZE

                    if [ "$WATCH_CRASH" = true ]; then
                        if echo "$NEW_DATA" | grep -qiE "$CRASH_PATTERNS" 2>/dev/null; then
                            echo ""
                            echo "═══ 检测到崩溃! ═══"
                            echo "$NEW_DATA" | grep -iE "$CRASH_PATTERNS" || true
                            echo ""
                            echo "崩溃日志已记录，停止监听。"
                            rm -f "$RUNNING_REQ" 2>/dev/null || true
                            rm -f "$REQ_FILE" "$RES_FILE" 2>/dev/null || true
                            exit 0
                        fi
                    fi

                    if [ -n "$TAGS" ]; then
                        IFS=',' read -ra TAG_LIST <<< "$TAGS"
                        for filter_tag in "${TAG_LIST[@]}"; do
                            echo "$NEW_DATA" | grep -i "$filter_tag" || true
                        done
                    fi
                fi
            fi
            sleep 1
            WAITED=$((WAITED + 1))
        done
    done
else
    TIMEOUT=30
    WAITED=0
    while [ ! -s "$RES_FILE" ] && [ "$WAITED" -lt "$TIMEOUT" ]; do
        sleep 1
        WAITED=$((WAITED + 1))
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

    OUTPUT=$(cat "$RES_FILE")

    if [ -n "$TAGS" ]; then
        IFS=',' read -ra TAG_LIST <<< "$TAGS"
        for filter_tag in "${TAG_LIST[@]}"; do
            echo "$OUTPUT" | grep -i "$filter_tag" || true
        done
    else
        echo "$OUTPUT"
    fi

    rm -f "$REQ_FILE" "$RES_FILE"
fi
