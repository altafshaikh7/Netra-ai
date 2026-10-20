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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.netraai.camera.CameraPreview
import com.example.netraai.ui.theme.NetraAITheme
import com.example.netraai.voice.TtsManager

class MainActivity : ComponentActivity() {
    private lateinit var ttsManager: TtsManager
    private var hasPermissions by mutableStateOf(false)
    private var isPermanentlyDenied by mutableStateOf(false)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        checkPermissions()
        if (hasPermissions) {
            ttsManager.speak("Permissions granted.")
        } else {
            val cameraDeniedPermanent = !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
            val audioDeniedPermanent = !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED

            if (cameraDeniedPermanent || audioDeniedPermanent) {
                isPermanentlyDenied = true
                ttsManager.speak("Permissions permanently denied. Please tap Open Settings to enable camera and microphone.")
            } else {
                isPermanentlyDenied = false
                ttsManager.speak("Camera and microphone permissions are required for Netra to assist you.")
            }
        }
    }

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
        ttsManager.speak("Opening app settings.")
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

        checkPermissions()

        // Spoken greeting and limitation note
        ttsManager.speak("Netra ready. Note: this app assists but does not replace a white cane or guide.")

        setContent {
            NetraAITheme {
                var currentLanguage by remember { mutableStateOf("en") }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (hasPermissions) {
                            CameraPreview(modifier = Modifier.fillMaxSize())
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Top Bar: Language toggle & Status
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .statusBarsPadding(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Netra Active",
                                    color = Color.Yellow,
                                    fontSize = 24.sp,
                                    modifier = Modifier.semantics {
                                        contentDescription = "Netra is active and running camera preview"
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
                                            contentDescription = "Switch language between English and Hindi. Current is $currentLanguage"
                                        },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Blue)
                                ) {
                                    Text(
                                        text = if (currentLanguage == "en") "हिंदी (HI)" else "English (EN)",
                                        color = Color.White,
                                        fontSize = 18.sp
                                    )
                                }
                            }

                            // Bottom Controls / Permission request
                            if (!hasPermissions) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.DarkGray.copy(alpha = 0.9f))
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = if (isPermanentlyDenied) {
                                            "Camera and microphone permissions were denied. Please open settings to grant them."
                                        } else {
                                            "Camera and microphone permissions are required for live detection and voice commands."
                                        },
                                        color = Color.White,
                                        fontSize = 20.sp,
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
                                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                                        ) {
                                            Text(
                                                text = "Open Settings",
                                                color = Color.White,
                                                fontSize = 22.sp
                                            )
                                        }
                                    } else {
                                        Button(
                                            onClick = {
                                                ttsManager.speak("Requesting camera and audio permissions.")
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
                                            colors = ButtonDefaults.buttonColors(containerColor = Color.Green)
                                        ) {
                                            Text(
                                                text = "Grant Permissions",
                                                color = Color.Black,
                                                fontSize = 22.sp
                                            )
                                        }
                                    }
                                }
                            } else {
                                // Status indicator when active
                                Box(
                                    modifier = Modifier
                                        .padding(bottom = 32.dp)
                                        .background(Color.Black.copy(alpha = 0.6f))
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Live camera preview active",
                                        color = Color.White,
                                        fontSize = 20.sp,
                                        modifier = Modifier.semantics {
                                            contentDescription = "Live camera preview is active"
                                        }
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
            ttsManager.speak("Permissions granted.")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ttsManager.shutdown()
    }
}
