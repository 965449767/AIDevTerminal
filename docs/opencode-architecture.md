# OpenCode 架构参考

> 给 AI agent 的参考文档。记录 OpenCode 架构事实，用于指导 AdvTerminal 项目的 OpenCode 相关功能开发。
> 来源：OpenCode 官方文档、源码分析、实际测试。标注了每条事实的来源和验证方式。

---

## 1. 运行架构

### 核心模型

```
opencode (无参数)
  ├── HTTP 服务器（监听随机端口）
  └── TUI 客户端（作为客户端连接该服务器）
```

**来源：** OpenCode 官方 Server 文档 [opencode.ai/docs/server](https://opencode.ai/docs/server/)
> "When you run `opencode` it starts a TUI and a server. Where the TUI is the client that talks to the server."

这意味着即使只用 TUI，后端 HTTP 服务也在运行。TUI 模式下仍然可以访问 HTTP API 和 SSE 事件流。

### 三种运行模式

| 命令 | 用途 | 界面 | 服务端口 |
|---|---|---|---|
| `opencode` | 日常使用 | TUI（同时启动本地服务） | 随机分配 |
| `opencode serve` | 纯 API 服务 | 无（headless） | 4096（默认） |
| `opencode web` | Web 访问 | 浏览器界面 | 4096（默认） |

### attach 模式

```bash
# 终端1：启动后台服务
opencode serve --port 4096

# 终端2：连接 TUI 到已有服务
opencode attach http://localhost:4096

# 非交互式运行（附着到已有服务，避免 MCP 冷启动）
opencode run --attach http://localhost:4096 "解释闭包"
```

**来源：** OpenCode CLI 文档 [dev.opencode.ai/docs/cli](https://dev.opencode.ai/docs/cli/)

---

## 2. CLI 命令参考

### 核心命令

| 命令 | 说明 |
|---|---|
| `opencode` | 启动 TUI（默认行为），同时启动后端服务 |
| `opencode --port 4096 --hostname 127.0.0.1` | 固定服务端口的 TUI |
| `opencode serve [--port 4096]` | 纯 API 服务（headless） |
| `opencode web [--port 4096]` | API + Web 界面 |
| `opencode attach <url>` | TUI 连接已有服务 |
| `opencode run --attach <url> "<prompt>"` | 非交互式运行 |
| `opencode init` | 初始化项目 AGENTS.md |
| `opencode session` | 管理会话 |
| `opencode auth` | 管理 AI provider 凭证 |
| `opencode export/import` | 导出/导入对话数据 |

### TUI 启动选项

```bash
opencode [--port <number>] [--hostname <string>] [--dir <path>]
         [--continue | -c] [--session <id> | -s] [--fork]
```

- `--port` / `--hostname`：固定 TUI 内部服务的端口和地址
- `--dir`：TUI 工作目录
- `--continue` / `-c`：继续上次会话
- `--session` / `-s`：恢复指定会话
- `--fork`：fork 模式（和 --continue/--session 一起使用）

### 服务端认证

```bash
OPENCODE_SERVER_PASSWORD=your-password opencode serve
OPENCODE_SERVER_USERNAME=admin          # 覆盖默认用户名 "opencode"
```

---

## 3. SSE 事件流（核心功能依赖）

### 端点

| 端点 | 说明 | 来源 |
|---|---|---|
| `GET /event` | 当前实例的 SSE 事件流 | OpenCode 源码 |
| `GET /global/event` | 全局 SSE 事件流（包含所有实例） | OpenCode 源码 |

### 连接方式

```bash
curl -sN http://localhost:4096/event
# 或
curl -sN http://localhost:4096/global/event
```

- `-s`：静默模式
- `-N`：禁用 buffering，实时流式输出

### 事件类型（完整列表）

| 事件 | 属性 | 含义 | 用途 |
|---|---|---|---|
| `server.connected` | `{}` | SSE 连接建立 | 连接确认 |
| `server.heartbeat` | `{}` | 保活信号，每 10 秒 | 连接健康检测 |
| `session.created` | `{ sessionID, info }` | 新会话创建 | 会话追踪 |
| `session.updated` | `{ sessionID, info }` | 会话更新 | 会话状态 |
| `session.status` | `{ sessionID, status }` | 会话状态：`busy` 或 `idle` | **对话结束检测** |
| `session.idle` | `{ sessionID }` | 兼容事件，已废弃 | 建议用 `session.status` |
| `session.compacted` | `{ sessionID }` | 会话压缩 | — |
| `message.updated` | `{ sessionID, messageID }` | 消息更新 | 进度追踪 |
| `message.part.updated` | `{ sessionID, messageID, partID }` | 消息部分更新 | 流式输出追踪 |
| `message.part.delta` | `{ sessionID, messageID, partID }` | 消息部分增量 | 流式输出追踪 |
| `server.instance.disposed` | `{ directory }` | 实例销毁 | 清理 |

**关键发现：** `session.status: busy → idle` 的转换 = "对话完成"信号。

**来源：**
- OpenCode 事件参考文档（github.com/anomalyco/opencode）
- OpenCode 源码 `packages/opencode/src/bus/*`
- GitHub Issue #26866 (SSE 事件流回归问题，确认了事件类型)

### SSE 行为的已知问题

| 版本 | 问题 | 状态 |
|---|---|---|
| 1.14.42–1.15.4 | SyncEvent 类事件（`message.updated` 等）不通过 SSE 推送 | 1.15.5+ 修复 |
| 1.14.46–1.14.48 | SSE 连接后只收到 `server.connected`，无后续事件 | 修复版本待查 |

**来源：** GitHub Issues #26866, #27966

---

## 4. HTTP API 端点（完整）

### 全局

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/global/health` | 健康检查 + 版本 |
| GET | `/global/event` | 全局 SSE 事件流 |

### 会话管理

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/session` | 会话列表 |
| POST | `/session` | 创建会话 |
| GET | `/session/status` | 所有会话状态 |
| GET | `/session/:id` | 会话详情 |
| DELETE | `/session/:id` | 删除会话 |
| POST | `/session/:id/abort` | 中止运行中的会话 |
| POST | `/session/:id/fork` | fork 会话 |
| POST | `/session/:id/permissions/:permissionID` | 响应权限请求 |

### 消息

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/session/:id/message` | 会话消息列表 |
| POST | `/session/:id/message` | 发送消息（同步，等待响应） |
| POST | `/session/:id/prompt_async` | 异步提交提示词（返回 204） |
| POST | `/session/:id/command` | 执行斜杠命令 |
| POST | `/session/:id/shell` | 执行 shell 命令 |

### TUI 控制

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/tui/append-prompt` | 追加文本到提示词 |
| POST | `/tui/submit-prompt` | 提交当前提示词 |
| POST | `/tui/open-help` | 打开帮助 |
| POST | `/tui/open-sessions` | 打开会话选择器 |
| POST | `/tui/execute-command` | 执行命令 |
| POST | `/tui/show-toast` | 显示 toast 通知 |
| GET | `/tui/control/next` | 等待下一个控制请求 |
| POST | `/tui/control/response` | 响应控制请求 |

### OpenAPI 规范

```
GET /doc → OpenAPI 3.1 规范的 HTML 页面
```

---

## 5. Notification / Attention 系统

### opencode.json 配置

```json
{
  "attention": {
    "enabled": true,
    "notifications": true,
    "sound": true
  }
}
```

**官方说明：** "requests desktop notifications only when the terminal is blurred"

**来源：** OpenCode 配置文档

### 桌面端实现机制

- OpenCode 通过终端 escape sequence 向终端模拟器发送通知请求
- 终端模拟器（如 iTerm2, kitty, WezTerm）解析这些序列并调用 OS 原生通知（macOS Notification Center、Linux notify-send）
- 常见协议：OSC 9、OSC 99、OSC 777

### Android 终端的问题

- **AdvTerminal 的终端模拟器没有实现这些 escape sequence 的解析**
- OpenCode 发出的通知事件在 Android 端被静默丢弃
- 核心缺口：不是"OpenCode 没有通知机制"，而是"Android 终端 App 没有桥接 OpenCode 的通知事件"

### 我们的桥接方案

**替代方案（取代不可靠的 escape sequence 解析）：**

```
SSE 监听器 → 检测 session.status: busy → idle → sysnotify
```

- 利用 `opencode --port 4096` 固定服务端口
- 后台 SSE 监听 `GET /event` 事件流
- 检测 `session.status` 从 `busy` 变为 `idle` 作为"对话完成"信号
- 调用 `sysnotify` 发送 Android 通知

**可靠性评估：**

| 方案 | 可靠性 | 复杂度 |
|---|---|---|
| SSE event `session.status` 监听 | ✅ 高（协议级 API） | ★★☆ |
| 终端输出解析（ANSI/TUI 模式匹配） | ❌ 低 | ★★★ |
| Escape sequence 拦截（OSC 9/99/777） | ⚠️ 中 | ★★★ |

---

## 6. AIDev 集成点

### 当前集成状态

| 组件 | 位置 | 说明 |
|---|---|---|
| opencode-check.sh | `assets/scripts/opencode-check.sh` | 验证 OpenCode 安装与 AIDev 命令注册状态 |
| setup-opencode.sh | `assets/scripts/setup-opencode.sh` | 安装包装脚本 |
| command .md 文件 | `assets/opencode-commands/` → rootfs `~/.config/opencode/commands/` | aidev-* 命令的 OpenCode 集成 |
| .aidevrc shell 函数 | `TerminalShellAssets.kt` (line 171-172) | `opencode-check()` 和 `setup-opencode()` 函数 |
| aidev-ubuntu-core 路由 | `UbuntuBootstrapScripts.kt` (line 573) | 路由到 PRoot 内的安装脚本 |
| 路径发现 | `EmbeddedSettingsPage.kt` (line 198-204) | 检查 opencode 二进制路径 |
| 命令面板 | `ShellActivity.kt` (line 264-277) | 命令面板中的 opencode 入口 |

### PRoot 内 opencode 路径

opencode 安装在 PRoot Ubuntu 内，可能的位置：
- `/usr/bin/opencode`
- `/usr/local/bin/opencode`
- `/root/.opencode/bin/opencode`
- `/bin/opencode`

检查顺序在 `EmbeddedSettingsPage.kt` (line 198-204) 中定义。

### `aidev-opencode` 包装脚本的设计模式

**位置：** `UbuntuBootstrapScripts.kt:agentPrivotScripts()` → PRoot `/usr/local/bin/aidev-opencode`

**架构：**
```
Android shell
  → (可选) $AIDEV_BIN/aidev-opencode (host 侧包装)
    → aidev-ubuntu-core aidev-opencode
      → PRoot run_ubuntu_command "/usr/local/bin/aidev-opencode"
        → opencode --port 4096 (TUI)
        → 后台 SSE 监听器 → http://127.0.0.1:4096/event
```

**PRoot 侧脚本（agentPrivotScripts 中的实现）：**
```sh
#!/bin/sh
START=$(date +%s)

# 后台 SSE 监听器：检测对话完成
(
  # 等待服务就绪
  for i in 1 2 3 4 5; do
    curl -s http://127.0.0.1:4096/global/health >/dev/null 2>&1 && break
    sleep 1
  done
  # SSE 事件流监听
  curl -sN http://127.0.0.1:4096/event 2>/dev/null | while read -r line; do
    case "$line" in
      *"session.status"*"idle"*)
        sysnotify "OpenCode" "对话完成" ;;
    esac
  done
) &
LISTENER=$?

# 运行 opencode（固定端口）
opencode --port 4096 --hostname 127.0.0.1 "$@"; EC=$?
END=$(date +%s); DUR=$((END - START))

# 清理
kill $LISTENER 2>/dev/null; wait $LISTENER 2>/dev/null

# 退出通知（debounce < 5s）
[ $DUR -lt 5 ] && exit $EC
# ... 格式话耗时，按 EC 选消息 ...
sysnotify --priority high "OpenCode" "$MSG"
exit $EC
```

### sysnotify 通知桥接

- Shell 脚本写 JSON 到 `$AIDEV_HOME/.aidev-notify/req-*.json`
- `NotifyBridgeService`（Kotlin coroutine）每 500ms 轮询读取并删除
- 调用 `AIDevCommandDispatcher.notify()` 发送 Android 通知
- sysnotify 在 PRoot 内可通过 `/host-home/dev-env/bin/sysnotify` 访问（PATH 已包含）

---

## 7. 决策记录

### 已取消的方案

| 方案 | 原因 | 日期 |
|---|---|---|
| Approval Center UI | OpenCode TUI 已内置 `"ask"` 权限系统 + TUI 交互 | 2026-06 |
| Agent Dashboard | OpenCode TUI 已有会话管理、agent 切换、sub-agent | 2026-06 |
| Security Sandbox | OpenCode 的 `external_directory` + 工具权限系统足够 | 2026-06 |
| Agent Config UI | `opencode.json` 编辑即可 | 2026-06 |
| 终端输出解析检测"等待审批" | ANSI escape 污染，模式匹配误报率高，不可靠 | 2026-06 |
| Escape sequence 拦截 | 终端模拟器层未实现，工程量大，版本变更易失效 | 2026-06 |

### 已确定的方案

| 方案 | 状态 | 说明 |
|---|---|---|
| `aidev-opencode` 退出通知 | ✅ 计划实现 | shell 包装脚本 + sysnotify |
| `aidev-opencode` 存活提醒 | ✅ 计划实现 | 5min/15min/30min/60min+ 时间驱动 |
| SSE `session.status` 监听（每轮对话通知） | ✅ 计划实现 | 利用 `opencode --port 4096` + SSE 事件流 |

### SSE 监听器的边界情况

| 情况 | 处理 |
|---|---|
| 端口 4096 被占用 | fallback 到另一个端口，或通知用户 |
| opencode 版本不支持 SSE | 静默降级到纯退出通知 |
| 多个会话同时运行 | 跟踪所有会话的 busy/idle 状态 |
| 启动时 session 已是 idle（初始状态） | 只通知 busy→idle 转换，不通知 idle→idle |
| SSE 连接断开 | 自动重连或静默降级 |
| 快速连续完成多个对话 | debounce，至少间隔 N 秒 |

---

## 8. 参考来源

| 来源 | URL | 内容 |
|---|---|---|
| OpenCode 官方文档 | https://opencode.ai/docs/ | 架构、配置、CLI、Server |
| OpenCode Server 文档 | https://opencode.ai/docs/server/ | HTTP API、SSE、OpenAPI |
| OpenCode CLI 文档 | https://dev.opencode.ai/docs/cli/ | CLI 命令、attach 模式 |
| OpenCode 事件参考 | https://github.com/anomalyco/opencode 源码 `packages/opencode/src/bus/` | 事件类型定义 |
| OpenCode 事件流源码 | https://github.com/sst/opencode/blob/dev/packages/opencode/src/server/routes/event.ts | SSE 路由实现 |
| GitHub Issue #26866 | https://github.com/anomalyco/opencode/issues/26866 | SSE 事件流回归问题 |
| GitHub Issue #27966 | https://github.com/anomalyco/opencode/issues/27966 | SyncEvent SSE 发布问题 |
| OpenCode 中文社区 | https://opencode.runman.ai/ | 远程模式基础教程 |
| AdvTerminal 源码 `TerminalShellAssets.kt` | 项目内 | opencode 命令部署和 shell 函数 |
| AdvTerminal 源码 `UbuntuBootstrapScripts.kt` | 项目内 | PRoot 内脚本和 aidev-ubuntu-core 路由 |
| AdvTerminal 源码 `NotifyBridgeService.kt` | 项目内 | sysnotify 通知桥接实现 |
