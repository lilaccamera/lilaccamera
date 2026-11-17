package io.github.lilaccamera.sonybridge

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import java.security.MessageDigest

/** Which Lilac the bridge serves: the package name and the certificates it is signed with. */
internal object LilacTrust {
    const val STABLE = "io.github.lilaccamera"
    const val BETA = "$STABLE.beta"

    /**
     * SHA-256 (upper-case hex) of the certificates whose Lilac a release bridge serves. Today the key that signs the
     * project's GitHub releases; the beta's certificate goes here once that key exists. The key this bridge itself is
     * signed with does not matter, so a ROM may sign the bridge with any key, AOSP's test keys included.
     */
    val PINNED: Set<String> = setOf(
        "CA67BBFA7DB7174346664DB24FAA924509A9523E95D0B19B8E193B8538108CAD", // release
    )

    /**
     * True when [packageName] is Lilac (`io.github.lilaccamera` or a package under it) and one of [callerCertDigests]
     * is pinned, or, only with [trustOwnKey] (a debug bridge serving its debug Lilac), is one of [ownCertDigests].
     * [callerCertDigests] holds every certificate in the caller's signing history, so a rotated key keeps working.
     */
    fun isTrustedCaller(
        packageName: String,
        callerCertDigests: Collection<String>,
        ownCertDigests: Collection<String>,
        pinnedDigests: Collection<String>,
        trustOwnKey: Boolean,
    ): Boolean {
        if (packageName != STABLE && !packageName.startsWith("$STABLE.")) return false
        return callerCertDigests.any { it in pinnedDigests || (trustOwnKey && it in ownCertDigests) }
    }

    /** The package's signing certificates as SHA-256 hex; null when it is not installed. */
    fun certDigests(pm: PackageManager, packageName: String): Set<String>? {
        val info = try {
            pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo
        } catch (_: PackageManager.NameNotFoundException) {
            return null
        } ?: return emptySet()
        val certs = if (info.hasMultipleSigners()) info.apkContentsSigners else info.signingCertificateHistory
        return certs.orEmpty().mapTo(HashSet()) { sig ->
            MessageDigest.getInstance("SHA-256").digest(sig.toByteArray()).joinToString("") { "%02X".format(it) }
        }
    }

    fun isTrusted(context: Context, packageName: String): Boolean {
        val pm = context.packageManager
        val caller = certDigests(pm, packageName) ?: return false
        val own = certDigests(pm, context.packageName).orEmpty()
        val debugBridge = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        return isTrustedCaller(packageName, caller, own, PINNED, trustOwnKey = debugBridge)
    }
}
