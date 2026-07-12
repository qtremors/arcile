package dev.qtremors.arcile.feature.quickaccess

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import dev.qtremors.arcile.core.ui.theme.spacing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Reorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import dev.qtremors.arcile.core.ui.rememberArcileHaptics
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.delay
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.storage.domain.QuickAccessItem
import dev.qtremors.arcile.core.storage.domain.QuickAccessType
import dev.qtremors.arcile.feature.quickaccess.QuickAccessState
import dev.qtremors.arcile.core.ui.QuickAccessAppIcon
import dev.qtremors.arcile.core.ui.keyboardInputField
import dev.qtremors.arcile.core.ui.menus.ExpandableFabMenu
import dev.qtremors.arcile.core.ui.menus.FabMenuItem
import dev.qtremors.arcile.core.ui.theme.ExpressiveShapes
import dev.qtremors.arcile.core.ui.theme.bounceClickable

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun QuickAccessScreen(
    state: QuickAccessState,
    actions: QuickAccessActions
) {
    var showCustomDialog by rememberSaveable { mutableStateOf(false) }
    var showReorderSheet by remember { mutableStateOf(false) }

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

    if (showReorderSheet) {
        val pinnedItems = state.items.filter { it.isPinned }
        ArrangeQuickAccessDialog(
            pinnedItems = pinnedItems,
            onDismiss = { showReorderSheet = false },
            onApply = { reordered ->
                actions.reorderItems(reordered)
                showReorderSheet = false
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
                },
                actions = {
                    val pinnedItems = state.items.filter { it.isPinned }
                    if (pinnedItems.isNotEmpty()) {
                        val reorderClick = { showReorderSheet = true }
                        IconButton(
                            onClick = reorderClick,
                            modifier = Modifier
                                .clip(CircleShape)
                                .bounceClickable(onClick = reorderClick)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Reorder,
                                contentDescription = stringResource(R.string.quick_access_arrange_order)
                            )
                        }
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
