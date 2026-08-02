package dev.qtremors.arcile.core.ui.texteditor

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

@Composable
internal fun MarkdownRenderer(
    content: String,
    modifier: Modifier = Modifier
) {
    SelectionContainer(modifier = modifier) {
        Text(
            text = content,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            color = Color.White.copy(alpha = 0.92f),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
