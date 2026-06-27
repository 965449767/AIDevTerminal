# 当前任务: OpenCode 通知包装

## 说明
实现 `aidev-opencode` 包装脚本，利用 OpenCode 的 SSE 事件流监听 `session.status: busy→idle` 来检测每轮对话完成，通过 sysnotify 发送 Android 通知。

## 已完成
- OpenCode 架构参考文档 `docs/opencode-architecture.md`
- AGENTS.md 引用更新
- `UbuntuBootstrapScripts.kt` 三处改动：
  1. agentPrivotScripts() + aidev-opencode 脚本（SSE 监听 + 存活提醒 + 退出通知）
  2. ensure_ubuntu_helpers() 复制列表添加 aidev-opencode
  3. aidev-ubuntu-core case 路由添加 aidev-opencode
- knowledge_base.json 添加 aidev-opencode 条目

## 待办
- 编译验证 compileDebugKotlin + testDebugUnitTest

## 用户验证
- 安装 APK 后，在 Ubuntu 内运行 `aidev-opencode`，验证：
  - OpenCode 正常启动（全屏 TUI）
  - 每轮对话完成时手机通知栏弹出"对话完成"
  - 退出时弹出退出通知（含耗时）
