#!/bin/bash
# aidev-create-android-project: 从模板创建新 Android 项目
# 自动匹配当前环境 AGP/Kotlin 版本
# 用法: aidev-create-android-project <应用名> <包名> [输出目录]

set -eo pipefail

APP_NAME="${1:-}"
PACKAGE="${2:-}"
OUTPUT_DIR="${3:-/root/Workspace/Android}"

if [ -z "$APP_NAME" ] || [ -z "$PACKAGE" ]; then
    echo "用法: aidev-create-android-project <应用名> <包名> [输出目录]"
    echo ""
    echo "示例:"
    echo "  aidev-create-android-project MyApp com.example.myapp"
    echo "  aidev-create-android-project MyApp com.example.myapp /root/Workspace/Android"
    exit 1
fi

if ! echo "$PACKAGE" | grep -qE '^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$'; then
    echo "错误: 包名格式无效: $PACKAGE"
    echo "正确格式: com.example.myapp"
    exit 1
fi

PROJECT_DIR="${OUTPUT_DIR}/${APP_NAME}"
PACKAGE_PATH=$(echo "$PACKAGE" | tr '.' '/')

if [ -d "$PROJECT_DIR" ]; then
    echo "错误: 目标目录已存在: $PROJECT_DIR"
    exit 1
fi

# AGP / Kotlin / Gradle 版本（与当前环境一致）
AGP_VERSION="8.7.3"
KOTLIN_VERSION="2.0.21"
GRADLE_VERSION="8.14.5"

# 从构建脚本所在目录上溯查找宿主项目（AdvTerminal）
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT=""
for candidate in "$SCRIPT_DIR" "$SCRIPT_DIR/.." "$SCRIPT_DIR/../.." "$SCRIPT_DIR/../../.." "$SCRIPT_DIR/../../../.."; do
    candidate=$(cd "$candidate" 2>/dev/null && pwd || true)
    [ -n "$candidate" ] && [ -f "$candidate/build.gradle.kts" ] && [ -f "$candidate/settings.gradle.kts" ] && { PROJECT_ROOT="$candidate"; break; }
done
HAS_HOST=false
if [ -n "$PROJECT_ROOT" ] && [ -f "$PROJECT_ROOT/build.gradle.kts" ]; then
    HAS_HOST=true
    FOUND_AGP=$(grep "com.android.application" "$PROJECT_ROOT/build.gradle.kts" 2>/dev/null | sed -n 's/.*version[[:space:]]*"\([^"]*\)".*/\1/p')
    [ -n "$FOUND_AGP" ] && AGP_VERSION="$FOUND_AGP"
    FOUND_KOTLIN=$(grep -E "^(plugins|id.*kotlin)" "$PROJECT_ROOT/build.gradle.kts" 2>/dev/null | grep -oP '"[0-9]+\.[0-9]+\.[0-9]+"' | head -1 | tr -d '"')
    [ -n "$FOUND_KOTLIN" ] && KOTLIN_VERSION="$FOUND_KOTLIN"
    FOUND_GRADLE=$(grep '^distributionUrl' "$PROJECT_ROOT/gradle/wrapper/gradle-wrapper.properties" 2>/dev/null | grep -oP 'gradle-\K[0-9.]+(?=-bin\.zip)')
    [ -n "$FOUND_GRADLE" ] && GRADLE_VERSION="$FOUND_GRADLE"
fi

# 从宿主提取仓库配置
HOST_PLUGIN_REPOS=""
HOST_DEP_REPOS=""
HOST_DIST_URL="distributionUrl=https\\://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
HOST_PROPS_EXTRA=""
if [ "$HAS_HOST" = true ]; then
    HOST_PLUGIN_REPOS=$(awk '/^pluginManagement {/,/^}/' "$PROJECT_ROOT/settings.gradle.kts" 2>/dev/null | sed '1,/repositories {/d; /^}/,$d')
    HOST_DEP_REPOS=$(awk '/^dependencyResolutionManagement {/,/^}/' "$PROJECT_ROOT/settings.gradle.kts" 2>/dev/null | sed '1,/repositories {/d; /^}/,$d')
    HOST_DIST_URL=$(grep '^distributionUrl' "$PROJECT_ROOT/gradle/wrapper/gradle-wrapper.properties" 2>/dev/null || echo "$HOST_DIST_URL")
    # 提取项目级 gradle.properties（排除注释/空行/全局配置/模板已有项）
    HOST_PROPS_EXTRA=$(grep -v '^#' "$PROJECT_ROOT/gradle.properties" 2>/dev/null \
        | grep -v '^$' \
        | grep -v 'android\.aapt2\|android\.suppress\|systemProp\.' \
        | grep -v '^org\.gradle\.jvmargs\|^android\.useAndroidX\|^kotlin\.code\.style\|^android\.nonTransitiveRClass' || true)
