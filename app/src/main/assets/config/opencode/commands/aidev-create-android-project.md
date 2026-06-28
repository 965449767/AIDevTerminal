---
description: 创建新的 Android 项目骨架
---

你可以使用 `aidev-create-android-project <应用名> <包名>` 快速创建 Android 项目。

例如: `aidev-create-android-project MyApp com.example.myapp` 创建到 `/root/Workspace/Android/MyApp`。

生成内容：
  - settings.gradle.kts / 根 build.gradle.kts / app/build.gradle.kts
  - AndroidManifest.xml（启动 Activity + Application 配置）
  - MainActivity.kt（XML 布局 + AppCompat）
  - res/layout/activity_main.xml
  - res/values/strings.xml / themes.xml
  - proguard-rules.pro / .gitignore
  - Git init + initial commit

AGP 8.7.3 + Kotlin 2.0.21 + Gradle 8.14.5，自动从当前系统提取。

当用户需要新建 Android 项目时使用此命令。
