# Test: setup-dev-env
SCRIPT="$ASSETS_DIR/setup-dev-env.sh"

# syntax + help - don't run (would install packages)
assert_syntax_ok "$SCRIPT"
