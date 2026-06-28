#!/bin/bash
# aidev-build: 智能 Android 构建包装
# 自动选择最轻验证方式，处理 AAPT2 死锁，诊断构建错误
# 用法: aidev-build [--full|--test|--compile] [--clean]

set -eo pipefail

MODE="auto"
CLEAN=false
ARGS=""

for arg in "$@"; do
    case "$arg" in
        --full)   MODE="full"   ;;
        --test)   MODE="test"   ;;
        --compile) MODE="compile" ;;
        --clean)  CLEAN=true    ;;
        --help|-h)
            echo "用法: aidev-build [选项]"
            echo ""
            echo "选项:"
            echo "  --full       全量编译 (assembleDebug)"
            echo "  --test       编译 + 单元测试"
            echo "  --compile    仅 kotlin 编译"
            echo "  --clean      先 clean 再构建"
            echo "  --help       显示此帮助"
            echo ""
            echo "模式选择:"
            echo "  纯逻辑/结构改动 → --compile (默认)"
            echo "  改完跑测试     → --test"
            echo "  UI/首次/依赖变更 → --full"
            echo ""
            echo "示例:"
            echo "  aidev-build               # 自动选最轻方式"
            echo "  aidev-build --full        # 全量编译"
            echo "  aidev-build --test        # 编译 + 单元测试"
            echo "  aidev-build --clean       # clean 后增量编译"
            exit 0
            ;;
        *) ARGS="$ARGS $arg" ;;
    esac
done

JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64
export JAVA_HOME

# 检查 gradlew
if [ ! -f "gradlew" ]; then
    echo "错误: 当前目录不是 Android 项目根目录 (gradlew 不存在)"
    exit 1
fi

chmod +x gradlew 2>/dev/null || true

# 检查 Android SDK
if [ ! -f "local.properties" ] && [ -z "${ANDROID_HOME:-}" ] && [ -z "${ANDROID_SDK_ROOT:-}" ]; then
    echo "错误: 未找到 Android SDK。请创建 local.properties:"
    echo "  echo 'sdk.dir=/Android' > local.properties"
    echo "或设置环境变量: export ANDROID_HOME=/Android"
    exit 1
fi

PROXY_ARGS=""
if grep -q "systemProp.http.proxyHost" ~/.gradle/gradle.properties 2>/dev/null; then
    PHOST=$(grep "systemProp.http.proxyHost" ~/.gradle/gradle.properties | head -1 | cut -d= -f2)
    PPORT=$(grep "systemProp.http.proxyPort" ~/.gradle/gradle.properties | head -1 | cut -d= -f2)
    PHOST="${PHOST:-127.0.0.1}"
    PPORT="${PPORT:-18080}"
    if ! command -v nc &>/dev/null || ! nc -z -w2 "$PHOST" "$PPORT" 2>/dev/null; then
        PROXY_ARGS="-Dhttp.proxyHost= -Dhttps.proxyHost= -Dhttp.proxyPort= -Dhttps.proxyPort="
    fi
fi

# 也检查项目级 gradle.properties
if [ -f "gradle.properties" ] && [ -z "$PROXY_ARGS" ]; then
    if grep -q "systemProp.http.proxyHost" gradle.properties 2>/dev/null; then
        PHOST=$(grep "systemProp.http.proxyHost" gradle.properties | head -1 | cut -d= -f2)
        PPORT=$(grep "systemProp.http.proxyPort" gradle.properties | head -1 | cut -d= -f2)
        PHOST="${PHOST:-127.0.0.1}"
        PPORT="${PPORT:-18080}"
        if ! command -v nc &>/dev/null || ! nc -z -w2 "$PHOST" "$PPORT" 2>/dev/null; then
            PROXY_ARGS="-Dhttp.proxyHost= -Dhttps.proxyHost= -Dhttp.proxyPort= -Dhttps.proxyPort="
        fi
    fi
fi

GRADLE_FLAGS="--no-daemon $PROXY_ARGS"

if [ "$CLEAN" = true ]; then
    echo "═══ 清理 ═══"
    ./gradlew clean $GRADLE_FLAGS 2>&1 | tail -5
fi

BUILD_START=$(date +%s)
BUILD_LOG="/tmp/aidev-build-$$.log"
> "$BUILD_LOG"

echo ""
echo "═══ 构建: $MODE 模式 ═══"

