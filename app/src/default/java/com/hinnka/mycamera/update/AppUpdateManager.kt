package com.hinnka.mycamera.update

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

data class AppUpdateRelease(
    val versionName: String?,
    val downloadUrl: String,
    val fileName: String
)

// This fork installs under its own app ID, so upstream's update server would offer an APK
// that installs as a separate app. Update checks stay off until the fork has its own feed.
object AppUpdateManager {
    val updatesEnabled = false

    val readyApk: StateFlow<File?> = MutableStateFlow(null)

    suspend fun checkForUpdate(currentVersion: String = ""): AppUpdateRelease? = null

    fun startSilentUpdate(context: Context) = Unit

    suspend fun downloadApk(context: Context, release: AppUpdateRelease): File {
        throw UnsupportedOperationException("App updates are disabled in this build.")
    }

    fun consumeReadyApk(apkFile: File?) = Unit

    fun startInstall(context: Context, apkFile: File): Boolean = false
}
