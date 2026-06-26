---
description: 智能 Android 构建 (auto/full/test/compile 模式)
---

你可以在 AIDev 终端中使用 `aidev-build` 命令来构建 Android 项目。

构建模式:
- `aidev-build` 或 `aidev-build --auto` — 自动模式，根据 git diff 判断改动范围
- `aidev-build --full` — 全量构建（assembleDebug）
- `aidev-build --test` — 构建 + 运行单元测试
- `aidev-build --compile` — 仅 Kotlin 编译检查

构建前自动检测：
  - AAPT2 daemon 死锁 → 自动修复并重试
  - 代理可用性 → 代理不可达时自动禁用
  - 构建失败 → 自动调用 `aidev-error-why` 诊断

当用户需要构建 Android 项目时，优先使用此命令。