BUILD_EXIT=0
case "$MODE" in
    compile)
        echo "→ ./gradlew :app:compileDebugKotlin --no-daemon"
        ./gradlew :app:compileDebugKotlin $GRADLE_FLAGS 2>&1 | tee -a "$BUILD_LOG"
        BUILD_EXIT=${PIPESTATUS[0]}
        ;;
    full)
        echo "→ ./gradlew assembleDebug --no-daemon"
        ./gradlew assembleDebug $GRADLE_FLAGS 2>&1 | tee -a "$BUILD_LOG"
        BUILD_EXIT=${PIPESTATUS[0]}
        ;;
    test)
        echo "→ ./gradlew :app:testDebugUnitTest --no-daemon"
        ./gradlew :app:assembleDebug :app:testDebugUnitTest $GRADLE_FLAGS 2>&1 | tee -a "$BUILD_LOG"
        BUILD_EXIT=${PIPESTATUS[0]}
        ;;
    auto)
        echo "→ 检测改动范围，选择最轻构建方式..."
        CHANGED=$( (git diff --name-only HEAD 2>/dev/null; git diff --name-only 2>/dev/null) | sort -u || echo "")
        if echo "$CHANGED" | grep -qE '\.(kts|gradle|properties|xml)$' 2>/dev/null || [ ! -f "app/build/outputs/apk/debug/app-debug.apk" ]; then
            echo "→ 依赖或首次构建，走全量"
            ./gradlew assembleDebug $GRADLE_FLAGS 2>&1 | tee -a "$BUILD_LOG"
            BUILD_EXIT=${PIPESTATUS[0]}
        elif echo "$CHANGED" | grep -qE '_test|Test\.kt$' 2>/dev/null || [ "$1" = "--test" ]; then
            echo "→ 测试文件变更，编译+测试"
            ./gradlew :app:assembleDebug :app:testDebugUnitTest $GRADLE_FLAGS 2>&1 | tee -a "$BUILD_LOG"
            BUILD_EXIT=${PIPESTATUS[0]}
        else
            echo "→ 增量 Kotlin 编译"
            ./gradlew :app:compileDebugKotlin $GRADLE_FLAGS 2>&1 | tee -a "$BUILD_LOG"
            BUILD_EXIT=${PIPESTATUS[0]}
        fi
        ;;
esac

BUILD_END=$(date +%s)
BUILD_SEC=$((BUILD_END - BUILD_START))
BUILD_MIN=$((BUILD_SEC / 60))
BUILD_SEC=$((BUILD_SEC % 60))

echo ""
echo "═══════════════════════════════════════════"

if [ $BUILD_EXIT -eq 0 ]; then

    APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
    if [ -f "$APK_PATH" ]; then
        APK_SIZE=$(stat --format=%s "$APK_PATH" 2>/dev/null || stat -f%z "$APK_PATH" 2>/dev/null)
        APK_SIZE_KB=$((APK_SIZE / 1024))
        echo "  BUILD SUCCESSFUL (${BUILD_MIN}m${BUILD_SEC}s)"
        echo "  APK: $APK_PATH (${APK_SIZE_KB}KB)"
        echo "  Install: cp $APK_PATH /sdcard/ && installapk /sdcard/app-debug.apk"
    else
        echo "  BUILD SUCCESSFUL (${BUILD_MIN}m${BUILD_SEC}s)"
    fi

    if [ "$MODE" = "test" ] || [ "$MODE" = "auto" ]; then
        if grep -q "fail" "$BUILD_LOG" 2>/dev/null || grep -q "FAILED" "$BUILD_LOG" 2>/dev/null; then
            echo "  ! 测试失败，查看上方输出"
        fi
    fi
else
    echo "  BUILD FAILED (${BUILD_MIN}m${BUILD_SEC}s)"
    echo ""

    ERR_COUNT=$(grep -c '^e: ' "$BUILD_LOG" 2>/dev/null || true)
    if [ "$ERR_COUNT" -gt 0 ] 2>/dev/null; then
        echo "  -- Kotlin 编译错误 ($ERR_COUNT) --"
        grep '^e: ' "$BUILD_LOG" | head -20
        [ "$ERR_COUNT" -gt 20 ] && echo "  ... 还有 $((ERR_COUNT - 20)) 个错误，查看完整日志: grep '^e: ' $BUILD_LOG"
    fi

    echo ""
    echo "  -- 错误诊断 --"
    if command -v aidev-error-why &>/dev/null; then
        cat "$BUILD_LOG" | aidev-error-why 2>/dev/null || true
    else
        echo "  (运行 aidev-error-why 查看详细诊断)"
    fi

    if grep -q "AAPT2" "$BUILD_LOG" 2>/dev/null; then
        echo ""
        echo "  AAPT2 相关错误，尝试修复..."
        if [ -f /usr/local/bin/wrap-android-native.sh ]; then
            /usr/local/bin/wrap-android-native.sh
            echo "  已重新包装 native 工具，重试: aidev-build --full"
        fi
    fi

    if grep -q "Gradle" "$BUILD_LOG" 2>/dev/null || grep -q "gradle" "$BUILD_LOG" 2>/dev/null; then
        echo ""
        echo "  可能是 Gradle 配置问题，尝试:"
        echo "  - 检查 gradle.properties 配置"
        echo "  - 运行 gradlew --stop 重新启动 daemon"
    fi

    echo ""
    echo "  完整日志: $BUILD_LOG"
fi

echo "═══ 日志: $BUILD_LOG ═══"
echo "═══════════════════════════════════════════"

# 构建成功则清理日志，失败则保留
[ $BUILD_EXIT -eq 0 ] && rm -f "$BUILD_LOG" 2>/dev/null || true
exit $BUILD_EXIT
