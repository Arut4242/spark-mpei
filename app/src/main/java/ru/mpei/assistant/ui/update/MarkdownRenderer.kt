package ru.mpei.assistant.ui.update

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.mpei.assistant.ui.theme.AppleBlue

sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class ListItem(val text: String, val indentLevel: Int, val bullet: String = "•") : MarkdownBlock
    data class OrderedListItem(val text: String, val number: String, val indentLevel: Int) : MarkdownBlock
    data class Blockquote(val text: String) : MarkdownBlock
    data class CodeBlock(val code: String, val language: String? = null) : MarkdownBlock
    object HorizontalRule : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
}

fun parseMarkdown(markdown: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val lines = markdown.lines()
    var i = 0

    while (i < lines.size) {
        val rawLine = lines[i]
        val trimmed = rawLine.trim()

        // Многострочный блок кода ```
        if (trimmed.startsWith("```")) {
            val lang = trimmed.removePrefix("```").trim().takeIf { it.isNotBlank() }
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                codeLines.add(lines[i])
                i++
            }
            blocks.add(MarkdownBlock.CodeBlock(codeLines.joinToString("\n"), lang))
            i++
            continue
        }

        if (trimmed.isEmpty()) {
            i++
            continue
        }

        // Горизонтальный разделитель ---, ***, ___
        if (trimmed.matches(Regex("""^([-*_]){3,}\s*$"""))) {
            blocks.add(MarkdownBlock.HorizontalRule)
            i++
            continue
        }

        // Заголовки # .. ######
        val headingMatch = Regex("""^(#{1,6})\s+(.*)$""").find(trimmed)
        if (headingMatch != null) {
            val level = headingMatch.groupValues[1].length
            val text = headingMatch.groupValues[2].trim()
            blocks.add(MarkdownBlock.Heading(level, text))
            i++
            continue
        }

        // Цитаты >
        if (trimmed.startsWith(">")) {
            val quoteText = trimmed.removePrefix(">").trim()
            blocks.add(MarkdownBlock.Blockquote(quoteText))
            i++
            continue
        }

        // Маркированный список *, -, +, •
        val bulletMatch = Regex("""^(\s*)([-*+•])\s+(.*)$""").find(rawLine)
        if (bulletMatch != null) {
            val leadingSpaces = bulletMatch.groupValues[1].length
            val indent = (leadingSpaces / 2).coerceAtMost(3)
            val text = bulletMatch.groupValues[3].trim()
            blocks.add(MarkdownBlock.ListItem(text, indentLevel = indent))
            i++
            continue
        }

        // Нумерованный список 1., 2)
        val orderedMatch = Regex("""^(\s*)(\d+)[.)]\s+(.*)$""").find(rawLine)
        if (orderedMatch != null) {
            val leadingSpaces = orderedMatch.groupValues[1].length
            val indent = (leadingSpaces / 2).coerceAtMost(3)
            val num = orderedMatch.groupValues[2] + "."
            val text = orderedMatch.groupValues[3].trim()
            blocks.add(MarkdownBlock.OrderedListItem(text, number = num, indentLevel = indent))
            i++
            continue
        }

        // Обычный абзац
        blocks.add(MarkdownBlock.Paragraph(trimmed))
        i++
    }

    return blocks
}

