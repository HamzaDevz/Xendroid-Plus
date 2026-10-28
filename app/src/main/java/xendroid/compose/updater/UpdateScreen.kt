package xendroid.compose.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xendroid.compose.BuildConfig
import xendroid.compose.R
import xendroid.compose.ui.design.BadgeTone
import xendroid.compose.ui.design.NoteTone
import xendroid.compose.ui.design.Xd
import xendroid.compose.ui.design.XdBadge
import xendroid.compose.ui.design.XdButton
import xendroid.compose.ui.design.XdButtonKind
import xendroid.compose.ui.design.XdButtonSize
import xendroid.compose.ui.design.XdCard
import xendroid.compose.ui.design.XdIcons
import xendroid.compose.ui.design.XdLogo
import xendroid.compose.ui.design.XdNote
import xendroid.compose.ui.design.XdSingleScreen
import xendroid.compose.ui.design.XdText
import xendroid.compose.ui.design.XdTwoColumns
import xendroid.compose.ui.settings.XdUpdateChannelOption

/** Where an offered update is: downloaded, its SHA-256 checked, handed to Android's installer. */
enum class UpdateStage { DOWNLOAD, VERIFY, INSTALL }

/**
 * R03/R04, lote 6: the updater as state a screen shows: what the last check said (an update,
 * the latest, paused) and what is being done with an update, one visible step at a time, with a
 * failure said in one sentence.
 */
class UpdateState(initial: UpdateResult? = null) {
    var result by mutableStateOf(initial)
    var checking by mutableStateOf(false)
    var stage by mutableStateOf<UpdateStage?>(null)
    /** Download progress, 0..1. */
    var progress by mutableFloatStateOf(0f)
    var failure by mutableStateOf<String?>(null)
    /** The download is checked but Android needs "install unknown apps" for this app first. */
    var needsPermission by mutableStateOf(false)
    /** "Skip this version" was chosen: it is not offered again. */
    var skipped by mutableStateOf(false)
    private var job: Job? = null

    /** Looks for an update now, or says when it may (the check is paused for a few minutes after one). */
    fun check(context: Context, scope: CoroutineScope) {
        if (checking || stage != null) return
        failure = null
        skipped = false
        if (!shouldCheckForUpdates(context)) {
            result = UpdateResult.Cooldown(getRemainingCooldown(context))
            return
        }
        checking = true
        scope.launch {
            try {
                result = checkForUpdates(context)
                saveLastCheck(context)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                failure = context.getString(R.string.xd_up_check_failed, e.message ?: e.javaClass.simpleName)
            } finally {
                checking = false
            }
        }
    }

    /** Downloads [release]'s APK (size and SHA-256 checked as it comes), checks it is this app,
     *  newer and signed by the same key, and hands it to Android, which asks the user. */
    fun install(context: Context, scope: CoroutineScope, release: FeedRelease) {
        failure = null
        needsPermission = false
        stage = UpdateStage.DOWNLOAD
        progress = 0f
        job = scope.launch {
            try {
                val apk = downloadUpdate(context, release) { progress = it.coerceIn(0f, 1f) }
                stage = UpdateStage.VERIFY
                val refusal = withContext(Dispatchers.IO) { UpdateInstaller.verify(context, apk) }
                if (refusal != null) {
                    apk.delete()
                    failure = context.getString(R.string.upd_not_installed_why, refusal)
                    stage = null
                    return@launch
                }
                stage = UpdateStage.INSTALL
                if (!context.packageManager.canRequestPackageInstalls()) {
                    needsPermission = true
                    return@launch
                }
                UpdateInstaller.install(context, apk)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) { stage = null; throw e }
                failure = (e as? UpdateDownloadException)?.let { downloadFailureText(context, it) }
                    ?: context.getString(R.string.upd_download_failed, e.message ?: e.javaClass.simpleName)
                stage = null
            }
        }
    }

    fun cancel() {
        job?.cancel()
        stage = null
    }

    fun skip(context: Context, release: FeedRelease) {
        release.tag?.versionCode?.let { skipVersion(context, it) }
        skipped = true
        result = UpdateResult.Latest(BuildConfig.VERSION_NAME)
    }
}

/** The SemVer of a published build ("1.2.0", "1.3.0-preview.88"); null for local and
 *  test builds, which are named after their commit. */
private val releaseVersion: String? =
    BuildConfig.VERSION_NAME.takeIf { Regex("""\d+\.\d+\.\d+(-[0-9A-Za-z.-]+)?""").matches(it) }

@Composable
fun rememberUpdateState(initial: UpdateResult? = null): UpdateState = remember { UpdateState(initial) }

