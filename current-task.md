# Current Task

## Goal

Complete design system refactoring (Phase 1–4) — update DesignSystem.kt palette, rewrite SSH page, migrate to Dialog pattern.

## Current Status

Phase 1–4 complete. DesignSystem.kt updated with MD3-inspired Matrix-green palette, `surfaceHighlight`, `roundedBackground()`, `showAsDialog()`. SshBookmarksPage.kt rewritten to consume design system, close button uses `dismiss` callback. EmbeddedShellPages.kt `showSshBookmarks()` uses `showAsDialog()`. Debug APK builds successfully.

## Scope

- DesignTokens: radius MD 8→12dp, LG 12→16dp; ACCENT teal→Matrix green; added SPACE_32
- DarkTheme/LightTheme palette: updated all color values, added `surfaceHighlight`
- `roundedBackground(color, radius)` helper on `AIDevUi`
- `showAsDialog(view, onDismiss)` wrapper for Theme_Translucent_NoTitleBar pattern
- SshBookmarksPage: all hardcoded colors → `ui.palette.*`; close button uses `dismiss?.invoke()` callback
- EmbeddedShellPages: `showSshBookmarks()` → `ui.showAsDialog()` + `page.dismiss = { dialog.dismiss() }`

## Next Steps

1. Future: Migrate remaining ShellPages to design system (ShellEnhancementsPage, SystemMonitorPage, etc.)
2. Future: Add elevation/shadow system to DesignTokens

## Changed Files

- `DesignSystem.kt` — palette, tokens, helpers updated
- `SshBookmarksPage.kt` — full rewrite
- `EmbeddedShellPages.kt` — `showSshBookmarks()` updated
