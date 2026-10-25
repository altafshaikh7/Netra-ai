package com.example.netraai.sos

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.telephony.SmsManager
import java.util.concurrent.Executors

class SosManager(private val context: Context) {

    fun requestLocationAndSend(
        contactNumber: String,
        onResult: (Boolean) -> Unit
    ) {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        var isCompleted = false

        fun proceedWithLocation(location: Location?, isLastKnown: Boolean) {
            if (isCompleted) return
            isCompleted = true

            val lat = location?.latitude
            val lng = location?.longitude
            val message = SosMessageBuilder.buildMessage(lat, lng, isLastKnown)

            sendSmsWithReceiver(contactNumber, message, onResult)
        }

        val handler = Handler(Looper.getMainLooper())
        val timeoutRunnable = Runnable {
            if (!isCompleted) {
                // Timeout after 8s: fallback to last known location
                val lastLoc = getLastKnown(locationManager)
                proceedWithLocation(lastLoc, isLastKnown = true)
            }
        }
        handler.postDelayed(timeoutRunnable, 8000L)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && locationManager != null) {
                val executor = Executors.newSingleThreadExecutor()
                val cancellationSignal = CancellationSignal()
                locationManager.getCurrentLocation(
                    LocationManager.GPS_PROVIDER,
                    cancellationSignal,
                    executor
                ) { loc ->
                    handler.removeCallbacks(timeoutRunnable)
                    val finalLoc = loc ?: getLastKnown(locationManager)
                    proceedWithLocation(finalLoc, isLastKnown = (loc == null))
                }
            } else if (locationManager != null) {
                val listener = object : LocationListener {
                    override fun onLocationChanged(loc: Location) {
                        try { locationManager.removeUpdates(this) } catch (_: Exception) {}
                        handler.removeCallbacks(timeoutRunnable)
                        proceedWithLocation(loc, isLastKnown = false)
                    }
                }
                locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, listener, Looper.getMainLooper())
            } else {
                handler.removeCallbacks(timeoutRunnable)
                proceedWithLocation(null, isLastKnown = false)
            }
        } catch (_: SecurityException) {
            handler.removeCallbacks(timeoutRunnable)
            val lastLoc = getLastKnown(locationManager)
            proceedWithLocation(lastLoc, isLastKnown = true)
        } catch (_: Exception) {
            handler.removeCallbacks(timeoutRunnable)
            val lastLoc = getLastKnown(locationManager)
            proceedWithLocation(lastLoc, isLastKnown = true)
        }
    }

    private fun getLastKnown(locationManager: LocationManager?): Location? {
        return try {
            locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } catch (_: SecurityException) {
            null
        }
    }

    private fun sendSmsWithReceiver(
        contactNumber: String,
        message: String,
        onResult: (Boolean) -> Unit
    ) {
        val action = "com.example.netraai.SMS_SENT_${System.currentTimeMillis()}"
        val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }

        val parts = smsManager.divideMessage(message)
        val numParts = parts.size
        var successCount = 0
        var failureCount = 0
        var isFinished = false

        val handler = Handler(Looper.getMainLooper())

        var receiver: BroadcastReceiver? = null

        fun finish(success: Boolean) {
            if (isFinished) return
            isFinished = true
            try {
                receiver?.let { context.unregisterReceiver(it) }
            } catch (_: Exception) {}
            onResult(success)
        }

        val timeoutRunnable = Runnable {
            finish(false)
        }
        handler.postDelayed(timeoutRunnable, 20000L) // 20s timeout

        receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (resultCode == Activity.RESULT_OK) {
                    successCount++
                } else {
                    failureCount++
                }

                if (successCount + failureCount >= numParts) {
                    handler.removeCallbacks(timeoutRunnable)
                    finish(failureCount == 0 && successCount == numParts)
                }
            }
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Context.RECEIVER_NOT_EXPORTED
        } else 0

        context.registerReceiver(receiver, IntentFilter(action), flags)

        val sentIntents = ArrayList<PendingIntent>()
        for (i in 0 until numParts) {
            val intent = Intent(action)
            val pi = PendingIntent.getBroadcast(
                context, i, intent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
            sentIntents.add(pi)
        }

        try {
            if (numParts > 1) {
                smsManager.sendMultipartTextMessage(contactNumber, null, parts, sentIntents, null)
            } else {
                smsManager.sendTextMessage(contactNumber, null, message, sentIntents[0], null)
            }
        } catch (_: Exception) {
            handler.removeCallbacks(timeoutRunnable)
            finish(false)
        }
    }
}
