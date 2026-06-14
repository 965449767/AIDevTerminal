# Git Workflow

## Current Status

Git was initialized after explicit user approval.

Current baseline branch:

```text
main
```

Local commit identity:

```text
AIDev Harness <aidev-harness@example.local>
```

Git commits are allowed automatically when a phase is complete, validation passes, and the change is worth preserving.

Do not run `git tag`, `git reset`, `git clean`, `git push`, or any remote operation unless the user explicitly confirms.

## Purpose

Git is the project backup and rollback boundary.

Harness files track session state, but Git should track recoverable code history once the repository is initialized.

## Before Each Phase

Run:

```sh
git status --short
```

If the directory is not a Git repository, report that and continue only with file-level changes approved by the user.

## After Each Phase

Run:

```sh
git status --short
git diff --stat
```

If Git is unavailable, record the limitation in `.harness/session-log.md`.

## Commit Policy

Commit automatically when:

- the phase is complete
- validation passed
- the working tree contains scoped changes
- the commit message can be stated clearly

Recommended message style:

```text
type(scope): subject
```

Examples:

```text
docs(harness): add project execution protocol
docs(android): add hyperos target guidelines
fix(ubuntu): handle android tar hardlink failures
```

## Tag Policy

Do not tag automatically.

Important completed versions may be tagged after user approval:

```sh
git tag v0.13.1-android-guidelines
```

## Rollback Policy

Use the least destructive rollback method:

| Situation | Preferred Action |
|---|---|
| Small mistake before commit | apply a corrective patch |
| Wrong file change before commit | ask before `git restore` |
| Committed bad change | prefer `git revert` |
| Severe local corruption | ask before any reset |

Never run these without explicit confirmation:

```sh
git reset --hard
git clean -fd
git push --force
git tag
```

## Backup Recommendation

Before large refactors, ask the user whether to initialize Git or create a manual backup copy.

Initial Git setup was approved by the user. For a new clone or replacement workspace, use only after approval:

```sh
git init
git add .
git commit -m "chore: initial project snapshot"
```

If a remote repository exists, the user should provide the remote URL before any push.
