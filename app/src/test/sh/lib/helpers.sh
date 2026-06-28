# Test helpers for AIDev shell script tests
PASS=0
FAIL=0
SCRIPT_DIR=""

init_tests() {
    ASSETS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../main/assets/scripts" && pwd)"
    PASS=0
    FAIL=0
}

ok() {
    PASS=$((PASS + 1))
}

fail() {
    local msg="$1"
    FAIL=$((FAIL + 1))
    echo "  FAIL: $msg" >&2
}

assert_eq() {
    local expected="$1"
    local actual="$2"
    local msg="${3:-expected '$expected', got '$actual'}"
    if [ "$expected" = "$actual" ]; then
        ok
    else
        fail "$msg"
    fi
}

assert_contains() {
    local haystack="$1"
    local needle="$2"
    local msg="${3:-expected to contain '$needle'}"
    if echo "$haystack" | grep -qF "$needle"; then
        ok
    else
        fail "$msg"
    fi
}

assert_not_contains() {
    local haystack="$1"
    local needle="$2"
    local msg="${3:-expected NOT to contain '$needle'}"
    if echo "$haystack" | grep -qF "$needle"; then
        fail "$msg"
    else
        ok
    fi
}

assert_syntax_ok() {
    local file="$1"
    if bash -n "$file" 2>/dev/null; then
        ok
    else
        fail "syntax error in $file"
    fi
}

assert_exit_code() {
    local expected="$1"
    shift
    local actual=0
    "$@" 2>/dev/null || actual=$?
    if [ "$actual" -eq "$expected" ]; then
        ok
    else
        fail "exit code: expected $expected, got $actual for: $*"
    fi
}

summary() {
    local name="${1:-tests}"
    echo ""
    echo "═══ $name: $PASS passed, $FAIL failed ═══"
    return $FAIL
}
