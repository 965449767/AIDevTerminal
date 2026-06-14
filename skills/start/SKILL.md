---
name: start
description: Recover project context at the beginning of a new agent session.
---

# Start Skill

## Goal

Recover project context before making changes.

## Rules

- Do not edit code during `/start`.
- Read required files first.
- Output a short Session Briefing.
- If files are missing, run or recommend `bash scripts/harness_check.sh`.

## Steps

1. Read `AGENTS.md`.
2. Read `current-task.md`.
3. Read `.harness/session-state.json`.
4. Read `.harness/session-log.md`.
5. Read `docs/verification.md`.
6. Read `docs/decisions.md`.
7. Read `docs/error-journal.md`.
8. Output Session Briefing.

## Output Format

```text
Session Briefing
Current Goal:
Current Status:
Current Phase:
Progress:
Next 3 Steps:
Validation:
Known Risks:
```
