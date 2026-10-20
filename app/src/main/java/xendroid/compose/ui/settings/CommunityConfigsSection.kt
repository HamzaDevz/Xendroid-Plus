package xendroid.compose.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import xendroid.compose.R
import xendroid.compose.community.CommunityConfigs
import xendroid.compose.community.CommunityConfigs.DraftProblem
import xendroid.compose.community.CommunityDevice
import xendroid.compose.community.CommunityException
import xendroid.compose.compatibility.CompatStatus
import xendroid.compose.compatibility.LoadedProfile
import xendroid.compose.settings.GameSettingsViewModel.CommunityState
import xendroid.compose.settings.SettingsSchema
import xendroid.compose.ui.design.controllerLeaves
import xendroid.compose.ui.library.compatStatusText

/** 15b: settings other players shared for the game, closest phone first, and sharing this game's own. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CommunityConfigsCard(
    state: CommunityState,
    canApply: Boolean,
    onSearch: () -> Unit,
    onPreview: (LoadedProfile) -> Unit,
    onVote: (String, Int) -> Unit,
    onDelete: (String) -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAll by rememberSaveable { mutableStateOf(false) }
    // (config id, name) waiting for "Delete?" to be confirmed.
    var confirmDelete by remember { mutableStateOf<Pair<String, String>?>(null) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.comm_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.comm_intro, state.server), style = MaterialTheme.typography.bodySmall, color = muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = onSearch, enabled = !state.busy) {
                    Text(stringResource(if (state.fetchedAt == null) R.string.comm_search else R.string.comm_search_again))
                }
                OutlinedButton(onClick = onShare, enabled = !state.busy) { Text(stringResource(R.string.comm_share)) }
                if (state.busy) CircularProgressIndicator(Modifier.size(24.dp).align(Alignment.CenterVertically), strokeWidth = 2.dp)
            }
            state.fetchedAt?.let { at ->
                val date = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT)
                    .format(java.util.Date(at))
                Text(stringResource(R.string.comm_searched, date), style = MaterialTheme.typography.bodySmall, color = muted)
            }
            state.error?.let { Text(communityErrorText(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            if (state.fetchedAt != null && state.entries.isEmpty() && state.hidden == 0 && state.skipped.isEmpty()) {
                Text(stringResource(R.string.comm_none), style = MaterialTheme.typography.bodySmall)
            }
            val shown = if (showAll) state.entries else state.entries.take(CommunityConfigs.FIRST_SHOWN)
            shown.forEach { entry ->
                val id = entry.config.id
                CommunityBlock(entry, state.myVotes[id], mine = id in state.mine, applyEnabled = canApply && !state.busy,
                    voteEnabled = !state.busy, onPreview, onVote, onDelete = { confirmDelete = id to entry.config.name })
            }
            if (!showAll && state.entries.size > CommunityConfigs.FIRST_SHOWN) {
                TextButton(onClick = { showAll = true }) { Text(stringResource(R.string.comm_show_all, state.entries.size)) }
            }
            // What this phone shared but the list does not show (not searched yet, hidden, or skipped).
            state.mine.values.filter { own -> state.entries.none { it.config.id == own.configId } }.forEach { own ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.comm_yours_item, own.name), style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f))
                    TextButton(onClick = { confirmDelete = own.configId to own.name }, enabled = !state.busy) {
                        Text(stringResource(R.string.comm_delete))
                    }
                }
            }
            if (state.hidden > 0) {
                Text(pluralStringResource(R.plurals.comm_hidden, state.hidden, state.hidden),
                    style = MaterialTheme.typography.bodySmall, color = muted)
            }
            if (state.skipped.isNotEmpty()) {
                Text(stringResource(R.string.comm_skipped, state.skipped.take(3).joinToString("; ")) +
                    (if (state.skipped.size > 3) " " + stringResource(R.string.prof_skipped_more, state.skipped.size - 3) else ""),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
    confirmDelete?.let { (id, name) ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            text = { Text(stringResource(R.string.comm_delete_confirm, name)) },
            confirmButton = { TextButton(onClick = { confirmDelete = null; onDelete(id) }) { Text(stringResource(R.string.comm_delete)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CommunityBlock(
    entry: CommunityConfigs.Entry,
    myVote: Int?,
    mine: Boolean,
    applyEnabled: Boolean,
    voteEnabled: Boolean,
    onPreview: (LoadedProfile) -> Unit,
    onVote: (String, Int) -> Unit,
    onDelete: () -> Unit,
) {
    val config = entry.config
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(config.name, style = MaterialTheme.typography.bodyLarge)
        Text(listOfNotNull(matchText(entry.match), compatStatusText(entry.status),
            if (mine) stringResource(R.string.comm_yours) else null).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.comm_device, deviceText(config.device), config.appBuild.ifBlank { "${config.appVersionCode}" },
            config.createdAt), style = MaterialTheme.typography.bodySmall, color = muted)
        Text(config.note, style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { onPreview(entry.profile) }, enabled = applyEnabled) { Text(stringResource(R.string.prof_preview)) }
            TextButton(onClick = { onVote(config.id, 1) }, enabled = voteEnabled) {
                Text(stringResource(R.string.comm_helped, config.votesUp), fontWeight = if (myVote == 1) FontWeight.Bold else FontWeight.Normal)
            }
            TextButton(onClick = { onVote(config.id, -1) }, enabled = voteEnabled) {
                Text(stringResource(R.string.comm_not_helped, config.votesDown),
                    fontWeight = if (myVote == -1) FontWeight.Bold else FontWeight.Normal)
            }
            if (mine) TextButton(onClick = onDelete, enabled = voteEnabled) { Text(stringResource(R.string.comm_delete)) }
        }
    }
}

@Composable
private fun matchText(match: CommunityConfigs.Match): String = stringResource(when (match) {
    CommunityConfigs.Match.MODEL -> R.string.comm_match_model
    CommunityConfigs.Match.SOC -> R.string.comm_match_soc
    CommunityConfigs.Match.GPU -> R.string.comm_match_gpu
    CommunityConfigs.Match.GPU_FAMILY -> R.string.comm_match_family
    CommunityConfigs.Match.OTHER -> R.string.comm_match_other
})

/** "samsung SM-S911B · SM8550 · Adreno (TM) 740 · Qualcomm 0.762 · API 34". */
@Composable
private fun deviceText(device: CommunityDevice): String =
    listOf("${device.manufacturer} ${device.model}".trim(), device.soc, device.gpu, device.driver,
        if (device.androidSdk > 0) "API ${device.androidSdk}" else "")
        .filter { it.isNotBlank() }.joinToString(" · ")
        .ifEmpty { stringResource(R.string.comm_unknown_device) }

