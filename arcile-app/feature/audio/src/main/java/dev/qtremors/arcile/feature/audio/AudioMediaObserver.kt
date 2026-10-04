package dev.qtremors.arcile.feature.audio

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject

internal class AudioMediaObserver @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    val changes: Flow<Unit> = callbackFlow {
        val resolver = context.contentResolver
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        resolver.registerContentObserver(
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
            true,
            observer
        )
        awaitClose { resolver.unregisterContentObserver(observer) }
    }
}
