package dev.qtremors.arcile.core.runtime.di

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi

data class ArcileDispatchers(
    val io: CoroutineDispatcher,
    val default: CoroutineDispatcher,
    val main: CoroutineDispatcher,
    val storage: CoroutineDispatcher
) {
    companion object {
        /**
         * The production set, shared by Hilt and by constructors that default it outside DI.
         * [storage] is one process-wide pool of two threads, so storage scans throttle each
         * other no matter which class launches them.
         */
        @OptIn(ExperimentalCoroutinesApi::class)
        val Standard: ArcileDispatchers by lazy {
            ArcileDispatchers(
                io = Dispatchers.IO,
                default = Dispatchers.Default,
                main = Dispatchers.Main,
                storage = Dispatchers.IO.limitedParallelism(2)
            )
        }
    }
}
