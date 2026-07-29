package dev.qtremors.arcile.core.ui.category

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.ui.ArcileDropdownMenu
import dev.qtremors.arcile.core.ui.ArcileDropdownMenuItem
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.SearchFiltersSheet
import dev.qtremors.arcile.core.ui.SplitButtonGroup
import dev.qtremors.arcile.core.ui.ToolbarAction
import dev.qtremors.arcile.core.ui.keyboardInputField
import dev.qtremors.arcile.core.ui.lists.ActiveFiltersRow
import dev.qtremors.arcile.core.ui.rememberArcileHaptics
import dev.qtremors.arcile.core.ui.theme.bounceClickable
import dev.qtremors.arcile.core.storage.domain.SearchFilters

/**
 * A category-specific action shown in the shared floating overflow menu.
 *
 * Category screens own the meaning of each action. The shared shell owns only
 * presentation, selection indication, dismissal, and interaction feedback.
 */
data class CategoryMenuAction(
    val label: String,
    val icon: ImageVector,
    val selected: Boolean = false,
    val enabled: Boolean = true,
    val onClick: () -> Unit
)

/**
 * A destination shown in the shared floating category navigation bar.
 */
data class CategoryTabSpec(
    val label: String,
    val icon: ImageVector,
    val selected: Boolean,
    val onClick: () -> Unit
)

/**
 * Image-gallery-derived top chrome shared by rich file categories.
 *
 * Search state and menu actions remain feature-owned so using the shell cannot
 * couple a category to image, video, audio, or document domain behavior.
 */
