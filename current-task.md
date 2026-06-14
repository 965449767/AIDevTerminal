# Current Task

## Goal

Complete `0.13.2-a` terminal structure cleanup.

## Current Status

`0.13.2-a` is complete.

## Scope

Allowed:

- extract Ubuntu script generation from `EmbeddedShellPages.kt`
- create `UbuntuBootstrapScripts.kt`
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
- `app/src/main/java/com/aidev/terminal/UbuntuBootstrapScripts.kt`
- `.harness/session-state.json`
- `.harness/session-log.md`

## Plan

1. Inspect `EmbeddedShellPages.kt`.
2. Move Ubuntu shell script generation into `UbuntuBootstrapScripts.kt`.
3. Keep call sites behavior-compatible.
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

- `UbuntuBootstrapScripts.kt` contains Ubuntu script generation.
- `EmbeddedShellPages.kt` no longer owns large Ubuntu script strings.
- Function calls remain behavior-compatible.
- `.harness/session-state.json` is valid JSON.
- `scripts/harness_check.sh` passes.
- Android debug build passes.

## Risks

- This is a code move; runtime behavior still needs device smoke testing in a later APK test.
- `EmbeddedShellPages.kt` remains large and still needs further split.

## Next 3 Steps

1. Review `0.13.2-a` diff.
2. Discuss `0.13.2-b` before implementation.
3. Ask before any commit or tag.

## Last Updated

2026-06-14
