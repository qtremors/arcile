package dev.qtremors.arcile.core.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Screenshot
import org.junit.Assert.assertEquals
import org.junit.Test

class FileIconTest {

    @Test
    fun `uses specialized icons for recognized storage folders`() {
        assertEquals(
            Icons.Outlined.Download,
            getFolderIconVector("Download", "/storage/emulated/0/Download")
        )
        assertEquals(
            Icons.Outlined.CameraAlt,
            getFolderIconVector("Camera", "/storage/1234-5678/DCIM/Camera")
        )
        assertEquals(
            Icons.Outlined.Mic,
            getFolderIconVector("Recordings", "/storage/emulated/0/Recordings")
        )
        assertEquals(
            Icons.Outlined.Screenshot,
            getFolderIconVector("Screenshots", "/storage/emulated/0/Pictures/Screenshots")
        )
        assertEquals(
            Icons.Outlined.Image,
            getFolderIconVector("Pictures", "/storage/emulated/0/Pictures")
        )
    }

    @Test
    fun `keeps ordinary nested folders generic`() {
        assertEquals(
            Icons.Outlined.Folder,
            getFolderIconVector("Music", "/storage/emulated/0/Projects/Music")
        )
        assertEquals(Icons.Outlined.Folder, getFolderIconVector("Pictures"))
    }

    @Test
    fun `disabled recognized folder icons always use generic folder`() {
        assertEquals(
            Icons.Outlined.Folder,
            getFolderIconVector(
                "DCIM",
                "/storage/emulated/0/DCIM",
                folderIconsEnabled = false
            )
        )
    }
}
