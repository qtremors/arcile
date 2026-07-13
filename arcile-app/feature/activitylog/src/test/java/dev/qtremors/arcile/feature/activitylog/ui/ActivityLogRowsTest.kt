package dev.qtremors.arcile.feature.activitylog.ui

import dev.qtremors.arcile.core.storage.domain.ActivityLogOperationStatus
import dev.qtremors.arcile.core.ui.R
import org.junit.Assert.assertEquals
import org.junit.Test

class ActivityLogRowsTest {

    @Test
    fun `known operation names map to localized labels`() {
        assertEquals(R.string.activity_log_operation_copy, "COPY".activityLogOperationNameRes())
        assertEquals(R.string.activity_log_operation_move, "MOVE".activityLogOperationNameRes())
        assertEquals(R.string.activity_log_operation_trash, "TRASH".activityLogOperationNameRes())
        assertEquals(R.string.activity_log_operation_delete, "DELETE".activityLogOperationNameRes())
        assertEquals(R.string.activity_log_operation_shred, "SHRED".activityLogOperationNameRes())
        assertEquals(R.string.activity_log_operation_create_fake, "CREATE_FAKE".activityLogOperationNameRes())
        assertEquals(R.string.activity_log_operation_extract_archive, "EXTRACT_ARCHIVE".activityLogOperationNameRes())
        assertEquals(R.string.activity_log_operation_create_archive, "CREATE_ARCHIVE".activityLogOperationNameRes())
        assertEquals(R.string.activity_log_operation_unknown, "future-operation".activityLogOperationNameRes())
    }

    @Test
    fun `every operation status maps to its localized label`() {
        assertEquals(R.string.activity_log_status_running, ActivityLogOperationStatus.RUNNING.activityLogStatusRes())
        assertEquals(R.string.activity_log_status_completed, ActivityLogOperationStatus.COMPLETED.activityLogStatusRes())
        assertEquals(R.string.activity_log_status_failed, ActivityLogOperationStatus.FAILED.activityLogStatusRes())
        assertEquals(R.string.activity_log_status_cancelled, ActivityLogOperationStatus.CANCELLED.activityLogStatusRes())
    }
}
