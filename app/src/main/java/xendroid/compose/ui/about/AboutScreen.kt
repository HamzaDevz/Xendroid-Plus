package xendroid.compose.ui.about

import android.content.Intent
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.produceState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import xendroid.compose.BuildConfig
import xendroid.compose.R
import xendroid.compose.ui.design.LocalXdToast
import xendroid.compose.ui.design.Xd
import xendroid.compose.ui.design.XdButton
import xendroid.compose.ui.design.XdButtonKind
import xendroid.compose.ui.design.XdButtonSize
import xendroid.compose.ui.design.XdCard
import xendroid.compose.ui.design.XdIcons
import xendroid.compose.ui.design.XdKv
import xendroid.compose.ui.design.XdListRow
import xendroid.compose.ui.design.XdLogo
import xendroid.compose.ui.design.XdSheet
import xendroid.compose.ui.design.XdSingleScreen
import xendroid.compose.ui.design.XdText
import xendroid.compose.ui.design.XdTwoColumns

/**
 * Lote 6: the version and build; this phone as a table to copy whole into a problem report;
 * credits and the open-source licenses; and the way to app updates, Diagnostics and the setup
 * assistant.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onUpdates: () -> Unit = {},
    onDiagnostics: () -> Unit = {},
    onSetup: () -> Unit = {},
) {
    val context = LocalContext.current
    val toast = LocalXdToast.current
    val c = Xd.colors
    var showLicenses by rememberSaveable { mutableStateOf(false) }
    var showReport by rememberSaveable { mutableStateOf(false) }
    // Off the main thread: the core's report creates a Vulkan instance to ask.
    val device by produceState<List<Pair<Int, String>>?>(null) { value = withContext(Dispatchers.IO) { DeviceInfo.rows(context) } }
    val report by produceState<CoreReport?>(null, device) { value = withContext(Dispatchers.IO) { DeviceInfo.coreReport() } }
    XdSingleScreen(title = stringResource(R.string.lib_menu_about), subtitle = "${stringResource(R.string.app_name)} · ${versionLine()}", onBack = onBack,
        headIcon = XdIcons.info) {
        BoxWithConstraints {
            val wide = maxWidth > 640.dp
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                XdCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        XdLogo(56.dp)
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.app_name), style = XdText.h1, color = c.fg)
                            Text(stringResource(R.string.ab_creator), style = XdText.bodySm, color = c.fg2)
                            Text(stringResource(R.string.ab_version, versionLine()), style = XdText.small, color = c.fg3)
                            Text(stringResource(R.string.xd_fr_tagline), style = XdText.bodySm, color = c.fg2)
                        }
                    }
                }
                XdTwoColumns(wide, left = {
                    XdCard(Modifier.fillMaxWidth(), title = stringResource(R.string.ab_device), icon = XdIcons.phone) {
                        XdKv(device.orEmpty().map { (label, value) -> stringResource(label) to value })
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            XdButton(stringResource(R.string.xd_ab_copy_all), {
                                DeviceInfo.copy(context)
                                toast.show(context.getString(R.string.xd_ab_copied))
                            }, size = XdButtonSize.SM, icon = XdIcons.copy, enabled = device != null)
                            if (report != null) XdButton(stringResource(R.string.xd_ab_full_report), { showReport = true },
                                kind = XdButtonKind.GHOST, size = XdButtonSize.SM, icon = XdIcons.chip)
                        }
                    }
                }, right = {
                    // Whole rows open their screen; a controller's A on the row does it.
                    XdCard(Modifier.fillMaxWidth(), title = stringResource(R.string.xd_ab_shortcuts_head), icon = XdIcons.spark) {
                        Column {
                            ShortcutRow(stringResource(R.string.lib_menu_updates), XdIcons.download, onUpdates)
                            ShortcutRow(stringResource(R.string.lib_menu_diagnostics), XdIcons.bug, onDiagnostics)
                            ShortcutRow(stringResource(R.string.lib_menu_setup), XdIcons.spark, onSetup, divider = false)
                        }
                    }
                    XdCard(Modifier.fillMaxWidth(), title = stringResource(R.string.xd_ab_credits_head), icon = XdIcons.info) {
                        Text(stringResource(R.string.xd_ab_credits_line), style = XdText.bodySm, color = c.fg2)
                        XdButton(stringResource(R.string.ab_licenses_open), { showLicenses = true }, kind = XdButtonKind.GHOST, size = XdButtonSize.SM)
                    }
                })
            }
        }
    }

    report?.takeIf { showReport }?.let { r ->
        XdSheet(onDismiss = { showReport = false }, title = stringResource(R.string.xd_ab_full_report), wide = true,
            subtitle = stringResource(R.string.xd_ab_report_note), actions = {
                XdButton(stringResource(R.string.xd_copy), {
                    DeviceInfo.coreReportText()?.let { text ->
                        context.getSystemService(android.content.ClipboardManager::class.java)
                            ?.setPrimaryClip(android.content.ClipData.newPlainText(context.getString(R.string.app_name), text))
                        toast.show(context.getString(R.string.xd_ab_report_copied))
                    }
                }, icon = XdIcons.copy)
                XdButton(stringResource(R.string.common_ok), { showReport = false }, kind = XdButtonKind.PRIMARY)
            }) {
            XdKv(listOfNotNull(
                r.coresLine?.let { stringResource(R.string.xd_ab_k_cpu) to it + (r.isa?.let { isa -> " · $isa" } ?: "") },
                r.gpu?.let { stringResource(R.string.xd_ab_k_gpu) to it + (r.vulkan?.let { v -> " · Vulkan $v" } ?: "") },
            ) + r.notes.map { stringResource(R.string.xd_ab_k_core_note) to it })
            ReportList(stringResource(R.string.xd_ab_report_cpu, r.cpuFeatures.size), r.cpuFeatures)
            ReportList(stringResource(R.string.xd_ab_report_ext, r.extensions.size), r.extensions)
        }
    }

    if (showLicenses) {
        var licenseView by remember { mutableStateOf<WebView?>(null) }
        var inText by remember { mutableStateOf(false) }   // a license text, not the list
        XdSheet(onDismiss = { showLicenses = false }, title = stringResource(R.string.ab_licenses), wide = true, actions = {
            if (inText) XdButton(stringResource(R.string.common_back), { licenseView?.goBack() })
            XdButton(stringResource(R.string.common_ok), { showLicenses = false }, kind = XdButtonKind.PRIMARY)
        }) {
            BackHandler(enabled = inText) { licenseView?.goBack() }
            AndroidView(factory = { ctx ->
                WebView(ctx).apply {
                    webViewClient = LicensesClient { inText = it }
                    loadUrl("file:///android_asset/licenses.html")
                    licenseView = this
                }
            }, modifier = Modifier.fillMaxWidth().height(380.dp), onRelease = { it.destroy() })
        }
    }
}

/**
 * Opens the bundled license texts (engine-licenses/…) in the page and web links in the browser,
 * as the WebView does with no client, and says through [onText] whether a text is showing, so
 * Back returns to the list: with no client, closing the sheet was the only way out of a text.
 */
