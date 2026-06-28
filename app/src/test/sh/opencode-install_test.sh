# Test: opencode-install
SCRIPT="$ASSETS_DIR/opencode-install.sh"

# run it (read-only check)
output=$(bash "$SCRIPT" 2>&1 || true)
assert_contains "$output" "AIDev OpenCode" "should show header"