fi

echo ""
echo "═══════════════════════════════════════════"
echo "  创建 Android 项目"
echo "═══════════════════════════════════════════"
echo "  应用名:    ${APP_NAME}"
echo "  包名:      ${PACKAGE}"
echo "  AGP:       ${AGP_VERSION}"
echo "  Kotlin:    ${KOTLIN_VERSION}"
echo "  Gradle:    ${GRADLE_VERSION}"
echo "  输出:      ${PROJECT_DIR}"
echo ""

mkdir -p "$PROJECT_DIR"
cd "$PROJECT_DIR"

# ─── settings.gradle.kts（仓库配置从宿主继承） ───
if [ -n "$HOST_PLUGIN_REPOS" ]; then
    # 从宿主提取的完整仓库列表
    printf 'pluginManagement {\n    repositories {\n' > settings.gradle.kts
    echo "$HOST_PLUGIN_REPOS" >> settings.gradle.kts
    printf '    }\n}\n\ndependencyResolutionManagement {\n    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)\n    repositories {\n' >> settings.gradle.kts
    echo "$HOST_DEP_REPOS" >> settings.gradle.kts
    printf '    }\n}\n\nrootProject.name = "%s"\ninclude(":app")\n' "${APP_NAME}" >> settings.gradle.kts
else
    # 宿主不可用时使用内置默认值
    cat > settings.gradle.kts <<'SETTINGS_EOF'