@Composable
fun CategoryFloatingTopBar(
    query: String,
    searchPlaceholder: String,
    showSearchBar: Boolean,
    menuActions: List<CategoryMenuAction>,
    onSearchClick: () -> Unit,
    onCloseSearch: () -> Unit,
    onQueryChange: (String) -> Unit,
    onViewSort: () -> Unit,
    onNavigateBack: () -> Unit,
    searchFilters: SearchFilters? = null,
    onSearchFiltersChange: ((SearchFilters) -> Unit)? = null,
    showCategoryFilter: Boolean = false,
    modifier: Modifier = Modifier
) {
    val haptics = rememberArcileHaptics()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    var showOverflow by rememberSaveable { mutableStateOf(false) }
    var showSearchFilters by rememberSaveable { mutableStateOf(false) }

    if (showSearchBar) {
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
        Column(
            modifier = modifier
                .statusBarsPadding()
                .padding(vertical = 8.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.85f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 4.dp,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(48.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val closeSearch = {
                        haptics.selectionChanged()
                        onCloseSearch()
                    }
                    IconButton(
                        onClick = closeSearch,
                        modifier = Modifier
                            .clip(CircleShape)
                            .bounceClickable(onClick = closeSearch)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                            .focusRequester(focusRequester)
                            .keyboardInputField(),
                        singleLine = true,
                        decorationBox = { innerTextField ->
                            if (query.isEmpty()) {
                                Text(
                                    text = searchPlaceholder,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                            innerTextField()
                        }
                    )
                    val searchActions = buildList {
                        if (query.isNotEmpty()) {
                            add(
                                ToolbarAction(
                                    icon = Icons.Default.Clear,
                                    contentDescription = stringResource(R.string.action_clear),
                                    onClick = {
                                        haptics.selectionChanged()
                                        onQueryChange("")
                                    }
                                )
                            )
                        }
                        if (searchFilters != null && onSearchFiltersChange != null) {
                            add(
                                ToolbarAction(
                                    icon = Icons.Default.FilterList,
                                    contentDescription = stringResource(R.string.action_filters),
                                    onClick = {
                                        haptics.selectionChanged()
                                        showSearchFilters = true
                                    }
                                )
                            )
                        }
                    }
                    if (searchActions.isNotEmpty()) {
                        SplitButtonGroup(
                            actions = searchActions,
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            height = 40.dp,
                            minWidth = 40.dp,
                            iconSize = 21.dp
                        )
                    }
                }
            }
            if (searchFilters != null && onSearchFiltersChange != null) {
                ActiveFiltersRow(
                    filters = searchFilters,
                    onClearFilter = onSearchFiltersChange
                )
            }
        }
        if (showSearchFilters && searchFilters != null && onSearchFiltersChange != null) {
            SearchFiltersSheet(
                currentFilters = searchFilters,
                onApplyFilters = onSearchFiltersChange,
                onDismiss = { showSearchFilters = false },
                showCategoryFilter = showCategoryFilter
            )
        }
        return
    }

    Box(
        modifier = modifier
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(56.dp)
    ) {
        val navigateBack = {
            haptics.selectionChanged()
            onNavigateBack()
        }
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.85f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 4.dp,
            shadowElevation = 4.dp,
            modifier = Modifier
                .size(48.dp)
                .align(Alignment.CenterStart)
                .bounceClickable(onClick = navigateBack)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .height(48.dp)
                .align(Alignment.CenterEnd)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SplitButtonGroup(
                    actions = listOf(
                        ToolbarAction(
                            icon = Icons.Default.Search,
                            contentDescription = stringResource(R.string.action_search),
                            onClick = {
                                haptics.selectionChanged()
                                onSearchClick()
                            }
                        ),
                        ToolbarAction(
                            icon = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = stringResource(R.string.action_sort),
                            onClick = {
                                haptics.selectionChanged()
                                onViewSort()
                            }
                        )
                    ),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.85f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    height = 48.dp,
                    minWidth = 48.dp,
                    iconSize = 24.dp
                )
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.85f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .bounceClickable {
                            haptics.toggleMenu()
                            showOverflow = true
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.action_more_options),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            val overflowItems = buildList<@Composable () -> Unit> {
                menuActions.forEach { action ->
                    add {
                        ArcileDropdownMenuItem(
                            text = {
                                Text(
                                    text = action.label,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            leadingIcon = {
                                Icon(action.icon, contentDescription = null)
                            },
                            trailingIcon = if (action.selected) {
                                {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null
                                    )
                                }
                            } else {
                                null
                            },
                            enabled = action.enabled,
                            isSelected = action.selected,
                            onClick = {
                                showOverflow = false
                                action.onClick()
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
            ArcileDropdownMenu(
                expanded = showOverflow,
                onDismissRequest = { showOverflow = false },
                items = overflowItems
            )
        }
    }
}

/**
 * Shared selection chrome matching the gallery count/size and selection controls.
 */
@Composable
fun CategorySelectionTopBar(
    selectedCountText: String,
    selectedSizeText: String,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberArcileHaptics()
    Box(
        modifier = modifier
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(56.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            tonalElevation = 6.dp,
            shadowElevation = 6.dp,
            modifier = Modifier
                .height(56.dp)
                .align(Alignment.CenterStart)
        ) {
            Row(
                modifier = Modifier.padding(start = 4.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val clearSelection = {
                    haptics.selectionChanged()
                    onClearSelection()
                }
                IconButton(
                    onClick = clearSelection,
                    modifier = Modifier
                        .clip(CircleShape)
                        .bounceClickable(onClick = clearSelection)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(Modifier.width(4.dp))
                Column {
                    Text(
                        text = selectedCountText,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = selectedSizeText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            tonalElevation = 6.dp,
            shadowElevation = 6.dp,
            modifier = Modifier
                .height(56.dp)
                .align(Alignment.CenterEnd)
        ) {
            Row(modifier = Modifier.padding(horizontal = 4.dp)) {
                CategorySelectionIconButton(
                    icon = Icons.Default.GridView,
                    description = stringResource(R.string.select_all)
                ) {
                    haptics.selectionChanged()
                    onSelectAll()
                }
                CategorySelectionIconButton(
                    icon = Icons.Default.SelectAll,
                    description = stringResource(R.string.invert_selection)
                ) {
                    haptics.selectionChanged()
                    onInvertSelection()
                }
            }
        }
    }
}

@Composable
private fun CategorySelectionIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .clip(CircleShape)
            .bounceClickable(onClick = onClick)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            modifier = Modifier.size(22.dp)
        )
    }
}

/**
 * Shared gallery-style tab navigation used inside floating category bottom chrome.
 */
@Composable
fun CategoryNavigationBar(
    tabs: List<CategoryTabSpec>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.85f),
                shape = CircleShape
            )
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tabs.forEach { tab ->
            CategoryTabItem(tab)
        }
    }
}

@Composable
private fun CategoryTabItem(tab: CategoryTabSpec) {
    val backgroundColor by animateColorAsState(
        targetValue = if (tab.selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            Color.Transparent
        },
        label = "categoryTabBackground"
    )
    val contentColor by animateColorAsState(
        targetValue = if (tab.selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "categoryTabContent"
    )
    val horizontalPadding by animateDpAsState(
        targetValue = if (tab.selected) 16.dp else 12.dp,
        label = "categoryTabPadding"
    )

    Surface(
        shape = CircleShape,
        color = backgroundColor,
        contentColor = contentColor,
        modifier = Modifier
            .height(44.dp)
            .clip(CircleShape)
            .bounceClickable(onClick = tab.onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = horizontalPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = tab.icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            AnimatedVisibility(
                visible = tab.selected,
                enter = expandHorizontally() + fadeIn(),
                exit = shrinkHorizontally() + fadeOut()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * Shared bottom-chrome motion and sizing.
 *
 * [supportingContent] is intentionally outside the flip surface for media-specific
 * UI such as Audio's mini player. The shell animates between normal and selection
 * content while the feature retains complete control over both payloads.
 */
@Composable
fun CategoryBottomChrome(
    visible: Boolean,
    selectionMode: Boolean,
    selectionBackProgress: Float = 0f,
    supportingContent: @Composable ColumnScope.() -> Unit = {},
    normalContent: @Composable () -> Unit,
    selectionContent: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val bottomOffset by animateDpAsState(
        targetValue = if (visible || selectionMode) 0.dp else 160.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "categoryBottomOffset"
    )
    val bottomAlpha by animateFloatAsState(
        targetValue = if (visible || selectionMode) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "categoryBottomAlpha"
    )
    val rotationX by animateFloatAsState(
        targetValue = if (selectionMode) 180f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "categoryBottomFlip"
    )
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .graphicsLayer {
                translationY = bottomOffset.toPx()
                alpha = bottomAlpha
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 600.dp)
        ) {
            supportingContent()
            Box(
                modifier = Modifier
                    .then(if (selectionMode) Modifier.fillMaxWidth() else Modifier)
                    .graphicsLayer {
                        this.rotationX = rotationX
                        cameraDistance = 12f * density.density
                        if (selectionMode && selectionBackProgress > 0f) {
                            val scale = 1f - selectionBackProgress * 0.15f
                            scaleX = scale
                            scaleY = scale
                            alpha = 1f - selectionBackProgress
                        }
                    }
                    .animateContentSize(),
                contentAlignment = Alignment.Center
            ) {
                if (rotationX <= 90f) {
                    normalContent()
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer { this.rotationX = 180f }
                    ) {
                        selectionContent()
                    }
                }
            }
        }
    }
}
