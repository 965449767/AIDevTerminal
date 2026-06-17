# Decision Log

Use this file to record stable project decisions.

## 2026-06-14 - Use ShellActivity as the single terminal entry

### Context

The project previously had both `MainActivity` and `EmbeddedTerminalPage` terminal paths.

### Decision

`ShellActivity` is the launcher and the embedded terminal is the active terminal entry.

### Consequences

- New terminal navigation should route through `AppNav.openTerminal` or `ShellHost.openTerminal`.
- `MainActivity` remains legacy code but is not registered in `AndroidManifest.xml`.
- Future cleanup can remove or archive old terminal code after more testing.

## 2026-06-14 - Use `.aidev-rootfs-ready` as Ubuntu readiness marker

### Context

Partial rootfs extraction can leave `etc/os-release` present even when Ubuntu is not usable.

### Decision

Ubuntu is ready only when `home/ubuntu-rootfs/.aidev-rootfs-ready` exists.

### Consequences

- Status cards and bootstrap checks should use the marker.
- Half-extracted rootfs should not be treated as installed.

## 2026-06-14 - Initialize Standard Project Harness

### Context

Future agent sessions need durable project context, validation rules, and handoff state.

### Decision

Create a Standard Harness with `AGENTS.md`, `docs`, `.harness`, `skills`, and `scripts`.

### Consequences

- Future sessions should start by reading harness state.
- Completion reports must include concise progress percentage.

## 2026-06-14 - Limit ROM adaptation to Xiaomi HyperOS

### Context

The active target device is Xiaomi 14 Pro on HyperOS 3.0 / Android 16.0.

### Decision

ROM-specific rules should focus on Xiaomi HyperOS unless the user requests additional vendors.

### Consequences

- Do not spend implementation effort on Huawei, OPPO, vivo, or other ROMs by default.
- HyperOS keep-alive, battery, notification, and permission behavior is the primary compatibility target.

## 2026-06-14 - Do not perform Git operations without approval

### Context

The project directory is currently not a Git repository, and backup behavior must be explicit.

### Decision

Document Git backup and rollback rules, but do not initialize Git, commit, tag, reset, clean, or push without user approval.

### Consequences

- `docs/git-workflow.md` defines backup and rollback rules.
- If Git is unavailable, record that limitation instead of pretending Git validation passed.

## 2026-06-14 - Initialize Git baseline after approval

### Context

The user approved Git initialization after the project reached a stable documentation and harness baseline.

### Decision

Initialize Git, rename the default branch to `main`, configure local commit identity, and create an initial project snapshot.

### Consequences

- Future phases can use `git status`, `git diff`, and commits for backup and rollback.
- Destructive Git operations still require explicit user approval.
- Remote push remains out of scope until the user provides a remote repository.

## 2026-06-14 - Allow automatic Git commits after validated phases

### Context

The user gave standing approval for automatic Git commits when the agent believes a completed phase should be preserved.

### Decision

Automatically commit scoped phase changes after validation passes.

### Consequences

- The agent no longer needs to ask before normal `git commit`.
- `git tag`, `git reset`, `git clean`, `git push`, and remote configuration still require explicit user approval.

## 2026-06-17 - Introduce AIDevBottomSheet as custom BottomSheet base

### Context

The project does not include AndroidX Material dependency, so standard `BottomSheetDialog` is unavailable. `EmbeddedSettingsPage` previously used inline `MenuItem` data class and `AlertDialog` for menus.

### Decision

Create a pure custom `AIDevBottomSheet` using `Dialog` + `LinearLayout` + `ScrollView`, with drag indicator, title bar, divider, and swipe-to-dismiss. Build `MenuBottomSheet` on top of it with `MenuBottomSheet.MenuItem` nested data class.

### Consequences

- All menu popups in `EmbeddedSettingsPage` now route through `MenuBottomSheet` for consistent UX.
- `MenuItem` is now a nested class of `MenuBottomSheet`; call sites must use `MenuBottomSheet.MenuItem`.
- `BackupRestorePage` was removed due to prior file corruption; backup/restore menu items temporarily toast "开发中" until the page is reimplemented.

## 2026-06-17 - Replace AlertDialog menus with MenuBottomSheet in EmbeddedSettingsPage

### Context

All secondary menus in `EmbeddedSettingsPage` were using `AlertDialog.Builder.setItems()`, which is an older Android pattern and does not support item descriptions.

### Decision

Introduce `MenuBottomSheet` (a custom bottom-sheet Dialog using the project's design tokens) and `MenuItem` data class, and migrate all menu methods (`appearanceMenu`, `terminalMenu`, `devMenu`, `aiServerMenu`, `permissionMenu`, `advancedMenu`, `backupRestoreMenu`) to use it.

### Consequences

- `AlertDialog` is still retained for content dialogs (`detail()`, `sliderDialog()`, `themePresetDialog()`, `backgroundModeDialog()`, `devCheckAndRepair()`, etc.) because they need custom views or single-choice items.
- `MenuBottomSheet` is self-contained and reusable for other pages if needed.
- Each menu item now has a title and an optional description, improving UX.
- Future bottom-sheet enhancements should be made in `MenuBottomSheet.kt` to keep `EmbeddedSettingsPage` focused on business logic.
