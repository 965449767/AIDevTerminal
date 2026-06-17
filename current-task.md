# Current Task

## Goal

Complete `0.12.16-terminal-completion-lite`.

## Current Status

`0.12.16` is in progress.

## Scope

Allowed:

- add terminal command completion suggestion bar
- track lightweight command input buffer
- add built-in command completions
- add recent command history completions
- keep terminal enhancement roadmap focused on performance, diagnostics, sessions, clipboard, virtual keys, and completion

Not allowed in this phase:

- dependency changes
- PRoot launch behavior changes
- Ubuntu rootfs reinstall logic
- Git tag, reset, clean, push, or remote configuration

## Relevant Files

- `app/src/main/java/com/aidev/terminal/EmbeddedShellPages.kt`
- `app/src/main/java/com/aidev/terminal/TerminalShellAssets.kt`
- `app/build.gradle.kts`

## Plan

1. Add suggestion bar between terminal content and virtual keyboard.
2. Add command completion model and built-in command dictionary.
3. Track virtual key, paste, menu, and common keyboard input.
4. Add recent command history suggestions.
5. Build and export debug APK.
6. Auto commit after validation.

## Validation Commands

```bash
bash scripts/harness_check.sh
git status --short
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 /data/user/work/gradle/gradle-8.14.5/bin/gradle -p "/workspace/AIDevTerminal" :app:assembleDebug --no-daemon
```

## Acceptance Criteria

- suggestion bar appears above virtual keyboard
- click suggestion completes text without Enter
- long press suggestion executes the command
- Enter records command history
- `scripts/harness_check.sh` passes.
- Android debug build passes.

## Risks

- system IME composition may not be perfectly tracked in this first version
- cursor-middle editing is approximate
- path completion is intentionally deferred

## Next 3 Steps

1. Test command suggestion click/long-press behavior.
2. Decide whether to add path completion.
3. Continue with clipboard/session enhancements.

## Side Notes

- `AIDevBottomSheet.kt` and `MenuBottomSheet.kt` created (2026-06-17).
- `BackupRestorePage.kt` removed due to prior corruption; backup/restore menu temporarily toasts "开发中".
- `MenuItem` is now nested inside `MenuBottomSheet`; all call sites updated.

## Last Updated

2026-06-14
