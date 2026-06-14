# Android Guidelines

## Target Device

The primary target device is:

```text
Xiaomi 14 Pro
HyperOS 3.0
Android 16.0
arm64-v8a
```

Do not expand ROM-specific work to Huawei, OPPO, vivo, or other vendors unless explicitly requested.

## Current Project Position

AIDev Terminal is currently:

- Kotlin-first Android app
- native Android View UI
- `ShellActivity` launcher
- embedded terminal based on Termux terminal-view
- Ubuntu rootfs through bundled PRoot
- OpenCode integration through CLI and native HTTP/SSE client

Do not force these changes during normal feature work:

- Compose migration
- Hilt migration
- Retrofit migration
- multi-module migration
- mandatory 80% test coverage

These can be revisited only as separate planned phases.

## HyperOS Runtime Rules

HyperOS may restrict long-running terminal work. Terminal reliability depends on:

- foreground service notification
- battery optimization allowlist
- auto-start permission
- recent-task lock when needed
- WakeLock and Wi-Fi Lock during long tasks
- stable notification permission on Android 13+

Settings UI should guide the user to:

- enable auto-start
- set battery mode to unrestricted
- allow notifications
- allow install unknown apps when APK installation is needed
- allow all-files access only when file manager features need shared storage

## Ubuntu and PRoot Rules

Ubuntu behavior is the product goal. Rules:

- `ShellActivity` is the only active terminal entry.
- `ubuntu`, `install-ubuntu`, and `aidev-auto-bootstrap` are shell functions.
- App-private scripts must run through `/system/bin/sh`.
- Ubuntu readiness requires `.aidev-rootfs-ready`.
- Do not rely on `etc/os-release` alone.
- Preserve Android `tar` hardlink fallback.
- Preserve PRoot `--link2symlink`.

## Permission Scope

Keep permissions tied to actual features:

- `INTERNET`: required for Ubuntu downloads, OpenCode, package installs.
- `POST_NOTIFICATIONS`: required for foreground service visibility.
- `FOREGROUND_SERVICE_DATA_SYNC`: required for keep-alive service behavior.
- `WAKE_LOCK`: required for long-running tasks.
- `CHANGE_WIFI_STATE`: required for Wi-Fi Lock behavior.
- `MANAGE_EXTERNAL_STORAGE`: only for broad file manager access.
- `REQUEST_INSTALL_PACKAGES`: only for APK installation flow.

Do not add sensitive permissions such as camera, microphone, contacts, SMS, or location unless a feature explicitly needs them.

## Validation on Xiaomi 14 Pro

After terminal, Ubuntu, keep-alive, or permission changes, test on the target device:

```sh
type ubuntu
ubuntu
cat /etc/os-release
pwd
```

Clean bootstrap test:

```sh
install-ubuntu --clean
ubuntu
cat /etc/os-release
```

Keep-alive check:

```sh
check-keepalive
```

If a command is unavailable, record it in `docs/error-journal.md`.

## 0.13.2 Preparation

Future code cleanup should be planned before implementation:

- split `EmbeddedShellPages.kt`
- extract Ubuntu bootstrap logic
- extract terminal session management
- extract HyperOS permission and keep-alive guidance
- remove or archive legacy `MainActivity` after confirmation
