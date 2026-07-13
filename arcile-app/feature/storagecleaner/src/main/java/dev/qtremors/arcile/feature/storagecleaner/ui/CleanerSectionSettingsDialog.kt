package dev.qtremors.arcile.feature.storagecleaner.ui

import android.graphics.Bitmap
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.graphics.graphicsLayer
import dev.qtremors.arcile.core.ui.theme.ExpressiveShapes
import dev.qtremors.arcile.core.ui.theme.bounceClickable
import dev.qtremors.arcile.core.ui.theme.sheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.ui.R
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import dev.qtremors.arcile.core.ui.loadApplicationIconBitmap
import dev.qtremors.arcile.core.ui.keyboardInputField
import dev.qtremors.arcile.core.storage.domain.CleanerRiskReason
import dev.qtremors.arcile.core.storage.domain.CleanerCandidate
import dev.qtremors.arcile.core.storage.domain.CleanerGroup
import dev.qtremors.arcile.core.storage.domain.CleanerGroupType
import dev.qtremors.arcile.core.storage.domain.CleanerRiskLevel
import dev.qtremors.arcile.core.storage.domain.CleanerSectionRule
import dev.qtremors.arcile.core.storage.domain.StorageCleanerRules
import dev.qtremors.arcile.core.ui.rememberDateFormatter
import dev.qtremors.arcile.core.ui.theme.bodyLargeMedium
import dev.qtremors.arcile.core.ui.theme.bodyMediumBold
import dev.qtremors.arcile.core.ui.theme.titleMediumBold
import dev.qtremors.arcile.core.presentation.formatFileSize
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CleanerSectionSettingsDialog(
    type: CleanerGroupType,
    rule: CleanerSectionRule,
    onSave: (CleanerSectionRule) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    var enabled by remember(type, rule) { mutableStateOf(rule.enabled) }
    var namePatterns by remember(type, rule) { mutableStateOf(rule.ignoredNamePatterns) }
    var pathPatterns by remember(type, rule) { mutableStateOf(rule.ignoredPathPatterns) }
    var namePatternInput by remember(type, rule) { mutableStateOf("") }
    var pathPatternInput by remember(type, rule) { mutableStateOf("") }
    var largeThresholdMb by remember(type, rule) {
        mutableStateOf(rule.largeFileThresholdBytes?.let { (it / (1024L * 1024L)).toString() }.orEmpty())
    }
    var oldDownloadAgeDays by remember(type, rule) {
        mutableStateOf(rule.oldDownloadAgeMs?.let { (it / DAY_MS).toString() }.orEmpty())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cleaner_section_settings)) },
        text = {
            Column(
                modifier = Modifier
                    .height(420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.cleaner_section_enabled))
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
                if (type == CleanerGroupType.LargeFiles) {
                    OutlinedTextField(
                        value = largeThresholdMb,
                        onValueChange = { largeThresholdMb = it.filter(Char::isDigit) },
                        label = { Text(stringResource(R.string.cleaner_large_threshold_mb)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = ExpressiveShapes.medium,
                        modifier = Modifier.fillMaxWidth().keyboardInputField()
                    )
                }
                if (type == CleanerGroupType.OldDownloads) {
                    OutlinedTextField(
                        value = oldDownloadAgeDays,
                        onValueChange = { oldDownloadAgeDays = it.filter(Char::isDigit) },
                        label = { Text(stringResource(R.string.cleaner_old_download_age_days)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = ExpressiveShapes.medium,
                        modifier = Modifier.fillMaxWidth().keyboardInputField()
                    )
                }
                PatternEditor(
                    title = stringResource(R.string.cleaner_ignore_name_patterns),
                    patterns = namePatterns,
                    input = namePatternInput,
                    onInputChange = { namePatternInput = it },
                    onAdd = {
                        val trimmed = namePatternInput.trim()
                        if (trimmed.isNotBlank()) {
                            namePatterns = namePatterns + trimmed
                            namePatternInput = ""
                        }
                    },
                    onRemove = { namePatterns = namePatterns - it }
                )
                PatternEditor(
                    title = stringResource(R.string.cleaner_ignore_path_patterns),
                    patterns = pathPatterns,
                    input = pathPatternInput,
                    onInputChange = { pathPatternInput = it },
                    onAdd = {
                        val trimmed = pathPatternInput.trim()
                        if (trimmed.isNotBlank()) {
                            pathPatterns = pathPatterns + trimmed
                            pathPatternInput = ""
                        }
                    },
                    onRemove = { pathPatterns = pathPatterns - it }
                )
            }
        },
        confirmButton = {
            val saveClick = {
                onSave(
                    CleanerSectionRule(
                        enabled = enabled,
                        ignoredNamePatterns = namePatterns,
                        ignoredPathPatterns = pathPatterns,
                        largeFileThresholdBytes = largeThresholdMb.toLongOrNull()?.times(1024L * 1024L),
                        oldDownloadAgeMs = oldDownloadAgeDays.toLongOrNull()?.times(DAY_MS)
                    )
                )
            }
            TextButton(
                onClick = saveClick,
                shape = ExpressiveShapes.medium
            ) {
                Text(stringResource(R.string.apply))
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = onReset,
                    shape = ExpressiveShapes.medium
                ) {
                    Text(stringResource(R.string.reset))
                }
                TextButton(
                    onClick = onDismiss,
                    shape = ExpressiveShapes.medium
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    )
}

@Composable
private fun PatternEditor(
    title: String,
    patterns: Set<String>,
    input: String,
    onInputChange: (String) -> Unit,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                label = { Text(stringResource(R.string.cleaner_patterns_hint)) },
                singleLine = true,
                shape = ExpressiveShapes.medium,
                modifier = Modifier.weight(1f).keyboardInputField()
            )
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(
                onClick = onAdd,
                shape = ExpressiveShapes.medium
            ) {
                Text(stringResource(R.string.add))
            }
        }
        patterns.sorted().forEach { pattern ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = pattern,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                TextButton(
                    onClick = { onRemove(pattern) },
                    shape = ExpressiveShapes.medium
                ) {
                    Text(stringResource(R.string.remove))
                }
            }
        }
    }
}
