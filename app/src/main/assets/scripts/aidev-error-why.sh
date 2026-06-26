#!/bin/bash
# aidev-error-why: 搜索常见构建错误并显示中文解决方案
# 用法: aidev-error-why <关键词>
#       cat build.log | aidev-error-why
#       aidev-build --full | aidev-error-why

set -eo pipefail

MATCH_ALL=false
KEYWORD=""

if [ "$1" = "--all" ]; then
    MATCH_ALL=true
elif [ -n "$1" ]; then
    KEYWORD="$1"
fi

INPUT=$(cat)

if [ -z "$INPUT" ] && [ -z "$KEYWORD" ]; then
    echo "用法: aidev-error-why <关键词>"
    echo "  cat build.log | aidev-error-why"
    echo "  aidev-build --full 2>&1 | aidev-error-why"
    exit 1
fi

found=0

check() {
    local label="$1"
    local pattern="$2"
    local solution="$3"
    if [ "$MATCH_ALL" = true ]; then
        echo ""
        echo "═══ $label ═══"
        echo "$solution"
        found=$((found + 1))
        return
    fi
    if echo "$INPUT" | grep -qi "$pattern" 2>/dev/null; then
        echo ""
        echo "═══ $label ═══"
        echo "匹配: $(echo "$INPUT" | grep -i "$pattern" | head -3 | tr '\n' ';')"
        echo ""
        echo "原因: $solution"
        found=$((found + 1))
    fi
}

if [ "$MATCH_ALL" = false ] && [ -z "$INPUT" ]; then
    check "关键词搜索" "$KEYWORD" "$(grep -i "$KEYWORD" "$0" | head -20)"
    exit 0
fi

check "AAPT2 Daemon 启动失败" \
    "AAPT2.*daemon\|AAPT2.*Daemon\|Daemon.*startup" \
    "AAPT2 守护进程在 QEMU 用户态下无法正常启动。
解决方案:
  1. 运行 /usr/local/bin/wrap-android-native.sh
  2. 确保 gradle.properties 中有:
     android.aapt2DaemonMode=false
     android.aapt2FromMavenOverride=<path-to-aapt2>
  3. 重新运行 aidev-build --full"

check "AAPT2 崩溃/段错误" \
    "AAPT2.*crash\|AAPT2.*signal\|AAPT2.*fatal\|SIGSEGV\|crash.*aapt" \
    "AAPT2 在 QEMU 用户态下运行时崩溃。
解决方案:
  1. 运行 /usr/local/bin/wrap-android-native.sh
  2. 如果持续崩溃，尝试: export AAPT2_DAEMON_MODE=false
  3. 或者使用 --no-daemon 参数运行 Gradle"

check "Kotlin 编译错误" \
    "^e: \|error.*Kotlin\|Kotlin.*Compilation" \
    "Kotlin 代码有编译错误。
解决方案:
  1. 查看上方 red 'e:' 开头的行
  2. 每个错误的格式: 文件路径:行号 错误描述
  3. 常见原因: 类型不匹配、未导包、空安全问题
  4. 修复后重新运行 aidev-build"

check "Gradle Daemon 问题" \
    "Gradle.*daemon\|Daemon.*disconnected\|could not be reached" \
    "Gradle 守护进程连接失败。
解决方案:
  1. ./gradlew --stop
  2. 移除 .gradle/ 目录: rm -rf .gradle/
  3. 重新运行 aidev-build --clean"

check "Gradle 配置错误" \
    "Gradle.*DSL\|could not run\|unknown property\|unsupported Gradle" \
    "Gradle 配置文件语法错误。
解决方案:
  1. 检查 settings.gradle.kts / build.gradle.kts 语法
  2. 检查 Gradle Wrapper 版本 (gradle/wrapper/gradle-wrapper.properties)
  3. 检查 AGP 和 Kotlin 版本是否兼容"

check "依赖冲突" \
    "Conflict\|dependency.*conflict\|Duplicate.*class\|More than one" \
    "存在依赖冲突，多个库包含相同类。
解决方案:
  1. 检查 app/build.gradle.kts 的 dependencies
  2. 使用 ./gradlew :app:dependencies 查看依赖树
  3. 使用 exclude 或 force 解决冲突"

check "资源未找到" \
    "Resource.*not found\|resource.*not found\|unresolved.*reference\|R\.\|cannot resolve" \
    "引用的资源不存在。
解决方案:
  1. 检查 res/ 目录下是否有对应资源文件
  2. 检查 R.id / R.layout / R.drawable 引用是否正确
  3. 如果使用了 dataBinding/ViewBinding，确保 build.gradle.kts 已启用"

check "NDK/ABI 问题" \
    "NDK\|abiFilter\|native.*library\|so.*error" \
    "NDK 或 ABI 配置问题。
解决方案:
  1. 检查 app/build.gradle.kts 中的 ndk.abiFilters
  2. 确保 .so 文件放置在正确的 jniLibs 目录下
  3. 当前项目仅编译 arm64-v8a"

check "Java 版本不匹配" \
    "Java.*version\|JVM.*version\|class.*version\|invalid source\|target" \
    "Java 版本配置不匹配。
解决方案:
  1. 确保 JAVA_HOME 指向 JDK 17
  2. 检查 build.gradle.kts 中的 compileOptions
  3. 检查 Gradle JVM 参数"

check "代理连接失败" \
    "proxy\|timeout\|Connection refused\|Could not resolve\|UnknownHost" \
    "网络连接失败，可能是代理问题。
解决方案:
  1. aidev-build 已自动检测代理可用性
  2. 如果代理不可达，会自动禁用代理参数
  3. 如需手动指定代理，修改 ~/.gradle/gradle.properties
  4. 检查清华镜像源是否可用: https://maven.aliyun.com/repository/google"

check "内存不足" \
    "OutOfMemoryError\|Out of memory\|GC overhead\|Metaspace" \
    "Gradle 进程内存不足。
解决方案:
  1. 在 gradle.properties 中增加: org.gradle.jvmargs=-Xmx4096m
  2. 关闭其他应用释放内存
  3. 使用 --no-daemon 避免 daemon 占用额外内存"

check "Android SDK 未找到" \
    "SDK.*not found\|compileSdk\|targetSdk\|platform.*not installed" \
    "Android SDK 平台或构建工具未找到。
解决方案:
  1. 确保 local.properties 的 sdk.dir 指向正确路径
  2. 检查 compileSdk 版本是否已安装
  3. 可用版本: $(ls /Android/platforms/ 2>/dev/null || echo '无法检测')"

if [ "$found" -eq 0 ] && [ "$MATCH_ALL" = false ]; then
    echo "未匹配到已知错误模式。"
    echo "尝试: aidev-error-why --all 查看所有支持的模式"
    echo "或手动搜索: grep 'error\|failed\|Exception' build.log"
fi
