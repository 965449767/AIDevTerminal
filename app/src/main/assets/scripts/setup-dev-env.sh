#!/bin/bash
set +e
export DEBIAN_FRONTEND=noninteractive
echo "AIDev 开发环境配置"
echo "原则：先检测，缺什么装什么"
echo
base_packages=(ca-certificates curl wget git unzip zip tar xz-utils nano vim less procps coreutils findutils build-essential python3 python3-pip openjdk-17-jdk cmake ninja-build openssl openssh-client htop lsof watch jq netcat-openbsd golang iproute2 dnsutils tinyproxy)
js_packages=(nodejs npm)
missing_base=()
for p in "${base_packages[@]}"; do
  if ! dpkg-query -W -f='${Status}' "$p" 2>/dev/null | grep -q "install ok installed"; then
    missing_base+=("$p")
  fi
done
missing_js=()
for p in "${js_packages[@]}"; do
  if ! dpkg-query -W -f='${Status}' "$p" 2>/dev/null | grep -q "install ok installed"; then
    missing_js+=("$p")
  fi
done
echo "[1/6] 当前检测结果"
if [ "${#missing_base[@]}" -eq 0 ]; then
  echo "  基础层：已全部安装。"
else
  echo "  基础层缺失：${missing_base[*]}"
fi
if [ "${#missing_js[@]}" -eq 0 ]; then
  echo "  JS 层：已全部安装。"
else
  echo "  JS 层缺失：${missing_js[*]}"
fi
if [ "${#missing_base[@]}" -gt 0 ] || [ "${#missing_js[@]}" -gt 0 ]; then
  echo "[2/6] 更新 apt 索引..."
  apt-get update
else
  echo "[2/4] 无需更新"
fi
if [ "${#missing_base[@]}" -gt 0 ]; then
  echo "[3/6] 安装基础层缺失包..."
  apt-get install -y --no-install-recommends "${missing_base[@]}" || {
    echo "基础层安装失败，尝试修复后重试..."
    repair-dev-env
    apt-get install -y --no-install-recommends "${missing_base[@]}" || {
      echo "基础层安装失败。请运行 check-dev-env 查看状态。"
      exit 1
    }
  }
else
  echo "[3/6] 基础层无需安装"
fi
if [ "${#missing_js[@]}" -gt 0 ]; then
  echo "[4/6] 安装 JS 层 nodejs/npm..."
  apt-get install -y --no-install-recommends "${missing_js[@]}" || {
    echo "JS 层安装遇到问题，尝试修复后重试..."
    repair-dev-env
    apt-get install -y --no-install-recommends "${missing_js[@]}" || {
      echo "JS 层 nodejs/npm 仍未完全配置。"
    }
  }
else
  echo "[4/6] JS 层无需安装"
fi
echo
echo "[5/6] Android SDK 工具..."
SDK_DIR="${AIDEV_HOME:-$HOME}/android-sdk"
CMDLINE_TOOLS="$SDK_DIR/cmdline-tools/latest/bin/sdkmanager"
if [ ! -f "$CMDLINE_TOOLS" ]; then
    echo "  下载 cmdline-tools..."
    mkdir -p "$SDK_DIR"
    CMDLINE_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
    curl -L "$CMDLINE_URL" -o /tmp/cmdline-tools.zip 2>/dev/null && \
    unzip -q /tmp/cmdline-tools.zip -d /tmp/cmdline-tools && \
    mkdir -p "$SDK_DIR/cmdline-tools" && \
    mv /tmp/cmdline-tools/cmdline-tools "$SDK_DIR/cmdline-tools/latest" && \
    rm -rf /tmp/cmdline-tools /tmp/cmdline-tools.zip && \
    echo "  cmdline-tools 已安装" || echo "  cmdline-tools 下载失败（可手动下载）"
fi
if [ -f "$CMDLINE_TOOLS" ]; then
    NDK_DIR="$SDK_DIR/ndk/27.0.12077973"
    if [ ! -d "$NDK_DIR" ]; then
        echo "  安装 NDK 27.0.12077973（约 1-2GB，可能需要较长时间）..."
        yes | "$CMDLINE_TOOLS" "ndk;27.0.12077973" 2>/dev/null && \
        echo "  NDK 已安装" || echo "  NDK 安装失败（可手动重试：sdkmanager ndk;27.0.12077973）"
    else
        echo "  NDK 已存在"
    fi
fi
echo
echo "[6/6] Rust 工具链..."
if ! command -v rustup >/dev/null 2>&1; then
    echo "  安装 rustup..."
    curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y 2>/dev/null && \
    echo "  rustup 已安装" || echo "  rustup 安装失败（可手动安装）"
fi
if command -v rustup >/dev/null 2>&1; then
    . "$HOME/.cargo/env" 2>/dev/null || true
    for target in aarch64-linux-android armv7-linux-androideabi x86_64-linux-android i686-linux-android; do
        if ! rustup target list --installed 2>/dev/null | grep -q "$target"; then
            echo "  添加 Android target: $target"
            rustup target add "$target" 2>/dev/null
        fi
    done
    if ! command -v cargo-ndk >/dev/null 2>&1; then
        echo "  安装 cargo-ndk..."
        cargo install cargo-ndk 2>/dev/null && echo "  cargo-ndk 已安装" || true
    fi
fi
echo
echo "配置完成。建议运行：check-dev-env"
