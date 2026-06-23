package com.aidev.terminal.domain

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(JUnit4::class)
class BackupBusinessLogicTest {

    private lateinit var repository: BackupRepository
    private lateinit var coroutineManager: CoroutineManager
    private lateinit var businessLogic: BackupBusinessLogic

    private val testItems = listOf(
        BackupItem("item1", "Item 1", "Description 1", false, true, { emptyList() }, true),
        BackupItem("item2", "Item 2", "Description 2", false, true, { emptyList() }, true)
    )

    @Before
    fun setUp() {
        repository = mock()
        coroutineManager = mock()
        businessLogic = BackupBusinessLogic(repository, coroutineManager)
    }

    @Test
    fun testGetBackupItemsDelegatesToRepository() = runTest {
        whenever(repository.getBackupItems()).thenReturn(flowOf(testItems))
        val items = businessLogic.getBackupItems().first()
        assertEquals(2, items.size)
        assertEquals("item1", items[0].id)
        verify(repository).getBackupItems()
    }

    @Test
    fun testExecuteBackupDelegatesToRepository() = runTest {
        val backupResult = BackupResult(BackupResult.ResultType.SUCCESS, "备份完成")
        whenever(repository.executeBackup(listOf("item1"))).thenReturn(flowOf(backupResult))
        val result = businessLogic.executeBackup(listOf("item1")).first()
        assertEquals(BackupResult.ResultType.SUCCESS, result.type)
        verify(repository).executeBackup(listOf("item1"))
    }

    @Test
    fun testGetBackupHistoryDelegatesToRepository() = runTest {
        whenever(repository.getBackupHistory()).thenReturn(flowOf(emptyList()))
        val history = businessLogic.getBackupHistory().first()
        assertTrue(history.isEmpty())
        verify(repository).getBackupHistory()
    }

    @Test
    fun testValidateBackupItemsWithValidItemsReturnsTrue() {
        assertTrue(businessLogic.validateBackupItems(listOf("item1", "item2")))
    }

    @Test
    fun testValidateBackupItemsWithEmptyListReturnsFalse() {
        assertFalse(businessLogic.validateBackupItems(emptyList()))
    }

    @Test
    fun testValidateBackupItemsWithBlankItemReturnsFalse() {
        assertFalse(businessLogic.validateBackupItems(listOf("item1", "")))
    }

    @Test
    fun testCancelDelegatesToCoroutineManager() {
        businessLogic.cancel()
        verify(coroutineManager).cancelAll()
    }
}
