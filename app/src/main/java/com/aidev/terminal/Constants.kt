package com.aidev.terminal

/**
 * AIDev Terminal 全局常量定义。
 * 集中管理所有魔法字符串，避免硬编码散落在各处。
 */
object Constants {

    // 应用包名相关
    const val PACKAGE_NAME = "com.aidev.terminal"

    // 通知渠道
    const val NOTIFICATION_CHANNEL_ID = "aidev_terminal"
    const val NOTIFICATION_CHANNEL_NAME = "AIDev Terminal"

    // 内部广播 Action（exported=false，仅本应用可用）
    object Actions {
        const val NOTIFY = "${PACKAGE_NAME}.internal.NOTIFY"
        const val CLIP = "${PACKAGE_NAME}.internal.CLIP"
        const val VOLUME = "${PACKAGE_NAME}.internal.VOLUME"
        const val BRIGHTNESS = "${PACKAGE_NAME}.internal.BRIGHTNESS"
    }

    // Shizuku Bridge 目录
    const val BRIDGE_DIR = ".aidev-shizuku-bridge"
    const val BRIDGE_REQUEST_DIR = "request"
    const val BRIDGE_RESULT_DIR = "result"

    // 存储路径
    const val SDCARD_PATH = "/sdcard"
    const val STORAGE_PATH = "/storage"
    const val SYSTEM_BIN_PATH = "/system/bin"

    // 脚本版本标记
    const val ASSET_VERSION = "0.12.34-agent-scripts-unified"

    // SharedPreferences
    const val PREFS_NAME = "aidev_ui"
    const val PREFS_KEEPALIVE_AUTO = "keepalive_auto"
    const val PREFS_WRITE_SETTINGS_PROMPTED = "write_settings_prompted"

    // 终端环境变量
    const val ENV_TERM = "xterm-256color"
    const val ENV_LANG = "C.UTF-8"

    // 超时配置（秒）
    const val CAMERA_TIMEOUT_SECONDS = 60
    const val IPC_TIMEOUT_SECONDS = 30
}
