package com.eazpire.creator.wear.handoff

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.eazpire.creator.auth.SecureTokenStore

/**
 * Silent session export for Eazpire Wear Player phone app (Join Now handoff).
 */
class WearPlayerSessionHandoffActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tokenStore = SecureTokenStore.get(this)
        val jwt = tokenStore.getJwt()?.trim().orEmpty()
        val ownerId = tokenStore.getOwnerId()?.trim().orEmpty()
        if (jwt.isNotBlank() && ownerId.isNotBlank()) {
            setResult(
                Activity.RESULT_OK,
                Intent()
                    .putExtra(WearPlayerSessionHandoff.EXTRA_JWT, jwt)
                    .putExtra(WearPlayerSessionHandoff.EXTRA_OWNER_ID, ownerId),
            )
        } else {
            setResult(Activity.RESULT_CANCELED)
        }
        finish()
    }
}

object WearPlayerSessionHandoff {
    const val ACTION = "com.eazpire.creator.action.WEAR_PLAYER_SESSION"
    const val EXTRA_JWT = "jwt"
    const val EXTRA_OWNER_ID = "owner_id"
}
