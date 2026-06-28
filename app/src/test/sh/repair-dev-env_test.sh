# Test: repair-dev-env
SCRIPT="$ASSETS_DIR/repair-dev-env.sh"

# dry run - just check script doesn't crash on syntax
assert_syntax_ok "$SCRIPT"
