# Current Task

## Goal

Complete `0.12.17-audit-fixes` — fix valid issues from project audit report.

## Current Status

In progress.

## Scope — This Phase

- screen rotation handling (`configChanges` + `onConfigurationChanged`)
- clipboard enhancement (`paste()` + terminal integration)

## Not Allowed in This Phase

- dependency changes (except clipboard/SSH)
- PRoot launch behavior changes
- Ubuntu rootfs reinstall logic
- Git tag, reset, clean, push, or remote configuration
- SSH client implementation (deferred)
- VT terminal emulation investigation (deferred)

## Plan

| Step | Description | Files |
|------|-------------|-------|
| 1 | Screen rotation: add `configChanges` to ShellActivity | `AndroidManifest.xml` |
| 2 | Screen rotation: add `onConfigurationChanged` | `ShellActivity.kt` |
| 3 | Clipboard: add `paste()` + `listen()` | `ClipboardHelper.kt` |
| 4 | Clipboard: integrate paste into terminal UX | `EmbeddedShellPages.kt` |
| 5 | Build and export debug APK | — |
| 6 | Auto commit after validation | — |

## Validation Commands

```bash
bash scripts/harness_check.sh
/usr/local/bin/wrap-android-native.sh
./gradlew assembleDebug
```

## Acceptance Criteria

- app survives screen rotation without Activity recreation
- terminal view resizes correctly on rotation
- clipboard: long-press terminal → "粘贴" reads from system clipboard
- `scripts/harness_check.sh` passes
- Android debug build passes

## Future Roadmap (Deferred from Audit)

| Feature | Priority | Estimate | Dependencies |
|---------|----------|----------|-------------|
| SSH client (JSch/sshj) | High | 600-800 lines | New Gradle dependency |
| Clipboard listen auto-paste | Low | 30 lines | None |
| VT terminal emulation audit | Low | 1h research | termux-terminal-view |
| CameraBridgeActivity → QR lib | Low | 200 lines | New dependency |
| SecurityAuditPage → Settings sub-page | Low | 50 lines | None |

## Side Notes

- Audit report `AdvTerminal_Audit_Report.md` reviewed 2026-06-22.
- Many audit claims were inaccurate (multi-session, keys, font size already exist).
- Three valid findings addressed in this phase: rotation, clipboard.
- SSH deferred to separate version due to scope.

## Last Updated

2026-06-22
