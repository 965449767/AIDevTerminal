# Verification Guide

## Purpose

This file defines how agents verify AIDev Terminal changes before reporting completion.

## Baseline Harness Check

Run after harness or documentation changes:

```bash
bash scripts/harness_check.sh
```

## Android Debug Build

Run after Kotlin, Android resource, manifest, Gradle, or terminal behavior changes:

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 /data/user/work/gradle/gradle-8.14.5/bin/gradle -p "/workspace/AIDevTerminal" :app:assembleDebug --no-daemon
```

If the build environment is missing, restore it with:

```bash
bash /workspace/setup-aidev-terminal-build-env.sh --build
```

## APK Export

After a successful debug build:

```bash
cp "/workspace/AIDevTerminal/app/build/outputs/apk/debug/app-debug.apk" "/workspace/aidev-terminal-debug.apk"
```

## Change-Type Validation

| Change Type | Required Validation |
|---|---|
| Harness files | `bash scripts/harness_check.sh` |
| Documentation only | `bash scripts/harness_check.sh` |
| Git workflow docs | `bash scripts/harness_check.sh` and `git status --short` if Git exists |
| Kotlin code | Android debug build |
| Manifest/resources | Android debug build |
| Ubuntu bootstrap scripts | Android debug build plus device smoke test |
| Navigation changes | Android debug build plus manual entry-path check |
| HyperOS keep-alive behavior | Android debug build plus Xiaomi device smoke test |

## Manual Device Smoke Tests

For Ubuntu terminal changes on Xiaomi 14 Pro / HyperOS 3.0 / Android 16.0, install the APK and run:

```sh
type ubuntu
ubuntu
cat /etc/os-release
pwd
```

For clean bootstrap testing:

```sh
install-ubuntu --clean
ubuntu
cat /etc/os-release
```

## If Validation Cannot Be Run

Record in `.harness/session-log.md`:

- command not run
- reason
- risk
- recommended follow-up

Do not claim completion without validation evidence.

## Git Validation

Before and after each phase, run if Git is available:

```sh
git status --short
git diff --stat
```

If Git is not initialized, record:

```text
Git unavailable: not a repository
```

Do not initialize Git or create commits without explicit user approval.
