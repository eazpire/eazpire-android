package com.eazpire.creator.auth

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.eazpire.creator.MainActivity
import com.eazpire.creator.R
import com.eazpire.shared.EazpireApps
import com.eazpire.shared.auth.AppExchangeClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receives `eazpire-creator://auth/handoff?exchange_token=…` (IDEA-093 dual-app SSO).
 * Empty token = guest launch from Shop (no login). On redeem failure, opens [MainActivity]
 * with [MainActivity.EXTRA_OPEN_AUTH] so the user can sign in locally.
 */
class AppAuthHandoffActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val token = intent?.data?.getQueryParameter("exchange_token")?.trim().orEmpty()
        if (token.isEmpty()) {
            finishToMain(ok = true, guest = true)
            return
        }
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val result = AppExchangeClient().completeExchange(
                    exchangeToken = token,
                    targetPackage = EazpireApps.CREATOR,
                )
                val store = SecureTokenStore.get(this@AppAuthHandoffActivity)
                store.saveTokens(
                    jwt = result.jwt,
                    ownerId = result.ownerId,
                    accessToken = result.shopifyAccessToken,
                    shopifyAccessExpiresAtEpochMs = result.shopifyExpiresAt,
                    refreshToken = result.shopifyRefreshToken,
                    clearRefreshTokenIfNull = result.shopifyRefreshToken == null,
                )
                finishToMain(ok = true, guest = false)
            } catch (_: Exception) {
                finishToMain(ok = false, guest = false)
            }
        }
    }

    private fun finishToMain(ok: Boolean, guest: Boolean) {
        if (!guest) {
            Toast.makeText(
                this,
                if (ok) R.string.creator_handoff_ok else R.string.creator_handoff_fail,
                Toast.LENGTH_SHORT,
            ).show()
        }
        val intent = Intent(this, MainActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP,
        )
        if (!ok) {
            intent.putExtra(MainActivity.EXTRA_OPEN_AUTH, true)
        }
        startActivity(intent)
        setResult(if (ok) Activity.RESULT_OK else Activity.RESULT_CANCELED)
        finish()
    }
}
