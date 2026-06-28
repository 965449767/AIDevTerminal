#!/bin/bash
# uninstallapp: 卸载应用（PRoot 内包装，委托 Shizuku）
# 用法: uninstallapp <包名>
set -eo pipefail

if [ $# -eq 0 ]; then
    echo "用法: uninstallapp <package_name>"
    echo "示例: uninstallapp com.example.myapp"
    exit 1
fi

exec aidev-shizuku exec "pm uninstall -k --user 0 $1"
