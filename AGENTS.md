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
