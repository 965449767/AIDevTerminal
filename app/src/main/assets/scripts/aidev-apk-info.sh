#!/bin/bash
# aidev-apk-info: 解析 APK 文件信息并以中文显示
# 用法: aidev-apk-info <apk_path>
# 依赖: aapt2 (优先) 或只显示基本信息

set -eo pipefail
CLEANUP_DIRS=""

cleanup() { for d in $CLEANUP_DIRS; do rm -rf "$d" 2>/dev/null || true; done; }
trap cleanup EXIT

if [ -z "$1" ]; then
    echo "用法: aidev-apk-info <apk_path>"
    echo ""
    echo "示例:"
    echo "  aidev-apk-info app/build/outputs/apk/debug/app-debug.apk"
    exit 1
fi

APK="$1"
if [ ! -f "$APK" ]; then
    echo "错误: 文件不存在: $APK"
    exit 1
fi

echo "═══════════════════════════════════════════"
echo "  APK 文件分析"
echo "═══════════════════════════════════════════"

FMT="stat --format=%s"
APK_SIZE=$($FMT "$APK" 2>/dev/null || stat -f%z "$APK" 2>/dev/null)
APK_SIZE_KB=$((APK_SIZE / 1024))
APK_SIZE_MB=$((APK_SIZE_KB / 1024))
echo "  文件: $(basename "$APK")"
if [ "$APK_SIZE_MB" -gt 0 ]; then
    echo "  大小: ${APK_SIZE_MB}MB (${APK_SIZE_KB}KB)"
else
    echo "  大小: ${APK_SIZE_KB}KB"
fi

# 尝试查找 aapt2
AAPT2=""
if command -v aapt2 &>/dev/null; then
    AAPT2=$(command -v aapt2)
elif [ -d "/Android/build-tools" ]; then
    AAPT2=$(find /Android/build-tools -name aapt2 -type f 2>/dev/null | head -1)
elif [ -n "$ANDROID_HOME" ] && [ -d "$ANDROID_HOME/build-tools" ]; then
    AAPT2=$(find "$ANDROID_HOME/build-tools" -name aapt2 -type f 2>/dev/null | head -1)
fi
if [ -z "$AAPT2" ] && [ -d "$HOME/.gradle/caches" ]; then
    AAPT2=$(find "$HOME/.gradle/caches" -name aapt2 -type f 2>/dev/null | head -1)
fi

if [ -z "$AAPT2" ] && [ -f "local.properties" ]; then
    SDK_DIR=$(grep "^sdk.dir" local.properties 2>/dev/null | cut -d= -f2)
    if [ -n "$SDK_DIR" ] && [ -d "$SDK_DIR/build-tools" ]; then
        AAPT2=$(find "$SDK_DIR/build-tools" -name aapt2 -type f 2>/dev/null | head -1)
    fi
fi

if [ -z "$AAPT2" ] && [ -f "../local.properties" ]; then
    SDK_DIR=$(grep "^sdk.dir" ../local.properties 2>/dev/null | cut -d= -f2)
    if [ -n "$SDK_DIR" ] && [ -d "$SDK_DIR/build-tools" ]; then
        AAPT2=$(find "$SDK_DIR/build-tools" -name aapt2 -type f 2>/dev/null | head -1)
    fi
fi

if [ -n "$AAPT2" ]; then
    echo "  解析引擎: aapt2 ($AAPT2)"
    echo ""

    DUMP=$("$AAPT2" dump badging "$APK" 2>/dev/null || true)
    if [ -z "$DUMP" ]; then
        echo "  ! aapt2 dump 失败，可能 APK 文件损坏"
    else
        echo "$DUMP" | while IFS= read -r line; do
            case "$line" in
                package:*)
                    PKG=$(echo "$line" | sed -n "s/.*name='\([^']*\)'.*/\1/p")
                    VER=$(echo "$line" | sed -n "s/.*versionName='\([^']*\)'.*/\1/p")
                    CODE=$(echo "$line" | sed -n "s/.*versionCode='\([^']*\)'.*/\1/p")
                    echo "  包名: $PKG"
                    echo "  版本名: $VER"
                    echo "  版本号: $CODE"
                    ;;
                application:*)
                    LABEL=$(echo "$line" | sed -n "s/.*label='\([^']*\)'.*/\1/p")
                    ICON=$(echo "$line" | sed -n "s/.*icon='\([^']*\)'.*/\1/p")
                    DEBUG=$(echo "$line" | grep -q "debuggable=true" && echo "是" || echo "否")
                    echo "  应用名: $LABEL"
                    echo "  Debuggable: $DEBUG"
                    ;;
                launchable-activity:*)
                    ACT=$(echo "$line" | sed -n "s/.*name='\([^']*\)'.*/\1/p")
                    echo "  启动 Activity: $ACT"
                    ;;
                sdkVersion:*)
                    echo "  minSdk: $(echo "$line" | sed -n "s/.*sdkVersion:'\([^']*\)'.*/\1/p")"
                    ;;
                targetSdkVersion:*)
                    echo "  targetSdk: $(echo "$line" | sed -n "s/.*targetSdkVersion:'\([^']*\)'.*/\1/p")"
                    ;;
                uses-permission:*)
                    PERM=$(echo "$line" | sed -n "s/.*name='\([^']*\)'.*/\1/p")
                    echo "  权限: $PERM"
                    ;;
                uses-feature:*)
                    FEAT=$(echo "$line" | sed -n "s/.*name='\([^']*\)'.*/\1/p")
                    echo "  特性: $FEAT"
                    ;;
                native-code:*)
                    NAT=$(echo "$line" | sed -n "s/.*native-code:'\([^']*\)'.*/\1/p")
                    echo "  Native ABI: $NAT"
                    ;;
            esac
        done
    fi
else
    echo "  解析引擎: 基础模式 (未找到 aapt2)"
    echo ""

    # 从 AndroidManifest.xml 提取基本信息
    if command -v unzip &>/dev/null; then
        TMPDIR=$(mktemp -d 2>/dev/null || echo "/tmp/apk-info-$$")
        CLEANUP_DIRS="$CLEANUP_DIRS $TMPDIR"
        mkdir -p "$TMPDIR"
        if unzip -o "$APK" AndroidManifest.xml -d "$TMPDIR" &>/dev/null; then
            MANIFEST="$TMPDIR/AndroidManifest.xml"
            if [ -f "$MANIFEST" ]; then
                PKG=$(strings "$MANIFEST" 2>/dev/null | grep -E '^[a-z][a-z0-9_]*\.[a-z]' | head -3 | tail -1)
                echo "  包名: $PKG"
            fi
        fi
    fi

    echo ""
    echo "  提示: 需要 aapt2 输出完整信息"
    echo "  安装 Android SDK 或确保 aapt2 在以下路径之一:"
    echo "  - \$ANDROID_HOME/build-tools/"
    echo "  - /Android/build-tools/"
    echo "  - ~/.gradle/caches/ (Gradle 构建后会缓存)"
fi

echo "═══════════════════════════════════════════"
