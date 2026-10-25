package com.example.netraai.sos

object SosMessageBuilder {
    fun buildMessage(latitude: Double?, longitude: Double?, isLastKnown: Boolean = false): String {
        return if (latitude != null && longitude != null) {
            val locType = if (isLastKnown) " (last known)" else ""
            "EMERGENCY from Netra user. Location$locType: https://maps.google.com/?q=$latitude,$longitude"
        } else {
            "EMERGENCY from Netra user. Location unavailable."
        }
    }
}
