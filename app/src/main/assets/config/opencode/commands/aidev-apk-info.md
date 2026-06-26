---
description: 解析 APK 文件信息（包名/版本/权限/组件）
---

你可以使用 `aidev-apk-info <apk文件路径>` 来解析 APK 文件信息。

输出内容包括：包名、版本号、版本名、权限列表、四大组件（Activity/Service/Receiver/Provider）、支持的 ABI、是否为 Debug 版本。

自动查找 aapt2 路径（Gradle 缓存 → SDK build-tools → ANDROID_HOME → local.properties），如果找不到则显示基础文件信息。

当用户需要查看 APK 详情时使用此命令。
