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
