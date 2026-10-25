package com.example.netraai.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.netraai.detection.DetectionProfile
import com.example.netraai.detection.DetectionSensitivity
import com.example.netraai.feedback.FlashlightMode
import com.example.netraai.ui.theme.DarkCard
import com.example.netraai.ui.theme.DarkSurface
import com.example.netraai.ui.theme.ElectricCyan
import com.example.netraai.ui.theme.TextPrimary
import com.example.netraai.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    language: String,
    speechSpeed: Float,
    flashlightMode: FlashlightMode,
    isDebugOverlayEnabled: Boolean,
    detectionSensitivity: DetectionSensitivity,
    distanceCalibration: Float,
    detectionProfile: DetectionProfile = DetectionProfile.BALANCED,
    isAllObjectsMode: Boolean = false,
    emergencyContactName: String = "",
    emergencyContactNumber: String = "",
    onLanguageChange: (String) -> Unit,
    onSpeechSpeedChange: (Float) -> Unit,
    onFlashlightModeChange: (FlashlightMode) -> Unit,
    onDebugOverlayChange: (Boolean) -> Unit,
    onDetectionSensitivityChange: (DetectionSensitivity) -> Unit,
    onDistanceCalibrationChange: (Float) -> Unit,
    onDetectionProfileChange: (DetectionProfile) -> Unit = {},
    onAllObjectsModeChange: (Boolean) -> Unit = {},
    onSaveEmergencyContact: (String, String) -> Unit = { _, _ -> },
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isHindi = language.equals("hi", ignoreCase = true)
    val context = LocalContext.current

    var contactName by remember { mutableStateOf(emergencyContactName) }
    var contactNumber by remember { mutableStateOf(emergencyContactNumber) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DarkSurface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isHindi) "सेटिंग्स" else "Settings",
                    color = ElectricCyan,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics {
                        contentDescription = if (isHindi) "सेटिंग्स पैनल" else "Settings panel"
                    }
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .height(48.dp)
                        .width(48.dp)
                        .semantics {
                            contentDescription = if (isHindi) "सेटिंग्स बंद करें" else "Close settings"
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = TextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 1. Language Toggle
            Text(
                text = if (isHindi) "भाषा (Language)" else "Language",
                color = TextSecondary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { onLanguageChange("en") },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (language == "en") ElectricCyan else DarkCard
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .semantics {
                            contentDescription = "Select English language"
                        }
                ) {
                    Text(
                        text = "English",
                        color = if (language == "en") Color.Black else TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = { onLanguageChange("hi") },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (language == "hi") ElectricCyan else DarkCard
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .semantics {
                            contentDescription = "Select Hindi language"
                        }
                ) {
                    Text(
                        text = "हिंदी",
                        color = if (language == "hi") Color.Black else TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 2. Detection Profile Selection (STRICT, BALANCED, SENSITIVE)
            Text(
                text = if (isHindi) "डिटेक्शन प्रोफाइल (Detection Profile)" else "Detection Profile",
                color = TextSecondary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DetectionProfile.entries.forEach { profile ->
                    val isSelected = detectionProfile == profile
                    val label = when (profile) {
                        DetectionProfile.STRICT -> if (isHindi) "सख्त" else "Strict"
                        DetectionProfile.BALANCED -> if (isHindi) "संतुलित" else "Balanced"
                        DetectionProfile.SENSITIVE -> if (isHindi) "संवेदनशील" else "Sensitive"
                    }
                    Button(
                        onClick = { onDetectionProfileChange(profile) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) ElectricCyan else DarkCard
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .semantics {
                                contentDescription = "Detection profile $label"
                            }
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) Color.Black else TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 3. Sensitivity Offset (Low / Normal / High)
            Text(
                text = if (isHindi) "संवेदनशीलता ऑफसेट (Sensitivity Offset)" else "Sensitivity Offset",
                color = TextSecondary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DetectionSensitivity.entries.forEach { sens ->
                    val isSelected = detectionSensitivity == sens
                    val label = when (sens) {
                        DetectionSensitivity.LOW -> if (isHindi) "कम" else "Low"
                        DetectionSensitivity.NORMAL -> if (isHindi) "सामान्य" else "Normal"
                        DetectionSensitivity.HIGH -> if (isHindi) "उच्च" else "High"
                    }
                    Button(
                        onClick = { onDetectionSensitivityChange(sens) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) ElectricCyan else DarkCard
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .semantics {
                                contentDescription = "Detection sensitivity $label"
                            }
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) Color.Black else TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 4. "All Objects" Mode Switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkCard, shape = RoundedCornerShape(12.dp))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isHindi) "सभी वस्तुएं मोड (All Objects Mode)" else "All Objects Mode",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isHindi) "व्हाइटलिस्ट हटाएं और किसी भी वस्तु की रिपोर्ट करें" else "Remove whitelist to detect any object",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }

                Switch(
                    checked = isAllObjectsMode,
                    onCheckedChange = onAllObjectsModeChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = ElectricCyan,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = DarkSurface
                    ),
                    modifier = Modifier.semantics {
                        contentDescription = "Toggle all objects mode"
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 5. Emergency Contact Fields & Explicit Save Button (min 56dp)
            Text(
                text = if (isHindi) "आपातकालीन संपर्क (Emergency Contact)" else "Emergency Contact",
                color = TextSecondary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = contactName,
                onValueChange = { contactName = it },
                label = { Text(if (isHindi) "नाम" else "Contact Name") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ElectricCyan,
                    unfocusedBorderColor = DarkCard,
                    focusedLabelColor = ElectricCyan,
                    unfocusedLabelColor = TextSecondary,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = "Emergency contact name input"
                    }
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = contactNumber,
                onValueChange = { contactNumber = it },
                label = { Text(if (isHindi) "फ़ोन नंबर" else "Phone Number") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ElectricCyan,
                    unfocusedBorderColor = DarkCard,
                    focusedLabelColor = ElectricCyan,
                    unfocusedLabelColor = TextSecondary,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = "Emergency contact phone number input"
                    }
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {
                    val cleaned = contactNumber.trim()
                    val isValid = cleaned.matches(Regex("^\\+?[0-9]{10,15}$"))
                    if (isValid) {
                        onSaveEmergencyContact(contactName, contactNumber)
                        val msg = if (isHindi) "संपर्क सहेजा गया" else "Contact saved"
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    } else {
                        val errMsg = if (isHindi) "अमान्य फोन नंबर (10-15 अंक आवश्यक)" else "Invalid phone number (10-15 digits required)"
                        Toast.makeText(context, errMsg, Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .semantics {
                        contentDescription = if (isHindi) "आपातकालीन संपर्क सहेजें" else "Save emergency contact"
                    }
            ) {
                Text(
                    text = if (isHindi) "संपर्क सहेजें" else "Save Emergency Contact",
                    color = Color.Black,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 6. Speech Speed Slider
            Text(
                text = if (isHindi) "वाक् गति (Speech Speed: ${String.format("%.1fx", speechSpeed)})" else "Speech Speed (${String.format("%.1fx", speechSpeed)})",
                color = TextSecondary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Slider(
                value = speechSpeed,
                onValueChange = onSpeechSpeedChange,
                valueRange = 0.5f..2.0f,
                steps = 15,
                colors = SliderDefaults.colors(
                    thumbColor = ElectricCyan,
                    activeTrackColor = ElectricCyan,
                    inactiveTrackColor = DarkCard
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = "Speech speed slider, currently ${String.format("%.1fx", speechSpeed)}"
                    }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 7. Flashlight Mode (Auto / On / Off)
            Text(
                text = if (isHindi) "टॉर्च मोड (Flashlight Mode)" else "Flashlight Mode",
                color = TextSecondary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FlashlightMode.entries.forEach { mode ->
                    val isSelected = flashlightMode == mode
                    val label = when (mode) {
                        FlashlightMode.AUTO -> if (isHindi) "ऑटो" else "Auto"
                        FlashlightMode.ON -> if (isHindi) "चालू" else "On"
                        FlashlightMode.OFF -> if (isHindi) "बंद" else "Off"
                    }
                    Button(
                        onClick = { onFlashlightModeChange(mode) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) ElectricCyan else DarkCard
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .semantics {
                                contentDescription = "Flashlight mode $label"
                            }
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) Color.Black else TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 8. Distance Calibration Multiplier Slider
            Text(
                text = if (isHindi) "दूरी अंशांकन (Distance Calibration: ${String.format("%.2fx", distanceCalibration)})" else "Distance Calibration (${String.format("%.2fx", distanceCalibration)})",
                color = TextSecondary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Slider(
                value = distanceCalibration,
                onValueChange = onDistanceCalibrationChange,
                valueRange = 0.5f..2.0f,
                steps = 14,
                colors = SliderDefaults.colors(
                    thumbColor = ElectricCyan,
                    activeTrackColor = ElectricCyan,
                    inactiveTrackColor = DarkCard
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = "Distance calibration multiplier slider, currently ${String.format("%.2fx", distanceCalibration)}"
                    }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 9. Debug Overlay Switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkCard, shape = RoundedCornerShape(12.dp))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isHindi) "डीबग ओवरले (Debug Overlay)" else "Debug Overlay",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isHindi) "बाउंडिंग बॉक्स, FPS और लेटेंसी दिखाएं" else "Show bounding boxes, FPS, and latency",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }

                Switch(
                    checked = isDebugOverlayEnabled,
                    onCheckedChange = onDebugOverlayChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = ElectricCyan,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = DarkSurface
                    ),
                    modifier = Modifier.semantics {
                        contentDescription = if (isHindi) "डीबग ओवरले टॉगल करें" else "Toggle debug overlay"
                    }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
