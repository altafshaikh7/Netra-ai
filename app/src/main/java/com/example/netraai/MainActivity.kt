package com.example.netraai

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
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

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        if (cameraGranted && audioGranted) {
            ttsManager.speak("Camera and audio permissions granted.")
        } else {
            ttsManager.speak("Permissions are required for Netra to assist you.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ttsManager = TtsManager(this)

        // First launch spoken greeting and limitation note
        ttsManager.speak("Netra ready. Note: this app assists but does not replace a white cane or guide.")

        setContent {
            NetraAITheme {
                var currentLanguage by remember { mutableStateOf("en") }
                var hasPermissions by remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED &&
                                ContextCompat.checkSelfPermission(
                                    this,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                    )
                }

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
                                        text = "Camera and microphone permissions are required for live detection and voice commands.",
                                        color = Color.White,
                                        fontSize = 20.sp,
                                        modifier = Modifier.padding(bottom = 16.dp)
                                    )

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
                            } else {
                                // Status or quick action indicator
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

    override fun onDestroy() {
        super.onDestroy()
        ttsManager.shutdown()
    }
}
