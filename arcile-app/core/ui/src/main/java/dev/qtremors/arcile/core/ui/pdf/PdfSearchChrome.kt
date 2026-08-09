package dev.qtremors.arcile.core.ui.pdf

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.ui.R

@Composable
internal fun PdfSearchChrome(
    query: String,
    onQueryChange: (String) -> Unit,
    resultPosition: Int,
    resultCount: Int,
    statusText: String?,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Surface(color = Color.Black.copy(alpha = 0.72f)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = Color(0xFF303134),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            stringResource(R.string.action_close_search),
                            tint = Color.White
                        )
                    }
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.72f),
                        modifier = Modifier.size(20.dp)
                    )
                    TextField(
                        value = query,
                        onValueChange = onQueryChange,
                        placeholder = { Text(stringResource(R.string.pdf_search_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
                        trailingIcon = if (query.isNotEmpty()) {
                            {
                                IconButton(onClick = { onQueryChange("") }) {
                                    Icon(Icons.Default.Clear, stringResource(R.string.action_clear))
                                }
                            }
                        } else null,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedPlaceholderColor = Color.White.copy(alpha = 0.62f),
                            unfocusedPlaceholderColor = Color.White.copy(alpha = 0.62f),
                            cursorColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester)
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Surface(
                color = Color(0xFF303134),
                shape = CircleShape,
                modifier = Modifier.height(56.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (statusText != null) {
                        Text(
                            statusText,
                            color = Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    } else if (resultCount > 0) {
                        Text(
                            "$resultPosition/$resultCount",
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        IconButton(onClick = onPrevious, enabled = resultCount > 0) {
                            Icon(Icons.Default.KeyboardArrowUp, stringResource(R.string.pdf_search_previous))
                        }
                    }
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        IconButton(onClick = onNext, enabled = resultCount > 0) {
                            Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.pdf_search_next))
                        }
                    }
                }
            }
        }
    }
}
