package com.example.netraai.sos

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.Build
import android.telephony.SmsManager

class SosManager(private val context: Context) {

    fun sendSos(
        contactNumber: String,
        onResult: (Boolean) -> Unit
    ) {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        var lat: Double? = null
        var lng: Double? = null
        var isLastKnown = false

        try {
            val loc = locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (loc != null) {
                lat = loc.latitude
                lng = loc.longitude
                isLastKnown = true
            }
        } catch (_: SecurityException) {}

        val message = SosMessageBuilder.buildMessage(lat, lng, isLastKnown)

        try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            val sentIntent = PendingIntent.getBroadcast(
                context, 0, Intent("com.example.netraai.SOS_SENT"),
                PendingIntent.FLAG_IMMUTABLE
            )

            val parts = smsManager.divideMessage(message)
            if (parts.size > 1) {
                val sentIntents = ArrayList<PendingIntent>().apply { add(sentIntent) }
                smsManager.sendMultipartTextMessage(contactNumber, null, parts, sentIntents, null)
            } else {
                smsManager.sendTextMessage(contactNumber, null, message, sentIntent, null)
            }
            onResult(true)
        } catch (e: Exception) {
            onResult(false)
        }
    }
}
