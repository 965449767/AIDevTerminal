---
name: handoff
description: Preserve session state so the next agent session can resume without chat history.
---

# Handoff Skill

## Goal

Close the current session by writing durable state.

## Rules

- Update state files.
- Record what changed.
- Record validation status.
- Record next steps.
- Do not claim completion without validation evidence.

## Steps

1. Summarize completed work.
2. Summarize changed files.
3. Record validation commands and results.
4. Update `current-task.md`.
5. Update `.harness/session-state.json`.
6. Append `.harness/session-log.md`.
7. Update `docs/decisions.md` or `docs/error-journal.md` if needed.

## Output Format

```text
Handoff Summary
Completed:
Changed Files:
Validation:
Known Issues:
Next 3 Steps:
Resume From:
```
