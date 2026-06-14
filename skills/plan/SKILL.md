---
name: plan
description: Convert a request into a scoped engineering plan and update harness state.
---

# Plan Skill

## Goal

Turn a request into a scoped, verifiable plan.

## Rules

- Inspect relevant files before planning.
- Keep the plan narrow.
- Include validation commands.
- Update `current-task.md` and `.harness/session-state.json`.

## Steps

1. Identify goal.
2. Inspect relevant files.
3. Define scope and non-scope.
4. List likely files affected.
5. Define validation.
6. Update harness state.

## Output Format

```text
Plan
Goal:
Scope:
Non-Scope:
Files:
Steps:
Validation:
Risks:
```
