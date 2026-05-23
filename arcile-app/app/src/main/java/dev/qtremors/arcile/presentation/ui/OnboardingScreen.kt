package dev.qtremors.arcile.presentation.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Source
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.FilledTonalButton
import coil.compose.AsyncImage
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.R
import dev.qtremors.arcile.presentation.onboarding.OnboardingStep
import dev.qtremors.arcile.presentation.onboarding.OnboardingUiState
import dev.qtremors.arcile.presentation.ui.components.settings.AccentColorPickerSheet
import dev.qtremors.arcile.presentation.ui.components.settings.accentLabelRes
import dev.qtremors.arcile.presentation.ui.components.settings.ThemeModeSelector
import dev.qtremors.arcile.ui.theme.ThemeState
import dev.qtremors.arcile.ui.theme.spacing
import kotlin.math.absoluteValue

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    currentThemeState: ThemeState,
    onThemeChange: (ThemeState) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onStepSelected: (OnboardingStep) -> Unit,
    onOpenStoragePermissionSettings: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    showOlderAndroidWarning: Boolean = false
) {
    val steps = OnboardingStep.entries.toTypedArray()
    val pagerState = rememberPagerState(pageCount = { steps.size })

    // Sync Pager -> ViewModel
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            if (state.step != steps[page]) {
                onStepSelected(steps[page])
            }
        }
    }

    // Sync ViewModel -> Pager
    LaunchedEffect(state.step) {
        val targetPage = steps.indexOf(state.step)
        if (pagerState.currentPage != targetPage) {
            pagerState.animateScrollToPage(
                page = targetPage,
                animationSpec = tween(durationMillis = 420)
            )
        }
    }

    val isPermissionOrDonePage = state.step == OnboardingStep.SetupPermissions ||
                                 state.step == OnboardingStep.Done
                                 
    // Allow swiping unless they are blocked on a permission step
    val canSwipe = !isPermissionOrDonePage || state.canContinue

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            OnboardingHeader(
                state = state,
                stepsCount = steps.size,
                currentPage = pagerState.currentPage,
                onBack = onBack,
                onSkip = onSkip
            )
        },
        bottomBar = {
            OnboardingBottomBar(
                state = state,
                onNext = onNext,
                onOpenStoragePermissionSettings = onOpenStoragePermissionSettings,
                onRequestNotificationPermission = onRequestNotificationPermission
            )
        }
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            userScrollEnabled = canSwipe,
            beyondViewportPageCount = 1
        ) { page ->
            val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue
            val alpha = 1f - (pageOffset.coerceIn(0f, 1f) * 0.18f)
            val scale = 1f - (pageOffset.coerceIn(0f, 1f) * 0.03f)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        this.alpha = alpha
                        this.scaleX = scale
                        this.scaleY = scale
                    }
            ) {
                when (steps[page]) {
                    OnboardingStep.WelcomeAndFeatures -> OnboardingWelcomeAndFeatures()
                    OnboardingStep.Privacy -> OnboardingPrivacy()
                    OnboardingStep.Theme -> OnboardingTheme(
                        currentThemeState = currentThemeState,
                        onThemeChange = onThemeChange
                    )
                    OnboardingStep.SetupPermissions -> OnboardingSetupPermissions(
                        state = state,
                        showOlderAndroidWarning = showOlderAndroidWarning
                    )
                    OnboardingStep.Done -> OnboardingDone()
                }
            }
        }
    }
}

@Composable
private fun OnboardingHeader(
    state: OnboardingUiState,
    stepsCount: Int,
    currentPage: Int,
    onBack: () -> Unit,
    onSkip: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = MaterialTheme.spacing.large)
                .padding(top = MaterialTheme.spacing.space12, bottom = MaterialTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space12)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state.step != OnboardingStep.WelcomeAndFeatures && state.step != OnboardingStep.Done) {
                    FilledTonalButton(
                        onClick = onBack,
                        modifier = Modifier
                            .width(92.dp)
                            .defaultMinSize(minHeight = 44.dp)
                    ) {
                        Text(stringResource(R.string.back))
                    }
                } else {
                    Spacer(modifier = Modifier.size(width = 92.dp, height = 44.dp))
                }

                // Dot Indicators
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(stepsCount) { index ->
                        val isSelected = index == currentPage
                        val width by animateDpAsState(
                            targetValue = if (isSelected) 24.dp else 8.dp,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy),
                            label = "indicator_width"
                        )
                        val color by animateColorAsState(
                            targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                            label = "indicator_color"
                        )
                        Box(
                            modifier = Modifier
                                .height(8.dp)
                                .width(width)
                                .clip(CircleShape)
                                .background(color)
                        )
                    }
                }

                if (state.step == OnboardingStep.WelcomeAndFeatures ||
                    state.step == OnboardingStep.Privacy ||
                    state.step == OnboardingStep.Theme
                ) {
                    FilledTonalButton(
                        onClick = onSkip,
                        modifier = Modifier
                            .width(92.dp)
                            .defaultMinSize(minHeight = 44.dp)
                    ) {
                        Text(stringResource(R.string.onboarding_skip))
                    }
                } else {
                    Spacer(modifier = Modifier.size(width = 92.dp, height = 44.dp))
                }
            }
        }
    }
}

