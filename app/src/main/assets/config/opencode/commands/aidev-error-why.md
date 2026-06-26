---
description: 诊断 Android 构建错误，给出中文解决方案
---

你可以在 AIDev 终端中使用 `aidev-error-why [关键词]` 来诊断构建错误。

支持的关键词和诊断类型:
  - `AAPT2` — AAPT2 daemon 崩溃/死锁/内存不足
  - `Kotlin` 或 `compile` — Kotlin 编译错误（类型不匹配、空安全等）
  - `Gradle` — Gradle Daemon 崩溃/配置错误/插件版本
  - `dependency` 或 `conflict` — 依赖冲突
  - `resource` — 资源未找到/资源 ID 错误
  - `NDK` 或 `ABI` — NDK/ABI 不匹配
  - `Java` 或 `version` — Java 版本兼容性问题
  - `proxy` 或 `timeout` — 代理/网络超时
  - `OOM` 或 `memory` — 构建内存不足
  - `SDK` — SDK 或 build-tools 未找到
  - `daemon` — Daemon 相关通用问题

也可以从 `aidev-build` 通过管道传入完整错误输出:
  `aidev-build --full 2>&1 | aidev-error-why`

当构建失败时，用户会询问原因，优先调用此命令诊断。
