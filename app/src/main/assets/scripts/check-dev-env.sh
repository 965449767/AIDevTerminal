#!/bin/bash
set +e
echo "AIDev 环境检测"
echo
echo "== 系统 =="
grep PRETTY_NAME /etc/os-release 2>/dev/null | cut -d= -f2- | tr -d '"' || true
uname -m
echo
echo "== 命令检测 =="
for c in curl wget git unzip zip tar xz node npm python3 pip3 java gcc g++ make cmake ninja openssl ssh htop lsof watch jq nc ip dig go rustc cargo cargo-ndk ndk-build sdkmanager apkanalyzer tinyproxy aidev-clean aidev-proxy opencode; do
  if command -v "$c" >/dev/null 2>&1; then
    v="$($c --version 2>/dev/null | head -1)"
    [ -z "$v" ] && v="$(command -v "$c")"
    printf "  %-10s ✓ %s\n" "$c" "$v"
  else
    printf "  %-10s ✗ 缺失\n" "$c"
  fi
done
echo
echo "== dpkg 包检测 =="
for p in ca-certificates curl wget git unzip zip tar xz-utils nano vim less procps coreutils findutils build-essential python3 python3-pip openjdk-17-jdk nodejs npm cmake ninja-build openssl openssh-client htop lsof watch jq netcat-openbsd golang iproute2 dnsutils tinyproxy; do
  if dpkg-query -W -f='${Status}' "$p" 2>/dev/null | grep -q "install ok installed"; then
    printf "  %-22s ✓ 已安装\n" "$p"
  else
    printf "  %-22s ✗ 未安装\n" "$p"
  fi
done
echo
echo "== 证书 =="
if [ -s /etc/ssl/certs/ca-certificates.crt ]; then
  echo "  ca-certificates ✓"
else
  echo "  ca-certificates ✗"
fi
echo
echo "== 网络 =="
getent hosts github.com >/dev/null 2>&1 && echo "  DNS github.com ✓" || echo "  DNS github.com ✗"
if command -v curl >/dev/null 2>&1; then
  curl -I -L -k --connect-timeout 10 https://github.com 2>&1 | head -8
else
  echo "  curl 缺失，无法测试 HTTPS。"
fi
