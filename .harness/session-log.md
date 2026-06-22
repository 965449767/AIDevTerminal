# Session Log

## 2026-06-14 - Harness Initialization

### Summary

Started `0.13.0` Standard Harness initialization.

### Files Created or Updated

- `AGENTS.md`
- `current-task.md`
- `docs/architecture.md`
- `docs/verification.md`
- `docs/coding-guidelines.md`
- `docs/decisions.md`
- `docs/error-journal.md`
- `.harness/session-state.json`

### Validation

Passed:

```bash
bash scripts/harness_check.sh
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 /data/user/work/gradle/gradle-8.14.5/bin/gradle -p "/workspace/AIDevTerminal" :app:assembleDebug --no-daemon
```

## 2026-06-14 - Git Initialization

### Summary

User approved Git initialization.

### Commands Run

```bash
git init
git branch -m main
git config user.name "AIDev Harness"
git config user.email "aidev-harness@example.local"
git status --short
```

### Files Created or Updated

- `.gitignore`
- `AGENTS.md`
- `docs/git-workflow.md`
- `docs/decisions.md`
- `current-task.md`
- `.harness/session-state.json`
- `.harness/session-log.md`

### Validation

Passed:

```bash
bash scripts/harness_check.sh
git status --short
git log --oneline -1
```

### Commit

```text
9a1b229 chore: initial aidev terminal snapshot
```

### Progress Report

```text
已完成：Git 初始化
本次完成：创建 main 分支初始快照
总体进度：100%
剩余：0 阶段
验证：通过
下一步：商量 0.13.2 工程结构整理
```

## 2026-06-14 - 0.13.2-a Ubuntu Script Extraction

### Summary

Moved Ubuntu script generation out of `EmbeddedShellPages.kt` into `UbuntuBootstrapScripts.kt`.

### Files Created or Updated

- `app/src/main/java/com/aidev/terminal/UbuntuBootstrapScripts.kt`
- `app/src/main/java/com/aidev/terminal/EmbeddedShellPages.kt`
- `current-task.md`
- `.harness/session-state.json`
- `.harness/session-log.md`
- `.harness/progress-map.md`

### Validation

Passed:

```bash
bash scripts/harness_check.sh
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 /data/user/work/gradle/gradle-8.14.5/bin/gradle -p "/workspace/AIDevTerminal" :app:assembleDebug --no-daemon
```

### Git Status

Changes are not committed.

### Progress Report

```text
已完成：0.13.2-a
本次完成：抽出 Ubuntu 脚本生成逻辑
总体进度：33%
剩余：2 小阶段
验证：通过
下一步：商量 0.13.2-b
```

## 2026-06-14 - 0.13.2-b Terminal Shell Assets Extraction

### Summary

Committed `0.13.2-a`, then moved terminal shell asset setup out of `EmbeddedShellPages.kt` into `TerminalShellAssets.kt`.

### Files Created or Updated

- `app/src/main/java/com/aidev/terminal/TerminalShellAssets.kt`
- `app/src/main/java/com/aidev/terminal/EmbeddedShellPages.kt`
- `current-task.md`
- `.harness/session-state.json`
- `.harness/session-log.md`
- `.harness/progress-map.md`

### Validation

Passed:

```bash
bash scripts/harness_check.sh
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 /data/user/work/gradle/gradle-8.14.5/bin/gradle -p "/workspace/AIDevTerminal" :app:assembleDebug --no-daemon
```

### Git Status

`0.13.2-b` changes are not committed.

### Progress Report

```text
已完成：0.13.2-b
本次完成：抽出终端启动资产逻辑
总体进度：66%
剩余：1 小阶段
验证：通过
下一步：商量 0.13.2-c
```

## 2026-06-14 - 0.13.2-c Embedded Page Split

### Summary

Moved file, task, and settings pages out of `EmbeddedShellPages.kt` into separate files.

### Files Created or Updated

- `app/src/main/java/com/aidev/terminal/EmbeddedFilesPage.kt`
- `app/src/main/java/com/aidev/terminal/EmbeddedTasksPage.kt`
- `app/src/main/java/com/aidev/terminal/EmbeddedSettingsPage.kt`
- `app/src/main/java/com/aidev/terminal/EmbeddedShellPages.kt`
- `current-task.md`
- `.harness/session-state.json`
- `.harness/session-log.md`
- `.harness/progress-map.md`

### Validation

Passed:

```bash
bash scripts/harness_check.sh
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 /data/user/work/gradle/gradle-8.14.5/bin/gradle -p "/workspace/AIDevTerminal" :app:assembleDebug --no-daemon
```

### Git Status

`0.13.2-b/c` changes are not committed.

### Progress Report

```text
已完成：0.13.2-c
本次完成：拆分文件页、任务页、设置页
总体进度：100%
剩余：0 小阶段
验证：通过
下一步：提交或导出 APK
```

## 2026-06-14 - Android and Git Guidelines

### Summary

Started `0.13.1` documentation update for Xiaomi HyperOS and Git backup rules.

### Files Created or Updated

