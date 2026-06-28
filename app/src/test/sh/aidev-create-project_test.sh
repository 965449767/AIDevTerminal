# Test: aidev-create-android-project
SCRIPT="$ASSETS_DIR/aidev-create-android-project.sh"

# no args -> help
output=$(bash "$SCRIPT" 2>&1 || true)
assert_contains "$output" "用法" "no args should show usage"
assert_contains "$output" "MyApp" "usage should show example"

# invalid package name
output=$(bash "$SCRIPT" MyApp invalid 2>&1 || true)
assert_contains "$output" "错误" "invalid package should error"
assert_contains "$output" "包名格式" "error should mention format"
