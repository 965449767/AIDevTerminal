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
    const val ASSET_VERSION = "0.13.00-no-opencode"

    // SharedPreferences 文件名
    const val PREFS_NAME = "aidev_ui"
    const val PREFS_SHELL = "aidev_shell"
    const val PREFS_SSH = "aidev_ssh"

    // SharedPreferences Key 常量
    object PrefKeys {
        const val THEME_PRESET = "theme_preset"
        const val BG_MODE = "bg_mode"
        const val BG_IMAGE_URI = "bg_image_uri"
        const val HAPTIC_TAP = "haptic_tap"
        const val FONT_SP = "font_sp"
        const val WRITE_SETTINGS_PROMPTED = "write_settings_prompted"
        const val SYNC_TERMINAL_FILES = "sync_terminal_files"
        const val CURRENT_PROJECT_PATH = "current_project_path"
        const val BACKUP_DIR = "backup_dir"
        const val PROJECTS_DIR_REL = "projects_dir_rel"
        const val EXTERNAL_AIDEV_DIR = "external_aidev_dir"
        const val PROJECT_ACTION_HISTORY = "project_action_history"
        const val RECENT_FILE_MORE = "recent_file_more"
        const val RECENT_TERMINAL_MORE = "recent_terminal_more"
        const val RECENT_AGENT_MORE = "recent_agent_more"
        const val TERMINAL_CUSTOM_KEYS = "terminal_custom_keys"
        const val TERMINAL_KEY_OVERRIDES = "terminal_key_overrides"
        const val TERMINAL_KEY_ALIASES = "terminal_key_aliases"
        const val TERMINAL_KEY_ORDER = "terminal_key_order"
        const val TERMINAL_PINNED_COMPLETIONS = "terminal_pinned_completions"
        const val AUTO_SHOW_KEYBOARD = "auto_show_keyboard"
        const val FILE_FAVORITES = "file_favorites"
        const val FILE_RECENT_DIRS = "file_recent_dirs"
        const val ONELINERS = "oneliners"
        const val SSH_CONNECTIONS = "connections"
    }

    // 终端环境变量
    const val ENV_TERM = "xterm-256color"
    const val ENV_LANG = "C.UTF-8"

    // 超时配置（秒）
    const val CAMERA_TIMEOUT_SECONDS = 60
    const val IPC_TIMEOUT_SECONDS = 30
}
