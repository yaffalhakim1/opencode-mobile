package dev.opencode.mobile.ui.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.opencode.mobile.ui.components.CodeBlock
import dev.opencode.mobile.ui.theme.extendedColors

/**
 * Renders a markdown document as Compose content.
 *
 * Blocks become real composables rather than one giant `Text`, because code
 * fences, tables and lists each need their own layout. Inline runs are built
 * into a single `AnnotatedString` per block, so text still wraps and selects as
 * one paragraph.
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    val blocks = remember(markdown) { MarkdownParserAdapter.parse(markdown) }

    Column(modifier = modifier.fillMaxWidth()) {
        blocks.forEachIndexed { index, block ->
            if (index > 0) BlockSpacer(previous = blocks[index - 1], current = block)
            MdBlockView(block, style, color)
        }
    }
}

/**
 * Spacing is driven by the pair of blocks, not by each block alone: a list
 * following a paragraph needs more air than two list items do.
 */
@Composable
private fun BlockSpacer(previous: MdBlock, current: MdBlock) {
    val height = when {
        previous is MdBlock.ListBlock && current is MdBlock.ListBlock -> 8.dp
        previous is MdBlock.Heading -> 8.dp
        else -> 12.dp
    }
    Box(Modifier.size(height))
}

@Composable
private fun MdBlockView(block: MdBlock, style: TextStyle, color: Color) {
    when (block) {
        is MdBlock.Heading -> Heading(block, color)
        is MdBlock.Paragraph -> InlineText(block.inline, style, color)
        is MdBlock.Code -> CodeBlock(text = block.text, label = block.language)
        is MdBlock.ListBlock -> ListView(block, style, color)
        is MdBlock.Quote -> Quote(block, style, color)
        is MdBlock.Alert -> Alert(block, style, color)
        is MdBlock.Table -> Table(block, color)
        MdBlock.Rule -> HorizontalDivider(
            modifier = Modifier.padding(vertical = 4.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

@Composable
private fun Heading(block: MdBlock.Heading, color: Color) {
    val style = when (block.level) {
        1 -> MaterialTheme.typography.headlineSmall
        2 -> MaterialTheme.typography.titleLarge
        3 -> MaterialTheme.typography.titleMedium
        else -> MaterialTheme.typography.titleSmall
    }
    InlineText(
        inline = block.inline,
        style = style,
        color = color,
        modifier = Modifier.padding(top = if (block.level <= 2) 4.dp else 0.dp),
    )
}

@Composable
private fun Quote(block: MdBlock.Quote, style: TextStyle, color: Color) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(2.dp)),
        )
        Column(
            modifier = Modifier.padding(start = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            block.blocks.forEach { child ->
                MdBlockView(
                    child,
                    style.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                    MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Alert(block: MdBlock.Alert, style: TextStyle, color: Color) {
    val tint = when (block.kind) {
        AlertKind.Note -> MaterialTheme.colorScheme.tertiary
        AlertKind.Tip -> MaterialTheme.extendedColors.success
        AlertKind.Important -> MaterialTheme.colorScheme.primary
        AlertKind.Warning -> Color(0xFFE0A02A)
        AlertKind.Caution -> MaterialTheme.colorScheme.error
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(tint.copy(alpha = 0.08f), MaterialTheme.shapes.medium)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = block.kind.label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = tint,
        )
        block.blocks.forEach { child -> MdBlockView(child, style, color) }
    }
}

@Composable
private fun ListView(block: MdBlock.ListBlock, style: TextStyle, color: Color) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        block.items.forEachIndexed { index, item ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier.width(22.dp),
                    contentAlignment = Alignment.TopStart,
                ) {
                    Bullet(block, index, item.checked)
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    item.blocks.forEach { child ->
                        when (child) {
                            // A nested list hangs under the item text.
                            is MdBlock.ListBlock -> ListView(child, style, color)
                            else -> MdBlockView(child, style, color)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Bullet(block: MdBlock.ListBlock, index: Int, checked: Boolean?) {
    val markerColor = MaterialTheme.colorScheme.onSurfaceVariant
    when {
        checked != null -> Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(14.dp)
                .background(
                    if (checked) MaterialTheme.colorScheme.primary else Color.Transparent,
                    RoundedCornerShape(4.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Text(
                    text = "✓",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .background(MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                        .padding(1.dp)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(3.dp)),
                )
            }
        }

        block.ordered -> Text(
            text = "${block.start + index}.",
            style = MaterialTheme.typography.bodyMedium,
            color = markerColor,
        )

        else -> Box(
            modifier = Modifier.padding(top = 9.dp, start = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .background(markerColor, CircleShape),
            )
        }
    }
}

/**
 * Tables render as a compact grid; a table whose cells wrap is unreadable, so
 * cells clamp to a few lines and the table keeps the full content width.
 */
@Composable
private fun Table(block: MdBlock.Table, color: Color) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.extendedColors.cardSurface, MaterialTheme.shapes.small)
            .padding(vertical = 4.dp),
    ) {
        TableRow(block.header, block.alignments, color, header = true)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        block.rows.forEach { row ->
            TableRow(row, block.alignments, color, header = false)
        }
    }
}

@Composable
private fun TableRow(
    cells: List<MdInline>,
    alignments: List<ColumnAlign>,
    color: Color,
    header: Boolean,
) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        cells.forEachIndexed { index, cell ->
            val align = alignments.getOrNull(index) ?: ColumnAlign.Start
            Text(
                text = cell.toAnnotatedString(
                    base = if (header) {
                        MaterialTheme.typography.labelLarge
                    } else {
                        MaterialTheme.typography.bodyMedium
                    },
                    color = color,
                ),
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                textAlign = when (align) {
                    ColumnAlign.Start -> TextAlign.Start
                    ColumnAlign.Center -> TextAlign.Center
                    ColumnAlign.End -> TextAlign.End
                },
                maxLines = 6,
            )
        }
    }
}

@Composable
private fun InlineText(
    inline: MdInline,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val palette = SpanPalette(
        link = MaterialTheme.colorScheme.primary,
        code = MaterialTheme.colorScheme.onSurfaceVariant,
        codeBackground = MaterialTheme.extendedColors.codeBackground,
    )
    val annotated = remember(inline, palette) { inline.toAnnotatedString(palette) }
    Text(
        text = annotated,
        style = style.copy(color = color),
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * Maps [MdInline] onto an `AnnotatedString`.
 *
 * Spans are applied in nesting order so an inner style (italic inside bold)
 * wins over the outer one for the properties it sets, which is what the source
 * markdown means.
 */
@Composable
private fun MdInline.toAnnotatedString(base: TextStyle, color: Color): AnnotatedString {
    val palette = SpanPalette(
        link = MaterialTheme.colorScheme.primary,
        code = MaterialTheme.colorScheme.onSurfaceVariant,
        codeBackground = MaterialTheme.extendedColors.codeBackground,
    )
    return toAnnotatedString(palette)
}

private fun MdInline.toAnnotatedString(palette: SpanPalette): AnnotatedString {
    if (spans.isEmpty()) return AnnotatedString(text)

    return buildAnnotatedString {
        var cursor = 0
        spans.sortedBy { it.start }.forEach { span ->
            if (span.start > cursor) append(text.substring(cursor, span.start))
            withStyle(span.toSpanStyle(palette)) {
                append(text.substring(span.start, span.end))
            }
            cursor = span.end
        }
        if (cursor < text.length) append(text.substring(cursor))
    }
}

/** Resolved theme colours, so span construction stays a pure function. */
private data class SpanPalette(
    val link: Color,
    val code: Color,
    val codeBackground: Color,
)

private fun MdSpan.toSpanStyle(palette: SpanPalette): SpanStyle {
    var result = SpanStyle()
    // Stable order so a range that is both bold and italic composes predictably.
    MdSpanStyle.entries.forEach { style ->
        if (style !in styles) return@forEach
        result = result.merge(
            when (style) {
                MdSpanStyle.Emphasis -> SpanStyle(fontStyle = FontStyle.Italic)
                MdSpanStyle.Strong -> SpanStyle(fontWeight = FontWeight.SemiBold)
                MdSpanStyle.Strikethrough ->
                    SpanStyle(textDecoration = TextDecoration.LineThrough)
                MdSpanStyle.Code -> SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = palette.code,
                    background = palette.codeBackground,
                )
                MdSpanStyle.Link -> SpanStyle(
                    color = palette.link,
                    textDecoration = TextDecoration.Underline,
                )
            },
        )
    }
    return result
}
