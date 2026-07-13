package dev.qtremors.arcile.feature.onboarding.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.feature.onboarding.OnboardingUiState
import dev.qtremors.arcile.feature.onboarding.OnboardingRestoreFailure
import dev.qtremors.arcile.feature.onboarding.OnboardingRestoreItem
import dev.qtremors.arcile.feature.onboarding.OnboardingRestoreState
import dev.qtremors.arcile.core.ui.ExpressiveFilterChip
import dev.qtremors.arcile.core.ui.settings.AccentColorSelector
import dev.qtremors.arcile.core.ui.settings.ThemeModeSelector
import dev.qtremors.arcile.core.ui.theme.ThemeState
import dev.qtremors.arcile.core.ui.theme.ExpressiveShapes
import dev.qtremors.arcile.core.ui.theme.bounceClickable
import dev.qtremors.arcile.core.ui.theme.spacing
import dev.qtremors.arcile.core.ui.rememberArcileHaptics

@Composable
internal fun OnboardingPage(
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
            .padding(top = 16.dp, bottom = 80.dp),
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
            if (customIcon != null) {
                Box(
                    modifier = Modifier.size(if (hasBodyContent) 120.dp else 160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    customIcon()
                }
            } else if (icon != null) {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    tonalElevation = 4.dp,
                    modifier = Modifier.size(if (hasBodyContent) 88.dp else 120.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            icon,
                            contentDescription = null,
                            modifier = Modifier.size(if (hasBodyContent) 40.dp else 56.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}

private data class FeatureInfo(
    val icon: ImageVector,
    val titleRes: Int,
    val descRes: Int
)
@Composable
internal fun OnboardingWelcomeAndFeatures() {
    var selectedFeatureIndex by remember { mutableStateOf(0) }

    val features = remember {
        listOf(
            FeatureInfo(Icons.Default.Storage, R.string.onboarding_feature_multi_volume, R.string.onboarding_feature_multi_volume_description),
            FeatureInfo(Icons.Default.Bolt, R.string.onboarding_feature_batch_operations, R.string.onboarding_feature_batch_operations_description),
            FeatureInfo(Icons.Default.Category, R.string.onboarding_feature_instant_categories, R.string.onboarding_feature_instant_categories_description),
            FeatureInfo(Icons.Default.RestoreFromTrash, R.string.onboarding_feature_trash_subsystem, R.string.onboarding_feature_trash_subsystem_description),
            FeatureInfo(Icons.Default.CloudOff, R.string.onboarding_privacy_offline, R.string.onboarding_privacy_offline_description),
            FeatureInfo(Icons.Default.Block, R.string.onboarding_privacy_trackers, R.string.onboarding_privacy_trackers_description),
            FeatureInfo(Icons.Default.Folder, R.string.onboarding_privacy_local_file_access, R.string.onboarding_privacy_local_file_access_description),
            FeatureInfo(Icons.Default.Source, R.string.onboarding_privacy_source, R.string.onboarding_privacy_source_description)
        )
    }

    OnboardingPage(
        customIcon = {
            MorphingBackgroundIcon {
                val context = androidx.compose.ui.platform.LocalContext.current
                val launcherIconResId = remember(context) {
                    context.resources.getIdentifier("ic_launcher", "mipmap", context.packageName)
                        .takeIf { it != 0 } ?: android.R.drawable.sym_def_app_icon
                }
                AsyncImage(
                    model = launcherIconResId,
                    contentDescription = stringResource(R.string.app_name),
                    modifier = Modifier.fillMaxSize()
                )
            }
        },
        title = stringResource(R.string.onboarding_welcome_title),
        description = stringResource(R.string.onboarding_intro_description),
        hasBodyContent = true
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.onboarding_highlight_features),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            features.forEachIndexed { index, feature ->
                val isSelected = selectedFeatureIndex == index
                ExpressiveFilterChip(
                    selected = isSelected,
                    onClick = { selectedFeatureIndex = index },
                    label = {
                        Text(
                            text = stringResource(feature.titleRes),
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = feature.icon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val selectedFeature = features[selectedFeatureIndex]

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 96.dp),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            AnimatedContent(
                targetState = selectedFeature,
                transitionSpec = {
                    fadeIn(animationSpec = tween(200)) togetherWith
                    fadeOut(animationSpec = tween(150))
                },
                label = "feature_description_animation"
            ) { targetFeature ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = targetFeature.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(targetFeature.titleRes),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = stringResource(targetFeature.descRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
internal fun OnboardingPrivacy() {
    OnboardingPage(
        icon = Icons.Default.PrivacyTip,
        title = stringResource(R.string.onboarding_privacy_title),
        description = stringResource(R.string.onboarding_privacy_description),
        hasBodyContent = true
    ) {
        FeatureRow(Icons.Default.CloudOff, stringResource(R.string.onboarding_privacy_offline), stringResource(R.string.onboarding_privacy_offline_description))
        FeatureRow(Icons.Default.Block, stringResource(R.string.onboarding_privacy_trackers), stringResource(R.string.onboarding_privacy_trackers_description))
        FeatureRow(Icons.Default.Folder, stringResource(R.string.onboarding_privacy_local_file_access), stringResource(R.string.onboarding_privacy_local_file_access_description))
        FeatureRow(Icons.Default.Source, stringResource(R.string.onboarding_privacy_source), stringResource(R.string.onboarding_privacy_source_description))
    }
}

@Composable
internal fun OnboardingTheme(
    currentThemeState: ThemeState,
    onThemeChange: (ThemeState) -> Unit
) {
    OnboardingPage(
        icon = Icons.Default.Palette,
        title = stringResource(R.string.onboarding_theme_title),
        description = stringResource(R.string.onboarding_theme_description),
        hasBodyContent = true
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                ThemeModeSelector(
                    currentMode = currentThemeState.themeMode,
                    onModeSelected = { onThemeChange(currentThemeState.copy(themeMode = it)) }
                )
                Spacer(modifier = Modifier.height(4.dp))
                AccentColorSelector(
                    currentAccent = currentThemeState.accentColor,
                    onAccentSelected = { onThemeChange(currentThemeState.copy(accentColor = it)) }
                )
            }
        }
    }
}

@Composable
internal fun OnboardingDone() {
    OnboardingPage(
        icon = Icons.Default.CheckCircle,
        title = stringResource(R.string.onboarding_done_title),
        description = stringResource(R.string.onboarding_done_description)
    )
}

@Composable
internal fun FeatureRow(
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
