package dev.qtremors.arcile.core.runtime.di

import kotlinx.coroutines.CoroutineDispatcher

data class ArcileDispatchers(
    val io: CoroutineDispatcher,
    val default: CoroutineDispatcher,
    val main: CoroutineDispatcher,
    val storage: CoroutineDispatcher
)
