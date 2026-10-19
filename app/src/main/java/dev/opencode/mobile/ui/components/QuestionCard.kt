package dev.opencode.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.opencode.mobile.data.remote.dto.FormDto
import dev.opencode.mobile.data.remote.dto.FormFieldDto
import dev.opencode.mobile.data.remote.dto.FormValueDto
import dev.opencode.mobile.ui.theme.extendedColors

/**
 * A question the agent is blocked on.
 *
 * The `question` tool does not produce a message — it opens a form and waits.
 * Rendering it is the difference between "the agent is thinking" and "the agent
 * asked you something and nothing is happening until you answer".
 *
 * Answers are collected locally and submitted once, so a multi-field form is a
 * single round trip.
 */
@Composable
fun QuestionCard(
    form: FormDto,
    onAnswer: (Map<String, FormValueDto>) -> Unit,
    modifier: Modifier = Modifier,
) {
    // key -> single value, and key -> selected set for multiselect.
    val singles = remember(form.id) { mutableStateMapOf<String, String>() }
    val multis = remember(form.id) { mutableStateMapOf<String, Set<String>>() }

    val visibleFields = form.fields.filter { it.key.isNotBlank() }
    val complete = visibleFields.all { field ->
        if (!field.required) return@all true
        if (field.isMulti) multis[field.key].orEmpty().isNotEmpty()
        else singles[field.key].orEmpty().isNotBlank()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.extendedColors.cardSurface, MaterialTheme.shapes.medium)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Glyphs.Sparkle,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.tertiary,
            )
            Text(
                text = form.title.ifBlank { "The agent needs an answer" },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        visibleFields.forEach { field ->
            FieldInput(
                field = field,
                single = singles[field.key].orEmpty(),
                selected = multis[field.key].orEmpty(),
                onSingle = { singles[field.key] = it },
                onToggle = { option ->
                    val current = multis[field.key].orEmpty()
                    multis[field.key] =
                        if (option in current) current - option else current + option
                },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(
                onClick = {
                    val answer = visibleFields.associate { field ->
                        field.key to if (field.isMulti) {
                            FormValueDto(strings = multis[field.key].orEmpty().toList())
                        } else {
                            FormValueDto(string = singles[field.key].orEmpty())
                        }
                    }
                    onAnswer(answer)
                },
                enabled = complete,
            ) {
                Text("Answer", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun FieldInput(
    field: FormFieldDto,
    single: String,
    selected: Set<String>,
    onSingle: (String) -> Unit,
    onToggle: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        field.title?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        field.description?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        when {
            // Choices are the common case for `question`, and tapping a chip is
            // far better on a phone than typing an exact option value.
            field.isChoice -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                field.options.forEach { option ->
                    val isSelected = if (field.isMulti) option.value in selected
                    else option.value == single
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                },
                                RoundedCornerShape(10.dp),
                            )
                            .clickable {
                                if (field.isMulti) onToggle(option.value) else onSingle(option.value)
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                }
            }

            field.isBoolean -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("true" to "Yes", "false" to "No").forEach { (value, label) ->
                    val isSelected = single == value
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier
                            .background(
                                if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                },
                                RoundedCornerShape(10.dp),
                            )
                            .clickable { onSingle(value) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }

            else -> OutlinedTextField(
                value = single,
                onValueChange = onSingle,
                modifier = Modifier.fillMaxWidth(),
                placeholder = field.placeholder?.let { { Text(it) } },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                textStyle = MaterialTheme.typography.bodyMedium,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            )
        }
    }
}
