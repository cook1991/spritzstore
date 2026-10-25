package net.spritzlive.spritzstore.model

import java.io.File

/**
 * Runtime status for one tracked repo, held in memory only -- never persisted. There's
 * no separate "downloading" step before [UpdateAvailable]: finding out whether an
 * update exists *requires* downloading the APK (to read its real `versionCode` --
 * see DESIGN.md), so by the time this state exists the file is already sitting in
 * [UpdateAvailable.apkFile], ready to hand to the installer.
 */
sealed interface AppUpdateState {
    data object Checking : AppUpdateState
    data class UpToDate(val installedVersionName: String, val installedVersionCode: Long) : AppUpdateState

    data class UpdateAvailable(
        val installedVersionName: String?,
        val installedVersionCode: Long,
        val latestVersionName: String,
        val latestVersionCode: Long,
        val apkFile: File,
    ) : AppUpdateState {
        val isFirstInstall: Boolean get() = installedVersionCode < 0
    }

    data object Installing : AppUpdateState
    data class Error(val message: String) : AppUpdateState
}

data class AppRow(
    val repo: TrackedRepo,
    val state: AppUpdateState,
)
