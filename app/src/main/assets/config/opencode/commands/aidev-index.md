---
description: Android 项目代码搜索索引（类/资源/方法/组件秒搜）
---

你可以使用 `aidev-index` 命令在 Android 项目中快速搜索代码。

用法:
- `aidev-index` — 首次构建/刷新索引
- `aidev-index class MainActivity` — 搜索类定义
- `aidev-index res layout` — 搜索布局资源
- `aidev-index string app_name` — 搜索字符串资源
- `aidev-index layout activity_main` — 搜索布局文件
- `aidev-index function loadData` — 搜索方法/函数
- `aidev-index component MainActivity` — 搜索 AndroidManifest 组件

索引保存在 `.aidev-index.json`，首次扫描后后续搜索秒级返回。

当用户需要在项目中快速定位代码或资源时使用此命令。
