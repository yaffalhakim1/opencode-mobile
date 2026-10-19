package dev.opencode.mobile.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.opencode.mobile.data.remote.dto.AgentDto
import dev.opencode.mobile.data.remote.dto.ModelDto
import dev.opencode.mobile.ui.components.Glyphs
import dev.opencode.mobile.ui.theme.extendedColors

/**
 * Agent, model and reasoning effort in one sheet.
 *
 * They are shown together because they are one decision: picking a heavy model
 * with an expensive agent at max effort is a different act from picking a cheap
 * one, and splitting them across three dialogs hides that.
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
    val efforts = catalog.efforts(selection.modelKey)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                Text(
                    text = "Who answers",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }

            item { SectionLabel("Agent") }
            items(catalog.primaryAgents, key = { "agent-${it.id}" }) { agent ->
                AgentRow(
                    agent = agent,
                    selected = agent.id == selection.agentID,
                    onClick = { onSelectAgent(agent.id) },
                )
            }

            // Effort sits above the model list because the list is long enough
            // that burying a two-option control under it makes it unreachable.
            if (efforts.size > 1) {
                item { SectionLabel("Effort · ${catalog.model(selection.modelKey)?.name.orEmpty()}") }
                items(efforts, key = { "effort-${it.id}" }) { effort ->
                    EffortRow(
                        label = effort.label,
                        selected = effort.id == selection.variant,
                        onClick = { onSelectEffort(effort.id) },
                    )
                }
            }

            item { SectionLabel("Model") }
            items(catalog.enabledModels, key = { "model-${it.key}" }) { model ->
                ModelRow(
                    model = model,
                    selected = model.key == selection.modelKey,
                    onClick = { onSelectModel(model.key) },
                )
            }

            item { Box(Modifier.size(24.dp)) }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun AgentRow(agent: AgentDto, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.surface,
                MaterialTheme.shapes.small,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = agent.name.ifBlank { agent.id },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (agent.description.isNotBlank()) {
                Text(
                    text = agent.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        SelectedMark(selected)
    }
}

@Composable
private fun ModelRow(model: ModelDto, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.surface,
                MaterialTheme.shapes.small,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = model.name.ifBlank { model.modelID },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    append(model.providerID)
                    if (model.supportsImages) append(" · images")
                    val variants = model.variants.size
                    if (variants > 1) append(" · $variants efforts")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        SelectedMark(selected)
    }
}

@Composable
private fun EffortRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.surface,
                MaterialTheme.shapes.small,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        SelectedMark(selected)
    }
}

@Composable
private fun SelectedMark(selected: Boolean) {
    if (selected) {
        Icon(
            imageVector = Glyphs.Check,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.extendedColors.brand,
        )
    } else {
        Box(Modifier.size(18.dp))
    }
}
