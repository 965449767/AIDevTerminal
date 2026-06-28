#!/bin/bash
# aidev-error-why: 搜索常见构建错误并显示解决方案
# 用法: aidev-error-why [--lang en] [--all] [<关键词>]
#       cat build.log | aidev-error-why
#       aidev-build --full 2>&1 | aidev-error-why

set -eo pipefail

MATCH_ALL=false
LANG_EN=false
KEYWORD=""

while [ $# -gt 0 ]; do
    case "$1" in
        --all) MATCH_ALL=true; shift ;;
        --lang) shift; [ "${1:-}" = "en" ] && LANG_EN=true; shift ;;
        *) KEYWORD="$1"; shift ;;
    esac
done

INPUT=$(cat)

if [ -z "$INPUT" ] && [ -z "$KEYWORD" ] && [ "$MATCH_ALL" = false ]; then
    if [ "$LANG_EN" = true ]; then
        echo "Usage: aidev-error-why [--lang en] [--all] [<keyword>]"
        echo "  cat build.log | aidev-error-why"
        echo "  aidev-build --full 2>&1 | aidev-error-why"
    else
        echo "用法: aidev-error-why [--lang en] [--all] [<关键词>]"
        echo "  cat build.log | aidev-error-why"
        echo "  aidev-build --full 2>&1 | aidev-error-why"
    fi
    exit 1
fi

found=0

check() {
    local label="$1"
    local pattern="$2"
    local solution_zh="$3"
    local solution_en="$4"
    if [ "$MATCH_ALL" = true ]; then
        echo ""
        echo "═══ $label ═══"
        if [ "$LANG_EN" = true ] && [ -n "$solution_en" ]; then
            echo "$solution_en"
        else
            echo "$solution_zh"
        fi
        found=$((found + 1))
        return
    fi
    if echo "$INPUT" | grep -qiE "$pattern" 2>/dev/null; then
        echo ""
        echo "═══ $label ═══"
        echo "$(echo "$INPUT" | grep -iE "$pattern" | head -3 | tr '\n' ';')"
        echo ""
        if [ "$LANG_EN" = true ] && [ -n "$solution_en" ]; then
            echo "$solution_en"
        else
            echo "$solution_zh"
        fi
        found=$((found + 1))
    fi
}

if [ "$MATCH_ALL" = false ] && [ -z "$INPUT" ]; then
    check "keyword" "$KEYWORD" "$(grep -i "$KEYWORD" "$0" | head -20)" ""
    exit 0
fi

AAPT2_ZH="AAPT2 守护进程在 QEMU 用户态下无法正常启动。
解决方案:
  1. 运行 /usr/local/bin/wrap-android-native.sh
  2. 确保 gradle.properties 中有:
     android.aapt2DaemonMode=false
     android.aapt2FromMavenOverride=<path-to-aapt2>
  3. 重新运行 aidev-build --full"
AAPT2_EN="AAPT2 daemon cannot start under QEMU user mode.
Fix:
  1. Run /usr/local/bin/wrap-android-native.sh
  2. Ensure gradle.properties has:
     android.aapt2DaemonMode=false
     android.aapt2FromMavenOverride=<path-to-aapt2>
  3. Re-run aidev-build --full"

check "AAPT2 Daemon" \
    "AAPT2.*(daemon|Daemon|crash|signal|fatal|shutdown)" \
    "$AAPT2_ZH" "$AAPT2_EN"

check "Kotlin 编译错误" \
    "^e: " \
    "Kotlin 代码有编译错误。
解决方案:
  1. 查看上方 'e:' 开头的行
  2. 每个错误的格式: 文件路径:行号 错误描述
  3. 常见原因: 类型不匹配、未导包、空安全问题
  4. 修复后重新运行 aidev-build" \
    "Kotlin compilation errors found.
Fix:
  1. Check lines starting with 'e:' above
  2. Format: file:line error description
  3. Common causes: type mismatch, missing import, null safety
  4. Fix and re-run aidev-build"

check "Gradle Daemon 问题" \
    "Gradle.*daemon|daemon.*disconnected|could not be reached" \
    "Gradle 守护进程连接失败。
解决方案:
  1. ./gradlew --stop
  2. 移除 .gradle/ 目录: rm -rf .gradle/
  3. 重新运行 aidev-build --clean" \
    "Gradle daemon connection failed.
Fix:
  1. ./gradlew --stop
  2. Remove .gradle/: rm -rf .gradle/
  3. Re-run aidev-build --clean"

check "Gradle 配置错误" \
    "(Gradle.*DSL|could not run|unknown property|unsupported Gradle)" \
    "Gradle 配置文件语法错误。
解决方案:
  1. 检查 settings.gradle.kts / build.gradle.kts 语法
  2. 检查 Gradle Wrapper 版本
  3. 检查 AGP 和 Kotlin 版本是否兼容" \
    "Gradle configuration syntax error.
