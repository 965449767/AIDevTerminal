#!/bin/bash
# AIDev shell script test runner
set -o pipefail

RUNNER_DIR="$(cd "$(dirname "$0")" && pwd)"
LIB_DIR="$RUNNER_DIR/lib"

source "$LIB_DIR/helpers.sh"

echo "═══ AIDev Shell Script Tests ═══"
echo ""

TOTAL_PASS=0
TOTAL_FAIL=0
FAILED_TESTS=""

for test_file in "$RUNNER_DIR"/*_test.sh; do
    [ -f "$test_file" ] || continue
    name="$(basename "$test_file" _test.sh)"

    PASS=0
    FAIL=0
    ASSETS_DIR=""
    init_tests

    echo "--- $name ---"
    source "$test_file"
    summary "$name"

    TOTAL_PASS=$((TOTAL_PASS + PASS))
    if [ "$FAIL" -gt 0 ]; then
        TOTAL_FAIL=$((TOTAL_FAIL + FAIL))
        FAILED_TESTS="$FAILED_TESTS $name"
    fi
done

echo ""
echo "═══════════════════════════════════════════"
echo "  Total: $TOTAL_PASS passed, $TOTAL_FAIL failed"
echo "═══════════════════════════════════════════"

if [ -n "$FAILED_TESTS" ]; then
    echo "  Failed:$FAILED_TESTS"
fi
exit $TOTAL_FAIL
