#!/usr/bin/env bash
set -euo pipefail

cmd="${*:-}"

if [[ -z "$cmd" ]]; then
  echo "Usage: $0 <command string>"
  exit 2
fi

dangerous_patterns=(
  "rm -rf /"
  "rm -rf ."
  "git reset --hard"
  "git clean -fd"
  "git push --force"
  "drop database"
  "truncate table"
  "supabase db reset"
  "prisma migrate reset"
)

lower_cmd="$(printf '%s' "$cmd" | tr '[:upper:]' '[:lower:]')"

for pattern in "${dangerous_patterns[@]}"; do
  if [[ "$lower_cmd" == *"$pattern"* ]]; then
    echo "BLOCKED: dangerous command pattern detected: $pattern"
    echo "Human confirmation is required before running this command."
    exit 1
  fi
done

echo "Command passed safe_bash_guard."
