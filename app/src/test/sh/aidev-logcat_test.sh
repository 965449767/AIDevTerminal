# Test: aidev-logcat
SCRIPT="$ASSETS_DIR/aidev-logcat.sh"

# help flag
output=$(bash "$SCRIPT" --help 2>&1 || true)
assert_contains "$output" "用法" "help should show usage"
assert_contains "$output" "lines" "help should mention --lines"
assert_contains "$output" "follow" "help should mention --follow"
assert_contains "$output" "watch-crash" "help should mention --watch-crash"
