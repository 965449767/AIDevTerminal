# Test: aidev-gen
SCRIPT="$ASSETS_DIR/aidev-gen.sh"

# no args -> help
output=$(bash "$SCRIPT" 2>&1 || true)
assert_contains "$output" "activity" "no args should show subcommands"
assert_contains "$output" "fragment" "should list fragment"

# unknown subcommand (from inside a project-like dir)
TMPDIR=$(mktemp -d)
mkdir -p "$TMPDIR/app/src/main"
echo '<?xml version="1.0" encoding="utf-8"?><manifest package="com.test"/>' > "$TMPDIR/app/src/main/AndroidManifest.xml"
pushd "$TMPDIR" >/dev/null
output=$(bash "$SCRIPT" unknown Foo 2>&1 || true)
assert_contains "$output" "未知" "unknown subcommand should error"
popd >/dev/null
rm -rf "$TMPDIR"

# help flag
output=$(bash "$SCRIPT" --help 2>&1 || true)
assert_contains "$output" "activity" "help should show subcommands"
