package xendroid.compose.updater

import xendroid.compose.R
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import java.io.File
import java.security.MessageDigest

/**
 * R03: the downloaded APK must be this app, newer than this install and signed by the
 * same key (Android would refuse anything else, this says why up front); then the
 * system installer takes over and asks the user to confirm. Nothing installs silently.
 */
object UpdateInstaller {
    private const val TAG = "UpdateInstaller"

    /**
     * What [verify] asks of the download. Android 10, and Android 13 before QPR1 (December
     * 2022), read an APK file's certificates only for GET_SIGNATURES, other versions for
     * either flag; without them signingInfo comes back null.
     */
    @Suppress("DEPRECATION")
    internal const val ARCHIVE_FLAGS = PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES

    /** Null when [apk] can be installed over this app, else the reason it cannot. */
    fun verify(context: Context, apk: File): String? {
        val pm = context.packageManager
        val archive = pm.getPackageArchiveInfo(apk.path, ARCHIVE_FLAGS)
            ?: return context.getString(R.string.upd_refuse_not_apk)
        if (archive.packageName != context.packageName) return context.getString(R.string.upd_refuse_other_app, archive.packageName)
        val installed = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        if (archive.longVersionCode <= installed.longVersionCode) return context.getString(R.string.upd_refuse_not_newer)
        return keyRefusal(signers(archive), signers(installed))?.let { context.getString(it) }
    }

    /**
     * The refusal for an update signed by [theirs] over an install signed by [ours] (SHA-256
     * of the certificates), or null when they share a key. Signatures Android did not report
     * are not another key: that would have the user uninstall for nothing.
     */
    internal fun keyRefusal(theirs: Set<String>, ours: Set<String>): Int? = when {
        theirs.isEmpty() || ours.isEmpty() -> R.string.upd_refuse_unread_key
        theirs.intersect(ours).isEmpty() -> R.string.upd_refuse_other_key
        else -> null
    }

    /** SHA-256 of the signing certificates, including the rotation history when there is one. */
    private fun signers(info: PackageInfo): Set<String> {
        val signing = info.signingInfo ?: return emptySet()
        val certificates = if (signing.hasMultipleSigners()) signing.apkContentsSigners else signing.signingCertificateHistory
        return certificates.orEmpty().map { cert ->
            MessageDigest.getInstance("SHA-256").digest(cert.toByteArray()).joinToString("") { "%02x".format(it) }
        }.toSet()
    }

    /**
     * Hands [apk] to the system installer. Without the user's "install unknown apps"
     * permission for this app, opens that setting instead (the user decides).
     */
    fun install(context: Context, apk: File) {
        val pm = context.packageManager
        if (!pm.canRequestPackageInstalls()) {
            Toast.makeText(context, context.getString(R.string.upd_allow_install),
                Toast.LENGTH_LONG).show()
            context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        val installer = pm.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
        }
        val id = installer.createSession(params)
        try {
            installer.openSession(id).use { session ->
                session.openWrite("base.apk", 0, apk.length()).use { out ->
                    apk.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
                val intent = Intent(context, UpdateInstallReceiver::class.java)
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                session.commit(PendingIntent.getBroadcast(context, id, intent, flags).intentSender)
            }
        } catch (e: Exception) {
            runCatching { installer.abandonSession(id) }
            throw e
        }
    }

    internal fun log(message: String) = Log.i(TAG, message)
}

/** Receives the installer's status: shows the confirmation screen, reports failures. */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                else intent.getParcelableExtra(Intent.EXTRA_INTENT)
                confirm?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            PackageInstaller.STATUS_SUCCESS -> UpdateInstaller.log("Update installed")
            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                UpdateInstaller.log("Update not installed: status $status ${message.orEmpty()}")
                Toast.makeText(context, context.getString(R.string.upd_not_installed) + message?.let { ": $it" }.orEmpty(), Toast.LENGTH_LONG).show()
            }
        }
    }
}
