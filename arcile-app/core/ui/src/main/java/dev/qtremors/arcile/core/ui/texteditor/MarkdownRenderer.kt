package dev.qtremors.arcile.core.ui.texteditor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MarkdownRenderer(
    content: String,
    modifier: Modifier = Modifier
) {
    val blocks = remember(content) { parseMarkdownBlocks(content) }

    SelectionContainer(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            blocks.forEachIndexed { index, block ->
                if (index > 0) Spacer(modifier = Modifier.height(10.dp))
                RenderMarkdownBlock(block)
            }
        }
    }
}

@Composable
private fun RenderMarkdownBlock(block: MarkdownBlock) {
    when (block) {
        is MarkdownBlock.Header -> {
            val style = when (block.level) {
                1 -> MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold)
                2 -> MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                3 -> MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold)
                4 -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                else -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium)
            }
            Text(
                text = parseInlineMarkdown(block.text),
                style = style,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
        is MarkdownBlock.CodeBlock -> {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    if (block.language.isNotBlank()) {
                        Text(
                            text = block.language.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                    Text(
                        text = block.code,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        is MarkdownBlock.BlockQuote -> {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(32.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = parseInlineMarkdown(block.text),
                        style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        is MarkdownBlock.ListItem -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
            ) {
                Text(
                    text = block.prefix,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(24.dp)
                )
                Text(
                    text = parseInlineMarkdown(block.text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        is MarkdownBlock.Divider -> {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 14.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }
        is MarkdownBlock.Paragraph -> {
            Text(
                text = parseInlineMarkdown(block.text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 22.sp,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }
    }
}

private sealed interface MarkdownBlock {
    data class Header(val level: Int, val text: String) : MarkdownBlock
    data class CodeBlock(val code: String, val language: String = "") : MarkdownBlock
    data class BlockQuote(val text: String) : MarkdownBlock
    data class ListItem(val prefix: String, val text: String) : MarkdownBlock
    data object Divider : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
}

private fun parseMarkdownBlocks(content: String): List<MarkdownBlock> {
    val lines = content.lines()
    val blocks = mutableListOf<MarkdownBlock>()
    var inCodeBlock = false
    val codeLines = mutableListOf<String>()
    var codeLang = ""

    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed.startsWith("```")) {
            if (inCodeBlock) {
                blocks.add(MarkdownBlock.CodeBlock(codeLines.joinToString("\n"), codeLang))
                codeLines.clear()
                inCodeBlock = false
            } else {
                inCodeBlock = true
                codeLang = trimmed.removePrefix("```").trim()
            }
            continue
        }

        if (inCodeBlock) {
            codeLines.add(line)
            continue
        }

        when {
            trimmed.startsWith("#") -> {
                val level = trimmed.takeWhile { it == '#' }.length
                val text = trimmed.drop(level).trim()
                if (level in 1..6 && text.isNotEmpty()) {
                    blocks.add(MarkdownBlock.Header(level, text))
                } else {
                    blocks.add(MarkdownBlock.Paragraph(line))
                }
            }
            trimmed.startsWith(">") -> {
                blocks.add(MarkdownBlock.BlockQuote(trimmed.removePrefix(">").trim()))
            }
            trimmed == "---" || trimmed == "***" || trimmed == "___" -> {
                blocks.add(MarkdownBlock.Divider)
            }
            trimmed.matches(Regex("""^(?:\*|-|\+)\s+.*""")) -> {
                val text = trimmed.replaceFirst(Regex("""^(?:\*|-|\+)\s+"""), "")
                blocks.add(MarkdownBlock.ListItem("•", text))
            }
            trimmed.matches(Regex("""^\d+\.\s+.*""")) -> {
                val prefix = trimmed.substringBefore('.') + "."
                val text = trimmed.substringAfter(". ").trim()
                blocks.add(MarkdownBlock.ListItem(prefix, text))
            }
            trimmed.isBlank() -> {
                // Skip empty spacing lines
            }
            else -> {
                blocks.add(MarkdownBlock.Paragraph(line))
            }
        }
    }

    if (inCodeBlock && codeLines.isNotEmpty()) {
        blocks.add(MarkdownBlock.CodeBlock(codeLines.joinToString("\n"), codeLang))
    }

    return blocks
}

@Composable
private fun parseInlineMarkdown(text: String): AnnotatedString {
    val primaryColor = MaterialTheme.colorScheme.primary
    val codeBg = MaterialTheme.colorScheme.surfaceContainerHighest

    return remember(text, primaryColor, codeBg) {
        buildAnnotatedString {
            var index = 0
            val length = text.length

            while (index < length) {
                when {
                    text.startsWith("**", index) -> {
                        val end = text.indexOf("**", index + 2)
                        if (end != -1) {
                            pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                            append(text.substring(index + 2, end))
                            pop()
                            index = end + 2
                        } else {
                            append(text[index])
                            index++
                        }
                    }
                    text.startsWith("*", index) -> {
                        val end = text.indexOf("*", index + 1)
                        if (end != -1) {
                            pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                            append(text.substring(index + 1, end))
                            pop()
                            index = end + 1
                        } else {
                            append(text[index])
                            index++
                        }
                    }
                    text.startsWith("`", index) -> {
                        val end = text.indexOf("`", index + 1)
                        if (end != -1) {
                            pushStyle(
                                SpanStyle(
                                    fontFamily = FontFamily.Monospace,
                                    background = codeBg,
                                    fontSize = 13.sp
                                )
                            )
                            append(text.substring(index + 1, end))
                            pop()
                            index = end + 1
                        } else {
                            append(text[index])
                            index++
                        }
                    }
                    text.startsWith("[", index) -> {
                        val linkTextEnd = text.indexOf("]", index)
                        val urlStart = text.indexOf("(", linkTextEnd)
                        val urlEnd = text.indexOf(")", urlStart)
                        if (linkTextEnd != -1 && urlStart == linkTextEnd + 1 && urlEnd != -1) {
                            val linkText = text.substring(index + 1, linkTextEnd)
                            val url = text.substring(urlStart + 1, urlEnd)
                            pushLink(
                                LinkAnnotation.Url(
                                    url = url,
                                    styles = TextLinkStyles(
                                        style = SpanStyle(
                                    color = primaryColor,
                                    textDecoration = TextDecoration.Underline,
                                    fontWeight = FontWeight.SemiBold
                                        )
                                    )
                                )
                            )
                            append(linkText)
                            pop()
                            index = urlEnd + 1
                        } else {
                            append(text[index])
                            index++
                        }
                    }
                    else -> {
                        append(text[index])
                        index++
                    }
                }
            }
        }
    }
}
