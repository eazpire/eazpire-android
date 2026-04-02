package com.eazpire.creator.update

import android.app.Activity
import android.content.IntentSender
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.eazpire.creator.BuildConfig
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability

/**
 * Checks for a newer version on the Play Store when the activity resumes and starts an
 * [in-app update](https://developer.android.com/guide/playcore/in-app-updates) flow.
 *
 * Tries **immediate** first; if Play does not allow it (common on metered networks, policy, etc.),
 * falls back to **flexible** so users still get an update prompt.
 *
 * **Play Console:** If the newer version is not in an **active** rollout for the user’s track,
 * the API returns [UpdateAvailability.UPDATE_NOT_AVAILABLE] — that cannot be fixed in-app.
 */
class PlayInAppUpdateHelper(
    private val activity: Activity,
    private val updateLauncher: ActivityResultLauncher<IntentSenderRequest>,
) {
    private val appUpdateManager = AppUpdateManagerFactory.create(activity)
    private var immediatePromptStartedThisSession = false
    private var flexiblePromptStartedThisSession = false
    private var installListener: InstallStateUpdatedListener? = null

    fun onResume() {
        appUpdateManager.appUpdateInfo
            .addOnSuccessListener { info -> handleAppUpdateInfo(info) }
            .addOnFailureListener { e ->
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "appUpdateInfo failed: ${e.message}")
                }
            }
    }

    fun onDestroy() {
        installListener?.let { appUpdateManager.unregisterListener(it) }
        installListener = null
    }

    private fun handleAppUpdateInfo(info: AppUpdateInfo) {
        if (BuildConfig.DEBUG) {
            logAvailability(info)
        }

        when {
            info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS -> {
                startImmediateFlow(info)
            }
            info.installStatus() == InstallStatus.DOWNLOADED -> {
                try {
                    appUpdateManager.completeUpdate()
                } catch (_: Exception) { /* ignore */ }
            }
            info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE -> {
                if (!immediatePromptStartedThisSession &&
                    info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
                ) {
                    immediatePromptStartedThisSession = true
                    startImmediateFlow(info)
                    return
                }
                if (!flexiblePromptStartedThisSession &&
                    info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
                ) {
                    flexiblePromptStartedThisSession = true
                    startFlexibleFlow(info)
                }
            }
        }
    }

    private fun logAvailability(info: AppUpdateInfo) {
        val imm = info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
        val flex = info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
        Log.d(
            TAG,
            "availability=${info.updateAvailability()} installStatus=${info.installStatus()} " +
                "immediate=$imm flexible=$flex stalenessDays=${info.clientVersionStalenessDays()}"
        )
    }

    private fun startImmediateFlow(info: AppUpdateInfo) {
        val options = AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build()
        try {
            appUpdateManager.startUpdateFlowForResult(info, updateLauncher, options)
        } catch (_: IntentSender.SendIntentException) {
            immediatePromptStartedThisSession = false
            tryFlexibleAfterImmediateFailure(info)
        }
    }

    private fun tryFlexibleAfterImmediateFailure(info: AppUpdateInfo) {
        if (flexiblePromptStartedThisSession) return
        if (!info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) return
        flexiblePromptStartedThisSession = true
        startFlexibleFlow(info)
    }

    private fun startFlexibleFlow(info: AppUpdateInfo) {
        val listener = InstallStateUpdatedListener { state ->
            if (state.installStatus() == InstallStatus.DOWNLOADED) {
                installListener?.let { appUpdateManager.unregisterListener(it) }
                installListener = null
                try {
                    appUpdateManager.completeUpdate()
                } catch (_: Exception) { /* ignore */ }
            }
        }
        installListener = listener
        appUpdateManager.registerListener(listener)
        val options = AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()
        try {
            appUpdateManager.startUpdateFlowForResult(info, updateLauncher, options)
        } catch (_: IntentSender.SendIntentException) {
            flexiblePromptStartedThisSession = false
            installListener?.let { appUpdateManager.unregisterListener(it) }
            installListener = null
        }
    }

    private companion object {
        const val TAG = "EazPlayUpdate"
    }
}
