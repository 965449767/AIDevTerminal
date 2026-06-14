# Coding Guidelines

## General Rules

- Keep changes small and scoped.
- Prefer existing project style over new style.
- Use Kotlin for new Android code.
- Treat Java as legacy maintenance only.
- Do not introduce new dependencies without a clear reason.
- Do not perform unrelated refactors.
- Update validation notes when behavior changes.

## Android Rules

- Current UI uses native Android Views. Do not force a Compose migration during unrelated work.
- Keep `ShellActivity` as the launcher and terminal entry.
- Keep terminal command entry paths routed through `AppNav.openTerminal` or `ShellHost.openTerminal`.
- Do not execute app-private files directly; use `/system/bin/sh <script>`.
- Use `filesDir/home` as the app-owned runtime home directory.
- Use `.aidev-rootfs-ready` as the Ubuntu readiness marker.
- Target ROM-specific guidance is Xiaomi HyperOS only unless the user requests more vendors.
- Do not add camera, microphone, contacts, SMS, or location permissions without an explicit feature requirement.

## Terminal Rules

- `ubuntu`, `install-ubuntu`, and `aidev-auto-bootstrap` must remain shell functions loaded through `.aidevrc`.
- Do not rely on functions sourced before `exec sh -i`; use `ENV`.
- Android `tar` may fail on hardlinks. Preserve the symlink fallback logic.
- Keep PRoot startup compatible with bundled native libraries.

## Agent Notes

- Inspect relevant files before editing.
- Match naming, formatting, and error-handling style already present.
- Record non-obvious failures in `docs/error-journal.md`.
- Record stable architecture decisions in `docs/decisions.md`.
- Follow `docs/git-workflow.md` before any commit, tag, reset, cleanup, or backup operation.