/**
 * Lote 6: "App updates" as a screen: the installed version, its channel and the last check with
 * "Check now", the channel to choose, and the update card ([UpdatePanel]), beside them in
 * landscape. Opened from the library menu or About, it checks at once (unless a check just ran).
 * A build without an update channel ([feed] false) says where its updates come from instead.
 */
@Composable
fun UpdateScreen(
    onBack: () -> Unit,
    state: UpdateState = rememberUpdateState(),
    checkOnOpen: Boolean = true,
    feed: Boolean = updateRepository() != null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = Xd.colors
    LaunchedEffect(Unit) { if (feed && checkOnOpen && state.result == null) state.check(context, scope) }
    XdSingleScreen(title = stringResource(R.string.upd_title), subtitle = "build ${BuildConfig.VERSION_CODE} · ${BuildConfig.BUILD_BASE_COMMIT.take(8)}",
        onBack = onBack, headIcon = XdIcons.download) {
        BoxWithConstraints {
            XdTwoColumns(maxWidth > 760.dp, left = {
                XdCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        XdLogo(46.dp)
                        Column(Modifier.weight(1f)) {
                            Text("${stringResource(R.string.app_name)} ${releaseVersion ?: "v${BuildConfig.VERSION_CODE}"}", style = XdText.h2, color = c.fg)
                            val version = releaseVersion ?: BuildConfig.VERSION_NAME.take(9)
                            Text(if (!feed) stringResource(R.string.xd_up_hero_no_feed, version) else stringResource(R.string.xd_up_hero_line, version,
                                channelText(updateChannel(context)), lastUpdateCheck(context)?.let {
                                    DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
                                } ?: stringResource(R.string.xd_lib_never)), style = XdText.small, color = c.fg3)
                        }
                        if (feed) XdButton(stringResource(if (state.checking) R.string.xd_up_checking else R.string.xd_up_check_now), { state.check(context, scope) },
                            size = XdButtonSize.SM, icon = XdIcons.refresh, enabled = !state.checking && state.stage == null)
                    }
                }
                if (!feed) XdNote(stringResource(R.string.xd_up_no_feed), tone = NoteTone.INFO)
                XdUpdateChannelOption(shown = feed)
            }, right = {
                UpdatePanel(state, onLater = onBack)
            })
        }
    }
}

/** An update found by the automatic check at start: the update card in a sheet; it cannot be
 *  dismissed while an update is being downloaded or handed over. */
@Composable
fun UpdateSheet(release: FeedRelease, onDismiss: () -> Unit) {
    val state = rememberUpdateState(UpdateResult.Available(release))
    xendroid.compose.ui.design.XdSheet(onDismiss = onDismiss, title = null, wide = true, dismissible = state.stage == null) {
        UpdatePanel(state, onLater = onDismiss)
    }
}

@Composable
private fun channelText(channel: UpdateChannel): String = stringResource(when (channel) {
    UpdateChannel.STABLE -> R.string.upd_stable
    UpdateChannel.PREVIEW -> R.string.upd_preview
    UpdateChannel.OFF -> R.string.upd_off
}).lowercase()

