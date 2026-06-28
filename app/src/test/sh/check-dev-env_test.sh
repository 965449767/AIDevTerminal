# Test: check-dev-env
SCRIPT="$ASSETS_DIR/check-dev-env.sh"

# syntax only (script may hang on network checks)
assert_syntax_ok "$SCRIPT"
