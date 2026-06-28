#!/bin/bash
# aidev-index: 代码搜索索引 — 从 Android 项目构建可搜索索引
# 用法: aidev-index [class|res|string|layout|function|refresh] <关键词>
#       aidev-index                          # 刷新索引
#       aidev-index class MainActivity        # 搜索类
#       aidev-index res layout                # 搜索资源
#       aidev-index string app_name           # 搜索字符串
#       aidev-index refresh                   # 强制刷新索引

set -eo pipefail

INDEX_FILE=".aidev-index.json"

detect_project() {
    local dir="${1:-.}"
    if [ -f "$dir/app/src/main/AndroidManifest.xml" ]; then
        PROJECT_DIR="$dir"
    elif [ -f "$dir/AndroidManifest.xml" ]; then
        PROJECT_DIR="$dir/.."
    else
        echo "错误: 找不到 AndroidManifest.xml，请在项目根目录运行"
        exit 1
    fi
    PROJECT_DIR=$(cd "$PROJECT_DIR" && pwd)
    SRC_DIR="$PROJECT_DIR/app/src/main"
    INDEX_FILE="$PROJECT_DIR/.aidev-index.json"
}

build_index() {
    detect_project "$1"
    echo "扫描项目: $PROJECT_DIR"
    echo "索引文件: $INDEX_FILE"

    mkdir -p /tmp/aidev-index-$$ 2>/dev/null || true
    local tmp="/tmp/aidev-index-$$"

    # — 类 / 对象 / 接口（排除 build/ 和 generated/） —
    find "$SRC_DIR/java" \( -name "build" -o -name "generated" -o -name ".gradle" \) -prune -o \( -name "*.kt" -o -name "*.java" \) -print 2>/dev/null | while read -r f; do
        local rel="${f#$SRC_DIR/java/}"
        local pkg=""
        pkg=$(grep "^package " "$f" 2>/dev/null | sed 's/package //;s/;//' | head -1)
        local classes
        classes=$(grep -E "^(class|interface|object|data class|sealed class) " "$f" 2>/dev/null | sed 's/[{:].*//;s/^ *//' | head -10)
        [ -n "$classes" ] && echo "$rel|$pkg|$classes"
    done > "$tmp/classes.txt"

    # — 布局文件 —
    find "$SRC_DIR/res/layout" -name "*.xml" 2>/dev/null | while read -r f; do
        local name
        name=$(basename "$f" .xml)
        echo "layout|$name|$f"
    done > "$tmp/layout.txt"

    # — 字符串资源 —
    if [ -f "$SRC_DIR/res/values/strings.xml" ]; then
        grep '<string name=' "$SRC_DIR/res/values/strings.xml" 2>/dev/null | \
            sed 's/.*name="\([^"]*\)".*/\1/' | \
            while read -r name; do
                echo "string|$name|strings.xml"
            done
    fi > "$tmp/strings.txt"

    # — 所有资源文件（drawable/mipmap/values） —
    find "$SRC_DIR/res" -type f -name "*.xml" -o -name "*.png" -o -name "*.jpg" -o -name "*.webp" -o -name "*.gif" 2>/dev/null | \
        grep -v "/values/" | \
        while read -r f; do
            local type
            type=$(basename "$(dirname "$f")")
            local name
            name=$(basename "$f" | sed 's/\.[^.]*$//')
            echo "resource|$type/$name|$f"
        done > "$tmp/resources.txt"

    # — 函数/方法签名 —
    find "$SRC_DIR/java" -name "*.kt" 2>/dev/null | while read -r f; do
        local rel="${f#$SRC_DIR/java/}"
        grep -E "^    (fun |suspend fun |private |public |internal )" "$f" 2>/dev/null | \
            sed 's/^ *//;s/{.*$//;s/):.*$/)/' | head -20 | \
            while read -r line; do
                echo "$rel|$line"
            done
    done > "$tmp/functions.txt"

    # — AndroidManifest 组件 —
    local manifest="$SRC_DIR/AndroidManifest.xml"
    if [ -f "$manifest" ]; then
        grep -E "<activity|<service|<receiver|<provider" "$manifest" 2>/dev/null | \
            sed 's/.*android:name="\([^"]*\)".*/\1/' | \
            while read -r name; do
                echo "component|$name|AndroidManifest.xml"
            done > "$tmp/components.txt"
    fi

    # — 合并为 JSON —
    json_escape() {
        local s="$1"
        s="${s//\\/\\\\}"
        s="${s//\"/\\\"}"
        s="${s//$'\t'/\\t}"
        s="${s//$'\r'/}"
        s="${s//$'\n'/\\n}"
        printf '%s' "$s"
    }
    echo "{" > "$tmp/index.json"

    # classes
    echo '  "classes": [' >> "$tmp/index.json"
    local first=true
    while IFS='|' read -r rel pkg classes; do
        echo "$classes" | while IFS= read -r cls; do
            [ -z "$cls" ] && continue
            $first || echo "," >> "$tmp/index.json"
            first=false
            local full_name=""
            [ -n "$pkg" ] && full_name="${pkg}.${cls%% *}" || full_name="${cls%% *}"
            echo -n "    {\"name\":\"$(json_escape "$full_name")\",\"file\":\"$(json_escape "$rel")\"}" >> "$tmp/index.json"
        done
    done < "$tmp/classes.txt"
    echo "" >> "$tmp/index.json"
    echo '  ],' >> "$tmp/index.json"

    # resources
    echo '  "resources": [' >> "$tmp/index.json"
    first=true
    while IFS='|' read -r type name path; do
        $first || echo "," >> "$tmp/index.json"
        first=false
        echo -n "    {\"type\":\"$(json_escape "$type")\",\"name\":\"$(json_escape "$name")\",\"file\":\"$(json_escape "${path#$SRC_DIR/res/}")\"}" >> "$tmp/index.json"
    done < <(cat "$tmp/layout.txt" "$tmp/strings.txt" "$tmp/resources.txt" 2>/dev/null)
    echo "" >> "$tmp/index.json"
    echo '  ],' >> "$tmp/index.json"

    # functions
    echo '  "functions": [' >> "$tmp/index.json"
    first=true
    while IFS='|' read -r rel sig; do
        $first || echo "," >> "$tmp/index.json"
        first=false
        echo -n "    {\"file\":\"$(json_escape "$rel")\",\"sig\":\"$(json_escape "$sig")\"}" >> "$tmp/index.json"
    done < "$tmp/functions.txt"
    echo "" >> "$tmp/index.json"
    echo '  ],' >> "$tmp/index.json"

    # components
    echo '  "components": [' >> "$tmp/index.json"
    first=true
    while IFS='|' read -r type name; do
        $first || echo "," >> "$tmp/index.json"
        first=false
        echo -n "    {\"type\":\"$(json_escape "$type")\",\"name\":\"$(json_escape "$name")\"}" >> "$tmp/index.json"
    done < "$tmp/components.txt"
    echo "" >> "$tmp/index.json"
    echo '  ]' >> "$tmp/index.json"

    echo "}" >> "$tmp/index.json"
    local tmp_index="${INDEX_FILE}.tmp-$$"
    cp "$tmp/index.json" "$tmp_index" && mv "$tmp_index" "$INDEX_FILE"
    rm -rf "$tmp" 2>/dev/null || true

    local count
    count=$(grep -c '"name"' "$INDEX_FILE" 2>/dev/null || true)
    echo "索引完成: $count 条记录"
}

search_index() {
    local type="$1"
    local keyword="$2"

    if [ ! -f "$INDEX_FILE" ]; then
        echo "索引不存在，先运行 aidev-index (无参数) 构建索引"
        exit 1
    fi

    case "$type" in
        class)
            echo "═══ 类搜索: $keyword ═══"
            grep -i "\"name\":.*$keyword" "$INDEX_FILE" 2>/dev/null | \
                sed 's/.*"name":"\([^"]*\)".*"file":"\([^"]*\)".*/  \1  (\2)/' || \
                echo "  未匹配"
            ;;
        res|resource)
            echo "═══ 资源搜索: $keyword ═══"
            grep -i "\"name\":.*$keyword" "$INDEX_FILE" 2>/dev/null | \
                sed 's/.*"type":"\([^"]*\)","name":"\([^"]*\)".*"file":"\([^"]*\)".*/  [\1] \2  (\3)/' || \
                echo "  未匹配"
            ;;
        string)
            echo "═══ 字符串搜索: $keyword ═══"
            grep -i "\"type\":\"string\".*$keyword" "$INDEX_FILE" 2>/dev/null | \
                sed 's/.*"name":"\([^"]*\)".*/  R.string.\1/' || \
                echo "  未匹配"
            ;;
        layout)
            echo "═══ 布局搜索: $keyword ═══"
            grep -i "\"type\":\"layout\".*$keyword" "$INDEX_FILE" 2>/dev/null | \
                sed 's/.*"name":"\([^"]*\)".*/  R.layout.\1/' || \
                echo "  未匹配"
            ;;
        function|fun)
            echo "═══ 方法搜索: $keyword ═══"
            grep -i "\"sig\":.*$keyword" "$INDEX_FILE" 2>/dev/null | \
                sed 's/.*"file":"\([^"]*\)","sig":"\([^"]*\)".*/  \1\n    \2/' || \
                echo "  未匹配"
            ;;
        component)
            echo "═══ 组件搜索: $keyword ═══"
            grep -i "\"name\":.*$keyword" "$INDEX_FILE" 2>/dev/null | \
                sed 's/.*"type":"\([^"]*\)","name":"\([^"]*\)".*/  [\1] \2/' || \
                echo "  未匹配"
            ;;
        *)
            echo "═══ 全文搜索: $keyword ═══"
            grep -i "$keyword" "$INDEX_FILE" 2>/dev/null | \
                sed 's/.*"name":"\([^"]*\)".*/  \1/' | sort -u || \
                echo "  未匹配"
            ;;
    esac
}

SUBCOMMAND="${1:-refresh}"
shift 2>/dev/null || true

case "$SUBCOMMAND" in
    refresh|--rebuild|--refresh|rebuild)
        build_index "."
        ;;
    class|res|resource|string|layout|function|fun|component)
        KEYWORD="${1:-}"
        if [ -z "$KEYWORD" ]; then
            echo "用法: aidev-index $SUBCOMMAND <关键词>"
            echo "示例: aidev-index $SUBCOMMAND Main"
            exit 1
        fi
        detect_project "."
        search_index "$SUBCOMMAND" "$KEYWORD"
        ;;
    *)
        detect_project "."
        search_index "all" "$SUBCOMMAND"
        ;;
esac
