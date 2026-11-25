package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri

object DialerHelper {

    fun buildDialIntent(phoneNumber: String): Intent {
        val sanitized = phoneNumber.trim()
        return Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:${Uri.encode(sanitized)}")
        }
    }

    /**
     * Opens the Android native dialer with the phone number pre-filled.
     * Never places a call automatically without explicit user confirmation.
     */
    fun openDialer(context: Context, phoneNumber: String): Boolean {
        val intent = buildDialIntent(phoneNumber)
        return try {
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
