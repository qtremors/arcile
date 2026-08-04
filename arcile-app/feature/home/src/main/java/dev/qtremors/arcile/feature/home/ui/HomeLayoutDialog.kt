package dev.qtremors.arcile.feature.home.ui

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.qtremors.arcile.core.storage.domain.HomeLayoutPreferences
import dev.qtremors.arcile.core.storage.domain.HomeSectionIds
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.rememberArcileHaptics
import dev.qtremors.arcile.core.ui.theme.ExpressiveShapes
import dev.qtremors.arcile.core.ui.theme.bounceClickable
import kotlinx.coroutines.delay

internal data class HomeSectionDefinition(
    val id: String,
    @param:StringRes val nameRes: Int,
    val icon: ImageVector
)

internal val HomeSectionCatalog = listOf(
    HomeSectionDefinition(HomeSectionIds.STORAGE, R.string.home_storage, Icons.Default.Storage),
    HomeSectionDefinition(HomeSectionIds.CATEGORIES, R.string.categories, Icons.Default.GridView),
    HomeSectionDefinition(HomeSectionIds.QUICK_ACCESS, R.string.quick_access, Icons.Default.Folder),
    HomeSectionDefinition(HomeSectionIds.UTILITIES, R.string.utilities, Icons.Default.Build),
    HomeSectionDefinition(HomeSectionIds.RECENT_FILES, R.string.recent_files, Icons.Default.History)
)

private data class HomeSectionDraft(
    val definition: HomeSectionDefinition,
    val enabled: Boolean
)

