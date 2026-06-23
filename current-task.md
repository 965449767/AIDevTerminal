# Current Task: Path Config + Shizuku APK Install — Complete

## Summary
Added centralized path management, path settings UI, and Shizuku-based silent APK installation with diagnostic mode.

## Changes Made
### Unified Path Management
- **PathConfig.kt** — Centralized path object with 6 system paths (3 configurable via prefs)
- **PreferencesManager.kt** — Added 3 path preferences: `backupDir`, `projectsDirRel`, `externalAidevDir`
- **EmbeddedSettingsPage.kt** — Added "路径设置" row with path menu:
  - Editable paths (backup dir, projects dir, external AIDev dir) with edit dialog + reset-to-default
  - Read-only paths (AIDev Home, Ubuntu Rootfs, tasks dir) in gray/muted style with copy-to-clipboard
  - Each path has a Chinese description of its purpose

### Shizuku-based APK Installation
- **ShellResult.kt** — Data class with stdout/stderr/exitCode for command results
- **ShizukuLogcat.kt**:
  - Added `ShizukuState` sealed class (NotInstalled/NotRunning/NotAuthorized/Ready) for tiered availability checks
  - Added `checkState()` — progressive status check (install → running → authorized)
  - Added `executeCommand()` — suspend function with 60s timeout, returns ShellResult
  - Added `executeFireAndForget()` — spawns process without waiting (instant return)
  - Added `pmInstallErrorHint()` — maps PM install exit codes/errors to Chinese hints
- **EmbeddedFilesPage.kt**:
  - Rewrote "系统 · 安装 APK" with 4-stage flow: path validation → Shizuku state check → install dialog → execution
  - Added "诊断安装" option that shows raw stdout/stderr/exitCode
  - Uses pipe method (`cat | pm install -S`) to bypass SELinux FUSE read restrictions
  - Fire-and-forget mode for normal install (matches MT Manager experience)
- **EmbeddedSettingsPage.kt** — Added "▶ 测试命令执行" button in Shizuku status dialog

### Backup & Repository Path Migration
- **BackupRestorePage.kt** — Backup path display uses `PathConfig.backupDir()`
- **BackupRepositoryImpl.kt** — Accepts Context parameter, uses PathConfig for external AIDev dir

## Verification
- APK builds successfully (6950KB)
- Shizuku test (`echo SHIZUKU_TEST_OK`) verified working
- Silent APK install verified working with `AIDE_3.2.210316.apk`
- All imports verified, no compilation errors
