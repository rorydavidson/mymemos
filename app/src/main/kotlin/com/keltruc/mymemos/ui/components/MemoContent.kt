package com.keltruc.mymemos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private val taskLine = Regex("^(\\s*)[-*] \\[([ xX])] (.*)$")
private val bulletLine = Regex("^(\\s*)[-*] (.*)$")
private val headingLine = Regex("^(#{1,3}) (.*)$")
private val inlineMarkup = Regex("(\\*\\*(.+?)\\*\\*)|(`([^`]+)`)|(~~(.+?)~~)|(?<![\\w/])#([\\p{L}\\p{N}_/-]+)|(https?://\\S+)")

/**
 * Small Markdown renderer covering what memos mostly contain: headings, bullets, task
 * lists with live checkboxes, fenced code, bold, inline code, strikethrough, tags, links.
 * Full CommonMark is a later concern.
 */
@Composable
fun MemoContent(
    content: String,
    onToggleTask: ((lineIndex: Int, checked: Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
) {
    val lines = content.lines()
    Column(modifier) {
        var inCode = false
        val codeBuffer = mutableListOf<String>()
        var shown = 0
        for ((index, line) in lines.withIndex()) {
            if (shown >= maxLines) {
                Text("…", style = MaterialTheme.typography.bodyLarge)
                break
            }
            if (line.trimStart().startsWith("```")) {
                if (inCode) {
                    CodeBlock(codeBuffer.joinToString("\n"))
                    codeBuffer.clear()
                    shown++
                }
                inCode = !inCode
                continue
            }
            if (inCode) {
                codeBuffer += line
                continue
            }
            taskLine.matchEntire(line)?.let { m ->
                val checked = m.groupValues[2] != " "
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = onToggleTask?.let { cb -> { cb(index, it) } },
                        modifier = Modifier.size(32.dp),
                    )
                    InlineText(
                        m.groupValues[3],
                        strike = checked,
                        modifier = Modifier.padding(start = 4.dp + (m.groupValues[1].length * 6).dp),
                    )
                }
                shown++
                return@let
            } ?: bulletLine.matchEntire(line)?.let { m ->
                Row(Modifier.fillMaxWidth().padding(start = (m.groupValues[1].length * 6).dp)) {
                    Text("•  ", style = MaterialTheme.typography.bodyLarge)
                    InlineText(m.groupValues[2])
                }
                shown++
            } ?: headingLine.matchEntire(line)?.let { m ->
                val style = when (m.groupValues[1].length) {
                    1 -> MaterialTheme.typography.titleLarge
                    2 -> MaterialTheme.typography.titleMedium
                    else -> MaterialTheme.typography.titleSmall
                }
                Text(m.groupValues[2], style = style, modifier = Modifier.padding(vertical = 2.dp))
                shown++
            } ?: run {
                InlineText(line)
                shown++
            }
        }
        if (inCode && codeBuffer.isNotEmpty()) CodeBlock(codeBuffer.joinToString("\n"))
    }
}

@Composable
private fun CodeBlock(code: String) {
    Text(
        code,
        fontFamily = FontFamily.Monospace,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(6.dp))
            .padding(8.dp),
    )
}

@Composable
private fun InlineText(text: String, strike: Boolean = false, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val annotated = buildAnnotatedString {
        var last = 0
        for (m in inlineMarkup.findAll(text)) {
            append(text.substring(last, m.range.first))
            when {
                m.groups[2] != null -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(m.groupValues[2]) }
                m.groups[4] != null -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(m.groupValues[4]) }
                m.groups[6] != null -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(m.groupValues[6]) }
                m.groups[7] != null -> withStyle(SpanStyle(color = primary)) { append("#${m.groupValues[7]}") }
                m.groups[8] != null -> withStyle(SpanStyle(color = primary, textDecoration = TextDecoration.Underline)) { append(m.groupValues[8]) }
            }
            last = m.range.last + 1
        }
        append(text.substring(last))
    }
    Text(
        annotated,
        style = MaterialTheme.typography.bodyLarge.copy(
            textDecoration = if (strike) TextDecoration.LineThrough else null,
            fontStyle = if (strike) FontStyle.Italic else null,
        ),
        color = if (strike) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Flips the checkbox on [lineIndex] and returns the new content, or null if not a task line. */
fun toggleTaskLine(content: String, lineIndex: Int, checked: Boolean): String? {
    val lines = content.lines().toMutableList()
    val line = lines.getOrNull(lineIndex) ?: return null
    val m = taskLine.matchEntire(line) ?: return null
    lines[lineIndex] = "${m.groupValues[1]}- [${if (checked) "x" else " "}] ${m.groupValues[3]}"
    return lines.joinToString("\n")
}
