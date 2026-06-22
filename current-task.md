# Current Task

## Goal

Round 2 terminal bug fixes: thread safety cleanup, TUI mode regression fix, IME keyboard overlap fix.

## Current Status

All Round 2 bug fixes done. IME keyboard overlap resolved (Plan C: `ADJUST_NOTHING` + `contentHost` inset deduction).

## Scope (Round 2 + IME fix)

| # | File | Fix |
|---|------|-----|
| 2 | `EmbeddedShellPages.kt` | `sessionClient()` UI callbacks wrapped in `Handler(Looper.getMainLooper()).post` |
| 3 | `EmbeddedShellPages.kt` | Removed useless `terminalView?.removeCallbacks(null)` from `onDestroy` |
| 5 | `DesignSystem.kt` | `pulse()` — null-safe `activity.window?.decorView?` |
| 1B | `ShellActivity.kt` | Added `onDestroy()` that iterates pages and calls each `ShellPage.onDestroy()` |
| 8R | `EmbeddedShellPages.kt` | `clearProxyText()` reverted: `setText("")` → `text?.clear()` (Binder deadlock fix) |
| — | `EmbeddedShellPages.kt` | `completionBarView = this` in `completionBar()` |
| IME | `ShellActivity.kt` | `buildShell()`: IME padding on `contentHost` via `navHost` insets listener, formula `max(0, imeHeight - sysBarsBottom - bottomNavHeight)` |

### Key Fix Chain

| Step | Approach | Result |
|------|----------|--------|
| A | `ADJUST_RESIZE`, no manual padding | IME 遮挡拓展键盘 |
| B | `ADJUST_NOTHING` + `pageRoot` IME padding | 空隙（未扣 bottomNavView + navBar） |
| C | `ADJUST_NOTHING` + `contentHost` IME padding 扣减法 | **成功** |

## Deferred

- DesignTokens elevation/shadow system (optional)
- `GradientDrawable` → `roundedBackground()` migration in ShellEnhancementsPage / SystemMonitorPage (low priority)

## Changed Files (this round)

- `DesignSystem.kt` — pulse() null guard
- `ShellActivity.kt` — onDestroy, IME padding on contentHost
- `EmbeddedShellPages.kt` — SessionClient handler, clearProxyText revert, completionBarView, onDestroy cleanup
- `AGENTS.md` — Hard Lesson #4 (setText Binder deadlock) + #5 (IME padding deduction)
