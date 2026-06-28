# Test: aidev-index
SCRIPT="$ASSETS_DIR/aidev-index.sh"

# search without index
output=$(bash "$SCRIPT" class Foo 2>&1 || true)
assert_contains "$output" "索引不存在" "no index should warn"

# invalid subcommand
output=$(bash "$SCRIPT" unknown 2>&1 || true)
