---
name: commit
description: Prepare a validated commit summary without committing unless explicitly requested.
---

# Commit Skill

## Goal

Prepare changes for commit after validation.

## Rules

- Do not run `git commit` unless the user explicitly asks.
- Inspect changed files before preparing the message.
- Run or confirm validation commands.
- Update `.harness/session-log.md`.

## Steps

1. Inspect changed files.
2. Review diff summary.
3. Run relevant validation.
4. Record result.
5. Prepare commit message.

## Output Format

```text
Commit Preparation
Files Changed:
Validation:
Commit Message:
Risks:
```
