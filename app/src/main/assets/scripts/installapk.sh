#!/bin/bash
# installapk: 安装 APK（PRoot 内包装，委托 aidev-install --gui）
# 用法: installapk <apk_path>
set -eo pipefail

if [ $# -eq 0 ]; then
    echo "用法: installapk <apk_path>"
    exit 1
fi

exec aidev-install --gui "$@"
