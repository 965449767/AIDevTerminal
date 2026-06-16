#!/bin/bash
set -e
echo "AIDev 将安装：opencode"
echo "来源：opencode 官方安装脚本 https://opencode.ai/install"
echo "目标目录：$HOME/.opencode/bin"
echo
if ! command -v curl >/dev/null 2>&1 || ! command -v unzip >/dev/null 2>&1; then
  echo "缺少 curl/unzip，先执行 deploy-dev-env。"
  deploy-dev-env
fi
if ! command -v curl >/dev/null 2>&1; then
  echo "curl 仍然不可用，请运行 check-dev-env 查看原因。"
  exit 1
fi
if command -v opencode >/dev/null 2>&1; then
  echo "检测到 opencode 已安装：$(command -v opencode)"
  opencode --version 2>/dev/null || true
  echo "如需强制重装，请先删除：rm -f ~/.opencode/bin/opencode"
  exit 0
fi
install_dir="$HOME/.opencode/bin"
export OPENCODE_INSTALL_DIR="$install_dir"
export SHELL="${SHELL:-/bin/bash}"
for attempt in 1 2 3; do
  echo "官方安装尝试 $attempt/3 ..."
  if curl -fsSL --retry 3 --retry-delay 2 --connect-timeout 20 https://opencode.ai/install | bash; then
    echo "安装成功。"
    exit 0
  fi
  echo "本次安装失败，等待后重试。"
  sleep 2
done
echo "opencode 官方安装脚本连续失败。"
echo "建议运行：check-dev-env"
exit 1
