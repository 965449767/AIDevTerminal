# Error Journal

Use this file to record repeated failures, non-obvious bugs, and lessons learned.

## 2026-06-14 - Shell functions lost after `exec sh -i`

### Symptom

`ubuntu` returned `inaccessible or not found`.

### Root Cause

The shell loaded functions before `exec sh -i`. The exec replaced the process and lost previously sourced functions.

### Fix

Set `ENV` to `.aidevrc` so the interactive shell loads the functions itself.

### Prevention

Do not source shell functions before replacing the shell process.

### Related Files

- `app/src/main/java/com/aidev/terminal/EmbeddedShellPages.kt`

## 2026-06-14 - Permission denied for app-private scripts

### Symptom

`ubuntu` existed in `dev-env/bin` but direct execution returned `Permission denied`.

### Root Cause

Android app private directories do not allow direct script execution.

### Fix

Define shell functions that call scripts through `/system/bin/sh`.

### Prevention

Never depend on executable bits for scripts under app private storage.

### Related Files

- `app/src/main/java/com/aidev/terminal/EmbeddedShellPages.kt`

## 2026-06-14 - Ubuntu Base hardlink extraction failure

### Symptom

`tar` failed while linking files such as `usr/bin/perl5.38.2` and `usr/bin/perl`.

### Root Cause

Android `tar` and app private storage do not reliably support hardlink creation.

### Fix

Allow extraction to continue when core rootfs files exist, then replace known hardlinks with symlinks.

### Prevention

Preserve PRoot `--link2symlink` and rootfs symlink fallback logic.

### Related Files

- `app/src/main/java/com/aidev/terminal/EmbeddedShellPages.kt`

## 2026-06-14 - Multiple terminal entries caused inconsistent behavior

### Symptom

Different pages could enter different terminal implementations.

### Root Cause

The project retained both old `MainActivity` terminal navigation and the newer embedded terminal.

### Fix

Route terminal navigation to `ShellActivity` and unregister `MainActivity` from the manifest.

### Prevention

Use `AppNav.openTerminal` or `ShellHost.openTerminal` for terminal navigation.

### Related Files

- `app/src/main/java/com/aidev/terminal/AppNav.kt`
- `app/src/main/AndroidManifest.xml`
