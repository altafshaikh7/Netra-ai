package com.example.netraai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.netraai.camera.CameraPreview
import com.example.netraai.data.PreferencesManager
import com.example.netraai.detection.DetectedObstacle
import com.example.netraai.detection.ObjectDetectorHelper
import com.example.netraai.detection.WalkingModeAnalyzer
import com.example.netraai.feedback.AlertEngine
import com.example.netraai.feedback.AlertStrings
import com.example.netraai.ui.theme.NetraAITheme
import com.example.netraai.voice.TtsManager
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var ttsManager: TtsManager
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var alertEngine: AlertEngine
    private lateinit var detectorHelper: ObjectDetectorHelper
    private lateinit var walkingModeAnalyzer: WalkingModeAnalyzer

    private var hasPermissions by mutableStateOf(false)
    private var isPermanentlyDenied by mutableStateOf(false)
    private var currentLanguage by mutableStateOf("en")
    private var isWalkingModeActive by mutableStateOf(false)
    private var latestObstacleText by mutableStateOf<String?>(null)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        checkPermissions()
        if (hasPermissions) {
            val msg = if (currentLanguage == "hi") "अनुमतियां प्राप्त हुईं।" else "Permissions granted."
            ttsManager.speak(msg)
        } else {
            val cameraDeniedPermanent = !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
            val audioDeniedPermanent = !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED

            if (cameraDeniedPermanent || audioDeniedPermanent) {
                isPermanentlyDenied = true
                val msg = if (currentLanguage == "hi") {
                    "अनुमतियां स्थायी रूप से अस्वीकृत हैं। कैमरा और माइक्रोफ़ोन सक्षम करने के लिए सेटिंग्स खोलें।"
                } else {
                    "Permissions permanently denied. Please tap Open Settings to enable camera and microphone."
                }
                ttsManager.speak(msg)
            } else {
                isPermanentlyDenied = false
                val msg = if (currentLanguage == "hi") {
                    "नेत्रा की सहायता के लिए कैमरा और माइक्रोफ़ोन अनुमतियां आवश्यक हैं।"
                } else {
                    "Camera and microphone permissions are required for Netra to assist you."
                }
                ttsManager.speak(msg)
            }
        }
    }

    /**
     * Camera and microphone are required. Location is optional and must not block the camera.
     */
    private fun checkPermissions() {
        val cameraGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        val audioGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        hasPermissions = cameraGranted && audioGranted
        if (hasPermissions) {
            isPermanentlyDenied = false
        }
    }

    private fun openAppSettings() {
        val msg = if (currentLanguage == "hi") "ऐप सेटिंग्स खोल रहे हैं।" else "Opening app settings."
        ttsManager.speak(msg)
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null)
        )
        startActivity(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        ttsManager = TtsManager(this)
        preferencesManager = PreferencesManager(this)
        alertEngine = AlertEngine(ttsManager)
        detectorHelper = ObjectDetectorHelper(this)

        walkingModeAnalyzer = WalkingModeAnalyzer(
            detectorHelper = detectorHelper,
            targetFps = WalkingModeAnalyzer.DEFAULT_TARGET_FPS
        ) { detectedObstacles ->
            if (isWalkingModeActive) {
                val spokenObstacle = alertEngine.processObstacles(detectedObstacles, currentLanguage)
                if (spokenObstacle != null) {
                    val alertSentence = AlertStrings.buildAlertSentence(
                        spokenObstacle.label,
                        spokenObstacle.direction,
                        spokenObstacle.distance,
                        currentLanguage
                    )
                    runOnUiThread {
                        latestObstacleText = alertSentence
                    }
                }
            }
        }

        checkPermissions()

        // Handle spoken greeting and initial automatic permission request
        lifecycleScope.launch {
            val isFirstLaunch = preferencesManager.isFirstLaunch()
            val greeting = if (isFirstLaunch) {
                preferencesManager.setFirstLaunchCompleted()
                if (currentLanguage == "hi") {
                    "नेत्रा तैयार है। ध्यान दें: यह ऐप सहायता करता है लेकिन सफेद छड़ी या गाइड का विकल्प नहीं है। सीढ़ियां, गड्ढे और खुले नाले अभी डिटेक्ट नहीं होते हैं।"
                } else {
                    "Netra ready. Note: this app assists but does not replace a white cane or guide dog. Stairs, potholes, and open drains are not detected yet."
                }
            } else {
                if (currentLanguage == "hi") "नेत्रा तैयार है।" else "Netra ready."
            }

            ttsManager.speak(greeting)

            // Automatically launch permission request after greeting if not granted
            if (!hasPermissions && !isPermanentlyDenied) {
                requestPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.CAMERA,
                        Manifest.permission.RECORD_AUDIO,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    )
                )
            }
        }

        setContent {
            NetraAITheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (hasPermissions) {
                            CameraPreview(
                                modifier = Modifier.fillMaxSize(),
                                analyzer = walkingModeAnalyzer,
                                onError = {
                                    val err = if (currentLanguage == "hi") "कैमरा अनुपलब्ध है।" else "Camera unavailable."
                                    ttsManager.speak(err, interrupt = true)
                                }
                            )
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .statusBarsPadding()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Top Bar: Title and Language Toggle
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Netra AI",
                                    color = Color.Yellow,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.semantics {
                                        contentDescription = "Netra AI application header"
                                    }
                                )

                                Button(
                                    onClick = {
                                        currentLanguage = if (currentLanguage == "en") "hi" else "en"
                                        ttsManager.setLanguage(currentLanguage)
                                        val msg = if (currentLanguage == "hi") "हिंदी भाषा चयनित" else "English language selected"
                                        ttsManager.speak(msg)
                                    },
                                    modifier = Modifier
                                        .height(72.dp)
                                        .width(140.dp)
                                        .semantics {
                                            contentDescription = if (currentLanguage == "en") {
                                                "Switch language to Hindi. Currently English."
                                            } else {
                                                "अंग्रेजी भाषा चुनें। वर्तमान में हिंदी।"
                                            }
                                        },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                                ) {
                                    Text(
                                        text = if (currentLanguage == "en") "हिंदी (HI)" else "English (EN)",
                                        color = Color.White,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // On-screen disclaimer card
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                                    .semantics {
                                        contentDescription = if (currentLanguage == "hi") {
                                            "सूचना: सीढ़ियां, गड्ढे और खुले नाले अभी डिटेक्ट नहीं होते। यह सफेद छड़ी का विकल्प नहीं है।"
                                        } else {
                                            "Disclaimer: Stairs, potholes, and open drains are not detected yet. Does not replace a white cane."
                                        }
                                    },
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF263238).copy(alpha = 0.9f)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = if (currentLanguage == "hi") {
                                        "⚠️ ध्यान दें: सीढ़ियां, गड्ढे और खुले नाले अभी डिटेक्ट नहीं होते। सफेद छड़ी का प्रयोग अवश्य करें।"
                                    } else {
                                        "⚠️ Note: Stairs, potholes, and open drains are not detected yet. Always use your white cane."
                                    },
                                    color = Color(0xFFFFD54F),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp)
                                )
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            // Live obstacle detection feedback box when active
                            if (hasPermissions && isWalkingModeActive) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.Black.copy(alpha = 0.8f), shape = RoundedCornerShape(12.dp))
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = latestObstacleText ?: if (currentLanguage == "hi") "रास्ता साफ है..." else "Scanning path...",
                                        color = Color.White,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.semantics {
                                            contentDescription = latestObstacleText ?: if (currentLanguage == "hi") "स्कैनिंग जारी है" else "Scanning path"
                                        }
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                            }

                            // Bottom Controls: Permission requests or Walking Mode Start/Stop button
                            if (!hasPermissions) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.DarkGray.copy(alpha = 0.95f), shape = RoundedCornerShape(12.dp))
                                        .padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = if (isPermanentlyDenied) {
                                            if (currentLanguage == "hi") {
                                                "कैमरा और माइक्रोफ़ोन अनुमतियां अस्वीकृत हैं। कृपया सेटिंग्स खोलें।"
                                            } else {
                                                "Camera and microphone permissions were denied. Please open settings to grant them."
                                            }
                                        } else {
                                            if (currentLanguage == "hi") {
                                                "लाइव डिटेक्शन और वॉयस फीडबैक के लिए कैमरा और माइक्रोफ़ोन आवश्यक हैं।"
                                            } else {
                                                "Camera and microphone permissions are required for live obstacle detection and speech."
                                            }
                                        },
                                        color = Color.White,
                                        fontSize = 18.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(bottom = 16.dp)
                                    )

                                    if (isPermanentlyDenied) {
                                        Button(
                                            onClick = { openAppSettings() },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(72.dp)
                                                .semantics {
                                                    contentDescription = "Open App Settings button"
                                                },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                                        ) {
                                            Text(
                                                text = if (currentLanguage == "hi") "सेटिंग्स खोलें" else "Open Settings",
                                                color = Color.White,
                                                fontSize = 22.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    } else {
                                        Button(
                                            onClick = {
                                                val msg = if (currentLanguage == "hi") "अनुमतियों का अनुरोध कर रहे हैं।" else "Requesting camera and audio permissions."
                                                ttsManager.speak(msg)
                                                requestPermissionLauncher.launch(
                                                    arrayOf(
                                                        Manifest.permission.CAMERA,
                                                        Manifest.permission.RECORD_AUDIO,
                                                        Manifest.permission.ACCESS_FINE_LOCATION
                                                    )
                                                )
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(72.dp)
                                                .semantics {
                                                    contentDescription = "Grant camera and audio permissions button"
                                                },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                                        ) {
                                            Text(
                                                text = if (currentLanguage == "hi") "अनुमति दें" else "Grant Permissions",
                                                color = Color.White,
                                                fontSize = 22.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            } else {
                                // Large Start/Stop Walking Mode Button (min 72dp, TalkBack accessible)
                                Button(
                                    onClick = {
                                        if (!isWalkingModeActive) {
                                            isWalkingModeActive = true
                                            walkingModeAnalyzer.isWalkingModeEnabled = true
                                            val msg = if (currentLanguage == "hi") "वॉकिंग मोड चालू" else "Walking mode on"
                                            ttsManager.speak(msg, interrupt = true)
                                        } else {
                                            isWalkingModeActive = false
                                            walkingModeAnalyzer.isWalkingModeEnabled = false
                                            alertEngine.reset()
                                            latestObstacleText = null
                                            val msg = if (currentLanguage == "hi") "वॉकिंग मोड बंद" else "Walking mode off"
                                            ttsManager.speak(msg, interrupt = true)
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(76.dp)
                                        .semantics {
                                            contentDescription = if (isWalkingModeActive) {
                                                if (currentLanguage == "hi") {
                                                    "वॉकिंग मोड बंद करें बटन। वॉकिंग मोड सक्रिय है।"
                                                } else {
                                                    "Stop Walking Mode button. Walking mode is currently active."
                                                }
                                            } else {
                                                if (currentLanguage == "hi") {
                                                    "वॉकिंग मोड शुरू करें बटन।"
                                                } else {
                                                    "Start Walking Mode button."
                                                }
                                            }
                                        },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isWalkingModeActive) Color(0xFFD50000) else Color(0xFF00C853)
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = if (isWalkingModeActive) {
                                            if (currentLanguage == "hi") "वॉकिंग मोड बंद करें" else "Stop Walking Mode"
                                        } else {
                                            if (currentLanguage == "hi") "वॉकिंग मोड शुरू करें" else "Start Walking Mode"
                                        },
                                        color = if (isWalkingModeActive) Color.White else Color.Black,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val previousState = hasPermissions
        checkPermissions()
        if (!previousState && hasPermissions) {
            val msg = if (currentLanguage == "hi") "अनुमतियां प्राप्त हुईं।" else "Permissions granted."
            ttsManager.speak(msg)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isWalkingModeActive = false
        walkingModeAnalyzer.isWalkingModeEnabled = false
        detectorHelper.close()
        ttsManager.shutdown()
    }
}
