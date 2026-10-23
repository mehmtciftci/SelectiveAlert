package com.example.sessizarama

import android.telecom.Call
import android.telecom.CallScreeningService
import android.telephony.PhoneNumberUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        try {
            if (callDetails.callDirection == Call.Details.DIRECTION_INCOMING) {
                val prefs = getSharedPreferences("p", MODE_PRIVATE)
                val incoming = callDetails.handle?.schemeSpecificPart
                val saved = prefs.getString("num", "") ?: ""
                val match = !incoming.isNullOrBlank() && saved.isNotBlank() &&
                    PhoneNumberUtils.compare(this, incoming, saved)

                val time = SimpleDateFormat("dd.MM HH:mm:ss", Locale.getDefault()).format(Date())
                val result = if (match) "EŞLEŞTİ" else "eşleşmedi"
                prefs.edit()
                    .putString("last", "$time | ${incoming ?: "numara yok"} | $result")
                    .apply()

                if (match) {
                    Ringer.start(this, true)
                }
            }
        } catch (e: Exception) {
            // Hata olsa bile aramayı engelleme
        }
        // Aramayı her zaman olduğu gibi geçir (reddetme yok)
        respondToCall(callDetails, CallResponse.Builder().build())
    }
}
