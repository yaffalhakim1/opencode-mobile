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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.crossfade
import dev.opencode.mobile.data.chat.ImageSource
import dev.opencode.mobile.ui.components.CodeBlock
import dev.opencode.mobile.ui.theme.extendedColors
/**
 * Spacing is driven by the pair of blocks, not by each block alone: a list
 * following a paragraph needs more air than two list items do.
 */
@Composable
internal fun BlockSpacer(previous: MdBlock, current: MdBlock) {
    val height = when {
        previous is MdBlock.ListBlock && current is MdBlock.ListBlock -> 8.dp
        previous is MdBlock.Heading -> 8.dp
        else -> 12.dp
    }
    Box(Modifier.size(height))
}

@Composable
internal fun MdBlockView(block: MdBlock, style: TextStyle, color: Color) {
    when (block) {
        is MdBlock.Heading -> Heading(block, color)
        is MdBlock.Paragraph -> InlineText(block.inline, style, color)
        is MdBlock.Code -> CodeBlock(text = block.text, label = block.language)
        is MdBlock.ListBlock -> ListView(block, style, color)
        is MdBlock.Quote -> Quote(block, style, color)
        is MdBlock.Alert -> Alert(block, style, color)
        is MdBlock.Table -> Table(block, color)
        is MdBlock.Image -> MarkdownImage(block)
        MdBlock.Rule -> HorizontalDivider(
            modifier = Modifier.padding(vertical = 4.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

/**
 * A picture in a document.
 *
 * The path in the markdown names a file on the machine running the server, so
 * it is resolved into a request for the server's own copy. The frame around it
 * is the same surface the code blocks use, so a picture and a snippet read as
 * the same kind of thing.
 *
 * A picture that cannot be loaded falls back to its description rather than
 * leaving a gap: the model usually wrote one, and a broken image says less
 * than the words do.
 */
@Composable
internal fun MarkdownImage(block: MdBlock.Image) {
    val context = LocalContext.current
    val baseUrl = LocalServer.baseUrl.current
    val directory = LocalServer.directory.current

    val resolved = remember(block.src, baseUrl, directory) {
        ImageSource.resolve(baseUrl, directory, block.src)
    }

    // The bytes of an inline picture, or the URL of one the server holds.
    val model: Any? = when (resolved) {
        is ImageSource.Resolved.Inline -> resolved.bytes
        is ImageSource.Resolved.Remote -> ImageRequest.Builder(context)
            .data(resolved.url)
            // The bytes come from the server, so the auth header travels with
            // the request instead of being in the URL.
            .httpHeaders(
                NetworkHeaders.Builder()
                    .set("Authorization", LocalServer.authorization.current)
                    .build()
            )
            .crossfade(true)
            .build()

        null -> null
    }

    var failed by remember(block.src) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.extendedColors.cardSurface, MaterialTheme.shapes.medium)
                .padding(6.dp),
        ) {
            if (model == null || failed) {
                Text(
                    text = block.alt.ifBlank { "Image" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(6.dp),
                )
            } else {
                AsyncImage(
                    model = model,
                    contentDescription = block.alt.ifBlank { null },
                    // A picture in a reply is content, not a thumbnail: keep its
                    // proportions and let it use the width it has.
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small),
                    onError = { failed = true },
                )
            }
        }
        if (block.alt.isNotBlank() && !failed) {
            Text(
                text = block.alt,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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