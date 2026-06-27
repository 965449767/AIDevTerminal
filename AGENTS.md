# AdvTerminal Repository Instructions

## Build & Test

```bash
# Full build
bash /root/.android-env/scripts/build-android.sh

# Or manual
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64 ./gradlew assembleDebug --no-daemon

# Unit tests
./gradlew :app:testDebugUnitTest --no-daemon

# Parallel build + test
./gradlew :app:assembleDebug :app:testDebugUnitTest --no-daemon
```

## Build Strategy

按改动范围选最轻的验证方式，避免全量编译：

| 场景 | 命令 | 耗时 |
|---|---|---|
| 纯逻辑/结构改动 | `./gradlew :app:compileDebugKotlin --no-daemon` | ~1-2m |
| 改完跑单元测试 | `./gradlew :app:testDebugUnitTest --no-daemon` | ~2-4m |
| UI/行为需真机验证 | `bash /root/.android-env/scripts/build-android.sh` | ~4-5m |
| 首次构建/依赖变更 | 同上（必须全量） | ~4-5m |

默认走 `compileDebugKotlin` 即可，必要时再升级到 test 或 full build。

## Workflow Execution Protocol

每次执行优化 Phase 时，必须遵循以下步骤：

### 执行步骤
1. **执行修改** — 一次只改一个文件或一组紧密相关的文件
2. **编译验证** — 按 Build Strategy 选最轻的验证方式（默认 `compileDebugKotlin`，必要时升级到 `assembleDebug`）
3. **单元测试** — `./gradlew :app:testDebugUnitTest --no-daemon`
4. **告知用户** — 明确说明本次改了什么、需要测试哪些功能、具体操作步骤
5. **等待反馈** — 必须等用户安装 APK 并确认测试结果后，才能进入下一步
6. **确认通过** — 用户确认无问题后，继续下一个步骤

### 禁止事项
- 不得跳过用户验证环节
- 不得一次修改多个不相关的文件
- 不得在用户反馈前继续下一步

## Project Conventions

- Kotlin, no comments in code
- UI via AIDevUi helper class
- Coroutines + Flow for async
- PreferencesManager wrapping SharedPreferences

## Critical Rules

## OpenCode 架构参考

开发 OpenCode 相关功能前，必须阅读 `docs/opencode-architecture.md`，其中记录了：
- OpenCode 运行架构（TUI + 服务端双进程模型）
- SSE 事件流（`session.status` 等关键事件）
- HTTP API 端点
- AIDev 集成点（agentPrivotScripts、aidev-ubuntu-core 路由）
- 已确认/已取消的方案决策

不要重复搜索已在文档中确认的信息。

## Android Development Tools

This terminal environment provides custom aidev commands for Android development.
These are available at `/usr/local/bin/aidev-*` inside the PRoot Ubuntu environment:

| Command | Purpose |
|---|---|
| `aidev-build [--full\|--test\|--compile]` | Smart Android build with auto AAPT2 fix and error diagnosis |
| `aidev-apk-info <file.apk>` | Parse APK (package/version/permissions/components) |
| `aidev-error-why [keyword]` | Diagnose build errors with Chinese solutions |
| `aidev-logcat [--tags ...] [--watch-crash]` | Enhanced log viewer with crash monitoring |
| `aidev-create-android-project <name> <pkg>` | Create new Android project scaffold |
| `aidev-gen activity\|fragment\|viewmodel <name>` | Generate component skeleton code |
| `aidev-index class\|res\|layout\|string\|function <kw>` | Code search index for Android projects |

Run `opencode-install` once to register these as OpenCode custom commands
(installs OpenCode if needed + writes command files to `~/.config/opencode/commands/`).

### 知识库同步规则
任何涉及新增、修改命令的操作，必须**同时**更新 `res/raw/knowledge_base.json`：
- 新增命令 → 添加完整条目（title/cmd/desc/tags/usage/permissions/notes）
- 修改命令行为/参数 → 同步更新 usage 和 notes
- 删除命令 → 移除对应条目
- 不允许出现「命令已实现但知识库找不到」的情况
- 知识库更新和代码修改在同一批次提交，先更新知识库再写代码
