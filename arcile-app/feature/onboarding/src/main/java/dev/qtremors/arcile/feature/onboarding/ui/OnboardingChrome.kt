package dev.qtremors.arcile.feature.onboarding.ui

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.feature.onboarding.OnboardingStep
import dev.qtremors.arcile.feature.onboarding.OnboardingUiState
import dev.qtremors.arcile.ui.theme.spacing
@Composable
internal fun OnboardingHeader(
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
internal fun OnboardingBottomBar(
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

