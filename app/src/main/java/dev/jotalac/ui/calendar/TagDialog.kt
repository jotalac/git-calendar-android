package dev.jotalac.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.jotalac.R
import dev.jotalac.ui.theme.TagColor
import dev.jotalac.ui.theme.contentColorOn

/** Create or edit a tag, or the untagged color of a calendar. Ported from web-client TagModal.vue. */
@Composable
fun TagDialog(
    target: TagEditTarget,
    defaultColor: String,
    onSave: (name: String, color: String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val untagged = target.tag?.id == ""
    var name by rememberSaveable { mutableStateOf(target.tag?.name.orEmpty()) }
    var color by rememberSaveable { mutableStateOf(target.tag?.color ?: if (untagged) defaultColor else "blue") }
    var nameError by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (target.tag == null) stringResource(R.string.create_tag, target.calendar)
                else stringResource(R.string.edit_tag, target.tag.name)
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = if (untagged) stringResource(R.string.tag_untagged) else name,
                    onValueChange = { name = it; nameError = false },
                    label = { Text(stringResource(R.string.tag_name)) },
                    readOnly = untagged,
                    enabled = !untagged,
                    singleLine = true,
                    isError = nameError,
                    supportingText = if (nameError) {
                        { Text(stringResource(R.string.error_tag_name_required)) }
                    } else null,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.size(12.dp))
                Text(stringResource(R.string.tag_color), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.size(8.dp))
                ColorPicker(color) { color = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (!untagged && name.isBlank()) {
                    nameError = true
                } else {
                    onSave(name.trim(), color)
                    onDismiss()
                }
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                if (target.tag != null && !untagged) {
                    TextButton(onClick = { onDelete(); onDismiss() }) {
                        Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPicker(selected: String, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TagColor.entries.forEach { option ->
            val label = stringResource(option.label)
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(option.color)
                    .then(
                        if (option.id == selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        else Modifier
                    )
                    .clickable(role = Role.RadioButton) { onSelect(option.id) }
                    .semantics { contentDescription = label },
                contentAlignment = Alignment.Center,
            ) {
                if (option.id == selected) Icon(Icons.Filled.Check, null, tint = contentColorOn(option.color))
            }
        }
        Spacer(Modifier.width(1.dp))
    }
}