- `docs/android-guidelines.md`
- `docs/git-workflow.md`
- `AGENTS.md`
- `docs/verification.md`
- `docs/coding-guidelines.md`
- `docs/decisions.md`
- `current-task.md`
- `.harness/session-state.json`

### Git Status

`git status --short` could not run because the project directory is not a Git repository.

No Git initialization, commit, tag, reset, cleanup, or push was performed.

### Validation

Passed:

```bash
bash scripts/harness_check.sh
```

Git status:

```text
git unavailable: not a repository
```

### Progress Report

```text
已完成：0.13.1
本次完成：小米 HyperOS 规范和 Git 备份规范
总体进度：100%
剩余：0 阶段
验证：通过；Git 未初始化
下一步：商量 0.13.2 工程结构整理
```

### Next Steps

1. Review generated harness files.
2. Start future work with `/start`.
3. Plan `0.13.1` Android guideline documentation.

### Progress Report

```text
已完成：0.13.0
本次完成：项目 Harness 初始化
总体进度：100%
剩余：0 阶段
验证：通过
下一步：进入 0.13.1 Android 规范文档落地
```

## 2026-06-22 - Session Resume

### Summary

Resumed session. Committed Gradle wrapper setup, SDK 34→36 upgrade, proxy cleanup, and AGENTS.md rewrite. Updated session-state to `0.12.16-terminal-completion-lite`.

### Commands Run

```bash
bash scripts/harness_check.sh
git add ... && git commit ...
```

### Git Status

```text
e4fe4ca chore: upgrade SDK to 36, add Gradle wrapper, update AGENTS.md
```

### Progress Report

```text
已完成：基础设施提交
本次完成：SDK 升级 + Gradle wrapper + AGENTS.md
总体进度：0%（0.12.16 阶段刚开始）
剩余：6 步骤
验证：通过
下一步：开始实现终端命令补全
```

## 2026-06-22 — Cleanup, Dedup, AI Tab Removal

### Summary

Completed a wide cleanup sweep: design system refactoring (Phase 1–4), SSH page rewrite, bloat removal (opencode subsystem, CameraBridge, AI Agent tab), menu deduplication, session tab long-press fix, and AGENTS.md hard lessons.

### Files Changed

- `DesignSystem.kt` — Matrix-green ACCENT, roundedBackground, showAsDialog
- `SshBookmarksPage.kt` — full rewrite using design system
- `EmbeddedShellPages.kt` — showSshBookmarks→showAsDialog, agentBar CLI buttons, deduplicated menus, long-press fix
- `ShellActivity.kt` — removed EmbeddedAIPage from pages, TAB_AI constant deleted, constants shifted, bottomLabels, commandPalette reindexed
- `AppNav.kt` — removed "AI代理" from bottom nav
- `AIAgentActivity.kt` — "查看日志" redirected to AppNav.openTerminal
- `ServerCenterActivity.kt` — "任务中心" redirected to AppNav.openTerminal
- `SettingsActivity.kt` — navItems and "AI与服务器" menu entry redirected
- `KeepAliveService.kt` — OpencodeManager removed
- `ShizukuBridgeService.kt` — CameraBridgeActivity→error response
- `TerminalShellAssets.kt` — camera scripts removed
- `AndroidManifest.xml` — CameraBridgeActivity, FileProvider removed
- `AGENTS.md` — Hard Lessons section added

### Files Deleted

- `app/src/main/java/com/aidev/terminal/opencode/` (5 files, 1347 lines)
- `app/src/main/java/com/aidev/terminal/CameraBridgeActivity.kt` (203 lines)
- `app/src/main/res/xml/file_paths.xml`
- `app/src/main/java/com/aidev/terminal/EmbeddedAIPage.kt` (196 lines)

### Validation

```bash
/usr/local/bin/wrap-android-native.sh
./gradlew assembleDebug --no-daemon
```

Result: BUILD SUCCESSFUL — only pre-existing deprecation warnings.

### Commit

Uncommitted. User has not approved commit.

### Progress Report

```text
已完成：0.15.0 — cleanup-dedup-ai-tab-removal
本次完成：设计系统 + 清理 bloats + AI 标签页删除 + 菜单去重 + 长按修复
总体进度：100%
剩余：0 阶段
验证：通过
下一步：无活跃计划。可选：阴影系统 / ShellPages 小迁移
```

## 2026-06-22 — 0.16.0 Terminal Bug Audit Round 1

### Summary

Audited terminal module for bugs. Found 18 issues. Fixed 15 in Round 1. BUG 11 deferred to Round 2 (needs real device IME testing). BUG 14 skipped (low probability, has fallback). BUG 15 skipped (requires Activity base class change).

### Files Changed

