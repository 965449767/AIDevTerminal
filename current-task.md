# Current Task

## Goal
Phase 4: Codebase cleanup — migrate legacy `Handler.post`/`Thread.runOnUiThread` → coroutines, fix deprecation warnings.

## Current Status

| Phase | Status |
|-------|--------|
| Phase 1 (Bug fixes, IME, error handler) | ✅ Complete |
| Phase 2 (Architecture optimization) | ✅ Complete |
| Phase 3 (Integration testing & validation) | ✅ Complete |
| Phase 4 (Cleanup: coroutine migration, deprecation fixes) | 🔜 In Progress |

## Phase 4 Progress

### P1: ShellEnhancementsPage — ✅ Complete
- 5× `Handler.post` → `scope.launch(IO){…withContext(Main){…}}`

### P2: Deprecation warnings — ✅ Complete (10 fixes)
- `EmbeddedShellPages.kt`: 4× `displayMetrics.scaledDensity` → `spToPx()` helper
- `AppNav.kt` + `ShellActivity.kt`: 5× `overridePendingTransition` → API 34 guard
- `KeepAliveService.kt`: `WIFI_MODE_FULL_HIGH_PERF` → `FULL_LOW_LATENCY`

### P3: Migrate remaining pages — ✅ Complete (4 pages)

| Page | Migration | Status |
|------|-----------|--------|
| NetworkDiagnosticsPage | 4× `Thread{…runOnUiThread{…}}` → coroutine | ✅ |
| ContainerManagerPage | 1× `Handler.post` → coroutine | ✅ |
| SecurityAuditPage | 1× `Handler.post` → coroutine | ✅ |
| SystemMonitorPage | postDelayed refresh cycle + 1× runOnUiThread → coroutine | ✅ |

### P4 (Optional): Integration tests — ⏳ Pending

## Summary of Changes

All legacy `Thread{… runOnUiThread{…}}` and `Handler.post` patterns in shell pages migrated to:
- `scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)`
- `scope.launch(IO){… withContext(Main){…}}`
- `scope.cancel()` in `onDestroy()`

Build: ✅ `assembleDebug` successful (35 tasks, 4 executed, BUILD SUCCESSFUL)
