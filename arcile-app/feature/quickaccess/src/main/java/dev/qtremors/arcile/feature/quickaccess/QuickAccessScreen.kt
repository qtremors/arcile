package dev.qtremors.arcile.feature.quickaccess

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.theme.bounceClickable

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun QuickAccessScreen(
    state: QuickAccessState,
    actions: QuickAccessActions
) {
    var showCustomDialog by rememberSaveable { mutableStateOf(false) }
    var tempPath by rememberSaveable { mutableStateOf("") }
    var tempLabel by rememberSaveable { mutableStateOf("") }

    var isFabExpanded by rememberSaveable { mutableStateOf(false) }

    if (showCustomDialog) {
        QuickAccessCustomPathDialog(
            label = tempLabel,
            path = tempPath,
            onLabelChange = { tempLabel = it },
            onPathChange = { tempPath = it },
            onDismiss = { showCustomDialog = false },
            onConfirm = {
                actions.addCustomFolder(tempPath.trim(), tempLabel.trim())
                showCustomDialog = false
            }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.quick_access_manage_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = actions.navigateBack,
                        modifier = Modifier
                            .clip(CircleShape)
                            .bounceClickable(onClick = actions.navigateBack)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        },
        floatingActionButton = {
            QuickAccessAddFab(
                expanded = isFabExpanded,
                onExpandedChange = { isFabExpanded = it },
                onRequestCustomPath = {
                    tempPath = ""
                    tempLabel = ""
                    showCustomDialog = true
                },
                actions = actions
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            QuickAccessSections(
                state = state,
                actions = actions,
                modifier = Modifier.fillMaxSize()
            )

            if (isFabExpanded) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { isFabExpanded = false }
                        )
                )
            }
        }
    }
}
