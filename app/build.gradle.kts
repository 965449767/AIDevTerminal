import java.io.File
import java.net.URL
import java.security.MessageDigest

fun File.sha256(): String {
    val md = MessageDigest.getInstance("SHA-256")
    inputStream().use { input ->
        val buf = ByteArray(8192)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            md.update(buf, 0, n)
        }
    }
    return md.digest().joinToString("") { b -> String.format("%02x", b.toInt() and 0xFF) }
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.aidev.terminal"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.aidev.terminal"
        minSdk = 26
        targetSdk = 36
        versionCode = 74
        versionName = "0.12.32-terminal-file-sync-debug"
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("keystore/debug.keystore")
            storePassword = "aidev123"
            keyAlias = "aidev-debug"
            keyPassword = "aidev123"
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".dev"
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
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

tasks.register("downloadCurlMusl") {
    val targetDir = file("src/main/assets/tools")
    val targetFile = file("$targetDir/curl")
    val sha256File = file("$targetDir/curl.sha256")
    val version = "8.21.0"
    val archiveUrl = URL("https://github.com/stunnel/static-curl/releases/download/$version/curl-linux-aarch64-musl-$version.tar.xz")
    val archiveFile = file("$temporaryDir/curl-musl.tar.xz")

    doLast {
        targetDir.mkdirs()

        if (targetFile.exists() && sha256File.exists()) {
            val expected = sha256File.readText().trim()
            val actual = targetFile.sha256()
            if (actual == expected) {
                logger.lifecycle("✓ curl-musl 已存在，SHA256 匹配，跳过下载")
                return@doLast
            }
            logger.lifecycle("→ curl 内容可能已变更，重新下载...")
        }

        logger.lifecycle("→ 下载 curl-musl v$version ...")
        temporaryDir.mkdirs()
        archiveUrl.openStream().use { input ->
            archiveFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        logger.lifecycle("→ 下载完成 (${archiveFile.length()} bytes)，解压中...")

        val extractDir = File(temporaryDir, "extracted")
        extractDir.mkdirs()
        val pb = ProcessBuilder("tar", "-xJf", archiveFile.absolutePath, "-C", extractDir.absolutePath)
        pb.inheritIO()
        val proc = pb.start()
        val exitCode = proc.waitFor()
        if (exitCode != 0) {
            throw RuntimeException("tar 解压失败，exit code=$exitCode")
        }

        val extractedCurl = extractDir.listFiles()?.firstOrNull { it.name == "curl" }
            ?: throw RuntimeException("在 tar 归档中未找到 curl 二进制文件")
        extractedCurl.copyTo(targetFile, overwrite = true)
        targetFile.setExecutable(true)
        val sha256 = targetFile.sha256()
        sha256File.writeText(sha256)
        logger.lifecycle("✓ curl-musl 部署完成: ${targetFile.length()} bytes, SHA256=$sha256")
    }
}

tasks.named("preBuild") {
    dependsOn("downloadCurlMusl")
}

dependencies {
    implementation("com.github.termux.termux-app:terminal-view:v0.118.3")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.google.android.material:material:1.12.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.0.21")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("org.mockito:mockito-core:5.12.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
}
