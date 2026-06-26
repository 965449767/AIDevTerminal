# 当前任务: 全部 Phase A—D 已完成

## 本阶段完成

### Phase A — Android 开发日常三件套
- Android SDK 知识库（aapt2/adb/dumpsys/pm/am/wm/settings）
- `aidev-apk-info` — APK 解析（自动找 aapt2，中文输出）
- `aidev-build` — 智能构建（auto/full/test/compile 模式 + 错误诊断）

### Phase B — 脚手架 + 代码生成
- `aidev-create-android-project` — 完整 Android 项目骨架
- `aidev-gen activity|fragment|viewmodel` — 组件代码生成（自动检测包名）

### Phase C — 诊断引擎
- `aidev-error-why` — 11 种常见错误模式的中文解决方案（AAPT2、Kotlin、Gradle、OOM、SDK 路径等）
- `aidev-build` 失败时自动调用 `aidev-error-why`
- `aidev-logcat` 增强: `--tags` 关键词过滤、`--watch-crash` 崩溃自动停止

### Phase D — 代码索引
- `aidev-index` — 扫描项目建立 `.aidev-index.json` 索引
- 支持 `class` / `res` / `string` / `layout` / `function` / `component` 搜索

### 附加修复
- 目录实时同步修复：`PwdFileObserver` 从 `FileObserver` 改为 coroutine 轮询（500ms）

## 全部新命令

| 命令 | 用途 |
|---|---|
| `aidev-apk-info <apk>` | APK 信息解析 |
| `aidev-build [--full\|--test\|--compile]` | 智能 Android 构建 |
| `aidev-create-android-project <name> <pkg>` | 新建 Android 项目 |
| `aidev-gen activity\|fragment\|viewmodel <name>` | 生成组件骨架代码 |
| `aidev-error-why [关键词]` | 构建错误诊断 |
| `aidev-logcat --tags --watch-crash` | 增强版日志查看 |
| `aidev-index class\|res\|layout\|string\|function <kw>` | 代码搜索索引 |

## 下一步
- 无活跃计划。可选: 更多命令条目的知识库增补、Termux 能力补齐、Tree 搜索
