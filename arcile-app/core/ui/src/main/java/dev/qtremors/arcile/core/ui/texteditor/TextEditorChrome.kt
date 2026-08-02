package dev.qtremors.arcile.core.ui.texteditor

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.ui.ArcileDropdownMenu
import dev.qtremors.arcile.core.ui.ArcileDropdownMenuItem
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.SplitButtonGroup
import dev.qtremors.arcile.core.ui.ToolbarAction
import dev.qtremors.arcile.core.ui.theme.LocalMarqueeFilenames
import dev.qtremors.arcile.core.ui.theme.bounceClickable

@Composable
internal fun TextEditorTopChrome(
    title: String,
    isDirty: Boolean,
    isSaving: Boolean,
    writable: Boolean,
    onNavigateBack: () -> Unit
) {
    val marqueeEnabled = LocalMarqueeFilenames.current
    Row(
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ViewerIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            description = stringResource(R.string.back),
            onClick = onNavigateBack
        )
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f).clip(RoundedCornerShape(20.dp))
                .background(Color.Black.copy(alpha = 0.62f)).padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = if (marqueeEnabled) TextOverflow.Clip else TextOverflow.Ellipsis,
                modifier = if (marqueeEnabled) Modifier.basicMarquee() else Modifier
            )
            Text(
                text = when {
                    !writable -> stringResource(R.string.text_editor_read_only)
                    isSaving -> stringResource(R.string.text_editor_saving)
                    isDirty -> stringResource(R.string.text_editor_unsaved_changes)
                    else -> stringResource(R.string.text_editor_saved)
                },
                color = Color.White.copy(alpha = 0.72f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1
            )
        }
    }
}

@Composable
internal fun TextEditorBottomChrome(
    text: String,
    mode: TextEditorMode,
    writable: Boolean,
    supportsMarkdownPreview: Boolean,
    isDirty: Boolean,
    isSaving: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    onModeSelected: (TextEditorMode) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onInfo: () -> Unit,
    onOpenWith: () -> Unit,
    onFormat: (prefix: String, suffix: String) -> Unit
) {
    val lines = remember(text) { text.lines().size }
    val words = remember(text) { text.wordCount() }
    val chars = remember(text) { text.length }
    var showMenu by rememberSaveable { mutableStateOf(false) }
    val actions = buildList {
        if (supportsMarkdownPreview) {
            add(
                ToolbarAction(
                    if (mode == TextEditorMode.EDIT) Icons.Default.Visibility else Icons.Default.Edit,
                    stringResource(
                        if (mode == TextEditorMode.EDIT) R.string.text_editor_mode_preview
                        else R.string.text_editor_mode_edit
                    )
                ) {
                    onModeSelected(
                        if (mode == TextEditorMode.EDIT) TextEditorMode.PREVIEW else TextEditorMode.EDIT
                    )
                }
            )
        }
        if (writable && mode == TextEditorMode.EDIT) {
            add(
                ToolbarAction(
                    Icons.Default.Save,
                    stringResource(R.string.text_editor_save),
                    tint = if (isDirty && !isSaving) Color.White else Color.White.copy(alpha = 0.35f)
                ) { if (isDirty && !isSaving) onSave() }
            )
        }
    }
    Column(
        modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.62f))
            .navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (actions.isNotEmpty()) {
                SplitButtonGroup(
                    actions = actions,
                    containerColor = Color.Black.copy(alpha = 0.5f),
                    contentColor = Color.White,
                    height = 48.dp,
                    minWidth = 48.dp,
                    iconSize = 24.dp
                )
            }
            Spacer(Modifier.weight(1f))
            Box {
                ViewerIconButton(
                    icon = Icons.Default.MoreVert,
                    description = stringResource(R.string.action_more_options),
                    onClick = { showMenu = true }
                )
                ArcileDropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    items = listOf(
                        {
                            ArcileDropdownMenuItem(
                                text = stringResource(R.string.text_editor_file_info),
                                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                                onClick = { showMenu = false; onInfo() }
                            )
                        },
                        {
                            ArcileDropdownMenuItem(
                                text = stringResource(R.string.open_app),
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
                                enabled = !isSaving,
                                onClick = { showMenu = false; onOpenWith() }
                            )
                        },
                        {
                            ArcileDropdownMenuItem(
                                text = stringResource(R.string.share),
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                enabled = !isSaving,
                                onClick = { showMenu = false; onShare() }
                            )
                        }
                    )
                )
            }
        }
        Text(
            text = stringResource(R.string.text_editor_stats_format, lines, words, chars),
            color = Color.White.copy(alpha = 0.72f),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

@Composable
private fun ViewerIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.62f),
        modifier = Modifier.size(48.dp).bounceClickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(24.dp))
        }
    }
}

internal fun String.wordCount(): Int =
    if (isBlank()) 0 else trim().split(Regex("""\s+""")).size
