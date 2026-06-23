package com.aidev.terminal.data

import com.aidev.terminal.domain.BackupRepository
import com.aidev.terminal.domain.BackupResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(JUnit4::class)
class BackupRepositoryTest {

    private val repository: BackupRepository = BackupRepositoryImpl()

    @Test
    fun testGetBackupItemsReturnsNonEmptyList() = runTest {
        val items = repository.getBackupItems().first()
        assertTrue(items.isNotEmpty())
        assertTrue(items.any { it.id == "ubuntu_config" })
        assertTrue(items.any { it.id == "ubuntu_packages" })
    }

    @Test
    fun testExecuteBackupReturnsSuccess() = runTest {
        val results = repository.executeBackup(listOf("ubuntu_config")).toList()
        assertTrue(results.isNotEmpty())
        val lastResult = results.last()
        assertEquals(BackupResult.ResultType.SUCCESS, lastResult.type)
    }

    @Test
    fun testExecuteBackupEmitsProgressBeforeSuccess() = runTest {
        val results = repository.executeBackup(listOf("ubuntu_config")).toList()
        assertTrue(results.size >= 2)
        assertEquals(BackupResult.ResultType.PROGRESS, results[0].type)
        assertEquals(BackupResult.ResultType.SUCCESS, results.last().type)
    }

    @Test
    fun testGetBackupHistoryReturnsEmptyList() = runTest {
        val history = repository.getBackupHistory().first()
        assertTrue(history.isEmpty())
    }

    @Test
    fun testBackupItemsHaveRequiredFields() = runTest {
        val items = repository.getBackupItems().first()
        items.forEach { item ->
            assertTrue(item.id.isNotBlank())
            assertTrue(item.name.isNotBlank())
            assertTrue(item.desc.isNotBlank())
        }
    }
}
