package com.example.sessizarama

import android.telecom.Call
import android.telecom.CallScreeningService
import android.telephony.PhoneNumberUtils

class ScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        try {
            if (callDetails.callDirection == Call.Details.DIRECTION_INCOMING) {
                val incoming = callDetails.handle?.schemeSpecificPart
                val saved = getSharedPreferences("p", MODE_PRIVATE).getString("num", "") ?: ""
                if (!incoming.isNullOrBlank() && saved.isNotBlank() &&
                    PhoneNumberUtils.compare(this, incoming, saved)
                ) {
                    Ringer.start(this, true)
                }
            }
        } catch (e: Exception) {
            // Hata olsa bile aramayı engelleme
        }
        // Aramayı her zaman olduğu gibi geçir (reddetme/sessize alma yok)
        respondToCall(callDetails, CallResponse.Builder().build())
    }
}
