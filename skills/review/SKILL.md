---
name: review
description: Review current changes for correctness, scope control, and validation readiness.
---

# Review Skill

## Goal

Review the current diff before completion.

## Rules

- Review actual changed files.
- Check scope drift.
- Check validation evidence.
- Classify findings as BLOCKER, MAJOR, MINOR, or QUESTION.

## Steps

1. Inspect changed files.
2. Compare against `current-task.md`.
3. Check validation commands.
4. Report findings.

## Output Format

```text
Review Result
BLOCKER:
MAJOR:
MINOR:
QUESTION:
Validation:
Recommended Fix:
```
