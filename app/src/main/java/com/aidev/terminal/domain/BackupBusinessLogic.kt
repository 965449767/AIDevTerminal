package com.aidev.terminal.domain

import kotlinx.coroutines.flow.Flow

class BackupBusinessLogic(
    private val repository: BackupRepository,
    private val coroutineManager: CoroutineManager = CoroutineManager()
) {
    fun executeBackup(items: List<String>): Flow<BackupResult> {
        return repository.executeBackup(items)
    }

    fun getBackupItems(): Flow<List<BackupItem>> {
        return repository.getBackupItems()
    }

    fun validateBackupItems(items: List<String>): Boolean {
        if (items.isEmpty()) return false
        return items.all { it.isNotBlank() }
    }

    fun getBackupHistory(): Flow<List<BackupHistory>> {
        return repository.getBackupHistory()
    }

    fun cancel() {
        coroutineManager.cancelAll()
    }
}