@Composable
private fun OnboardingBottomBar(
    state: OnboardingUiState,
    onNext: () -> Unit,
    onOpenStoragePermissionSettings: () -> Unit,
    onRequestNotificationPermission: () -> Unit
) {
    AnimatedContent(
        targetState = state.step,
        transitionSpec = {
            fadeIn(animationSpec = tween(220)) + slideInVertically(
                animationSpec = tween(220),
                initialOffsetY = { it / 5 }
            ) togetherWith fadeOut(animationSpec = tween(160)) using SizeTransform(clip = false)
        },
        label = "bottom_bar_animation",
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = MaterialTheme.spacing.large, vertical = MaterialTheme.spacing.space12)
    ) { step ->
        Column(
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space12),
            modifier = Modifier.fillMaxWidth()
        ) {
            when (step) {
                OnboardingStep.SetupPermissions -> {
                    Button(
                        onClick = onOpenStoragePermissionSettings,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 56.dp)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null)
                        Spacer(modifier = Modifier.width(MaterialTheme.spacing.small))
                        Text(stringResource(R.string.onboarding_open_storage_settings))
                    }
                    if (state.notificationPermissionRequired && !state.hasNotificationPermission) {
                        FilledTonalButton(
                            onClick = onRequestNotificationPermission,
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 56.dp)
                        ) {
                            Icon(Icons.Default.Notifications, contentDescription = null)
                            Spacer(modifier = Modifier.width(MaterialTheme.spacing.small))
                            Text(stringResource(R.string.onboarding_enable_notifications))
                        }
                    }
                    Button(
                        onClick = onNext,
                        enabled = state.hasStoragePermission,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 56.dp)
                    ) {
                        Text(stringResource(R.string.onboarding_finish))
                    }
                }
                OnboardingStep.Done -> {
                    Button(
                        onClick = onNext,
                        enabled = !state.isCompleting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 56.dp)
                    ) {
                        Text(stringResource(R.string.onboarding_finish))
                    }
                }
                else -> {
                    Button(
                        onClick = onNext,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 56.dp)
                    ) {
                        Text(stringResource(R.string.onboarding_continue))
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingPage(
    icon: ImageVector? = null,
    customIcon: (@Composable () -> Unit)? = null,
    title: String,
    description: String,
    hasBodyContent: Boolean = false,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MaterialTheme.spacing.large)
            .padding(
                top = if (hasBodyContent) MaterialTheme.spacing.large else MaterialTheme.spacing.extraLarge,
                bottom = if (hasBodyContent) 184.dp else 144.dp
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = if (hasBodyContent) {
            Arrangement.Top
        } else {
            Arrangement.Center
        }
    ) {
        Column(
            modifier = Modifier.widthIn(max = 560.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                tonalElevation = 4.dp,
                modifier = Modifier.size(if (hasBodyContent) 88.dp else 120.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (customIcon != null) {
                        customIcon()
                    } else if (icon != null) {
                        Icon(
                            icon,
                            contentDescription = null,
                            modifier = Modifier.size(if (hasBodyContent) 40.dp else 56.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(if (hasBodyContent) MaterialTheme.spacing.large else MaterialTheme.spacing.extraLarge))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(MaterialTheme.spacing.space12))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(if (hasBodyContent) MaterialTheme.spacing.large else MaterialTheme.spacing.extraLarge))
            content()
        }
    }
}

@Composable
private fun OnboardingWelcomeAndFeatures() {
    OnboardingPage(
        customIcon = {
            AsyncImage(
                model = R.mipmap.ic_launcher,
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier.fillMaxSize()
            )
        },
        title = stringResource(R.string.onboarding_welcome_title),
        description = stringResource(R.string.onboarding_intro_description),
        hasBodyContent = true
    ) {
        FeatureRow(Icons.Default.Storage, stringResource(R.string.onboarding_feature_multi_volume), stringResource(R.string.onboarding_feature_multi_volume_description))
        FeatureRow(Icons.Default.Bolt, stringResource(R.string.onboarding_feature_batch_operations), stringResource(R.string.onboarding_feature_batch_operations_description))
        FeatureRow(Icons.Default.Category, stringResource(R.string.onboarding_feature_instant_categories), stringResource(R.string.onboarding_feature_instant_categories_description))
        FeatureRow(Icons.Default.RestoreFromTrash, stringResource(R.string.onboarding_feature_trash_subsystem), stringResource(R.string.onboarding_feature_trash_subsystem_description))
    }
}

@Composable
private fun OnboardingPrivacy() {
    OnboardingPage(
        icon = Icons.Default.PrivacyTip,
        title = stringResource(R.string.onboarding_privacy_title),
        description = stringResource(R.string.onboarding_privacy_description),
        hasBodyContent = true
    ) {
        FeatureRow(androidx.compose.material.icons.Icons.Default.CloudOff, stringResource(R.string.onboarding_privacy_offline), stringResource(R.string.onboarding_privacy_offline_description))
        FeatureRow(androidx.compose.material.icons.Icons.Default.Block, stringResource(R.string.onboarding_privacy_trackers), stringResource(R.string.onboarding_privacy_trackers_description))
        FeatureRow(Icons.Default.Folder, stringResource(R.string.onboarding_privacy_local_file_access), stringResource(R.string.onboarding_privacy_local_file_access_description))
        FeatureRow(androidx.compose.material.icons.Icons.Default.Source, stringResource(R.string.onboarding_privacy_source), stringResource(R.string.onboarding_privacy_source_description))
    }
}

@Composable
private fun OnboardingTheme(
    currentThemeState: ThemeState,
    onThemeChange: (ThemeState) -> Unit
) {
    var showAccentPicker by remember { mutableStateOf(false) }

    OnboardingPage(
        icon = Icons.Default.Palette,
        title = stringResource(R.string.onboarding_theme_title),
        description = stringResource(R.string.onboarding_theme_description),
        hasBodyContent = true
    ) {
        ThemeModeSelector(
            currentMode = currentThemeState.themeMode,
            onModeSelected = { onThemeChange(currentThemeState.copy(themeMode = it)) }
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.accent_color)) },
            supportingContent = { Text(stringResource(accentLabelRes(currentThemeState.accentColor))) },
            leadingContent = {
                Icon(Icons.Default.ColorLens, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            },
            trailingContent = {
                Icon(Icons.Default.ChevronRight, contentDescription = null)
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .clickable { showAccentPicker = true }
        )
    }

    if (showAccentPicker) {
        AccentColorPickerSheet(
            currentAccent = currentThemeState.accentColor,
            onAccentSelected = {
                onThemeChange(currentThemeState.copy(accentColor = it))
                showAccentPicker = false
            },
            onDismiss = { showAccentPicker = false }
        )
    }
}

@Composable
private fun OnboardingSetupPermissions(
    state: OnboardingUiState,
    showOlderAndroidWarning: Boolean
) {
    OnboardingPage(
        icon = if (state.hasStoragePermission) Icons.Default.CheckCircle else Icons.Default.Folder,
        title = if (state.hasStoragePermission) {
            stringResource(R.string.onboarding_storage_ready_title)
        } else {
            stringResource(R.string.onboarding_storage_title)
        },
        description = if (state.hasStoragePermission) {
            stringResource(R.string.onboarding_storage_ready_description)
        } else {
            stringResource(R.string.onboarding_storage_description)
        },
        hasBodyContent = true
    ) {
        FeatureRow(Icons.Default.CheckCircle, stringResource(R.string.onboarding_storage_can_read), stringResource(R.string.onboarding_storage_can_read_description))
        FeatureRow(Icons.Default.PrivacyTip, stringResource(R.string.onboarding_storage_private), stringResource(R.string.onboarding_storage_private_description))
        FeatureRow(Icons.Default.WarningAmber, stringResource(R.string.onboarding_storage_restricted), stringResource(R.string.onboarding_storage_restricted_description))
        FeatureRow(
            icon = if (state.hasNotificationPermission) Icons.Default.CheckCircle else Icons.Default.Notifications,
            title = if (state.hasNotificationPermission) {
                stringResource(R.string.onboarding_notifications_ready_title)
            } else {
                stringResource(R.string.onboarding_notifications_title)
            },
            description = if (state.notificationPermissionRequired) {
                stringResource(R.string.onboarding_notifications_description)
            } else {
                stringResource(R.string.onboarding_notifications_not_required_description)
            }
        )
        AnimatedVisibility(visible = showOlderAndroidWarning) {
            FeatureRow(
                icon = Icons.Default.WarningAmber,
                title = stringResource(R.string.onboarding_limited_android_title),
                description = stringResource(R.string.onboarding_limited_android_description)
            )
        }
    }
}

@Composable
private fun OnboardingDone() {
    OnboardingPage(
        icon = Icons.Default.CheckCircle,
        title = stringResource(R.string.onboarding_done_title),
        description = stringResource(R.string.onboarding_done_description)
    )
}

@Composable
private fun FeatureRow(
    icon: ImageVector,
    title: String,
    description: String
) {
    val containerColor = MaterialTheme.colorScheme.surfaceContainer
    val iconContainerColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.primaryContainer,
        label = "onboardingFeatureIconContainer"
    )

    Surface(
        shape = MaterialTheme.shapes.large,
        color = containerColor,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = MaterialTheme.spacing.small)
    ) {
        Row(
            modifier = Modifier.padding(MaterialTheme.spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = iconContainerColor,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
