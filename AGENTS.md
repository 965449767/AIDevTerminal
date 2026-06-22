# Agent Operating Guide

## Required Reading Order (every session)

1. `/root/.android-env/env-summary.json` — 环境快照
2. `current-task.md`
3. `.harness/session-state.json`
4. `.harness/session-log.md`
5. `docs/verification.md` → `docs/decisions.md` → `docs/error-journal.md`

Then output a short Session Briefing.

## Repo Facts

- **Android app**, Kotlin-first, native Android Views. No Compose, no Hilt, no Retrofit, no multi-module.
- **AGP 8.7.3 / Kotlin 2.0.21 / Gradle 8.14.5** (wrapper present). compileSdk=36, minSdk=26, targetSdk=36, arm64-v8a only.
- **Aliyun Maven mirrors** in `settings.gradle.kts`. 代理配置在全局 `~/.gradle/gradle.properties`（已注释，环境无代理）。
- **Package:** `com.aidev.terminal`, debug `applicationIdSuffix=".dev"`, debug keystore at `app/keystore/debug.keystore`.

## Architecture Constraints

- **ShellActivity** is the launcher and only active terminal entry. `MainActivity` is legacy (unregistered in manifest).
- Terminal navigation: `AppNav.openTerminal` or `ShellHost.openTerminal`.
- Shell functions (`ubuntu`, `install-ubuntu`, `aidev-auto-bootstrap`) are defined in `.aidevrc`, loaded via `ENV` env var at shell startup. Do NOT source before `exec sh -i`.
- App-private scripts must run through `/system/bin/sh <script>` — Android forbids direct execution in app private dirs.
- Ubuntu readiness: check `home/ubuntu-rootfs/.aidev-rootfs-ready`. Do NOT rely on `etc/os-release` alone.
- PRoot uses `--link2symlink`. Android `tar` may fail on hardlinks; preserve symlink fallback logic.
- Shell input tracking: `EmbeddedShellPages.kt` contains `TerminalCompletion` data class and suggestion bar logic.

## Build & Validation

Primary validation order: **harness check → lint/typecheck → debug build**.

```bash
bash scripts/harness_check.sh                                      # harness file integrity
/usr/local/bin/wrap-android-native.sh                              # required before Gradle
./gradlew assembleDebug                                            # debug APK
```

- Debug APK output: `app/build/outputs/apk/debug/app-debug.apk`
- Use `wrap-android-native.sh` before every Gradle invocation (QEMU user-mode ARM64 environment).
- If AAPT2 daemon fails, re-run the wrapper script again. 全局 `~/.gradle/gradle.properties` 已配置 `aapt2DaemonMode=false` + `aapt2FromMavenOverride`。
- 替代命令（自动处理包装、代理检测、APK导出）：`bash /root/.android-env/scripts/build-android.sh`

## Project Structure

| Path | Purpose |
|---|---|
| `app/src/main/java/com/aidev/terminal/` | All app Kotlin sources |
| `app/src/main/java/com/aidev/terminal/opencode/` | OpenCode HTTP/SSE client |
| `app/src/main/assets/proot-libs/` | PRoot support libs (copied at runtime) |
| `app/src/main/jniLibs/arm64-v8a/` | Bundled native PRoot binaries |
| `.harness/` | Agent session state, log, progress |
| `docs/` | Architecture, decisions, errors, git workflow, coding/verification guides |
| `skills/` | Agent skill definitions (start, plan, review, commit, handoff) |

## Key Files

- `EmbeddedShellPages.kt` — main terminal page (~1800 lines), terminal sessions, virtual keys, completion bar.
- `TerminalShellAssets.kt` — shell asset installation, RC file writing, PRoot lib deployment.
- `EmbeddedSettingsPage.kt` — settings page menus, uses `MenuBottomSheet` (custom Dialog-based bottom sheet).
- `AIDevBottomSheet.kt` — base class for custom bottom sheets.
- `MenuBottomSheet.kt` — menu data class and bottom sheet on top of `AIDevBottomSheet`.

## Git Policy

