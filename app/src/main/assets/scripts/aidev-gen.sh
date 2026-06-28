#!/bin/bash
# aidev-gen: 生成 Android 组件骨架代码
# 用法: aidev-gen activity|fragment|viewmodel <名称> [选项]
# 自动检测项目结构和包名

set -eo pipefail

detect_project() {
    local dir="${1:-.}"
    local manifest
    if [ -f "$dir/app/src/main/AndroidManifest.xml" ]; then
        manifest="$dir/app/src/main/AndroidManifest.xml"
        PROJECT_DIR="$dir"
    elif [ -f "$dir/AndroidManifest.xml" ]; then
        manifest="$dir/AndroidManifest.xml"
        PROJECT_DIR="$dir/.."
    else
        echo "错误: 找不到 AndroidManifest.xml"
        echo "请在 Android 项目根目录运行"
        exit 1
    fi
    PACKAGE=$(grep 'package=' "$manifest" | sed 's/.*package="\([^"]*\)".*/\1/')
    [ -n "$PACKAGE" ] || {
        echo "错误: 无法从 AndroidManifest.xml 提取包名"
        exit 1
    }
    SRC_DIR="$PROJECT_DIR/app/src/main/java/$(echo "$PACKAGE" | tr '.' '/')"
    RES_DIR="$PROJECT_DIR/app/src/main/res"
    mkdir -p "$SRC_DIR" "$RES_DIR/layout" "$RES_DIR/values" 2>/dev/null || true
}

to_camel() {
    echo "$1" | sed 's/[-_]./\U&/g;s/[-_]//g'
}

to_snake() {
    echo "$1" | sed 's/\([A-Z]\)/_\L\1/g;s/^_//'
}

show_help() {
    echo "用法: aidev-gen <子命令> <名称> [选项]"
    echo ""
    echo "子命令:"
    echo "  activity <名称>    生成 Activity + layout XML"
    echo "  fragment <名称>    生成 Fragment + layout XML"
    echo "  viewmodel <名称>   生成 ViewModel 类"
    echo ""
    echo "选项:"
    echo "  --dir <路径>       指定项目目录 (默认: 当前目录)"
    echo "  --layout <名称>    指定布局文件名 (默认: activity_<名称>)"
    echo "  --help             显示此帮助"
    echo ""
    echo "示例:"
    echo "  aidev-gen activity SettingsActivity"
    echo "  aidev-gen fragment DetailFragment"
    echo "  aidev-gen viewmodel MainViewModel"
    echo "  aidev-gen activity Profile --dir /root/projects/MyApp"
    exit 0
}

SUBCOMMAND="${1:-}"
[ -z "$SUBCOMMAND" ] && show_help
shift

NAME="${1:-}"
[ -z "$NAME" ] && show_help
shift

CUSTOM_DIR=""
CUSTOM_LAYOUT=""
while [ $# -gt 0 ]; do
    case "$1" in
        --dir) CUSTOM_DIR="$2"; shift 2 ;;
        --layout) CUSTOM_LAYOUT="$2"; shift 2 ;;
        --help) show_help ;;
        *) echo "未知选项: $1"; exit 1 ;;
    esac
done

detect_project "${CUSTOM_DIR:-.}"

CLASS=$(to_camel "$NAME")
SNAKE=$(to_snake "$CLASS")
LAYOUT="${CUSTOM_LAYOUT:-${SNAKE}}"

case "$SUBCOMMAND" in
    activity)
        ACTIVITY_FILE="$SRC_DIR/${CLASS}.kt"
        LAYOUT_FILE="$RES_DIR/layout/${LAYOUT}.xml"

        if [ -f "$ACTIVITY_FILE" ]; then
            echo "警告: $ACTIVITY_FILE 已存在，跳过"
        else
            cat > "$ACTIVITY_FILE" <<EOF
package ${PACKAGE}

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class ${CLASS} : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.${LAYOUT})
    }
}
EOF
            echo "  Activity: $ACTIVITY_FILE"
        fi

        if [ -f "$LAYOUT_FILE" ]; then
            echo "警告: $LAYOUT_FILE 已存在，跳过"
        else
            cat > "$LAYOUT_FILE" <<EOF
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

</androidx.constraintlayout.widget.ConstraintLayout>
EOF
            echo "  Layout: $LAYOUT_FILE"
        fi

        MANIFEST="$PROJECT_DIR/app/src/main/AndroidManifest.xml"
        if grep -q "android:name=\".${CLASS}\"" "$MANIFEST" 2>/dev/null; then
            echo "  Manifest: ${CLASS} 已在 AndroidManifest.xml 中注册"
        else
            sed -i '/<\/application>/i\        <activity android:name=".'"${CLASS}"'" android:exported="false" \/>' "$MANIFEST"
            echo "  Manifest: 已注册 ${CLASS}"
        fi
        ;;

    fragment)
        FRAGMENT_FILE="$SRC_DIR/${CLASS}.kt"
        LAYOUT_FILE="$RES_DIR/layout/fragment_${SNAKE}.xml"

        if [ -f "$FRAGMENT_FILE" ]; then
            echo "警告: $FRAGMENT_FILE 已存在，跳过"
        else
            cat > "$FRAGMENT_FILE" <<EOF
package ${PACKAGE}

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment

class ${CLASS} : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_${SNAKE}, container, false)
    }
}
EOF
            echo "  Fragment: $FRAGMENT_FILE"
        fi

        LAYOUT_FILE="$RES_DIR/layout/fragment_${SNAKE}.xml"
        if [ -f "$LAYOUT_FILE" ]; then
            echo "警告: $LAYOUT_FILE 已存在，跳过"
        else
            cat > "$LAYOUT_FILE" <<EOF
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

</androidx.constraintlayout.widget.ConstraintLayout>
EOF
            echo "  Layout: $LAYOUT_FILE"
        fi
        ;;

    viewmodel)
        VM_FILE="$SRC_DIR/${CLASS}.kt"

        if [ -f "$VM_FILE" ]; then
            echo "警告: $VM_FILE 已存在，跳过"
        else
            cat > "$VM_FILE" <<EOF
package ${PACKAGE}

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ${CLASS} : ViewModel() {

    private val _uiState = MutableStateFlow(true)
    val uiState: StateFlow<Boolean> = _uiState.asStateFlow()

    fun loadData() {
        viewModelScope.launch {

        }
    }
}
EOF
            echo "  ViewModel: $VM_FILE"
        fi
        ;;

    *)
        echo "错误: 未知子命令 '$SUBCOMMAND'"
        echo "可用子命令: activity, fragment, viewmodel"
        exit 1
        ;;
esac

echo "  包名: $PACKAGE"
echo "  项目: $PROJECT_DIR"
