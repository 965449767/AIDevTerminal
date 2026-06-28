# Test: aidev-apk-info
SCRIPT="$ASSETS_DIR/aidev-apk-info.sh"

# no args -> help
output=$(bash "$SCRIPT" 2>&1 || true)
assert_contains "$output" "用法" "no args should show usage"

# nonexistent file
output=$(bash "$SCRIPT" /nonexistent.apk 2>&1 || true)
assert_contains "$output" "错误" "nonexistent file should error"

# help flag
output=$(bash "$SCRIPT" --help 2>&1 || true)

# on a real apk (if one exists locally)
if [ -f "app/build/outputs/apk/debug/app-debug.apk" ]; then
    output=$(bash "$SCRIPT" "app/build/outputs/apk/debug/app-debug.apk" 2>&1 || true)
    assert_contains "$output" "APK" "should parse real APK"
fi