/** U02: why a community call failed, in the shown language. */
@Composable
fun communityErrorText(error: CommunityException): String = when (error.kind) {
    CommunityException.Kind.NETWORK -> stringResource(R.string.comm_err_network)
    CommunityException.Kind.REJECTED -> error.detail?.let { stringResource(R.string.comm_err_rejected, it) }
        ?: stringResource(R.string.comm_err_rejected_plain)
    CommunityException.Kind.FORBIDDEN -> stringResource(R.string.comm_err_forbidden)
    CommunityException.Kind.NOT_FOUND -> stringResource(R.string.comm_err_not_found)
    CommunityException.Kind.BUSY -> stringResource(R.string.comm_err_busy)
    CommunityException.Kind.SERVER -> stringResource(R.string.comm_err_server)
    CommunityException.Kind.INVALID_ANSWER -> stringResource(R.string.comm_err_invalid)
}

/**
 * Sharing this game's settings: a name, what they fix and what was seen with them, then exactly
 * what goes to the server (the settings, the phone, the build) and what stays here; nothing is
 * sent before "Share".
 */
@Composable
fun CommunityShareDialog(
    server: String,
    draftOf: (name: String, note: String, result: CompatStatus?) -> CommunityConfigs.Draft?,
    onShare: (name: String, note: String, result: CompatStatus?) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var result by rememberSaveable { mutableStateOf<CompatStatus?>(null) }
    val draft = draftOf(name, note, result)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.comm_share)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= CommunityConfigs.MAX_NAME) name = it },
                    label = { Text(stringResource(R.string.comm_share_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().controllerLeaves { name.isEmpty() },
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { if (it.length <= CommunityConfigs.MAX_NOTE) note = it },
                    label = { Text(stringResource(R.string.comm_share_note)) },
                    modifier = Modifier.fillMaxWidth().controllerLeaves(singleLine = false) { note.isEmpty() },
                )
                Text(stringResource(R.string.comm_share_result), style = MaterialTheme.typography.bodyMedium)
                CompatStatus.entries.forEach { status ->
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = result == status, onClick = { result = status }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = result == status, onClick = { result = status })
                        Text(compatStatusText(status), style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (draft != null) {
                    Text(stringResource(R.string.comm_share_sent, server), style = MaterialTheme.typography.bodyMedium)
                    draft.shared.forEach { (key, raw) ->
                        val setting = SettingsSchema.byKey[key] ?: return@forEach
                        Text("• ${settingTitle(setting)}: ${valueText(setting, raw)}", style = MaterialTheme.typography.bodySmall)
                    }
                    Text(stringResource(R.string.comm_share_phone, deviceText(draft.device)), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.comm_share_app, draft.upload?.appBuild ?: "—"), style = MaterialTheme.typography.bodySmall)
                    if (draft.notShared.isNotEmpty()) {
                        val names = draft.notShared.map { key -> SettingsSchema.byKey[key]?.let { settingTitle(it) } ?: key }
                        Text(pluralStringResource(R.plurals.comm_share_not_sent, names.size, names.size, names.joinToString(", ")),
                            style = MaterialTheme.typography.bodySmall, color = muted)
                    }
                    Text(stringResource(R.string.comm_share_privacy), style = MaterialTheme.typography.bodySmall, color = muted)
                    draft.problems.forEach { problem ->
                        Text(stringResource(when (problem) {
                            DraftProblem.NOTHING_TO_SHARE -> R.string.comm_problem_nothing
                            DraftProblem.TOO_MANY_SETTINGS -> R.string.comm_problem_too_many
                            DraftProblem.NAME_LENGTH -> R.string.comm_problem_name
                            DraftProblem.NOTE_LENGTH -> R.string.comm_problem_note
                            DraftProblem.NO_RESULT -> R.string.comm_problem_result
                        }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onShare(name, note, result) }, enabled = draft?.upload != null) {
                Text(stringResource(R.string.comm_share_send))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