private class LicensesClient(private val onText: (Boolean) -> Unit) : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url
        if (url.scheme == "file") return !url.path.orEmpty().startsWith("/android_asset/")
        if (url.scheme == "http" || url.scheme == "https") runCatching {
            view.context.startActivity(Intent(Intent.ACTION_VIEW, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        return true
    }

    override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
        onText(view.canGoBack())
    }
}

/** The version as people quote it: "v412 · 5cb79f4d" from CI; a local build's own changes as a
 *  short digest ("5cb79f4d+local.ab12cd-debug"), never cut in the middle. */
private fun versionLine(): String {
    val name = BuildConfig.VERSION_NAME.replace(Regex("""(\+local\.[0-9a-f]{6})[0-9a-f]+"""), "$1")
    return (BuildConfig.VERSION_CODE.takeIf { it > 1 }?.let { "v$it · " } ?: "") + name
}

/** A list of the core's report (CPU features, Vulkan extensions) in the mono font, wrapping as words. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReportList(title: String, items: List<String>) {
    if (items.isEmpty()) return
    val c = Xd.colors
    Text(title, style = XdText.label, color = c.fg, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (item in items) Text(item, style = XdText.mono.copy(fontSize = 11.sp), color = c.fg2,
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(c.s1).padding(horizontal = 7.dp, vertical = 3.dp))
    }
}

@Composable
private fun ShortcutRow(title: String, icon: ImageVector, onClick: () -> Unit, divider: Boolean = true) {
    XdListRow(title, icon = icon, onClick = onClick, divider = divider) {
        Icon(XdIcons.chevR, null, Modifier.size(18.dp), tint = Xd.colors.fg3)
    }
}