Fix:
  1. Check settings.gradle.kts / build.gradle.kts syntax
  2. Check Gradle Wrapper version
  3. Verify AGP and Kotlin version compatibility"

check "依赖冲突" \
    "(dependency.*[Cc]onflict|[Cc]onflict.*dependency|Duplicate.*class|More than one)" \
    "存在依赖冲突，多个库包含相同类。
解决方案:
  1. 检查 app/build.gradle.kts 的 dependencies
  2. 使用 ./gradlew :app:dependencies 查看依赖树
  3. 使用 exclude 或 force 解决冲突" \
    "Dependency conflict found.
Fix:
  1. Check dependencies in app/build.gradle.kts
  2. Run ./gradlew :app:dependencies to view tree
  3. Use exclude or force to resolve"

check "资源未找到" \
    "(resource.*not found|unresolved.*reference|cannot resolve symbol)" \
    "引用的资源不存在。
解决方案:
  1. 检查 res/ 目录下是否有对应资源文件
  2. 检查 R.id / R.layout / R.drawable 引用是否正确
  3. 如果使用了 dataBinding/ViewBinding，确保 build.gradle.kts 已启用" \
    "Resource reference not found.
Fix:
  1. Check res/ directory for the resource
  2. Verify R.id / R.layout / R.drawable references
  3. If using dataBinding/ViewBinding, ensure build.gradle.kts has it enabled"

check "NDK/ABI 问题" \
    "(NDK|abiFilter|native.*library|\.so.*error)" \
    "NDK 或 ABI 配置问题。
解决方案:
  1. 检查 app/build.gradle.kts 中的 ndk.abiFilters
  2. 确保 .so 文件放置在正确的 jniLibs 目录下
  3. 当前项目仅编译 arm64-v8a" \
    "NDK or ABI configuration issue.
Fix:
  1. Check ndk.abiFilters in app/build.gradle.kts
  2. Ensure .so files are in correct jniLibs directory
  3. Current project only compiles arm64-v8a"

check "Java 版本不匹配" \
    "(Java.*version|JVM.*version|invalid source.*version|bad class file)" \
    "Java 版本配置不匹配。
解决方案:
  1. 确保 JAVA_HOME 指向 JDK 17
  2. 检查 build.gradle.kts 中的 compileOptions
  3. 检查 Gradle JVM 参数" \
    "Java version mismatch.
Fix:
  1. Ensure JAVA_HOME points to JDK 17
  2. Check compileOptions in build.gradle.kts
  3. Check Gradle JVM arguments"

check "代理连接失败" \
    "(proxy.*(error|refused|timeout|denied)|Connection refused|Could not resolve|UnknownHost)" \
    "网络连接失败，可能是代理问题。
解决方案:
  1. aidev-build 已自动检测代理可用性
  2. 如果代理不可达，会自动禁用代理参数
  3. 如需手动指定代理，修改 ~/.gradle/gradle.properties" \
    "Network connection failure, possibly proxy-related.
Fix:
  1. aidev-build auto-detects proxy availability
  2. Automatically disables proxy if unreachable
  3. To manually configure, edit ~/.gradle/gradle.properties"

check "内存不足" \
    "(OutOfMemoryError|Out of memory|GC overhead|Metaspace)" \
    "Gradle 进程内存不足。
解决方案:
  1. 在 gradle.properties 中增加: org.gradle.jvmargs=-Xmx4096m
  2. 关闭其他应用释放内存
  3. 使用 --no-daemon 避免 daemon 占用额外内存" \
    "Gradle process out of memory.
Fix:
  1. Increase in gradle.properties: org.gradle.jvmargs=-Xmx4096m
  2. Close other apps to free memory
  3. Use --no-daemon to reduce memory usage"

check "Android SDK 未找到" \
    "(SDK.*not found|compileSdk.*not installed|platform.*not installed)" \
    "Android SDK 平台或构建工具未找到。
解决方案:
  1. 确保 local.properties 的 sdk.dir 指向正确路径
  2. 检查 compileSdk 版本是否已安装
  3. 可用版本: $(ls /Android/platforms/ 2>/dev/null || echo '无法检测')" \
    "Android SDK platform or build tools not found.
Fix:
  1. Ensure local.properties sdk.dir points to correct path
  2. Check if compileSdk version is installed
  3. Available: $(ls /Android/platforms/ 2>/dev/null || echo 'cannot detect')"

if [ "$found" -eq 0 ] && [ "$MATCH_ALL" = false ]; then
    if [ "$LANG_EN" = true ]; then
        echo "No known error patterns matched."
        echo "Try: aidev-error-why --all to see all supported patterns"
        echo "Or manually search: grep -E 'error|failed|Exception' build.log"
    else
        echo "未匹配到已知错误模式。"
        echo "尝试: aidev-error-why --all 查看所有支持的模式"
        echo "或手动搜索: grep 'error\|failed\|Exception' build.log"
    fi
fi
