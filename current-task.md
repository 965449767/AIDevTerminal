# Current Task

## Goal

Complete `0.13.2-c` terminal structure cleanup.

## Current Status

`0.13.2-c` is complete.

## Scope

Allowed:

- split embedded non-terminal pages from `EmbeddedShellPages.kt`
- create `EmbeddedFilesPage.kt`
- create `EmbeddedTasksPage.kt`
- create `EmbeddedSettingsPage.kt`
- update `.harness/*`

Not allowed in this phase:

- business logic changes
- terminal behavior changes
- Android UI refactors
- dependency changes
- build configuration changes
- UI behavior changes
- Ubuntu bootstrap behavior changes
- version bump
- Git commit, tag, reset, clean, push, or remote configuration

## Relevant Files

- `app/src/main/java/com/aidev/terminal/EmbeddedShellPages.kt`
- `app/src/main/java/com/aidev/terminal/EmbeddedFilesPage.kt`
- `app/src/main/java/com/aidev/terminal/EmbeddedTasksPage.kt`
- `app/src/main/java/com/aidev/terminal/EmbeddedSettingsPage.kt`
- `.harness/session-state.json`
- `.harness/session-log.md`

## Plan

1. Inspect class boundaries in `EmbeddedShellPages.kt`.
2. Move file, task, and settings pages into separate files.
3. Keep class names and package unchanged.
4. Run harness check.
5. Run Android debug build.
6. Record handoff state.

## Validation Commands

```bash
bash scripts/harness_check.sh
git status --short
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 /data/user/work/gradle/gradle-8.14.5/bin/gradle -p "/workspace/AIDevTerminal" :app:assembleDebug --no-daemon
```

## Acceptance Criteria

- `EmbeddedFilesPage.kt` contains file page logic.
- `EmbeddedTasksPage.kt` contains task page logic.
- `EmbeddedSettingsPage.kt` contains settings page logic.
- `EmbeddedShellPages.kt` keeps terminal page logic.
- Function calls remain behavior-compatible.
- `.harness/session-state.json` is valid JSON.
- `scripts/harness_check.sh` passes.
- Android debug build passes.

## Risks

- This is a code move; runtime behavior still needs device smoke testing in a later APK test.
- Some imports may remain broader than ideal and can be cleaned later.

## Next 3 Steps

1. Review `0.13.2` diff.
2. Commit if approved.
3. Build and export APK if requested.

## Last Updated

2026-06-14
