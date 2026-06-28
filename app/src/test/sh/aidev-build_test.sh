# Test: aidev-build
SCRIPT="$ASSETS_DIR/aidev-build.sh"

# help output
output=$(bash "$SCRIPT" --help 2>&1 || true)
assert_contains "$output" "用法" "help should contain 用法"
assert_contains "$output" "full" "help should mention --full"
assert_contains "$output" "clean" "help should mention --clean"

# syntax only for full execution (script may hang on aapt2/proxy checks)
assert_syntax_ok "$SCRIPT"
