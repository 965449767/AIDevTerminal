package com.aidev.terminal

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("aidev_ui", Context.MODE_PRIVATE)

    var themePreset: String
        get() = prefs.getString("theme_preset", "system") ?: "system"
        set(value) = prefs.edit().putString("theme_preset", value).apply()

    var bgMode: String
        get() = prefs.getString("bg_mode", "solid") ?: "solid"
        set(value) = prefs.edit().putString("bg_mode", value).apply()

    var bgImageUri: String?
        get() = prefs.getString("bg_image_uri", null)
        set(value) = prefs.edit().putString("bg_image_uri", value).apply()

    var hapticTap: Boolean
        get() = prefs.getBoolean("haptic_tap", true)
        set(value) = prefs.edit().putBoolean("haptic_tap", value).apply()

    var fontSp: Float
        get() = prefs.getFloat("font_sp", 10f)
        set(value) = prefs.edit().putFloat("font_sp", value).apply()

    var terminalCustomKeys: String
        get() = prefs.getString("terminal_custom_keys", "") ?: ""
        set(value) = prefs.edit().putString("terminal_custom_keys", value).apply()

    var terminalKeyOverrides: String
        get() = prefs.getString("terminal_key_overrides", "") ?: ""
        set(value) = prefs.edit().putString("terminal_key_overrides", value).apply()

    var terminalKeyAliases: String
        get() = prefs.getString("terminal_key_aliases", "") ?: ""
        set(value) = prefs.edit().putString("terminal_key_aliases", value).apply()

    var terminalKeyOrder: String
        get() = prefs.getString("terminal_key_order", "") ?: ""
        set(value) = prefs.edit().putString("terminal_key_order", value).apply()

    var terminalPinnedCompletions: String
        get() = prefs.getString("terminal_pinned_completions", "") ?: ""
        set(value) = prefs.edit().putString("terminal_pinned_completions", value).apply()

    var keepaliveAuto: Boolean
        get() = prefs.getBoolean("keepalive_auto", true)
        set(value) = prefs.edit().putBoolean("keepalive_auto", value).apply()

    var writeSettingsPrompted: Boolean
        get() = prefs.getBoolean("write_settings_prompted", false)
        set(value) = prefs.edit().putBoolean("write_settings_prompted", value).apply()

    var syncTerminalFiles: Boolean
        get() = prefs.getBoolean("sync_terminal_files", false)
        set(value) = prefs.edit().putBoolean("sync_terminal_files", value).apply()

    var currentProjectPath: String
        get() = prefs.getString("current_project_path", "") ?: ""
        set(value) = prefs.edit().putString("current_project_path", value).apply()

    var projectActionHistory: String
        get() = prefs.getString("project_action_history", "") ?: ""
        set(value) = prefs.edit().putString("project_action_history", value).apply()

    var recentFileMore: String
        get() = prefs.getString("recent_file_more", "") ?: ""
        set(value) = prefs.edit().putString("recent_file_more", value).apply()

    var recentTerminalMore: String
        get() = prefs.getString("recent_terminal_more", "") ?: ""
        set(value) = prefs.edit().putString("recent_terminal_more", value).apply()

    var recentAgentMore: String
        get() = prefs.getString("recent_agent_more", "") ?: ""
        set(value) = prefs.edit().putString("recent_agent_more", value).apply()
}
