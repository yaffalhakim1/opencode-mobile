package dev.opencode.mobile.ui.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.opencode.mobile.ui.components.CodeBlock
import dev.opencode.mobile.ui.theme.extendedColors
import dev.opencode.mobile.ui.theme.PlexMono

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
    streaming: Boolean = false,
) {
    val blocks = rememberMarkdownBlocks(markdown, streaming)

    Column(modifier = modifier.fillMaxWidth()) {
        blocks.forEachIndexed { index, block ->
            if (index > 0) BlockSpacer(previous = blocks[index - 1], current = block)
            MdBlockView(block, style, color)
        }
    }
}

/**
 * Parses the document into blocks.
 *
 * A finished message is parsed once, when it changes. A streaming one is
 * different: the text changes many times a second, and each change would
 * re-parse the whole document and rebuild every inline span. Over the length
 * of an answer that is quadratic, and it all happens on the main thread while
 * the reader is watching the text appear.
 *
 * So a streaming document is parsed on a timer instead. The text the reader
 * sees lags by up to one interval, which at a tenth of a second is invisible,
 * and the work per second stays flat however long the answer gets.
 */
@Composable
private fun rememberMarkdownBlocks(markdown: String, streaming: Boolean): List<MdBlock> {
    if (!streaming) {
        return remember(markdown) { MarkdownParserAdapter.parse(markdown) }
    }

    // The bucket only advances once per interval, so a burst of deltas inside
    // one window parses once rather than once per delta.
    val bucket = System.currentTimeMillis() / STREAM_PARSE_INTERVAL_MS
    val cache = remember { ParseCache() }
    return remember(bucket) {
        if (cache.markdown != markdown) {
            cache.blocks = MarkdownParserAdapter.parse(markdown)
            cache.markdown = markdown
        }
        cache.blocks
    }
}

/** The last parse, so an unchanged document is not parsed again. */
private class ParseCache {
    var markdown: String = ""
    var blocks: List<MdBlock> = emptyList()
}

/**
 * How often a streaming document is re-parsed.
 *
 * Fast enough that the text still reads as arriving, slow enough that the
 * parse is not the thing deciding the frame rate.
 */
private const val STREAM_PARSE_INTERVAL_MS = 100L

@Composable
internal fun InlineText(
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
    val context = LocalContext.current
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }

    Text(
        text = annotated,
        style = style.copy(color = color),
        modifier = modifier
            .fillMaxWidth()
            // A link is only a colour until a tap can find it. The tap is
            // resolved against the layout the text was drawn with.
            .pointerInput(inline, layout) {
                detectTapGestures { offset ->
                    val position = layout?.getOffsetForPosition(offset) ?: return@detectTapGestures
                    val url = inline.linkAt(position) ?: return@detectTapGestures
                    openLink(context, url)
                }
            },
        onTextLayout = { layout = it },
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
internal fun MdInline.toAnnotatedString(base: TextStyle, color: Color): AnnotatedString {
    val palette = SpanPalette(
        link = MaterialTheme.colorScheme.primary,
        code = MaterialTheme.colorScheme.onSurfaceVariant,
        codeBackground = MaterialTheme.extendedColors.codeBackground,
    )
    return toAnnotatedString(palette)
}

internal fun MdInline.toAnnotatedString(palette: SpanPalette): AnnotatedString {
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

/**
 * The link at [position], if there is one.
 *
 * The destination lives on the span, not on the rendered text, so a tap is
 * resolved by asking which span covers the character it landed on.
 */
internal fun MdInline.linkAt(position: Int): String? =
    spans.firstOrNull { position >= it.start && position < it.end }?.url

/** Opens [url] in whatever the phone uses for it. */
internal fun openLink(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
    Unit
}

/** Resolved theme colours, so span construction stays a pure function. */
internal data class SpanPalette(
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
                    fontFamily = PlexMono,
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
