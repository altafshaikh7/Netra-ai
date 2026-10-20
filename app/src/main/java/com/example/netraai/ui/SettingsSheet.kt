package com.example.netraai.ui

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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    onLanguageChange: (String) -> Unit,
    onSpeechSpeedChange: (Float) -> Unit,
    onFlashlightModeChange: (FlashlightMode) -> Unit,
    onDebugOverlayChange: (Boolean) -> Unit,
    onDetectionSensitivityChange: (DetectionSensitivity) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isHindi = language.equals("hi", ignoreCase = true)

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
                            contentDescription = "हिंदी भाषा चुनें"
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

            // 2. Speech Rate Slider
            Text(
                text = if (isHindi) "बोलने की गति: ${String.format("%.1fx", speechSpeed)}" else "Speech Speed: ${String.format("%.1fx", speechSpeed)}",
                color = TextSecondary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics {
                    contentDescription = if (isHindi) "बोलने की गति ${String.format("%.1fx", speechSpeed)}" else "Speech speed ${String.format("%.1fx", speechSpeed)}"
                }
            )
            Slider(
                value = speechSpeed,
                onValueChange = onSpeechSpeedChange,
                valueRange = 0.75f..1.5f,
                steps = 2,
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

            // 3. Flashlight Mode (Auto / On / Off)
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

            // 4. Detection Sensitivity (Low / Normal / High)
            Text(
                text = if (isHindi) "डिटेक्शन संवेदनशीलता (Sensitivity)" else "Detection Sensitivity",
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

            // 5. Debug Overlay Switch
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
