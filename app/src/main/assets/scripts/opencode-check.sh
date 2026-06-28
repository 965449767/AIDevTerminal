#!/bin/bash
# opencode-check — 验证 OpenCode 安装与 AIDev 命令注册状态
# 所有二进制和配置文件已由 Kotlin 侧自动部署，本脚本仅做检查和提示

set +e

echo "╔═══════════════════════════════════════════╗"
echo "║  AIDev OpenCode 环境检查                 ║"
echo "╚═══════════════════════════════════════════╝"

HAS_ERROR=0
OPENCODE_CONFIG_DIR="${XDG_CONFIG_HOME:-$HOME/.config}/opencode"

# ─── 检查 opencode 二进制 ──────────────────────────
if command -v opencode >/dev/null 2>&1; then
  echo "✓ OpenCode 已安装: $(command -v opencode)"
  opencode --version 2>/dev/null || true
else
  echo "⚠ opencode 未在 PATH 中找到"
  echo "  应有路径: /usr/local/bin/opencode"
  echo "  如果在 PRoot 内，确认 rootfs 已安装 opencode"
  echo "  请尝试: 重启 terminal 或运行 aidev-doctor"
  HAS_ERROR=1
fi

# ─── 检查命令注册文件 ──────────────────────────────
cmds_dir="$OPENCODE_CONFIG_DIR/commands"
count=0
if [ -d "$cmds_dir" ]; then
  count=$(ls "$cmds_dir"/aidev-*.md 2>/dev/null | wc -l)
fi

if [ "$count" -ge 7 ]; then
  echo "✓ AIDev 开发命令已注册: $count 个"
else
  echo "→ AIDev 开发命令注册不完整 ($count/7)"
  echo "  重启 terminal 或重装 rootfs 可自动修复"
  HAS_ERROR=1
fi

echo ""
if [ "$HAS_ERROR" -eq 0 ]; then
  echo "╔═══════════════════════════════════════════╗"
  echo "║  AIDev OpenCode 环境就绪                  ║"
  echo "║                                           ║"
  echo "║  输入 opencode 启动                       ║"
  echo "║  在 OpenCode 中可直接使用 Android 开发命令 ║"
  echo "╚═══════════════════════════════════════════╝"
else
  echo "╔═══════════════════════════════════════════╗"
  echo "║  环境未就绪，请修复上述问题后重试          ║"
  echo "╚═══════════════════════════════════════════╝"
fi
exit $HAS_ERROR