fun buildMarkdownAnnotatedString(
    text: String,
    defaultColor: Color,
    linkColor: Color = AppleBlue,
    codeBg: Color = Color.Transparent,
    codeColor: Color = defaultColor
): AnnotatedString {
    val builder = AnnotatedString.Builder()
    val regex = Regex("""(\*\*\*[^*]+\*\*\*|___[^_]+___|\*\*[^*]+\*\*|__[^_]+__|`[^`]+`|~~[^~]+~~|\[[^\]]+\]\([^)]+\)|\*[^*]+\*|_[^_]+_)""")

    var lastIndex = 0
    val matches = regex.findAll(text)

    for (match in matches) {
        val matchStart = match.range.first
        val matchEnd = match.range.last + 1

        if (matchStart > lastIndex) {
            builder.append(text.substring(lastIndex, matchStart))
        }

        val token = match.value
        when {
            (token.startsWith("***") && token.endsWith("***")) ||
            (token.startsWith("___") && token.endsWith("___")) -> {
                val inner = token.substring(3, token.length - 3)
                builder.withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)) {
                    append(inner)
                }
            }
            (token.startsWith("**") && token.endsWith("**")) ||
            (token.startsWith("__") && token.endsWith("__")) -> {
                val inner = token.substring(2, token.length - 2)
                builder.withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(inner)
                }
            }
            token.startsWith("`") && token.endsWith("`") -> {
                val inner = token.substring(1, token.length - 1)
                builder.withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = codeBg,
                        color = codeColor,
                        fontSize = 11.sp
                    )
                ) {
                    append(" $inner ")
                }
            }
            token.startsWith("~~") && token.endsWith("~~") -> {
                val inner = token.substring(2, token.length - 2)
                builder.withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                    append(inner)
                }
            }
            token.startsWith("[") && token.contains("](") && token.endsWith(")") -> {
                val label = token.substringAfter("[").substringBefore("]")
                builder.withStyle(
                    SpanStyle(
                        color = linkColor,
                        textDecoration = TextDecoration.Underline,
                        fontWeight = FontWeight.Medium
                    )
                ) {
                    append(label)
                }
            }
            (token.startsWith("*") && token.endsWith("*")) ||
            (token.startsWith("_") && token.endsWith("_")) -> {
                val inner = token.substring(1, token.length - 1)
                builder.withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(inner)
                }
            }
            else -> {
                builder.append(token)
            }
        }
        lastIndex = matchEnd
    }

    if (lastIndex < text.length) {
        builder.append(text.substring(lastIndex))
    }

    return builder.toAnnotatedString()
}

@Composable
fun MarkdownContent(
    markdown: String,
    modifier: Modifier = Modifier
) {
    val blocks = remember(markdown) { parseMarkdown(markdown) }
    val defaultColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val primaryColor = MaterialTheme.colorScheme.primary
    val codeBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> {
                    val (fontSize, fontWeight, color) = when (block.level) {
                        1 -> Triple(15.sp, FontWeight.Bold, primaryColor)
                        2 -> Triple(14.sp, FontWeight.Bold, primaryColor)
                        3 -> Triple(13.sp, FontWeight.SemiBold, defaultColor)
                        else -> Triple(12.sp, FontWeight.SemiBold, onSurfaceVariant)
                    }
                    val annotated = remember(block.text, color) {
                        buildMarkdownAnnotatedString(
                            text = block.text,
                            defaultColor = color,
                            codeBg = codeBg
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = annotated,
                        fontSize = fontSize,
                        fontWeight = fontWeight,
                        color = color,
                        lineHeight = (fontSize.value + 4).sp
                    )
                }

                is MarkdownBlock.ListItem -> {
                    val indentPadding = (block.indentLevel * 12).dp
                    val annotated = remember(block.text, defaultColor) {
                        buildMarkdownAnnotatedString(
                            text = block.text,
                            defaultColor = defaultColor,
                            codeBg = codeBg
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = indentPadding, top = 1.dp, bottom = 1.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "•",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryColor,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        Text(
                            text = annotated,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = defaultColor,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                is MarkdownBlock.OrderedListItem -> {
                    val indentPadding = (block.indentLevel * 12).dp
                    val annotated = remember(block.text, defaultColor) {
                        buildMarkdownAnnotatedString(
                            text = block.text,
                            defaultColor = defaultColor,
                            codeBg = codeBg
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = indentPadding, top = 1.dp, bottom = 1.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = block.number,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = primaryColor,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        Text(
                            text = annotated,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = defaultColor,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                is MarkdownBlock.Blockquote -> {
                    val annotated = remember(block.text, onSurfaceVariant) {
                        buildMarkdownAnnotatedString(
                            text = block.text,
                            defaultColor = onSurfaceVariant,
                            codeBg = codeBg
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(18.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(AppleBlue)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = annotated,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 12.sp,
                            fontStyle = FontStyle.Italic,
                            lineHeight = 16.sp,
                            color = onSurfaceVariant
                        )
                    }
                }

                is MarkdownBlock.CodeBlock -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(codeBg)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = block.code,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = defaultColor
                        )
                    }
                }

                is MarkdownBlock.HorizontalRule -> {
                    Spacer(modifier = Modifier.height(3.dp))
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        thickness = 0.5.dp
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                }

                is MarkdownBlock.Paragraph -> {
                    val annotated = remember(block.text, defaultColor) {
                        buildMarkdownAnnotatedString(
                            text = block.text,
                            defaultColor = defaultColor,
                            codeBg = codeBg
                        )
                    }
                    Text(
                        text = annotated,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = defaultColor
                    )
                }
            }
        }
    }
}
