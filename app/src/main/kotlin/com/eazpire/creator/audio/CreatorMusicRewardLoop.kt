package com.eazpire.creator.audio

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.billing.EazBalanceRefreshBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

private const val TAG = "CreatorMusicReward"
private const val REWARD_INTERVAL_MS = 10_000L

/** Credits 0.5 free EAZ every 10s while Creator audio is playing (until free cap). */
@Composable
fun CreatorMusicRewardLoop(
    audioStore: CreatorAudioStore,
    api: CreatorApi,
    ownerId: String,
) {
    val isPlaying by audioStore.isPlaying.collectAsState()

    LaunchedEffect(isPlaying, ownerId) {
        if (!isPlaying || ownerId.isBlank()) return@LaunchedEffect
        delay(REWARD_INTERVAL_MS)
        while (isActive && audioStore.isPlaying.value) {
            try {
                val res = withContext(Dispatchers.IO) { api.postCreatorMusicReward(ownerId) }
                if (res.optBoolean("ok", false) && !res.optBoolean("already_credited", false)) {
                    val amount = res.optDouble("amount_eaz", 0.5)
                    val balance = res.opt("balance_after")?.toString().orEmpty()
                    Log.i(
                        TAG,
                        "Free EAZ +$amount (music listen) balance=$balance ref=${res.optString("ref_id")}"
                    )
                    EazBalanceRefreshBus.requestRefresh()
                } else if (res.optString("error") == "free_eaz_cap_reached") {
                    Log.i(TAG, "Free EAZ cap reached — music reward paused")
                    break
                }
            } catch (e: Exception) {
                Log.w(TAG, "Music reward failed: ${e.message}")
            }
            delay(REWARD_INTERVAL_MS)
        }
    }
}
