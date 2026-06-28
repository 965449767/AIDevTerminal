# Test: aidev-error-why
SCRIPT="$ASSETS_DIR/aidev-error-why.sh"

# no input + no keyword
output=$(bash "$SCRIPT" 2>&1 || true)
assert_contains "$output" "用法" "no input should show usage"

# pipe a known error pattern
output=$(echo "AAPT2 daemon startup failed" | bash "$SCRIPT" 2>&1 || true)
assert_contains "$output" "AAPT2" "should detect AAPT2 error"

# pipe gradle error
output=$(echo "Could not resolve all dependencies" | bash "$SCRIPT" 2>&1 || true)
assert_contains "$output" "代理连接失败" "should detect proxy error"

# --all flag (needs stdin input to activate)
output=$(echo "test" | bash "$SCRIPT" --all 2>&1 || true)
assert_contains "$output" "AAPT2" "--all should show AAPT2 entry"
assert_contains "$output" "Kotlin" "--all should show Kotlin entry"

# keyword search
output=$(echo "something" | bash "$SCRIPT" AAPT2 2>&1 || true)

# SDK error detection
output=$(echo "SDK location not found" | bash "$SCRIPT" 2>&1 || true)
assert_contains "$output" "SDK" "should detect SDK error"
