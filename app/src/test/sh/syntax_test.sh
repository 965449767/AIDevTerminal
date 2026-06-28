# Syntax check all scripts
for f in "$ASSETS_DIR"/*.sh; do
    assert_syntax_ok "$f"
done