pluginManagement {
    repositories {
        maven { setUrl("https://maven.aliyun.com/repository/google") }
        maven { setUrl("https://maven.aliyun.com/repository/central") }
        maven { setUrl("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { setUrl("https://jitpack.io") }
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { setUrl("https://maven.aliyun.com/repository/google") }
        maven { setUrl("https://maven.aliyun.com/repository/central") }
        maven { setUrl("https://jitpack.io") }
    }
}
SETTINGS_EOF
    echo "rootProject.name = \"${APP_NAME}\"" >> settings.gradle.kts
    echo 'include(":app")' >> settings.gradle.kts
fi

# ─── 根 build.gradle.kts ───
cat > build.gradle.kts <<EOF
plugins {
    id("com.android.application") version "${AGP_VERSION}" apply false
    id("org.jetbrains.kotlin.android") version "${KOTLIN_VERSION}" apply false
}
EOF

# ─── gradle.properties（基础设置 + 宿主继承项） ───
cat > gradle.properties <<EOF
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
$(echo "$HOST_PROPS_EXTRA")
EOF

# ─── local.properties (SDK 路径探测) ───
SDK_DIR=""
if [ -d "/Android/platforms" ]; then
    SDK_DIR="/Android"
elif [ -n "$ANDROID_HOME" ] && [ -d "$ANDROID_HOME/platforms" ]; then
    SDK_DIR="$ANDROID_HOME"
elif [ -n "$ANDROID_SDK_ROOT" ] && [ -d "$ANDROID_SDK_ROOT/platforms" ]; then
    SDK_DIR="$ANDROID_SDK_ROOT"
elif [ -f "$PROJECT_ROOT/local.properties" ]; then
    SDK_DIR=$(grep "^sdk.dir\|^sdk" "$PROJECT_ROOT/local.properties" 2>/dev/null | head -1 | cut -d= -f2 || true)
fi

if [ -n "$SDK_DIR" ]; then
    echo "sdk.dir=$SDK_DIR" > local.properties
    echo "  SDK:       $SDK_DIR"
else
    echo "  警告: 未找到 Android SDK，需要手动创建 local.properties"
    echo "         echo 'sdk.dir=/Android' > local.properties"
fi

# ─── Gradle Wrapper（distributionUrl 从宿主继承） ───
mkdir -p gradle/wrapper
cat > gradle/wrapper/gradle-wrapper.properties <<EOF
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
${HOST_DIST_URL}
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
EOF

# ─── app/build.gradle.kts ───
mkdir -p app
cat > app/build.gradle.kts <<EOF
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "${PACKAGE}"
    compileSdk = 36

    defaultConfig {
        applicationId = "${PACKAGE}"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
}
EOF

# ─── AndroidManifest.xml ───
mkdir -p "app/src/main"
cat > app/src/main/AndroidManifest.xml <<EOF
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.${APP_NAME}">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.${APP_NAME}">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
EOF

# ─── Kotlin 源码 ───
SRC_DIR="app/src/main/java/${PACKAGE_PATH}"
mkdir -p "$SRC_DIR"

cat > "${SRC_DIR}/MainActivity.kt" <<EOF
package ${PACKAGE}

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
}
EOF

# ─── 资源文件 ───
mkdir -p app/src/main/res/layout
mkdir -p app/src/main/res/values
mkdir -p app/src/main/res/mipmap-hdpi

cat > app/src/main/res/layout/activity_main.xml <<EOF
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Hello ${APP_NAME}!"
        android:textSize="24sp"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="parent" />

</androidx.constraintlayout.widget.ConstraintLayout>
EOF

cat > app/src/main/res/values/strings.xml <<EOF
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">${APP_NAME}</string>
</resources>
EOF

cat > app/src/main/res/values/themes.xml <<EOF
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.${APP_NAME}" parent="Theme.MaterialComponents.DayNight.DarkActionBar">
        <item name="colorPrimary">#6200EE</item>
        <item name="colorPrimaryVariant">#3700B3</item>
        <item name="colorOnPrimary">#FFFFFF</item>
        <item name="colorSecondary">#03DAC5</item>
        <item name="colorSecondaryVariant">#018786</item>
        <item name="colorOnSecondary">#000000</item>
    </style>
</resources>
EOF

# ─── proguard-rules.pro ───
cat > app/proguard-rules.pro <<EOF
# ProGuard rules for ${APP_NAME}
EOF

# ─── 下载 Gradle Wrapper ───
echo "  下载 Gradle Wrapper..."
WRAPPER_JAR="gradle/wrapper/gradle-wrapper.jar"
WRAPPER_JAR_URL="https://raw.githubusercontent.com/gradle/gradle/v${GRADLE_VERSION}/gradle/wrapper/gradle-wrapper.jar"
if command -v curl &>/dev/null; then
    curl -fsSL -k -o "$WRAPPER_JAR" "$WRAPPER_JAR_URL" 2>/dev/null || true
elif command -v wget &>/dev/null; then
    wget -q -O "$WRAPPER_JAR" "$WRAPPER_JAR_URL" 2>/dev/null || true
fi

cat > gradlew <<'GRADLEW_EOF'
#!/bin/sh
GRADLEW_EOF

cat > gradlew <<'GRADLEW_SCRIPT'
#!/bin/sh
#
# Gradle start up script for POSIX generated by Gradle.
# 简化版：自动下载 Gradle wrapper jar

APP_NAME="Gradle"
APP_BASE_NAME=$(basename "$0")
DIRNAME=$(dirname "$0")

# 如果 wrapper jar 不存在，尝试从 Gradle 分布下载
JARFILE="$DIRNAME/gradle/wrapper/gradle-wrapper.jar"
if [ ! -f "$JARFILE" ]; then
    echo "下载 Gradle wrapper..."
    GRADLE_VERSION=$(grep "distributionUrl" "$DIRNAME/gradle/wrapper/gradle-wrapper.properties" | sed 's/.*gradle-\([0-9.]*\)-bin.zip.*/\1/')
    JAR_URL="https://raw.githubusercontent.com/gradle/gradle/v${GRADLE_VERSION}/gradle/wrapper/gradle-wrapper.jar"
    if command -v curl >/dev/null 2>&1; then
        curl -fsSL -k -o "$JARFILE" "$JAR_URL" || true
    elif command -v wget >/dev/null 2>&1; then
        wget -q -O "$JARFILE" "$JAR_URL" || true
    fi
fi

if [ ! -f "$JARFILE" ]; then
    echo "错误: 无法下载 Gradle wrapper jar"
    echo "请手动下载并放置到: $JARFILE"
    exit 1
fi

exec java -jar "$JARFILE" "$@"
GRADLEW_SCRIPT

chmod +x gradlew

# ─── 初始化 Git ───
echo "  初始化 Git..."
git init 2>/dev/null || true
git checkout -b main 2>/dev/null || true

cat > .gitignore <<EOF
.gradle/
build/
/local.properties
*.iml
.idea/
.navigation/
captures/
.externalNativeBuild/
.cxx/
EOF

git add -A 2>/dev/null || true
git commit -m "Initial commit: ${APP_NAME}" --allow-empty 2>/dev/null || true

# 构建代码搜索索引
echo "  构建索引..."
aidev-index 2>/dev/null || true

echo ""
echo "═══════════════════════════════════════════"
echo "  项目创建完成!"
echo "═══════════════════════════════════════════"
echo "  目录: ${PROJECT_DIR}"
echo "  构建: cd ${PROJECT_DIR} && aidev-build --full"
echo "  解析 APK: aidev-apk-info app/build/outputs/apk/debug/app-debug.apk"
echo "═══════════════════════════════════════════"
