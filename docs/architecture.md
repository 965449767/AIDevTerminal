# Architecture Notes

## Repository Type

AIDev Terminal is an Android application written mainly in Kotlin.

## Main Directories

- `app/src/main/java/com/aidev/terminal`: Android activities, shell UI, terminal pages, settings, tasks, server center, and app navigation.
- `app/src/main/java/com/aidev/terminal/opencode`: OpenCode HTTP/SSE client and native panel integration.
- `app/src/main/assets/proot-libs`: PRoot support libraries copied into app private storage at runtime.
- `app/src/main/jniLibs/arm64-v8a`: native PRoot binaries bundled into the APK.
- `app/src/main/res`: Android resources.
- `app/build.gradle.kts`: Android app build configuration.

## Current Entry Points

- Launcher activity: `ShellActivity`
- Main terminal page: `EmbeddedTerminalPage` inside `EmbeddedShellPages.kt`
- App navigation: `AppNav`
- Keep-alive service: `KeepAliveService`
- Settings: `SettingsActivity`

## Important Runtime Flow

On app launch, `ShellActivity` selects the embedded terminal page and dispatches `aidev-auto-bootstrap`.

The terminal session loads `.aidevrc`, which defines shell functions for:

- `ubuntu`
- `install-ubuntu`
- `aidev-auto-bootstrap`

These functions call `dev-env/bin/aidev-ubuntu-core` through `/system/bin/sh` because Android app private directories cannot be executed directly.

Ubuntu runs through bundled PRoot with `--link2symlink`.

## Current Architecture Notes

- `ShellActivity` is now the launcher and terminal entry.
- Old `MainActivity` terminal code still exists but is no longer registered in `AndroidManifest.xml`.
- `EmbeddedShellPages.kt` is too large and should be split later.
- Ubuntu readiness is based on `home/ubuntu-rootfs/.aidev-rootfs-ready`.

## Notes for Future Agents

- Do not reintroduce direct `MainActivity` terminal navigation.
- Do not treat `etc/os-release` alone as proof that Ubuntu is ready.
- Do not execute files directly from app private storage; use `/system/bin/sh <script>`.
- Inspect nearby code before moving terminal logic.
