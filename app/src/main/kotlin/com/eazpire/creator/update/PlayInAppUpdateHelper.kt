package com.eazpire.creator.update

import android.app.Activity
import android.content.IntentSender
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability

/**
 * Checks for a newer version on the Play Store when the activity resumes and starts the
 * [immediate in-app update](https://developer.android.com/guide/playcore/in-app-updates) flow.
 *
 * Only applies when the app was installed from Play; sideloaded/debug APKs typically get
 * [UpdateAvailability.UPDATE_NOT_AVAILABLE] or no eligible update.
 */
class PlayInAppUpdateHelper(
    private val activity: Activity,
    private val updateLauncher: ActivityResultLauncher<IntentSenderRequest>,
) {
    private val appUpdateManager = AppUpdateManagerFactory.create(activity)
    private var immediatePromptStartedThisSession = false

    fun onResume() {
        appUpdateManager.appUpdateInfo
            .addOnSuccessListener { info -> handleAppUpdateInfo(info) }
            .addOnFailureListener { /* Play services / not from Play — ignore */ }
    }

    private fun handleAppUpdateInfo(info: AppUpdateInfo) {
        when {
            info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS -> {
                startImmediateFlow(info)
            }
            !immediatePromptStartedThisSession &&
                info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) -> {
                immediatePromptStartedThisSession = true
                startImmediateFlow(info)
            }
        }
    }

    private fun startImmediateFlow(info: AppUpdateInfo) {
        val options = AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build()
        try {
            appUpdateManager.startUpdateFlowForResult(info, updateLauncher, options)
        } catch (_: IntentSender.SendIntentException) {
            immediatePromptStartedThisSession = false
        }
    }
}
