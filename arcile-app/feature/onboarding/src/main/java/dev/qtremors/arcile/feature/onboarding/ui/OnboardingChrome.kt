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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import dev.qtremors.arcile.ui.theme.ExpressiveShapes
import dev.qtremors.arcile.ui.theme.bounceClickable
import dev.qtremors.arcile.shared.ui.rememberArcileHaptics
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
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
    onBack: () -> Unit
) {
    val haptics = rememberArcileHaptics()
    val actionWidth = 104.dp
    val actionPadding = PaddingValues(horizontal = 8.dp)

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
                    val backClick = {
                        haptics.selectionChanged()
                        onBack()
                    }
                    FilledTonalButton(
                        onClick = backClick,
                        contentPadding = PaddingValues(start = 8.dp, end = 12.dp),
                        modifier = Modifier
                            .width(actionWidth)
                            .defaultMinSize(minHeight = 44.dp)
                            .bounceClickable(onClick = backClick),
                        shape = ExpressiveShapes.medium
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(text = stringResource(R.string.back), maxLines = 1)
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.size(width = actionWidth, height = 44.dp))
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
                                .testTag("onboardingStepIndicator")
                        )
                    }
                }

                Spacer(modifier = Modifier.size(width = actionWidth, height = 44.dp))
            }
        }
    }
}

@Composable
internal fun OnboardingBottomBar(
    state: OnboardingUiState,
    onNext: () -> Unit
) {
    val haptics = rememberArcileHaptics()
    val buttonText = when (state.step) {
        OnboardingStep.SetupPermissions -> stringResource(R.string.onboarding_finish)
        OnboardingStep.Done -> stringResource(R.string.onboarding_finish)
        else -> stringResource(R.string.onboarding_continue)
    }
    val isEnabled = when (state.step) {
        OnboardingStep.SetupPermissions -> state.hasStoragePermission
        OnboardingStep.Done -> !state.isCompleting
        else -> true
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = MaterialTheme.spacing.large, vertical = MaterialTheme.spacing.space12),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!isEnabled && state.step == OnboardingStep.SetupPermissions) {
            Text(
                text = stringResource(R.string.onboarding_storage_permission_required_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }
        val nextClick = {
            haptics.selectionChanged()
            onNext()
        }
        val isFinish = state.step == OnboardingStep.SetupPermissions || state.step == OnboardingStep.Done
        Button(
            onClick = nextClick,
            enabled = isEnabled,
            shape = ExpressiveShapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp)
                .bounceClickable(
                    enabled = isEnabled,
                    onClick = nextClick
                )
        ) {
            Icon(
                imageVector = if (isFinish) Icons.Default.Check else Icons.Default.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(buttonText)
        }
    }
}

