#!/bin/bash
# aidev-clean: 清理构建缓存（PRoot 内版）
# 用法: aidev-clean [--all|--gradle|--builds|--dry-run]
set -eo pipefail

case "${1:-}" in
    --all|-a)
        echo "=== Gradle 缓存 ==="
        GCACHE="${GRADLE_USER_HOME:-$HOME/.gradle}/caches"
        if [ -d "$GCACHE" ]; then
            echo "  $(du -sh "$GCACHE" 2>/dev/null | cut -f1)"
            rm -rf "$GCACHE/modules-2/tmp" 2>/dev/null
            find "$GCACHE" -name "*.lock" -delete 2>/dev/null
            echo "  tmp/locks 已清理"
        else
            echo "  (无)"
        fi
        echo "=== 构建目录 ==="
        for d in build app/build .gradle; do
            if [ -d "$d" ]; then
                echo "  $d: $(du -sh "$d" 2>/dev/null | cut -f1) → 删除"
                rm -rf "$d" 2>/dev/null
            fi
        done
        echo "=== 系统缓存 ==="
        apt-get clean 2>/dev/null && echo "  apt 缓存已清理" || echo "  apt 不可用"
        ;;
    --gradle|-g)
        GCACHE="${GRADLE_USER_HOME:-$HOME/.gradle}/caches"
        if [ -d "$GCACHE" ]; then
            echo "Gradle caches: $(du -sh "$GCACHE" 2>/dev/null | cut -f1)"
            rm -rf "$GCACHE/modules-2/tmp" 2>/dev/null
            echo "tmp 已清理"
        else
            echo "无 Gradle 缓存"
        fi
        ;;
    --builds|-b)
        for d in build app/build; do
            if [ -d "$d" ]; then
                echo "  $d: $(du -sh "$d" 2>/dev/null | cut -f1) → 删除"
                rm -rf "$d" 2>/dev/null
            fi
        done
        ;;
    --dry-run|-n)
        echo "=== Gradle caches ==="
        GCACHE="${GRADLE_USER_HOME:-$HOME/.gradle}"
        [ -d "$GCACHE" ] && du -sh "$GCACHE" || echo "  (无)"
        echo "=== Build dirs ==="
        for d in build app/build .gradle; do
            [ -d "$d" ] && echo "  $d: $(du -sh "$d" 2>/dev/null | cut -f1)" || true
        done
        echo "=== apt cache ==="
        du -sh /var/cache/apt 2>/dev/null || echo "  (无)"
        ;;
    *)
        echo "用法: aidev-clean [选项]"
        echo ""
        echo "选项:"
        echo "  --all, -a        全部清理（Gradle 缓存 + 构建目录 + apt）"
        echo "  --gradle, -g     仅清理 Gradle 临时缓存"
        echo "  --builds, -b     仅删除 build/ 目录"
        echo "  --dry-run, -n    仅预览可清理空间（不删除）"
        ;;
esac
