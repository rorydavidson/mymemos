package com.keltruc.mymemos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.keltruc.mymemos.data.text.TaskLine
import com.keltruc.mymemos.model.TagStyle
import com.keltruc.mymemos.ui.tags.LocalTagStyles
import com.keltruc.mymemos.ui.tags.label
import com.keltruc.mymemos.ui.tags.styleFor
import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableBody
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableHead
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.task.list.items.TaskListItemMarker
import org.commonmark.ext.task.list.items.TaskListItemsExtension
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.Image
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.IncludeSourceSpans
import org.commonmark.parser.Parser

/**
 * CommonMark rendering for memos: headings, lists, task lists with live checkboxes,
 * fenced code, quotes, tables, links, emphasis, strikethrough, autolinks and #tags.
 * Task toggles report the source line so the caller can edit the raw text.
 */
@Composable
fun MemoContent(
    content: String,
    onToggleTask: ((lineIndex: Int, checked: Boolean) -> Unit)?,
    onTagClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
) {
    val document = remember(content) { parser.parse(content) }
    val truncated = maxLines != Int.MAX_VALUE && content.lines().size > maxLines
    Column(modifier) {
        var child = document.firstChild
        var budget = maxLines
        while (child != null && budget > 0) {
            Block(child, content, onToggleTask, onTagClick, depth = 0)
            budget -= (child.sourceSpans.size).coerceAtLeast(1)
            child = child.next
        }
        if (truncated) Text("…", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val parser: Parser = Parser.builder()
    .extensions(listOf(TablesExtension.create(), StrikethroughExtension.create(), TaskListItemsExtension.create(), AutolinkExtension.create()))
    // Needed so a task checkbox knows which source line it edits.
    .includeSourceSpans(IncludeSourceSpans.BLOCKS)
    .build()

@Composable
private fun Block(node: Node, source: String, onToggleTask: ((Int, Boolean) -> Unit)?, onTagClick: ((String) -> Unit)?, depth: Int) {
    when (node) {
        is Heading -> Text(
            inline(node, onTagClick),
            style = when (node.level) {
                1 -> MaterialTheme.typography.titleLarge
                2 -> MaterialTheme.typography.titleMedium
                else -> MaterialTheme.typography.titleSmall
            },
            modifier = Modifier.padding(top = if (depth == 0) 4.dp else 0.dp, bottom = 2.dp),
        )
        is Paragraph -> InlineParagraph(node, onTagClick)
        is BulletList -> ListItems(node, source, onToggleTask, onTagClick, depth, ordered = false)
        is OrderedList -> ListItems(node, source, onToggleTask, onTagClick, depth, ordered = true, start = node.markerStartNumber ?: 1)
        is FencedCodeBlock -> CodeBlock(node.literal.trimEnd('\n'))
        is IndentedCodeBlock -> CodeBlock(node.literal.trimEnd('\n'))
        is BlockQuote -> Row(Modifier.padding(vertical = 4.dp)) {
            Box(Modifier.width(3.dp).padding(vertical = 2.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)).size(3.dp, 20.dp))
            Column(Modifier.padding(start = 12.dp)) { Children(node, source, onToggleTask, onTagClick, depth + 1) }
        }
        is ThematicBreak -> HorizontalDivider(Modifier.padding(vertical = 8.dp))
        is TableBlock -> Table(node, onTagClick)
        else -> Children(node, source, onToggleTask, onTagClick, depth)
    }
}

@Composable
private fun Children(node: Node, source: String, onToggleTask: ((Int, Boolean) -> Unit)?, onTagClick: ((String) -> Unit)?, depth: Int) {
    var child = node.firstChild
    while (child != null) {
        Block(child, source, onToggleTask, onTagClick, depth)
        child = child.next
    }
}

@Composable
private fun ListItems(list: Node, source: String, onToggleTask: ((Int, Boolean) -> Unit)?, onTagClick: ((String) -> Unit)?, depth: Int, ordered: Boolean, start: Int = 1) {
    var item = list.firstChild
    var n = start
    while (item != null) {
        if (item is ListItem) {
            val marker = item.firstChild as? TaskListItemMarker
            Row(Modifier.fillMaxWidth().padding(start = (depth * 14).dp), verticalAlignment = Alignment.Top) {
                if (marker != null) {
                    val line = item.sourceSpans.firstOrNull()?.lineIndex ?: -1
                    val checked = marker.isChecked
                    Checkbox(
                        checked = checked,
                        onCheckedChange = onToggleTask?.takeIf { line >= 0 }?.let { cb -> { cb(line, it) } },
                        modifier = Modifier.size(32.dp).semantics { contentDescription = if (checked) "Done task" else "Open task" },
                    )
                } else {
                    Text(
                        if (ordered) "$n." else "•",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(end = 8.dp, top = 1.dp).width(if (ordered) 22.dp else 12.dp),
                    )
                }
                Column(Modifier.padding(start = if (marker != null) 4.dp else 0.dp)) {
                    var child = if (marker != null) item.firstChild?.next else item.firstChild
                    while (child != null) {
                        if (child is Paragraph) {
                            InlineParagraph(child, onTagClick, strike = marker?.isChecked == true)
                        } else {
                            Block(child, source, onToggleTask, onTagClick, depth + 1)
                        }
                        child = child.next
                    }
                }
            }
            n++
        }
        item = item.next
    }
}

@Composable
private fun InlineParagraph(node: Node, onTagClick: ((String) -> Unit)?, strike: Boolean = false) {
    val text = inline(node, onTagClick)
    val style = MaterialTheme.typography.bodyLarge.copy(
        color = if (strike) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        textDecoration = if (strike) TextDecoration.LineThrough else null,
    )
    Text(
        text = text,
        style = style,
        modifier = Modifier.padding(vertical = 1.dp),
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * Memo text can come from any server or shared note, so a link only opens if its scheme is one
 * we are willing to hand to the system. Anything else (intent:, file:, javascript:, a custom
 * app scheme) is ignored rather than launched.
 */
private fun isOpenable(uri: String): Boolean {
    val scheme = uri.substringBefore(':', missingDelimiterValue = "").lowercase()
    return scheme in openableSchemes
}

private val openableSchemes = setOf("http", "https", "mailto", "geo")

private fun linkStyle(primary: Color, underline: Boolean = false) = TextLinkStyles(
    SpanStyle(color = primary, textDecoration = if (underline) TextDecoration.Underline else null),
)

private val tagRegex = Regex("(?<![\\w/])#([\\p{L}\\p{N}_/-]+)")

@Composable
private fun inline(node: Node, onTagClick: ((String) -> Unit)?): AnnotatedString {
    val uriHandler = LocalUriHandler.current
    // Links and tags carry their own click handling as LinkAnnotations. A tap handler over the
    // whole paragraph swallowed every tap on the text, which is most of a memo card, so tapping a
    // card's body did nothing instead of opening the memo.
    val openUrl = LinkInteractionListener { link ->
        val url = (link as? LinkAnnotation.Url)?.url ?: return@LinkInteractionListener
        if (isOpenable(url)) runCatching { uriHandler.openUri(url) }
    }
    val openTag = LinkInteractionListener { link ->
        (link as? LinkAnnotation.Clickable)?.tag?.let { onTagClick?.invoke(it) }
    }
    val primary = MaterialTheme.colorScheme.primary
    val codeBg = MaterialTheme.colorScheme.surfaceContainerHighest
    val styles = LocalTagStyles.current
    return buildAnnotatedString {
        var child = node.firstChild
        while (child != null) {
            appendInline(child, primary, codeBg, styles, openUrl, openTag)
            child = child.next
        }
    }
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.appendInline(
    node: Node,
    primary: Color,
    codeBg: Color,
    styles: Map<String, TagStyle>,
    openUrl: LinkInteractionListener,
    openTag: LinkInteractionListener,
) {
    when (node) {
        is org.commonmark.node.Text -> appendWithTags(node.literal, primary, styles, openTag)
        is Emphasis -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { children(node, primary, codeBg, styles, openUrl, openTag) }
        is StrongEmphasis -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { children(node, primary, codeBg, styles, openUrl, openTag) }
        is Strikethrough -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { children(node, primary, codeBg, styles, openUrl, openTag) }
        is Code -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg, fontSize = 14.sp)) { append(node.literal) }
        is Link -> withLink(LinkAnnotation.Url(node.destination, linkStyle(primary, underline = true), openUrl)) {
            if (node.firstChild == null) append(node.destination) else children(node, primary, codeBg, styles, openUrl, openTag)
        }
        is Image -> withLink(LinkAnnotation.Url(node.destination, linkStyle(primary), openUrl)) {
            append("🖼 ${node.title ?: node.destination}")
        }
        is SoftLineBreak -> append("\n")
        is HardLineBreak -> append("\n")
        else -> children(node, primary, codeBg, styles, openUrl, openTag)
    }
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.children(
    node: Node,
    primary: Color,
    codeBg: Color,
    styles: Map<String, TagStyle>,
    openUrl: LinkInteractionListener,
    openTag: LinkInteractionListener,
) {
    var child = node.firstChild
    while (child != null) {
        appendInline(child, primary, codeBg, styles, openUrl, openTag)
        child = child.next
    }
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.appendWithTags(
    text: String,
    primary: Color,
    styles: Map<String, TagStyle>,
    openTag: LinkInteractionListener,
) {
    var last = 0
    for (m in tagRegex.findAll(text)) {
        append(text.substring(last, m.range.first))
        val tag = m.groupValues[1]
        val style = styles.styleFor(tag)
        val colour = style?.colour?.let { Color(0xFF000000 or it.hex) }?.let { c -> if (c.luminance() > 0.7f) primary else c } ?: primary
        withLink(LinkAnnotation.Clickable(tag, TextLinkStyles(SpanStyle(color = colour, fontWeight = FontWeight.Medium)), openTag)) {
            append(styles.label(tag))
        }
        last = m.range.last + 1
    }
    append(text.substring(last))
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
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(8.dp))
            .padding(10.dp)
            .horizontalScroll(rememberScrollState()),
    )
}

@Composable
private fun Table(table: TableBlock, onTagClick: ((String) -> Unit)?) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).horizontalScroll(rememberScrollState())) {
        var section = table.firstChild
        while (section != null) {
            val isHead = section is TableHead
            if (section is TableHead || section is TableBody) {
                var row = section.firstChild
                while (row != null) {
                    if (row is TableRow) {
                        Row {
                            var cell = row.firstChild
                            while (cell != null) {
                                if (cell is TableCell) {
                                    Text(
                                        inline(cell, onTagClick),
                                        style = if (isHead) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp).width(120.dp),
                                    )
                                }
                                cell = cell.next
                            }
                        }
                        if (isHead) HorizontalDivider()
                    }
                    row = row.next
                }
            }
            section = section.next
        }
    }
}


@Suppress("unused")
private val keepContext = LocalContext

@Suppress("unused")
private val keepStyle: TextStyle? = null
