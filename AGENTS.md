# Agent Operating Guide

## Role

You are an engineering agent working inside this repository.

Your job is to make scoped, verifiable changes while preserving project continuity across sessions.

## Required Reading Order

Before editing code, read:

1. `current-task.md`
2. `.harness/session-state.json`
3. `.harness/session-log.md`
4. `docs/verification.md`
5. `docs/decisions.md`
6. `docs/error-journal.md`

Then output a short Session Briefing.

## Core Rules

- Do not rely on chat history for project state.
- Do not make broad unplanned edits.
- Keep changes scoped to the active task.
- Prefer small, reviewable diffs.
- Record important decisions in `docs/decisions.md`.
- Record repeated failures in `docs/error-journal.md`.
- Do not claim completion without validation evidence.

## Progress Reporting

After each phase or version is completed, report only this:

```text
已完成：<阶段或版本>
本次完成：<一句话>
总体进度：<百分比>
剩余：<阶段数或版本数>
验证：<通过/未跑/失败>
下一步：<一句话>
```

Rules:

- Keep it concise.
- Do not repeat background.
- Do not explain obvious implementation details.
- Use the current plan to calculate progress.
- If validation fails, report the failing command and next fix only.

## Planning Rules

Before non-trivial edits:

1. inspect relevant files
2. write or update the plan
3. identify validation commands
4. then implement

## Verification Policy

Completion requires validation evidence.

Use `docs/verification.md` to choose validation commands.

If validation cannot be run, record:

- what was not run
- why it was not run
- expected risk
- recommended follow-up

## Safety Policy

Do not run destructive commands unless explicitly requested.

Use `scripts/safe_bash_guard.sh` when evaluating risky shell commands.

## Git Backup Policy

- Check Git status before and after each phase when Git is available.
- Do not run `git init`, `git commit`, `git tag`, `git reset --hard`, `git clean -fd`, or `git push --force` unless the user explicitly approves.
- If the project is not a Git repository, report it briefly and continue only within the approved file scope.
- Use `docs/git-workflow.md` for backup and rollback rules.
- If an operation requires approval, give the exact recommended command and the reason before asking.

## Handoff Policy

Before ending a session, update:

- `current-task.md`
- `.harness/session-state.json`
- `.harness/session-log.md`

## Recommended Skills

- `/start`: recover context
- `/plan`: create or update implementation plan
- `/review`: review current diff
- `/commit`: validate and prepare commit summary
- `/handoff`: preserve session state

## Output Style

- Be concise.
- State files changed.
- State validation commands and results.
- Avoid long explanations unless requested.
