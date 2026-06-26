#!/bin/bash
# aidev-create-android-project: 从模板创建新 Android 项目
# 自动匹配当前环境 AGP/Kotlin 版本
# 用法: aidev-create-android-project <应用名> <包名> [输出目录]

set -eo pipefail

APP_NAME="${1:-}"
PACKAGE="${2:-}"
OUTPUT_DIR="${3:-/root/projects}"

if [ -z "$APP_NAME" ] || [ -z "$PACKAGE" ]; then
    echo "用法: aidev-create-android-project <应用名> <包名> [输出目录]"
    echo ""
    echo "示例:"
    echo "  aidev-create-android-project MyApp com.example.myapp"
    echo "  aidev-create-android-project MyApp com.example.myapp /root/projects"
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

# AGP / Kotlin 版本（与当前环境一致）
AGP_VERSION="8.7.3"
KOTLIN_VERSION="2.0.21"
GRADLE_VERSION="8.14.5"

# 从 build.gradle.kts 获取实际版本（本项目）
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../../../.." 2>/dev/null && pwd || true)"
if [ -f "$PROJECT_ROOT/build.gradle.kts" ]; then
    FOUND_AGP=$(grep "com.android.application" "$PROJECT_ROOT/build.gradle.kts" 2>/dev/null | sed -n 's/.*version[[:space:]]*"\([^"]*\)".*/\1/p')
    [ -n "$FOUND_AGP" ] && AGP_VERSION="$FOUND_AGP"
    FOUND_KOTLIN=$(grep -E "^(plugins|id.*kotlin)" "$PROJECT_ROOT/build.gradle.kts" 2>/dev/null | grep -oP '"[0-9]+\.[0-9]+\.[0-9]+"' | head -1 | tr -d '"')
    [ -n "$FOUND_KOTLIN" ] && KOTLIN_VERSION="$FOUND_KOTLIN"
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

# ─── settings.gradle.kts ───
cat > settings.gradle.kts <<EOF
pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupByRegex(".*google.*")
                includeGroupByRegex(".*android.*")
            }
        }
        maven("https://maven.aliyun.com/repository/public")
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven("https://maven.aliyun.com/repository/public")
        mavenCentral()
    }
}

rootProject.name = "${APP_NAME}"
include(":app")
EOF

# ─── 根 build.gradle.kts ───
cat > build.gradle.kts <<EOF
plugins {
    id("com.android.application") version "${AGP_VERSION}" apply false
    id("org.jetbrains.kotlin.android") version "${KOTLIN_VERSION}" apply false
}
EOF

# ─── gradle.properties ───
cat > gradle.properties <<EOF
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
EOF

# ─── local.properties (从本项目复制 SDK 路径) ───
if [ -f "$PROJECT_ROOT/local.properties" ]; then
    grep "^sdk.dir\|^sdk" "$PROJECT_ROOT/local.properties" > local.properties 2>/dev/null || true
fi

# ─── Gradle Wrapper ───
mkdir -p gradle/wrapper
cat > gradle/wrapper/gradle-wrapper.properties <<EOF
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip
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

echo ""
echo "═══════════════════════════════════════════"
echo "  项目创建完成!"
echo "═══════════════════════════════════════════"
echo "  目录: ${PROJECT_DIR}"
echo "  构建: cd ${PROJECT_DIR} && aidev-build --full"
echo "  解析 APK: aidev-apk-info app/build/outputs/apk/debug/app-debug.apk"
echo "═══════════════════════════════════════════"
