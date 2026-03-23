package dev.qtremors.arcile.presentation.ui.components.menus
import dev.qtremors.arcile.R
import androidx.compose.ui.res.stringResource

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape

@Composable
fun ExpandableFabMenu(
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    fabIconRotation: Float,
    onCreateFileClick: () -> Unit,
    onCreateFolderClick: () -> Unit
) {
    val fabShape = if (isExpanded) MaterialTheme.shapes.extraLarge else MaterialTheme.shapes.large

    Column(horizontalAlignment = Alignment.End) {
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn() + slideInVertically(initialOffsetY = { 50 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { 50 })
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                ExtendedFloatingActionButton(
                    onClick = onCreateFileClick,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = MaterialTheme.shapes.extraLarge,
                    elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(
                        defaultElevation = 2.dp,
                        pressedElevation = 4.dp
                    ),
                    text = { Text("New File", style = MaterialTheme.typography.labelLarge) },
                    icon = { Icon(Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null) },
                    modifier = androidx.compose.ui.Modifier.padding(end = 2.dp)
                )
                ExtendedFloatingActionButton(
                    onClick = onCreateFolderClick,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = MaterialTheme.shapes.extraLarge,
                    elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(
                        defaultElevation = 2.dp,
                        pressedElevation = 4.dp
                    ),
                    text = { Text("New Folder", style = MaterialTheme.typography.labelLarge) },
                    icon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) },
                    modifier = androidx.compose.ui.Modifier.padding(end = 2.dp)
                )
            }
        }
        FloatingActionButton(
            onClick = onToggleExpand,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            shape = fabShape
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = stringResource(R.string.action_create_new),
                modifier = Modifier.rotate(fabIconRotation)
            )
        }
    }
}
