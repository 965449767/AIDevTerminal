package com.aidev.terminal.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

class CoroutineManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun <T> executeIoTask(block: suspend () -> T): Flow<T> = flow {
        emit(block())
    }.flowOn(Dispatchers.IO)

    fun <T> executeMainTask(block: suspend () -> T): Flow<T> = flow {
        emit(block())
    }.flowOn(Dispatchers.Main)

    fun launchCoroutine(context: CoroutineContext, block: suspend () -> Unit): Job {
        return scope.launch(context) { block() }
    }

    fun cancelAll() {
        scope.coroutineContext.cancelChildren()
    }
}