- `AIDevApp.kt` — currentActivity → WeakReference (BUG 17)
- `ShizukuBridgeService.kt` — atomicWriteText helper for batch writes (BUG 7)
- `SystemMonitorPage.kt` — isVisible fix (BUG 9), BatteryManager API (BUG 10)
- `TaskManagerHelper.kt` — ProcessBuilder + drain streams before waitFor (BUG 1)
- `ShellActivity.kt` — onConfigurationChanged skin refresh (BUG 16), KeepAliveService logging (BUG 18)
- `ShellEnhancementsPage.kt` — async reload, thread {} IO wrappers (BUG 3)
- `EmbeddedShellPages.kt` — PWD cache (BUG 2), stale activity guard (BUG 4), session cap 8 + dialog (BUG 5), null activity guard (BUG 6), shared Handler (BUG 8), refreshKeyboard in-place (BUG 12), dead code removal (BUG 13)

### Validation

```bash
/usr/local/bin/wrap-android-native.sh
./gradlew assembleDebug --no-daemon
```

Result: BUILD SUCCESSFUL — only pre-existing deprecation warnings.

### Commit

Uncommitted. User has not approved commit.

### Progress Report

```text
已完成：0.16.0 — terminal-bug-audit-round1
本次完成：修复 15 个 bug（进程死锁、主线程 IO、引用泄漏、session 上限、Handler 泄漏等）
总体进度：100%
剩余：1（BUG 11 延期到第二轮）
验证：通过
下一步：第二轮测试 BUG 11（真机 IME 兼容性）
```

## 2026-06-22 — 0.16.1 Terminal Bug Audit Round 2

### Summary

Fixed 6 bugs (thread safety, cleanup, null safety, lifecycle, IME deadlock). Added `completionBarView = this` (small visual fix). Reverted `clearProxyText()` `setText("")` to `text?.clear()` after discovering Binder deadlock in TUI mode.

### Regression Discovered

`clearProxyText()` `setText("")` inside `post {}` Runnable caused Binder deadlock:
- `setText("")` → `TextView.setText()` → `checkForRelayout()` + `Editor.afterTextChanged()` → `IMM.updateSelection()` → Binder callback to IME
- Concurrent `sendKeyEvent(ENTER)` → `tuiKeyHandler` → `session.write("\r")` from IME Binder thread
- Binder thread pool starvation → main thread + IME frozen
- Reverted to `text?.clear()` (in-place `Editable` clear, no `TextView.setText()` pipeline)

### Files Changed

- `DesignSystem.kt` — `pulse()` null-safe access (fix 5)
- `ShellActivity.kt` — `onDestroy()` page iteration (fix 1B)
- `EmbeddedShellPages.kt` — SessionClient Handler.post wrap (fix 2), removeCallbacks cleanup (fix 3), clearProxyText revert (fix 8R), completionBarView = this (small fix)
- `AGENTS.md` — Hard Lesson #4: IME proxy EditText setText("") Binder deadlock rule

### Validation

```bash
/usr/local/bin/wrap-android-native.sh
./gradlew assembleDebug --no-daemon
```

Result: BUILD SUCCESSFUL — only pre-existing deprecation warnings.

### Commit

Uncommitted. User has not approved commit.

### Progress Report

```text
已完成：0.16.1 — terminal-bug-audit-round2
本次完成：6 个 Round 2 修复 + 1 个回归回退（setText Binder 死锁）
总体进度：100%
剩余：0 阶段
验证：通过
下一步：无活跃计划。可选：阴影系统 / GradientDrawable 迁移
```

## 2026-06-22 — IME Keyboard Overlap Fix (HyperOS)

### Summary

Extending keyboard was covered by IME. Tried 3 approaches:

| Approach | Result |
|----------|--------|
| **A**: `ADJUST_RESIZE`, no manual padding | IME 遮挡拓展键盘 |
| **B**: `ADJUST_NOTHING` + pageRoot IME padding | 空隙（未扣 bottomNavView + navBar） |
| **C**: `ADJUST_NOTHING` + `contentHost` IME padding `max(0, imeHeight - sysBarsBottom - bottomNavHeight)` | **成功** |

Root cause: `contentHost` sits above `bottomNavView` + `sysNavBar` in layout. Raw `Type.ime()` padding pushes content up by IME height only, but the content also needs to clear the nav bar + bottom tab bar below `contentHost`. Formula: `extra = imeHeight - sysBarsBottom - bottomNavHeight`.

### Files Changed

- `ShellActivity.kt` — IME insets listener on `navHost` now sets `contentHost` padding with deduction calculation
- `AGENTS.md` — Hard Lesson #5: IME manual padding deduction rule

### Validation

```bash
/usr/local/bin/wrap-android-native.sh
./gradlew assembleDebug --no-daemon
```

Result: BUILD SUCCESSFUL — only pre-existing deprecation warnings.

### Commit

Uncommitted. User has not approved commit.

### Hard Lessons Added

- #4: IME proxy EditText `setText("")` Binder deadlock
- #5: IME manual padding must account for all layers between padded view and window bottom

### Progress Report

```text
已完成：IME 键盘遮挡修复
本次完成：ADJUST_NOTHING + contentHost padding 扣减法（HyperOS）
总体进度：100%
剩余：0 阶段
验证：通过
下一步：无活跃计划。可选：阴影系统 / GradientDrawable 迁移
```
