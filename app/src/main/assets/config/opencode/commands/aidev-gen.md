---
description: 生成 Android 组件骨架代码（Activity/Fragment/ViewModel）
---

你可以使用 `aidev-gen <子命令> <组件名>` 生成组件代码。

子命令:
- `aidev-gen activity SettingsActivity` — 生成 Activity + 对应 layout XML
- `aidev-gen fragment ProfileFragment` — 生成 Fragment + 对应 layout XML
- `aidev-gen viewmodel ProfileViewModel` — 生成 ViewModel（StateFlow 架构）

自动检测项目包名（从 AndroidManifest.xml 读取 package），生成标准架构代码。

当用户需要在 Android 项目中添加新组件时使用此命令。