@Composable
internal fun HomeLayoutDialog(
    preferences: HomeLayoutPreferences,
    onDismiss: () -> Unit,
    onApply: (HomeLayoutPreferences) -> Unit
) {
    val catalogById = remember { HomeSectionCatalog.associateBy(HomeSectionDefinition::id) }
    var draftOrderedIds by rememberSaveable(preferences.orderedSectionIds) {
        mutableStateOf<List<String>>(preferences.orderedSectionIds.toList())
    }
    var draftEnabledIds by rememberSaveable(preferences.enabledSectionIds) {
        mutableStateOf<List<String>>(preferences.enabledSectionIds.toList())
    }
    val draftSections = draftOrderedIds.mapNotNull { id ->
        catalogById[id]?.let { definition ->
            HomeSectionDraft(
                definition = definition,
                enabled = id in draftEnabledIds
            )
        }
    }
    val haptics = rememberArcileHaptics()
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    var draggedId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val currentDraftSections = rememberUpdatedState(draftSections)
    val currentDraggedId = rememberUpdatedState(draggedId)

    fun moveDraggedSection(currentIndex: Int, currentOffset: Float, itemHeight: Float) {
        val targetIndex = when {
            currentOffset > itemHeight / 2f && currentIndex < draftSections.lastIndex -> currentIndex + 1
            currentOffset < -itemHeight / 2f && currentIndex > 0 -> currentIndex - 1
            else -> return
        }
        haptics.selectionChanged()
        draftOrderedIds = ArrayList(draftOrderedIds).apply {
            add(targetIndex, removeAt(currentIndex))
        }
        dragOffset = currentOffset + if (targetIndex > currentIndex) -itemHeight else itemHeight
    }

    LaunchedEffect(draggedId) {
        while (draggedId != null) {
            val currentIndex = currentDraftSections.value.indexOfFirst {
                it.definition.id == draggedId
            }
            val itemInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull {
                it.index == currentIndex
            }
            if (itemInfo != null) {
                val threshold = with(density) { 48.dp.toPx() }
                val itemTop = itemInfo.offset + dragOffset
                val itemBottom = itemTop + itemInfo.size
                val viewportHeight = listState.layoutInfo.viewportSize.height
                val scrollAmount = when {
                    itemTop < threshold -> (itemTop - threshold) / 5f
                    itemBottom > viewportHeight - threshold ->
                        (itemBottom - (viewportHeight - threshold)) / 5f
                    else -> 0f
                }
                if (scrollAmount != 0f) {
                    listState.scrollBy(scrollAmount)
                    dragOffset += scrollAmount
                    moveDraggedSection(currentIndex, dragOffset, itemInfo.size.toFloat())
                }
            }
            delay(16)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.86f)
                .widthIn(max = 560.dp),
            shape = ExpressiveShapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(top = 24.dp, bottom = 16.dp)
            ) {
                Text(
                    text = stringResource(R.string.home_edit_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                Text(
                    text = stringResource(R.string.home_edit_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = ExpressiveShapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    LazyColumn(state = listState) {
                        itemsIndexed(
                            items = draftSections,
                            key = { _, item -> item.definition.id }
                        ) { index, draft ->
                            val isDragging = draggedId == draft.definition.id
                            val rowColor by animateColorAsState(
                                targetValue = if (isDragging) {
                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                } else {
                                    Color.Transparent
                                },
                                label = "homeSectionDragColor"
                            )
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateItem()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .graphicsLayer {
                                            translationY = if (isDragging) dragOffset else 0f
                                            if (isDragging) {
                                                scaleX = 1.02f
                                                scaleY = 1.02f
                                                shadowElevation = 8.dp.toPx()
                                            }
                                        }
                                        .background(rowColor)
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Surface(
                                        modifier = Modifier.size(40.dp),
                                        shape = ExpressiveShapes.medium,
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = draft.definition.icon,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                    Text(
                                        text = stringResource(draft.definition.nameRes),
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    val visibilityDescription = stringResource(
                                        R.string.home_show_section,
                                        stringResource(draft.definition.nameRes)
                                    )
                                    Switch(
                                        checked = draft.enabled,
                                        onCheckedChange = { enabled ->
                                            haptics.toggleMenu()
                                            draftEnabledIds = ArrayList(draftEnabledIds).apply {
                                                if (enabled) {
                                                    if (draft.definition.id !in this) add(draft.definition.id)
                                                } else {
                                                    remove(draft.definition.id)
                                                }
                                            }
                                        },
                                        thumbContent = {
                                            Icon(
                                                imageVector = if (draft.enabled) Icons.Default.Check else Icons.Default.Close,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        modifier = Modifier.semantics {
                                            contentDescription = visibilityDescription
                                        }
                                    )
                                    var itemCoordinates: LayoutCoordinates? by remember {
                                        mutableStateOf(null)
                                    }
                                    var previousRootPosition by remember {
                                        mutableStateOf(Offset.Zero)
                                    }
                                    Icon(
                                        imageVector = Icons.Default.DragHandle,
                                        contentDescription = stringResource(
                                            R.string.home_reorder_section,
                                            stringResource(draft.definition.nameRes)
                                        ),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .clip(ExpressiveShapes.small)
                                            .onGloballyPositioned { coordinates ->
                                                itemCoordinates = coordinates
                                            }
                                            .pointerInput(draft.definition.id) {
                                                detectDragGestures(
                                                    onDragStart = { offset ->
                                                        draggedId = draft.definition.id
                                                        dragOffset = 0f
                                                        val coordinates = itemCoordinates
                                                        if (coordinates != null && coordinates.isAttached) {
                                                            previousRootPosition = coordinates.localToRoot(offset)
                                                        }
                                                        haptics.selectionStart()
                                                    },
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        val currentIndex = currentDraftSections.value.indexOfFirst {
                                                            it.definition.id == draft.definition.id
                                                        }
                                                        if (
                                                            currentIndex == -1 ||
                                                            currentDraggedId.value != draft.definition.id
                                                        ) return@detectDragGestures
                                                        val coordinates = itemCoordinates
                                                        val deltaY = if (
                                                            coordinates != null && coordinates.isAttached
                                                        ) {
                                                            val currentRootPosition = coordinates.localToRoot(
                                                                change.position
                                                            )
                                                            val distance = currentRootPosition.y -
                                                                previousRootPosition.y
                                                            previousRootPosition = currentRootPosition
                                                            distance
                                                        } else {
                                                            dragAmount.y
                                                        }
                                                        dragOffset += deltaY
                                                        val itemHeight = listState.layoutInfo.visibleItemsInfo
                                                            .firstOrNull { it.index == currentIndex }
                                                            ?.size
                                                            ?.toFloat()
                                                            ?: 60.dp.toPx()
                                                        moveDraggedSection(currentIndex, dragOffset, itemHeight)
                                                    },
                                                    onDragCancel = {
                                                        draggedId = null
                                                        dragOffset = 0f
                                                    },
                                                    onDragEnd = {
                                                        draggedId = null
                                                        dragOffset = 0f
                                                    }
                                                )
                                            }
                                            .padding(8.dp)
                                    )
                                }
                                if (index < draftSections.lastIndex && !isDragging) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val resetClick = {
                        haptics.toggleMenu()
                        draftOrderedIds = HomeSectionIds.ALL
                        draftEnabledIds = HomeSectionIds.ALL
                    }
                    TextButton(
                        onClick = resetClick,
                        modifier = Modifier.bounceClickable(onClick = resetClick),
                        shape = ExpressiveShapes.medium
                    ) {
                        Text(stringResource(R.string.reset))
                    }
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
                    val dismissClick = onDismiss
                    TextButton(
                        onClick = dismissClick,
                        modifier = Modifier.bounceClickable(onClick = dismissClick),
                        shape = ExpressiveShapes.medium
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                    val applyClick = {
                        onApply(
                            HomeLayoutPreferences(
                                orderedSectionIds = draftOrderedIds,
                                enabledSectionIds = draftEnabledIds.toSet()
                            )
                        )
                    }
                    Button(
                        onClick = applyClick,
                        modifier = Modifier.bounceClickable(onClick = applyClick),
                        shape = ExpressiveShapes.medium
                    ) {
                        Text(stringResource(R.string.apply))
                    }
                }
            }
        }
    }
}
