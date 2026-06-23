package com.aidev.terminal

import android.util.Log

/**
 * 统一错误处理工具类
 * 
 * 提供一致的错误处理机制，减少代码中的try-catch块
 * 
 * @author Terminal Team
 */
object ErrorHandler {
    
    /**
     * 执行代码块并捕获异常
     * 
     * @param block 要执行的代码块
     * @return Result.success(结果) 或 Result.failure(异常)
     */
    fun <T> execute(block: () -> T): Result<T> {
        return try {
            Result.success(block())
        } catch (e: Exception) {
            logError(e)
            Result.failure(e)
        }
    }
    
    /**
     * 记录错误日志
     * 
     * @param e 异常对象
     */
    private fun logError(e: Exception) {
        AIDevLogger.e("ErrorHandler", "Error occurred: ${e.message}", e)
    }
}