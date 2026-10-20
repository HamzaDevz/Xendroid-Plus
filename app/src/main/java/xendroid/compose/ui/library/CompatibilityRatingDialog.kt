package xendroid.compose.ui.library

import xendroid.compose.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import xendroid.compose.compatibility.CompatStatus
import xendroid.compose.ui.design.controllerLeaves

/** The user's own result for a game; touch and D-pad/A both select through `selectable`. */
@Composable
fun CompatibilityRatingDialog(
    current: CompatStatus?,
    onDismiss: () -> Unit,
    onSave: (CompatStatus, String) -> Unit,
) {
    var selected by rememberSaveable { mutableStateOf(current) }
    var note by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rate_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CompatStatus.entries.forEach { status ->
                    Row(
                        Modifier.fillMaxWidth()
                            .selectable(selected = selected == status, onClick = { selected = status })
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected == status, onClick = { selected = status })
                        Text(compatStatusText(status))
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { if (it.length <= 500) note = it },
                    label = { Text(stringResource(R.string.rate_notes)) },
                    modifier = Modifier.fillMaxWidth().controllerLeaves(singleLine = false) { note.isEmpty() },
                )
                Text(stringResource(R.string.rate_note))
            }
        },
        confirmButton = {
            TextButton(enabled = selected != null, onClick = { selected?.let { onSave(it, note) } }) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
