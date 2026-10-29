package dev.opencode.mobile.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.opencode.mobile.data.remote.dto.ModelDto
import dev.opencode.mobile.ui.components.Glyphs
import dev.opencode.mobile.ui.components.panel
import dev.opencode.mobile.ui.theme.extendedColors

/**
 * Agent, model and reasoning effort in one sheet.
 *
 * They are shown together because they are one decision: picking a heavy model
 * with an expensive agent at max effort is a different act from picking a cheap
 * one, and splitting them across three dialogs hides that.
 *
 * Each group is one card with hairlines between its rows, so the eye reads three
 * choices rather than a list of twenty. The title stays put while the cards
 * scroll: the model list is the long one, and a heading that scrolls away leaves
 * the sheet unnamed at exactly the point it is hardest to read.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatSelectionSheet(
    catalog: ChatCatalog,
    selection: ChatSelection,
    onSelectAgent: (String) -> Unit,
    onSelectModel: (String) -> Unit,
    onSelectEffort: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val agents = catalog.primaryAgents
    val models = catalog.enabledModels
    val efforts = catalog.efforts(selection.modelKey)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Text(
            text = "Who answers",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 10.dp),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (agents.isEmpty() && models.isEmpty()) {
                // An empty catalog is a sheet with a title and nothing to pick.
                // Saying so is cheaper than leaving the user to guess.
                Text(
                    text = "Loading agents and models…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                SelectionGroup(label = "Agent", items = agents) { agent ->
                    SelectionRow(
                        title = agent.name.ifBlank { agent.id },
                        subtitle = agent.description,
                        selected = agent.id == selection.agentID,
                        onClick = { onSelectAgent(agent.id) },
                    )
                }

                // Effort sits above the model list because the list is long enough
                // that burying a two-option control under it makes it unreachable.
                if (efforts.size > 1) {
                    SelectionGroup(
                        label = "Effort · ${catalog.model(selection.modelKey)?.name.orEmpty()}",
                        items = efforts,
                    ) { effort ->
                        SelectionRow(
                            title = effort.label,
                            selected = effort.id == selection.variant,
                            onClick = { onSelectEffort(effort.id) },
                            // Same size as an agent name, lighter weight: effort is a
                            // modifier of the model, not a third choice beside it.
                            titleStyle = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium,
                            ),
                        )
                    }
                }

                SelectionGroup(label = "Model", items = models) { model ->
                    SelectionRow(
                        title = model.name.ifBlank { model.modelID },
                        subtitle = modelFacts(model),
                        selected = model.key == selection.modelKey,
                        onClick = { onSelectModel(model.key) },
                    )
                }
            }
        }
    }
}

/** `opencode-go · images · 4 efforts` — what tells two similar models apart. */
private fun modelFacts(model: ModelDto): String =
    buildString {
        append(model.providerID)
        if (model.supportsImages) append(" · images")
        val variants = model.variants.size
        if (variants > 1) append(" · $variants efforts")
    }

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
}

/**
 * One labelled card of rows.
 *
 * The card is clipped to its own shape rather than each row carrying a corner:
 * the selected row's fill then meets the card's edge exactly, instead of poking
 * a square corner through the rounded one.
 *
 * An empty group draws nothing — the catalog is fetched, so a group can be
 * missing for a moment and a bare label with no rows reads as a bug.
 */
@Composable
private fun <T> SelectionGroup(
    label: String,
    items: List<T>,
    row: @Composable (T) -> Unit,
) {
    if (items.isEmpty()) return

    val scheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.large

    Column(modifier = Modifier.fillMaxWidth()) {
        SectionLabel(label)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .panel(
                    fill = MaterialTheme.extendedColors.cardSurface,
                    border = scheme.outlineVariant.copy(alpha = 0.5f),
                    shape = shape,
                )
                .selectableGroup(),
        ) {
            items.forEachIndexed { index, item ->
                if (index > 0) {
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = scheme.outlineVariant.copy(alpha = 0.3f),
                    )
                }
                row(item)
            }
        }
    }
}

/**
 * One choice.
 *
 * [selected] is announced to a screen reader and carried by the tick as well as
 * the fill, so the state survives a colour-blind reading and a glance alike.
 */
@Composable
private fun SelectionRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    subtitle: String? = null,
    titleStyle: TextStyle = MaterialTheme.typography.titleSmall,
) {
    val scheme = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) scheme.surfaceContainerHigh else Color.Transparent)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            // 48dp is the smallest a row may be; two lines of text already clear
            // it, a one-line effort row does not.
            .heightIn(min = 48.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = titleStyle,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (selected) {
            Icon(
                imageVector = Glyphs.Tick,
                // The row already reports itself as selected, so the tick is
                // decoration rather than a second announcement.
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.extendedColors.brand,
            )
        } else {
            // Keeps every title on the same left edge as the tick appears and
            // disappears; a row that shifts when chosen looks like it moved.
            Box(Modifier.size(18.dp))
        }
    }
}
