#!/bin/bash
set +e
export DEBIAN_FRONTEND=noninteractive
echo "AIDev 环境修复"
echo "[1/4] 清理 apt/dpkg 锁残留..."
rm -f /var/cache/apt/archives/lock /var/lib/apt/lists/lock /var/lib/dpkg/lock-frontend /var/lib/dpkg/lock 2>/dev/null
echo "[2/4] 配置未完成的包..."
dpkg --configure -a
echo "[3/4] 修复破损依赖..."
apt-get -f install -y
echo "[4/4] 再次配置确认..."
dpkg --configure -a
echo
echo "修复完成。建议运行：check-dev-env"
