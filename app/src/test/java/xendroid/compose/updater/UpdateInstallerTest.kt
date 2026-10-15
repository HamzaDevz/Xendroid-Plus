package xendroid.compose.updater

import android.content.pm.PackageManager
import org.junit.Assert.*
import org.junit.Test
import xendroid.compose.R

class UpdateInstallerTest {
    @Suppress("DEPRECATION")
    @Test fun theDownloadIsAskedForItsCertificatesOnEveryAndroid() {
        // GET_SIGNATURES is what Android 10 and 13 before QPR1 read certificates for;
        // GET_SIGNING_CERTIFICATES is what fills signingInfo.
        assertEquals(PackageManager.GET_SIGNATURES, UpdateInstaller.ARCHIVE_FLAGS and PackageManager.GET_SIGNATURES)
        assertEquals(PackageManager.GET_SIGNING_CERTIFICATES, UpdateInstaller.ARCHIVE_FLAGS and PackageManager.GET_SIGNING_CERTIFICATES)
    }

    @Test fun aSharedKeyPasses() {
        assertNull(UpdateInstaller.keyRefusal(setOf("a"), setOf("a")))
        // A rotated key: the update's history still holds this install's certificate.
        assertNull(UpdateInstaller.keyRefusal(setOf("old", "new"), setOf("old")))
    }

    @Test fun anotherKeyIsSaidSo() {
        assertEquals(R.string.upd_refuse_other_key, UpdateInstaller.keyRefusal(setOf("b"), setOf("a")))
    }

    @Test fun signaturesAndroidDidNotReportAreNotAnotherKey() {
        assertEquals(R.string.upd_refuse_unread_key, UpdateInstaller.keyRefusal(emptySet(), setOf("a")))
        assertEquals(R.string.upd_refuse_unread_key, UpdateInstaller.keyRefusal(setOf("a"), emptySet()))
    }
}
