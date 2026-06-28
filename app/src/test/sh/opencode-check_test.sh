# Test: opencode-check
SCRIPT="$ASSETS_DIR/opencode-check.sh"

# run it (read-only check)
output=$(bash "$SCRIPT" 2>&1 || true)
assert_contains "$output" "AIDev OpenCode" "should show header"
