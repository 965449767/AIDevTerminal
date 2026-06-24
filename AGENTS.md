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

## Workflow Execution Protocol

每次执行优化 Phase 时，必须遵循以下步骤：

### 执行步骤
1. **执行修改** — 一次只改一个文件或一组紧密相关的文件
2. **编译验证** — `bash /root/.android-env/scripts/build-android.sh`
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
