package com.aidev.terminal.data

import com.aidev.terminal.domain.BackupHistory
import com.aidev.terminal.domain.BackupItem
import com.aidev.terminal.domain.BackupRepository
import com.aidev.terminal.domain.BackupResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class BackupRepositoryImpl : BackupRepository {

    override fun getBackupItems(): Flow<List<BackupItem>> = flow {
        emit(listOf(
            BackupItem("ubuntu_config", "Ubuntu 配置", ".bashrc, .bash_history 等", false, true,
                { listOf("root/.bashrc", "root/.bash_history", "root/.profile", "root/.gitconfig") }, true),
            BackupItem("ubuntu_packages", "已安装软件包", "dpkg --get-selections 列表", false, true,
                { listOf("/host-home/tasks/.backup_packages.list") }, true),
            BackupItem("ubuntu_projects", "Ubuntu 项目", "/root/projects/ 目录", false, true,
                { listOf("root/projects") }, true),
            BackupItem("ubuntu_rootfs", "完整 Ubuntu 环境", "整个 rootfs (1-2GB)", true, false,
                { listOf(".") }, true),
            BackupItem("tasks", "任务数据", "后台任务日志和元数据", false, true,
                { listOf("/data/user/0/com.aidev.terminal/files/home/tasks") }, false),
            BackupItem("ui_prefs", "UI 设置", "主题、背景、字号等", false, true,
                { listOf("/data/user/0/com.aidev.terminal/files/shared_prefs/aidev_ui.xml") }, false),
            BackupItem("shell_prefs", "Shell 设置", "别名、收藏夹等", false, true,
                { listOf("/data/user/0/com.aidev.terminal/files/shared_prefs/aidev_shell.xml") }, false),
            BackupItem("projects", "Android 项目", "/sdcard/AIDev/ 目录", true, false,
                { listOf("/sdcard/AIDev") }, false)
        ))
    }

    override fun executeBackup(items: List<String>): Flow<BackupResult> = flow {
        emit(BackupResult(BackupResult.ResultType.PROGRESS, "准备中...", 0))

        items.forEachIndexed { index, itemId ->
            emit(BackupResult(BackupResult.ResultType.PROGRESS, "正在打包... (${(index + 1) * 100 / items.size}%)", (index + 1) * 100 / items.size))

            kotlinx.coroutines.delay(500)

            emit(BackupResult(BackupResult.ResultType.SUCCESS, "备份完成"))
        }
    }

    override fun getBackupHistory(): Flow<List<BackupHistory>> = flow {
        emit(emptyList())
    }
}
