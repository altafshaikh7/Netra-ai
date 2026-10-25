package com.example.netraai.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.netraai.detection.DetectionProfile
import com.example.netraai.detection.DetectionSensitivity
import com.example.netraai.feedback.FlashlightMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "netra_preferences")

/**
 * Manages persistent app preferences using Jetpack DataStore.
 */
class PreferencesManager(private val context: Context) {

    companion object {
        val KEY_FIRST_LAUNCH = booleanPreferencesKey("is_first_launch")
        val KEY_DISCLAIMER_DISMISSED = booleanPreferencesKey("is_disclaimer_dismissed")
        val KEY_LANGUAGE = stringPreferencesKey("language")
        val KEY_SPEECH_SPEED = floatPreferencesKey("speech_speed")
        val KEY_FLASHLIGHT_MODE = stringPreferencesKey("flashlight_mode")
        val KEY_DEBUG_OVERLAY = booleanPreferencesKey("debug_overlay")
        val KEY_DETECTION_SENSITIVITY = stringPreferencesKey("detection_sensitivity")
        val KEY_DISTANCE_CALIBRATION = floatPreferencesKey("distance_calibration")
        val KEY_DEVICE_ID = stringPreferencesKey("device_id")
        val KEY_CLOUD_CONSENT = stringPreferencesKey("cloud_consent")
        val KEY_DETECTION_PROFILE = stringPreferencesKey("detection_profile")
        val KEY_ALL_OBJECTS_MODE = booleanPreferencesKey("all_objects_mode")
        val KEY_EMERGENCY_CONTACT_NAME = stringPreferencesKey("emergency_contact_name")
        val KEY_EMERGENCY_CONTACT_NUMBER = stringPreferencesKey("emergency_contact_number")
    }

    val isFirstLaunchFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_FIRST_LAUNCH] ?: true
    }

    val isDisclaimerDismissedFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_DISCLAIMER_DISMISSED] ?: false
    }

    val languageFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_LANGUAGE] ?: "en"
    }

    val speechSpeedFlow: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[KEY_SPEECH_SPEED] ?: 0.85f
    }

    val flashlightModeFlow: Flow<FlashlightMode> = context.dataStore.data.map { preferences ->
        FlashlightMode.fromString(preferences[KEY_FLASHLIGHT_MODE] ?: "AUTO")
    }

    val debugOverlayFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_DEBUG_OVERLAY] ?: false
    }

    val detectionSensitivityFlow: Flow<DetectionSensitivity> = context.dataStore.data.map { preferences ->
        DetectionSensitivity.fromString(preferences[KEY_DETECTION_SENSITIVITY] ?: "NORMAL")
    }

    val distanceCalibrationFlow: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[KEY_DISTANCE_CALIBRATION] ?: 1.0f
    }

    val cloudConsentFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_CLOUD_CONSENT] ?: "UNSET"
    }

    val detectionProfileFlow: Flow<DetectionProfile> = context.dataStore.data.map { preferences ->
        DetectionProfile.fromString(preferences[KEY_DETECTION_PROFILE] ?: "BALANCED")
    }

    val allObjectsModeFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_ALL_OBJECTS_MODE] ?: false
    }

    val emergencyContactNameFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_EMERGENCY_CONTACT_NAME] ?: ""
    }

    val emergencyContactNumberFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_EMERGENCY_CONTACT_NUMBER] ?: ""
    }

    suspend fun getOrCreateDeviceId(): String {
        val existing = context.dataStore.data.first()[KEY_DEVICE_ID]
        if (!existing.isNullOrEmpty()) return existing
        val newId = UUID.randomUUID().toString()
        context.dataStore.edit { it[KEY_DEVICE_ID] = newId }
        return newId
    }

    suspend fun isFirstLaunch(): Boolean = isFirstLaunchFlow.first()

    suspend fun setFirstLaunchCompleted() {
        context.dataStore.edit { it[KEY_FIRST_LAUNCH] = false }
    }

    suspend fun setDisclaimerDismissed(dismissed: Boolean) {
        context.dataStore.edit { it[KEY_DISCLAIMER_DISMISSED] = dismissed }
    }

    suspend fun setLanguage(language: String) {
        context.dataStore.edit { it[KEY_LANGUAGE] = language }
    }

    suspend fun setSpeechSpeed(speed: Float) {
        context.dataStore.edit { it[KEY_SPEECH_SPEED] = speed }
    }

    suspend fun setFlashlightMode(mode: FlashlightMode) {
        context.dataStore.edit { it[KEY_FLASHLIGHT_MODE] = mode.name }
    }

    suspend fun setDebugOverlay(enabled: Boolean) {
        context.dataStore.edit { it[KEY_DEBUG_OVERLAY] = enabled }
    }

    suspend fun setDetectionSensitivity(sensitivity: DetectionSensitivity) {
        context.dataStore.edit { it[KEY_DETECTION_SENSITIVITY] = sensitivity.name }
    }

    suspend fun setDistanceCalibration(multiplier: Float) {
        context.dataStore.edit { it[KEY_DISTANCE_CALIBRATION] = multiplier.coerceIn(0.5f, 2.0f) }
    }

    suspend fun setCloudConsent(consent: String) {
        context.dataStore.edit { it[KEY_CLOUD_CONSENT] = consent }
    }

    suspend fun setDetectionProfile(profile: DetectionProfile) {
        context.dataStore.edit { it[KEY_DETECTION_PROFILE] = profile.name }
    }

    suspend fun setAllObjectsMode(enabled: Boolean) {
        context.dataStore.edit { it[KEY_ALL_OBJECTS_MODE] = enabled }
    }

    suspend fun setEmergencyContact(name: String, number: String) {
        context.dataStore.edit {
            it[KEY_EMERGENCY_CONTACT_NAME] = name
            it[KEY_EMERGENCY_CONTACT_NUMBER] = number
        }
    }
}