- Automatic commits allowed after validated phases.
- `git tag`, `git reset --hard`, `git clean -fd`, `git push` require explicit user approval.
- Commit style: `type(scope): subject`.

## Progress Reporting

After each phase, report:

```text
已完成：<阶段或版本>
本次完成：<一句话>
总体进度：<百分比>
剩余：<阶段数或版本数>
验证：<通过/未跑/失败>
下一步：<一句话>
```

## Handoff

Before ending session, update:
1. `current-task.md`
2. `.harness/session-state.json`
3. `.harness/session-log.md`

## Hard Lessons (2026-06-22)

### 1. 全局 token 改动必须先 grep 审计所有引用点
改 `RADIUS_MD` (8→12) / `RADIUS_LG` (12→16) 之前没有 grep 外部文件 → `ShellEnhancementsPage`、`SystemMonitorPage` 受影响。
**规则**: 动任何全局 token，必须先 `grep -r TOKEN src/` 列全所有引用，逐一评估 Visual diff。

### 2. UI 改写必须逐行对比新旧视觉效果
SSH 页面重写时直接把硬编码字号映射到 token，没逐行对照：连接名 15→14、端口 13→12、时间戳 12→10，全面变小。
**规则**: 替换硬编码值前先建对照表 `oldValue → newToken`；如果旧值 > token 值，保留旧值或用更大的 token，不准默默缩小。

### 3. Dialog 模式切换必须审查完整窗口生命周期
`AlertDialog` → `Theme_Translucent_NoTitleBar` + `MATCH_PARENT` 没考虑 system bars insets 和 max size。
**规则**: 换 dialog 底座时检查：(1) 是否处理 system window insets？(2) 是否限制最大高度？(3) dismiss 通路是否正常？(4) 极端内容（太长、横屏、分屏）是否溢出？

### 4. IME 代理 EditText 中 `setText("")` 与 Binder 并发死锁
在 `InputConnectionWrapper` 回调路径内，`clearProxyText()` 的 `post {}` 中用 `setText("")` 替代 `text?.clear()` 导致 TUI 模式卡死。`setText("")` 走 `TextView.setText()` 完整管道 → `checkForRelayout()` + `Editor.afterTextChanged()` → `IMM.updateSelection()` → Binder 回调 IME。若 `post {}` 的执行与 `sendKeyEvent(KEYCODE_ENTER)` 的 Binder 同步调用并发，三者（主线程等 IMM、IME Binder 线程等 sendKeyEvent 返回、线程池等待）形成死锁。`text?.clear()` 原地改 `SpannableStringBuilder`，不走 `TextView.setText()`，不触发布局/`restartInput` /额外 Binder 调用。
**规则**: 在 IME 代理 EditText（`InputConnection` 回调路径以及任何 `post {}` Runnable）中清缓冲区，只用 `text?.clear()`（`Editable` 原地改），永不调用 `setText("")`。所有在 `post {}` 中操作 EditText 且可能与 Binder 线程并发的改动，优先用 `getText().clear()` / `getText().delete()` / `getText().replace()` 等原地修改方式。

### 5. IME 手动 padding 必须扣除 `contentHost` 到窗口底的全部间隔
用 `ADJUST_NOTHING` + 手动 IME padding 防键盘遮挡时，padding 不能直接用 `Type.ime().bottom`。布局层次中，`contentHost`（页面容器）下方可能还有底部导航栏 + 系统导航栏，它们已经占了空间。直接对 `contentHost` 设 `imeHeight` padding 会多出一段空隙 = `bottomNavView.height + systemBars.bottom`。
**规则**: 手动 IME padding 的计算公式：
```
extra = max(0, imeHeight - sysBarsBottom - bottomNavHeight)
```
其中 `sysBarsBottom` = `Type.systemBars().bottom`（系统导航栏），`bottomNavHeight` = 应用底部导航栏高度。对 `contentHost`（或 root 容器）设这个 padding，而非对页面根布局设。IME 关闭时 `imeHeight=0` → `extra=0`，padding 自动归零。
