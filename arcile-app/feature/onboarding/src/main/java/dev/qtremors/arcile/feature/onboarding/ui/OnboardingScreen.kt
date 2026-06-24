package dev.qtremors.arcile.feature.onboarding.ui

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
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.feature.onboarding.OnboardingStep
import dev.qtremors.arcile.feature.onboarding.OnboardingUiState
import dev.qtremors.arcile.shared.ui.settings.AccentColorPickerSheet
import dev.qtremors.arcile.shared.ui.settings.accentLabelRes
import dev.qtremors.arcile.shared.ui.settings.ThemeModeSelector
import androidx.activity.compose.PredictiveBackHandler
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
    onStepSelected: (OnboardingStep) -> Unit,
    onOpenStoragePermissionSettings: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    showOlderAndroidWarning: Boolean = false,
    restoreState: OnboardingRestoreState = OnboardingRestoreState.Idle,
    onChooseRestoreBackup: () -> Unit = {},
    onApplyRestoreBackup: () -> Unit = {},
    onDismissRestoreBackup: () -> Unit = {},
    onRestartApp: () -> Unit = {}
) {
    val steps = remember {
        arrayOf(
            OnboardingStep.WelcomeAndFeatures,
            OnboardingStep.SetupPermissions
        )
    }
    val pagerState = rememberPagerState(pageCount = { steps.size })

    // Sync Pager -> ViewModel
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            if (state.step != OnboardingStep.Done && state.step != steps[page]) {
                onStepSelected(steps[page])
            }
        }
    }

    // Sync ViewModel -> Pager
    LaunchedEffect(state.step) {
        val targetStep = if (state.step == OnboardingStep.Done) {
            OnboardingStep.SetupPermissions
        } else {
            state.step
        }
        val targetPage = steps.indexOf(targetStep)
        if (pagerState.currentPage != targetPage) {
            pagerState.animateScrollToPage(
                page = targetPage,
                animationSpec = tween(durationMillis = 420)
            )
        }
    }

    PredictiveBackHandler(enabled = pagerState.currentPage > 0) { progressFlow ->
        var completed = false
        try {
            progressFlow.collect { backEvent ->
                pagerState.scrollToPage(page = 0, pageOffsetFraction = 1f - backEvent.progress)
            }
            completed = true
            onBack()
        } finally {
            if (!completed) {
                pagerState.animateScrollToPage(1)
            }
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
                onBack = onBack
            )
        },
        bottomBar = {
            OnboardingBottomBar(
                state = state,
                onNext = onNext
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
                    OnboardingStep.SetupPermissions -> OnboardingSetupPermissions(
                        state = state,
                        currentThemeState = currentThemeState,
                        onThemeChange = onThemeChange,
                        restoreState = restoreState,
                        onChooseRestoreBackup = onChooseRestoreBackup,
                        onOpenStoragePermissionSettings = onOpenStoragePermissionSettings,
                        onRequestNotificationPermission = onRequestNotificationPermission,
                        showOlderAndroidWarning = showOlderAndroidWarning
                    )
                    OnboardingStep.Done -> Unit
                }
            }
        }
    }

    OnboardingRestoreDialog(
        state = restoreState,
        onApplyRestoreBackup = onApplyRestoreBackup,
        onDismissRestoreBackup = onDismissRestoreBackup,
        onRestartApp = onRestartApp
    )
}

sealed interface OnboardingRestoreState {
    data object Idle : OnboardingRestoreState
    data object Busy : OnboardingRestoreState
    data class Preview(val items: List<OnboardingRestoreItem>) : OnboardingRestoreState
    data class Restored(val items: List<OnboardingRestoreItem>, val failures: List<OnboardingRestoreFailure>) : OnboardingRestoreState
    data class Failed(val message: String) : OnboardingRestoreState
}

data class OnboardingRestoreItem(
    val label: String,
    val status: String
)

data class OnboardingRestoreFailure(
    val label: String,
    val message: String
)
