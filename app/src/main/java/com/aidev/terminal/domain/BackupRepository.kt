package com.aidev.terminal.domain

import kotlinx.coroutines.flow.Flow

interface BackupRepository {

    fun getBackupItems(): Flow<List<BackupItem>>

    fun executeBackup(items: List<String>): Flow<BackupResult>

    fun getBackupHistory(): Flow<List<BackupHistory>>
}
