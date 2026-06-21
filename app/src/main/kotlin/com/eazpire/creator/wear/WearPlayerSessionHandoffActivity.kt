package com.eazpire.creator.wear

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.eazpire.creator.auth.SecureTokenStore

/** Silent handoff: returns Creator JWT session to Wear Player phone app via activity result. */
class WearPlayerSessionHandoffActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val tokenStore = SecureTokenStore.get(this)
        if (tokenStore.isLoggedIn()) {
            val jwt = tokenStore.getJwt().orEmpty()
            val ownerId = tokenStore.getOwnerId().orEmpty()
            setResult(
                RESULT_OK,
                Intent().apply {
                    putExtra(WearPlayerSessionHandoff.EXTRA_JWT, jwt)
                    putExtra(WearPlayerSessionHandoff.EXTRA_OWNER_ID, ownerId)
                },
            )
        } else {
            setResult(RESULT_CANCELED)
        }
        finish()
    }
}
