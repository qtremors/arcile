package dev.qtremors.arcile.core.privilege.android.remote

import android.system.ErrnoException
import android.system.OsConstants
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RemoteFailureTest {
    @Test
    fun `errno failures map to the typed remote contract`() {
        val expectations = mapOf(
            OsConstants.EACCES to RemoteFailureCode.ACCESS_DENIED,
            OsConstants.ENOENT to RemoteFailureCode.PATH_MISSING,
            OsConstants.EEXIST to RemoteFailureCode.PATH_ALREADY_EXISTS,
            OsConstants.EROFS to RemoteFailureCode.READ_ONLY_FILESYSTEM,
            OsConstants.ENOTSUP to RemoteFailureCode.UNSUPPORTED_FILE_TYPE,
            OsConstants.ENOSPC to RemoteFailureCode.INSUFFICIENT_STORAGE,
            OsConstants.EINTR to RemoteFailureCode.OPERATION_INTERRUPTED,
            OsConstants.EINVAL to RemoteFailureCode.INVALID_PATH
        )

        expectations.forEach { (errno, expectedCode) ->
            val failure = runCatching {
                remoteCall("/protected/test") {
                    throw ErrnoException("contract-test", errno)
                }
            }.exceptionOrNull() as RemoteFileException

            assertEquals(expectedCode, failure.failureCode)
        }
    }

    @Test
    fun `remote failure messages are bounded to one safe line`() {
        val failure = runCatching {
            remoteCall("/protected/test") {
                throw ErrnoException("first line\nsecret second line", OsConstants.EACCES)
            }
        }.exceptionOrNull() as RemoteFileException

        assertEquals(false, failure.message.orEmpty().contains('\n'))
        assertEquals(false, failure.message.orEmpty().contains("secret second line"))
    }
}
