# Test: aidev-shizuku
SCRIPT="$ASSETS_DIR/aidev-shizuku.sh"

# no args -> help
output=$(bash "$SCRIPT" 2>&1 || true)
assert_contains "$output" "exec" "no args should show subcommands"
assert_contains "$output" "install" "should list install"

# unknown subcommand
output=$(bash "$SCRIPT" unknown 2>&1 || true)
assert_contains "$output" "用法" "unknown subcommand should show usage"
