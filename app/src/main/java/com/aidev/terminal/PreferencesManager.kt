package com.aidev.terminal

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
    val sharedPreferences: SharedPreferences get() = prefs

    private var pendingEdit: SharedPreferences.Editor? = null

    fun batch(block: PreferencesManager.() -> Unit) {
        val ed = prefs.edit()
        pendingEdit = ed
        try {
            block()
        } finally {
            ed.apply()
            pendingEdit = null
        }
    }

    var themePreset: String
        get() = prefs.getString(Constants.PrefKeys.THEME_PRESET, "system") ?: "system"
        set(value) = write { putString(Constants.PrefKeys.THEME_PRESET, value) }

    var bgMode: String
        get() = prefs.getString(Constants.PrefKeys.BG_MODE, "solid") ?: "solid"
        set(value) = write { putString(Constants.PrefKeys.BG_MODE, value) }

    var bgImageUri: String?
        get() = prefs.getString(Constants.PrefKeys.BG_IMAGE_URI, null)
        set(value) = write { putString(Constants.PrefKeys.BG_IMAGE_URI, value) }

    var hapticTap: Boolean
        get() = prefs.getBoolean(Constants.PrefKeys.HAPTIC_TAP, true)
        set(value) = write { putBoolean(Constants.PrefKeys.HAPTIC_TAP, value) }

    var fontSp: Float
        get() = prefs.getFloat(Constants.PrefKeys.FONT_SP, 10f)
        set(value) = write { putFloat(Constants.PrefKeys.FONT_SP, value) }

    var terminalCustomKeys: String
        get() = prefs.getString(Constants.PrefKeys.TERMINAL_CUSTOM_KEYS, "") ?: ""
        set(value) = write { putString(Constants.PrefKeys.TERMINAL_CUSTOM_KEYS, value) }

    var terminalKeyOverrides: String
        get() = prefs.getString(Constants.PrefKeys.TERMINAL_KEY_OVERRIDES, "") ?: ""
        set(value) = write { putString(Constants.PrefKeys.TERMINAL_KEY_OVERRIDES, value) }

    var terminalKeyAliases: String
        get() = prefs.getString(Constants.PrefKeys.TERMINAL_KEY_ALIASES, "") ?: ""
        set(value) = write { putString(Constants.PrefKeys.TERMINAL_KEY_ALIASES, value) }

    var terminalKeyOrder: String
        get() = prefs.getString(Constants.PrefKeys.TERMINAL_KEY_ORDER, "") ?: ""
        set(value) = write { putString(Constants.PrefKeys.TERMINAL_KEY_ORDER, value) }

    var terminalPinnedCompletions: String
        get() = prefs.getString(Constants.PrefKeys.TERMINAL_PINNED_COMPLETIONS, "") ?: ""
        set(value) = write { putString(Constants.PrefKeys.TERMINAL_PINNED_COMPLETIONS, value) }

    var writeSettingsPrompted: Boolean
        get() = prefs.getBoolean(Constants.PrefKeys.WRITE_SETTINGS_PROMPTED, false)
        set(value) = write { putBoolean(Constants.PrefKeys.WRITE_SETTINGS_PROMPTED, value) }

    var syncTerminalFiles: Boolean
        get() = prefs.getBoolean(Constants.PrefKeys.SYNC_TERMINAL_FILES, false)
        set(value) = write { putBoolean(Constants.PrefKeys.SYNC_TERMINAL_FILES, value) }

    var currentProjectPath: String
        get() = prefs.getString(Constants.PrefKeys.CURRENT_PROJECT_PATH, "") ?: ""
        set(value) = write { putString(Constants.PrefKeys.CURRENT_PROJECT_PATH, value) }

    var backupDir: String
        get() = prefs.getString(Constants.PrefKeys.BACKUP_DIR, "/sdcard/AIDev/backups/") ?: "/sdcard/AIDev/backups/"
        set(value) = write { putString(Constants.PrefKeys.BACKUP_DIR, value) }

    var projectsDirRel: String
        get() = prefs.getString(Constants.PrefKeys.PROJECTS_DIR_REL, "root/projects") ?: "root/projects"
        set(value) = write { putString(Constants.PrefKeys.PROJECTS_DIR_REL, value) }

    var externalAidevDir: String
        get() = prefs.getString(Constants.PrefKeys.EXTERNAL_AIDEV_DIR, "/sdcard/AIDev") ?: "/sdcard/AIDev"
        set(value) = write { putString(Constants.PrefKeys.EXTERNAL_AIDEV_DIR, value) }

    var projectActionHistory: String
        get() = prefs.getString(Constants.PrefKeys.PROJECT_ACTION_HISTORY, "") ?: ""
        set(value) = write { putString(Constants.PrefKeys.PROJECT_ACTION_HISTORY, value) }

    var recentFileMore: String
        get() = prefs.getString(Constants.PrefKeys.RECENT_FILE_MORE, "") ?: ""
        set(value) = write { putString(Constants.PrefKeys.RECENT_FILE_MORE, value) }

    var recentTerminalMore: String
        get() = prefs.getString(Constants.PrefKeys.RECENT_TERMINAL_MORE, "") ?: ""
        set(value) = write { putString(Constants.PrefKeys.RECENT_TERMINAL_MORE, value) }

    var recentAgentMore: String
        get() = prefs.getString(Constants.PrefKeys.RECENT_AGENT_MORE, "") ?: ""
        set(value) = write { putString(Constants.PrefKeys.RECENT_AGENT_MORE, value) }

    var autoShowKeyboard: Boolean
        get() = prefs.getBoolean(Constants.PrefKeys.AUTO_SHOW_KEYBOARD, true)
        set(value) = write { putBoolean(Constants.PrefKeys.AUTO_SHOW_KEYBOARD, value) }

    var fileFavorites: Set<String>
        get() = prefs.getStringSet(Constants.PrefKeys.FILE_FAVORITES, emptySet()) ?: emptySet()
        set(value) = write { putStringSet(Constants.PrefKeys.FILE_FAVORITES, value) }

    var fileRecentDirs: Set<String>
        get() = prefs.getStringSet(Constants.PrefKeys.FILE_RECENT_DIRS, emptySet()) ?: emptySet()
        set(value) = write { putStringSet(Constants.PrefKeys.FILE_RECENT_DIRS, value) }

    var fileLayoutMode: String
        get() = prefs.getString(Constants.PrefKeys.FILE_LAYOUT_MODE, "split") ?: "split"
        set(value) = write { putString(Constants.PrefKeys.FILE_LAYOUT_MODE, value) }

    var treeExpandedPaths: Set<String>
        get() = prefs.getStringSet(Constants.PrefKeys.TREE_EXPANDED_PATHS, emptySet()) ?: emptySet()
        set(value) = write { putStringSet(Constants.PrefKeys.TREE_EXPANDED_PATHS, value) }

    private fun write(block: SharedPreferences.Editor.() -> Unit) {
        val ed = pendingEdit
        if (ed != null) {
            ed.block()
        } else {
            prefs.edit().apply { block(); apply() }
        }
    }
}
