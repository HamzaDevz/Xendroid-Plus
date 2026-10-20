package xendroid.compose.ui.library

import xendroid.compose.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import xendroid.compose.data.GameCollection
import xendroid.compose.data.GameCollections
import xendroid.compose.ui.design.controllerLeaves

/**
 * L06: which of the user's collections hold one game, a new collection, and deleting one
 * (only the group goes; its games stay in the library).
 */
@Composable
fun CollectionsDialog(
    gameName: String,
    gameKey: String,
    collections: List<GameCollection>,
    onSetMember: (collection: String, member: Boolean) -> Unit,
    onCreate: (name: String) -> Unit,
    onDelete: (collection: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var newName by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf<String?>(null) }
    confirmDelete?.let { name ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.col_delete_title, name)) },
            text = { Text(stringResource(R.string.col_delete_text)) },
            confirmButton = { TextButton(onClick = { confirmDelete = null; onDelete(name) }) { Text(stringResource(R.string.common_delete)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.col_title, gameName)) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (collections.isEmpty()) Text(stringResource(R.string.col_none))
                collections.forEach { collection ->
                    val member = gameKey in collection.members
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = member, onCheckedChange = { onSetMember(collection.name, it) })
                        Text("${collection.name} (${collection.members.size})", Modifier.weight(1f))
                        TextButton(onClick = { confirmDelete = collection.name }) { Text(stringResource(R.string.common_delete)) }
                    }
                }
                if (collections.size < GameCollections.MAX_COLLECTIONS) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it.take(GameCollections.MAX_NAME) },
                            label = { Text(stringResource(R.string.col_new)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f).controllerLeaves { newName.isEmpty() },
                        )
                        TextButton(enabled = GameCollections.cleanName(newName) != null, onClick = {
                            onCreate(newName)
                            newName = ""
                        }) { Text(stringResource(R.string.col_create)) }
                    }
                }
                Text(stringResource(R.string.col_note), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) } },
    )
}