/** What the last check said: checking, the latest, paused, or the offered update with its steps. */
@Composable
fun UpdatePanel(state: UpdateState, onLater: (() -> Unit)? = null) {
    val c = Xd.colors
    when (val r = state.result) {
        null -> if (state.checking) XdCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(20.dp), color = c.acc, strokeWidth = 2.dp)
                Text(stringResource(R.string.xd_up_checking), style = XdText.body, color = c.fg2)
            }
        }
        is UpdateResult.Latest -> XdCard(Modifier.fillMaxWidth()) {
            XdNote(stringResource(R.string.upd_latest, r.commitHash.take(9)), tone = NoteTone.OK)
            if (state.skipped) Text(stringResource(R.string.xd_up_not_offered), style = XdText.note, color = c.fg3)
        }
        is UpdateResult.Cooldown -> XdCard(Modifier.fillMaxWidth(), title = stringResource(R.string.upd_cooldown_title), icon = XdIcons.clock) {
            val seconds = r.remainingMillis / 1000
            Text(stringResource(R.string.xd_up_cooldown, seconds / 60, seconds % 60), style = XdText.body, color = c.fg2)
        }
        is UpdateResult.Available -> AvailableCard(r.release, state, onLater)
    }
    if (state.result !is UpdateResult.Available) state.failure?.let { XdNote(it, tone = NoteTone.ERROR) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AvailableCard(release: FeedRelease, state: UpdateState, onLater: (() -> Unit)?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = Xd.colors
    val asset = apkAsset(release)
    val verifiable = asset?.sha256 != null
    val openPage = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.pageUrl))) }
    XdCard(Modifier.fillMaxWidth(), title = stringResource(R.string.upd_available), icon = XdIcons.download) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(release.title ?: release.tagName, style = XdText.h2, color = c.fg, modifier = Modifier.align(Alignment.CenterVertically))
            if (release.prerelease) XdBadge(stringResource(R.string.upd_preview).lowercase(), tone = BadgeTone.WARN, modifier = Modifier.align(Alignment.CenterVertically))
            asset?.sha256?.let { XdBadge(it.take(7), modifier = Modifier.align(Alignment.CenterVertically)) }
            asset?.let { Text(Formatter.formatShortFileSize(context, it.size), style = XdText.note, color = c.fg3, modifier = Modifier.align(Alignment.CenterVertically)) }
        }
        val language = LocalConfiguration.current.locales[0].toLanguageTag()
        val notes = release.notes?.let { updateNotes(it, language) }.orEmpty()
        if (notes.isEmpty()) Text(stringResource(R.string.upd_no_changelog), style = XdText.note, color = c.fg3)
        else Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            notes.take(12).forEach { Text("• $it", style = XdText.bodySm, color = c.fg2) }
        }
        XdNote(stringResource(if (verifiable) R.string.upd_verifiable else R.string.upd_not_verifiable).replaceFirstChar { it.uppercase() },
            tone = if (verifiable) NoteTone.OK else NoteTone.WARN, icon = if (verifiable) XdIcons.shield else XdIcons.alert)
        val stage = state.stage
        if (stage != null) {
            Steps(stage)
            when (stage) {
                UpdateStage.DOWNLOAD -> {
                    LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth(), color = c.acc, trackColor = c.s3)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val total = asset?.size ?: 0L
                        Text(stringResource(R.string.xd_up_of, Formatter.formatShortFileSize(context, (total * state.progress).toLong()),
                            Formatter.formatShortFileSize(context, total)), style = XdText.note, color = c.fg3, modifier = Modifier.weight(1f))
                        XdButton(stringResource(R.string.xd_up_cancel), state::cancel, kind = XdButtonKind.GHOST, size = XdButtonSize.SM)
                    }
                }
                UpdateStage.VERIFY -> Unit
                UpdateStage.INSTALL -> if (state.needsPermission) {
                    XdNote(stringResource(R.string.upd_allow_install), tone = NoteTone.WARN)
                    XdButton(stringResource(R.string.xd_up_allow), {
                        context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        state.stage = null
                        state.needsPermission = false
                    }, kind = XdButtonKind.PRIMARY, size = XdButtonSize.SM)
                } else XdNote(stringResource(R.string.xd_up_verified), tone = NoteTone.OK, icon = XdIcons.shield)
            }
        } else {
            state.failure?.let { XdNote(it, tone = NoteTone.ERROR) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (verifiable) XdButton(stringResource(if (state.failure != null) R.string.xd_up_try_again else R.string.upd_download_install),
                    { state.install(context, scope, release) }, kind = XdButtonKind.PRIMARY, icon = XdIcons.download)
                XdButton(stringResource(R.string.upd_release_page), { openPage() }, kind = if (verifiable) XdButtonKind.GHOST else XdButtonKind.PRIMARY)
                XdButton(stringResource(R.string.upd_skip), { state.skip(context, release) }, kind = XdButtonKind.GHOST)
                if (onLater != null) XdButton(stringResource(R.string.upd_later), onLater, kind = XdButtonKind.GHOST)
            }
        }
    }
}

/** Download, check, install: done, under way, or waiting. */
@Composable
private fun Steps(stage: UpdateStage) {
    val c = Xd.colors
    Column(Modifier.padding(vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        UpdateStage.entries.forEach { s ->
            val (icon, tint) = when {
                s.ordinal < stage.ordinal -> XdIcons.checkCircle to c.ok
                s == stage -> XdIcons.refresh to c.acc
                else -> XdIcons.clock to c.fg3
            }
            StepRow(icon, tint, stringResource(when (s) {
                UpdateStage.DOWNLOAD -> R.string.xd_up_step_download
                UpdateStage.VERIFY -> R.string.xd_up_step_verify
                UpdateStage.INSTALL -> R.string.xd_up_step_install
            }), current = s == stage)
        }
    }
}

@Composable
private fun StepRow(icon: ImageVector, tint: androidx.compose.ui.graphics.Color, text: String, current: Boolean) {
    val c = Xd.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, null, Modifier.size(20.dp), tint = tint)
        Text(text, style = if (current) XdText.label else XdText.body, color = if (current) c.fg else c.fg2)
    }
}
