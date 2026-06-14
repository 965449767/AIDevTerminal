#!/usr/bin/env bash
set -euo pipefail

required_files=(
  "AGENTS.md"
  "current-task.md"
  "docs/architecture.md"
  "docs/verification.md"
  "docs/coding-guidelines.md"
  "docs/decisions.md"
  "docs/error-journal.md"
  ".harness/session-state.json"
  ".harness/session-log.md"
  ".harness/progress-map.md"
  ".harness/command-history.md"
  "skills/start/SKILL.md"
  "skills/plan/SKILL.md"
  "skills/review/SKILL.md"
  "skills/commit/SKILL.md"
  "skills/handoff/SKILL.md"
  "scripts/harness_check.sh"
  "scripts/safe_bash_guard.sh"
)

missing=0

for file in "${required_files[@]}"; do
  if [[ ! -f "$file" ]]; then
    echo "MISSING: $file"
    missing=1
  fi
done

if [[ -f ".harness/session-state.json" ]]; then
  if command -v python3 >/dev/null 2>&1; then
    python3 -m json.tool .harness/session-state.json >/dev/null
  elif command -v python >/dev/null 2>&1; then
    python -m json.tool .harness/session-state.json >/dev/null
  else
    echo "WARN: python not found; skipping JSON validation"
  fi
fi

if [[ "$missing" -ne 0 ]]; then
  echo "Harness check failed."
  exit 1
fi

echo "Harness check passed."
