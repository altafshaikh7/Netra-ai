package com.example.netraai

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
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
import com.example.netraai.detection.AlertPriority
import com.example.netraai.detection.DebugOverlay
import com.example.netraai.detection.DetectedObstacle
import com.example.netraai.detection.DetectionSensitivity
import com.example.netraai.detection.GateStatus
import com.example.netraai.detection.ObjectDetectorHelper
import com.example.netraai.detection.WalkingModeAnalyzer
import com.example.netraai.feedback.AlertEngine
import com.example.netraai.feedback.FlashlightAutoController
import com.example.netraai.feedback.FlashlightMode
import com.example.netraai.ocr.TextReader
import com.example.netraai.ui.SettingsSheet
import com.example.netraai.ui.theme.DangerRed
import com.example.netraai.ui.theme.DarkBackground
import com.example.netraai.ui.theme.DarkCard
import com.example.netraai.ui.theme.DarkSurfaceSemiOpaque
import com.example.netraai.ui.theme.ElectricAmber
import com.example.netraai.ui.theme.ElectricCyan
import com.example.netraai.ui.theme.NetraAITheme
import com.example.netraai.ui.theme.TextPrimary
import com.example.netraai.ui.theme.TextSecondary
import com.example.netraai.ui.theme.WalkingGreen
import com.example.netraai.voice.TtsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private lateinit var ttsManager: TtsManager
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var alertEngine: AlertEngine
    private lateinit var detectorHelper: ObjectDetectorHelper
    private lateinit var walkingModeAnalyzer: WalkingModeAnalyzer
    private val flashlightController = FlashlightAutoController()
    private val textReader = TextReader()

    private var cameraControl: androidx.camera.core.CameraControl? = null
    private var imageCapture: androidx.camera.core.ImageCapture? = null
    private var hasFlashUnit: Boolean = false
    private var hasWarnedNoFlash: Boolean = false

    @Volatile
    private var currentFlashlightMode: FlashlightMode = FlashlightMode.AUTO

    private var hasPermissions by mutableStateOf(false)
    private var isPermanentlyDenied by mutableStateOf(false)
    private var isWalkingModeActive by mutableStateOf(false)
    private var showSettingsSheet by mutableStateOf(false)
    private var currentLanguage by mutableStateOf("en")
    private var isReadingActive by mutableStateOf(false)

    // Debug overlay data
    private var debugDetections by mutableStateOf<List<DetectedObstacle>>(emptyList())
    private var debugFps by mutableIntStateOf(0)
    private var debugInferenceTimeMs by mutableLongStateOf(0L)
    private var debugUprightWidth by mutableIntStateOf(0)
    private var debugUprightHeight by mutableIntStateOf(0)
    private var debugGateStatus by mutableStateOf(GateStatus.ACTIVE)
    private var debugAngularSpeed by mutableFloatStateOf(0f)
    private var debugSharpnessScore by mutableFloatStateOf(0f)
    private var debugWhyReason by mutableStateOf("")

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

    private fun triggerDangerHaptic() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val effect = VibrationEffect.createWaveform(longArrayOf(0, 150, 80, 200), -1)
                vibratorManager?.vibrate(CombinedVibration.createParallel(effect))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val effect = VibrationEffect.createWaveform(longArrayOf(0, 150, 80, 200), -1)
                    vibrator?.vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(300L)
                }
            }
        } catch (_: Exception) {
            // Ignore haptic failures on unsupported hardware
        }
    }

    private fun applyFlashlightState(desiredState: Boolean, speakAnnouncement: Boolean = false, reason: String = "") {
        if (!hasFlashUnit) {
            if (speakAnnouncement && !hasWarnedNoFlash) {
                hasWarnedNoFlash = true
                ttsManager.speak("Flashlight unavailable on this device.")
            }
            return
        }

        try {
            cameraControl?.enableTorch(desiredState)
            if (speakAnnouncement) {
                ttsManager.speak(reason)
            }
        } catch (_: Exception) {
            // Torch control error
        }
    }

    private fun isMostlyDevanagari(text: String): Boolean {
        var devanagariCount = 0
        var letterCount = 0
        for (ch in text) {
            if (ch.isLetter()) {
                letterCount++
                if (ch in '\u0900'..'\u097F') {
                    devanagariCount++
                }
            }
        }
        return letterCount > 0 && (devanagariCount.toFloat() / letterCount) >= 0.5f
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        ttsManager = TtsManager(this)
        preferencesManager = PreferencesManager(this)
        alertEngine = AlertEngine(ttsManager)
        detectorHelper = ObjectDetectorHelper(this)

        detectorHelper.onWhyLogged = { reason ->
            runOnUiThread { debugWhyReason = reason }
        }
        alertEngine.onWhyLogged = { reason ->
            runOnUiThread { debugWhyReason = reason }
        }

        walkingModeAnalyzer = WalkingModeAnalyzer(
            detectorHelper = detectorHelper,
            onLumaSampled = { luma ->
                if (isWalkingModeActive && currentFlashlightMode == FlashlightMode.AUTO) {
                    val stateChange = flashlightController.evaluate(luma)
                    if (stateChange == true) {
                        applyFlashlightState(true, speakAnnouncement = true, reason = "Low light, flashlight on")
                    } else if (stateChange == false) {
                        applyFlashlightState(false, speakAnnouncement = true, reason = "Flashlight off")
                    }
                }
            },
            onAnalysisComplete = { confirmed, rawAccepted, fps, infMs, uprightW, uprightH, gateState, speed, sharpness ->
                if (isWalkingModeActive) {
                    if (isReadingActive) {
                        android.util.Log.d("NetraWhy", "Skipped alert: REASON=READING_ACTIVE")
                        runOnUiThread { debugWhyReason = "READING_ACTIVE" }
                    } else if (gateState.isPaused) {
                        android.util.Log.d("NetraWhy", "Skipped alert: REASON=GATE_PAUSED")
                        runOnUiThread { debugWhyReason = "GATE_PAUSED" }
                    } else if (gateState == GateStatus.ACTIVE && !isReadingActive) {
                        lifecycleScope.launch {
                            val spoken = alertEngine.processObstacles(confirmed, currentLanguage)
                            if (spoken != null && spoken.priority == AlertPriority.PRIORITY_1) {
                                triggerDangerHaptic()
                            }
                        }
                    }
                }

                runOnUiThread {
                    debugDetections = rawAccepted
                    debugFps = fps
                    debugInferenceTimeMs = infMs
                    debugUprightWidth = uprightW
                    debugUprightHeight = uprightH
                    debugGateStatus = gateState
                    debugAngularSpeed = speed
                    debugSharpnessScore = sharpness
                }
            }
        )
        walkingModeAnalyzer.setContext(this)
        walkingModeAnalyzer.onDebugFrameSaved = { path ->
            runOnUiThread {
                ttsManager.speak("Debug frame saved to cache.")
                Toast.makeText(this, "Debug frame saved: $path", Toast.LENGTH_LONG).show()
            }
        }

        checkPermissions()

        lifecycleScope.launch {
            preferencesManager.flashlightModeFlow.collect { mode ->
                currentFlashlightMode = mode
            }
        }

        lifecycleScope.launch {
            preferencesManager.languageFlow.collect { lang ->
                currentLanguage = lang
                if (!isReadingActive) {
                    ttsManager.setLanguage(lang)
                }
            }
        }

        lifecycleScope.launch {
            preferencesManager.speechSpeedFlow.collect { speed ->
                ttsManager.setSpeechRate(speed)
            }
        }

        lifecycleScope.launch {
            preferencesManager.detectionSensitivityFlow.collect { sensitivity ->
                detectorHelper.sensitivity = sensitivity
            }
        }

        lifecycleScope.launch {
            preferencesManager.distanceCalibrationFlow.collect { multiplier ->
                detectorHelper.calibrationMultiplier = multiplier
            }
        }

        lifecycleScope.launch {
            val isFirst = preferencesManager.isFirstLaunch()
            val greeting = if (isFirst) {
                preferencesManager.setFirstLaunchCompleted()
                if (currentLanguage == "hi") {
                    "नेत्रा तैयार है। ध्यान दें: यह ऐप सहायता करता है लेकिन सफेद छड़ी या गाइड का विकल्प नहीं है।"
                } else {
                    "Netra ready. Note: this app assists but does not replace a white cane or guide dog."
                }
            } else {
                if (currentLanguage == "hi") "नेत्रा तैयार है।" else "Netra ready."
            }

            ttsManager.speak(greeting)

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
                val currentLanguageState by preferencesManager.languageFlow.collectAsState(initial = "en")
                val speechSpeedState by preferencesManager.speechSpeedFlow.collectAsState(initial = 0.85f)
                val flashlightModeState by preferencesManager.flashlightModeFlow.collectAsState(initial = FlashlightMode.AUTO)
                val isDebugOverlayState by preferencesManager.debugOverlayFlow.collectAsState(initial = false)
                val sensitivityState by preferencesManager.detectionSensitivityFlow.collectAsState(initial = DetectionSensitivity.NORMAL)
                val distanceCalibrationState by preferencesManager.distanceCalibrationFlow.collectAsState(initial = 1.0f)
                val isDisclaimerDismissedState by preferencesManager.isDisclaimerDismissedFlow.collectAsState(initial = false)

                val localView = LocalView.current
                val activityContext = this@MainActivity

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkBackground
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // 1. Fullscreen Camera Preview with ImageCapture
                        if (hasPermissions) {
                            CameraPreview(
                                modifier = Modifier.fillMaxSize(),
                                analyzer = walkingModeAnalyzer,
                                onCameraBound = { camera ->
                                    cameraControl = camera.cameraControl
                                    hasFlashUnit = camera.cameraInfo.hasFlashUnit()
                                },
                                onImageCaptureCreated = { capture ->
                                    imageCapture = capture
                                },
                                onError = {
                                    val err = if (currentLanguageState == "hi") "कैमरा अनुपलब्ध है।" else "Camera unavailable."
                                    ttsManager.speak(err, interrupt = true)
                                }
                            )
                        }

                        // 2. Debug Overlay
                        if (isDebugOverlayState && isWalkingModeActive) {
                            DebugOverlay(
                                detections = debugDetections,
                                fps = debugFps,
                                inferenceTimeMs = debugInferenceTimeMs,
                                uprightWidth = debugUprightWidth,
                                uprightHeight = debugUprightHeight,
                                gateStatus = debugGateStatus,
                                angularSpeed = debugAngularSpeed,
                                sharpnessScore = debugSharpnessScore,
                                lastWhyReason = debugWhyReason
                            )
                        }

                        // 3. UI Layer
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .statusBarsPadding()
                                .navigationBarsPadding(),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Top Bar: Status Pill & Settings Icon
                            TopBarSection(
                                isWalkingModeActive = isWalkingModeActive,
                                language = currentLanguageState,
                                flashlightMode = flashlightModeState,
                                onToggleDebugOverlay = {
                                    localView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                    walkingModeAnalyzer.debugCaptureRequested = true
                                    lifecycleScope.launch {
                                        preferencesManager.setDebugOverlay(!isDebugOverlayState)
                                    }
                                },
                                onOpenSettings = {
                                    localView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    showSettingsSheet = true
                                }
                            )

                            // One-time Dismissible Disclaimer Card
                            AnimatedVisibility(
                                visible = !isDisclaimerDismissedState,
                                enter = fadeIn(tween(200)),
                                exit = fadeOut(tween(200))
                            ) {
                                DisclaimerCard(
                                    isHindi = currentLanguageState == "hi",
                                    onDismiss = {
                                        localView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                        lifecycleScope.launch {
                                            preferencesManager.setDisclaimerDismissed(true)
                                        }
                                    }
                                )
                            }

                            // "Hold steady" indicator badge when detection is paused due to motion or blur
                            if (isWalkingModeActive && debugGateStatus.isPaused) {
                                Card(
                                    modifier = Modifier
                                        .padding(top = 16.dp)
                                        .semantics {
                                            contentDescription = if (currentLanguageState == "hi") "कैमरा स्थिर रखें" else "Hold camera steady"
                                        },
                                    colors = CardDefaults.cardColors(containerColor = ElectricAmber),
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Text(
                                        text = if (currentLanguageState == "hi") "🖐️ कैमरा स्थिर रखें" else "🖐️ Hold camera steady",
                                        color = Color.Black,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            // Bottom Control Dock
                            if (!hasPermissions) {
                                PermissionRequestCard(
                                    isPermanentlyDenied = isPermanentlyDenied,
                                    isHindi = currentLanguageState == "hi",
                                    onRequestPermissions = {
                                        localView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                        requestPermissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.CAMERA,
                                                Manifest.permission.RECORD_AUDIO,
                                                Manifest.permission.ACCESS_FINE_LOCATION
                                            )
                                        )
                                    },
                                    onOpenSettings = {
                                        localView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                        openAppSettings()
                                    }
                                )
                            } else {
                                BottomControlDock(
                                    isWalkingModeActive = isWalkingModeActive,
                                    flashlightMode = flashlightModeState,
                                    currentLanguage = currentLanguageState,
                                    isReadingActive = isReadingActive,
                                    onToggleWalkingMode = {
                                        localView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                        if (!isWalkingModeActive) {
                                            isWalkingModeActive = true
                                            walkingModeAnalyzer.isWalkingModeEnabled = true
                                            val msg = if (currentLanguageState == "hi") "वॉकिंग मोड चालू" else "Walking mode on"
                                            ttsManager.speak(msg, interrupt = true)

                                            if (flashlightModeState == FlashlightMode.ON) {
                                                applyFlashlightState(true)
                                            }
                                        } else {
                                            isWalkingModeActive = false
                                            walkingModeAnalyzer.isWalkingModeEnabled = false
                                            alertEngine.reset()
                                            walkingModeAnalyzer.reset()
                                            applyFlashlightState(false)
                                            val msg = if (currentLanguageState == "hi") "वॉकिंग मोड बंद" else "Walking mode off"
                                            ttsManager.speak(msg, interrupt = true)
                                        }
                                    },
                                    onLongClickWalkingMode = {
                                        localView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                        walkingModeAnalyzer.debugCaptureRequested = true
                                    },
                                    onReadTextClicked = {
                                        localView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                        if (isReadingActive) {
                                            isReadingActive = false
                                            ttsManager.stop()
                                            ttsManager.setLanguage(currentLanguage)
                                            val stopMsg = if (currentLanguageState == "hi") "रुक गए" else "Stopped"
                                            ttsManager.speak(stopMsg, interrupt = true)
                                        } else {
                                            isReadingActive = true
                                            val readingMsg = if (currentLanguageState == "hi") "पढ़ रहे हैं" else "Reading"
                                            ttsManager.speak(readingMsg, interrupt = true)

                                            val capture = imageCapture
                                            if (capture != null) {
                                                val photoFile = java.io.File.createTempFile("ocr_capture", ".jpg", cacheDir)
                                                val outputOptions = androidx.camera.core.ImageCapture.OutputFileOptions.Builder(photoFile).build()
                                                capture.takePicture(
                                                    outputOptions,
                                                    ContextCompat.getMainExecutor(activityContext),
                                                    object : androidx.camera.core.ImageCapture.OnImageSavedCallback {
                                                        override fun onImageSaved(outputFileResults: androidx.camera.core.ImageCapture.OutputFileResults) {
                                                            lifecycleScope.launch(Dispatchers.IO) {
                                                                try {
                                                                    val bitmap = android.graphics.BitmapFactory.decodeFile(photoFile.absolutePath)
                                                                    val recognizedText = if (bitmap != null) textReader.readText(bitmap, 0) else ""
                                                                    photoFile.delete()

                                                                    withContext(Dispatchers.Main) {
                                                                        val isValid = recognizedText.length >= 3 && recognizedText.any { it.isLetterOrDigit() }
                                                                        if (!isValid) {
                                                                            isReadingActive = false
                                                                            val retryMsg = if (currentLanguage == "hi") "फोन को थोड़ा पास या दूर ले जाएं, स्थिर रखें" else "Move phone a little closer or further, hold steady"
                                                                            ttsManager.speak(retryMsg, interrupt = true)
                                                                        } else {
                                                                            val useHindiVoice = isMostlyDevanagari(recognizedText)
                                                                            if (useHindiVoice) {
                                                                                ttsManager.setLanguage("hi")
                                                                            } else {
                                                                                ttsManager.setLanguage("en")
                                                                            }

                                                                            val maxLength = ttsManager.maxSpeechInputLength.coerceAtMost(200)
                                                                            val chunks = TextReader.splitTextIntoChunks(recognizedText, maxChunkLength = maxLength)
                                                                            for (i in chunks.indices) {
                                                                                val chunk = chunks[i].trim()
                                                                                val isLast = (i == chunks.size - 1)
                                                                                if (isLast) {
                                                                                    ttsManager.speak(chunk, interrupt = false, utteranceId = "last_utterance", onDone = {
                                                                                        runOnUiThread {
                                                                                            ttsManager.setLanguage(currentLanguage)
                                                                                            isReadingActive = false
                                                                                        }
                                                                                    })
                                                                                } else {
                                                                                    ttsManager.speak(chunk, interrupt = false, utteranceId = "chunk_$i")
                                                                                }
                                                                            }
                                                                        }
                                                                    }
                                                                } catch (_: Exception) {
                                                                    photoFile.delete()
                                                                    withContext(Dispatchers.Main) {
                                                                        ttsManager.setLanguage(currentLanguage)
                                                                        isReadingActive = false
                                                                        val errMsg = if (currentLanguage == "hi") "पाठ पढ़ने में त्रुटि।" else "Error reading text."
                                                                        ttsManager.speak(errMsg, interrupt = true)
                                                                    }
                                                                }
                                                            }
                                                        }

                                                        override fun onError(exception: androidx.camera.core.ImageCaptureException) {
                                                            isReadingActive = false
                                                            val errMsg = if (currentLanguage == "hi") "पाठ पढ़ने में त्रुटि।" else "Error reading text."
                                                            ttsManager.speak(errMsg, interrupt = true)
                                                        }
                                                    }
                                                )
                                            } else {
                                                isReadingActive = false
                                                val errMsg = if (currentLanguageState == "hi") "कैमरा तैयार नहीं है।" else "Camera not ready."
                                                ttsManager.speak(errMsg, interrupt = true)
                                            }
                                        }
                                    },
                                    onCycleFlashlight = {
                                        localView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                        val nextMode = when (flashlightModeState) {
                                            FlashlightMode.AUTO -> FlashlightMode.ON
                                            FlashlightMode.ON -> FlashlightMode.OFF
                                            FlashlightMode.OFF -> FlashlightMode.AUTO
                                        }
                                        lifecycleScope.launch {
                                            preferencesManager.setFlashlightMode(nextMode)
                                        }
                                        val announcement = when (nextMode) {
                                            FlashlightMode.AUTO -> {
                                                if (!isWalkingModeActive) applyFlashlightState(false)
                                                if (currentLanguageState == "hi") "टॉर्च ऑटो मोड" else "Flashlight auto"
                                            }
                                            FlashlightMode.ON -> {
                                                applyFlashlightState(true)
                                                if (currentLanguageState == "hi") "टॉर्च चालू" else "Flashlight on"
                                            }
                                            FlashlightMode.OFF -> {
                                                applyFlashlightState(false)
                                                if (currentLanguageState == "hi") "टॉर्च बंद" else "Flashlight off"
                                            }
                                        }
                                        ttsManager.speak(announcement)
                                    },
                                    onToggleLanguage = {
                                        localView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                        val newLang = if (currentLanguageState == "en") "hi" else "en"
                                        lifecycleScope.launch {
                                            preferencesManager.setLanguage(newLang)
                                        }
                                        val msg = if (newLang == "hi") "हिंदी भाषा चयनित" else "English selected"
                                        ttsManager.speak(msg)
                                    },
                                    onSosClicked = {
                                        localView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                        triggerDangerHaptic()
                                        val msg = if (currentLanguageState == "hi") "आपातकालीन एसओएस सुविधा शीघ्र उपलब्ध होगी।" else "Emergency SOS feature coming soon."
                                        ttsManager.speak(msg, interrupt = true)
                                    },
                                    onOpenSettings = {
                                        localView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                        showSettingsSheet = true
                                    }
                                )
                            }
                        }

                        // Settings Bottom Sheet
                        if (showSettingsSheet) {
                            SettingsSheet(
                                language = currentLanguageState,
                                speechSpeed = speechSpeedState,
                                flashlightMode = flashlightModeState,
                                isDebugOverlayEnabled = isDebugOverlayState,
                                detectionSensitivity = sensitivityState,
                                distanceCalibration = distanceCalibrationState,
                                onLanguageChange = { lang ->
                                    lifecycleScope.launch { preferencesManager.setLanguage(lang) }
                                },
                                onSpeechSpeedChange = { speed ->
                                    lifecycleScope.launch { preferencesManager.setSpeechSpeed(speed) }
                                },
                                onFlashlightModeChange = { mode ->
                                    lifecycleScope.launch {
                                        preferencesManager.setFlashlightMode(mode)
                                        if (mode == FlashlightMode.ON && isWalkingModeActive) {
                                            applyFlashlightState(true)
                                        } else if (mode == FlashlightMode.OFF) {
                                            applyFlashlightState(false)
                                        }
                                    }
                                },
                                onDebugOverlayChange = { enabled ->
                                    lifecycleScope.launch { preferencesManager.setDebugOverlay(enabled) }
                                },
                                onDetectionSensitivityChange = { sens ->
                                    lifecycleScope.launch { preferencesManager.setDetectionSensitivity(sens) }
                                },
                                onDistanceCalibrationChange = { mult ->
                                    lifecycleScope.launch { preferencesManager.setDistanceCalibration(mult) }
                                },
                                onDismiss = { showSettingsSheet = false }
                            )
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
        if (hasPermissions) {
            walkingModeAnalyzer.motionGate.register(this)
        }
    }

    override fun onPause() {
        super.onPause()
        walkingModeAnalyzer.motionGate.unregister(this)
    }

    override fun onStop() {
        super.onStop()
        applyFlashlightState(false)
        walkingModeAnalyzer.motionGate.unregister(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        isWalkingModeActive = false
        walkingModeAnalyzer.isWalkingModeEnabled = false
        walkingModeAnalyzer.motionGate.unregister(this)
        applyFlashlightState(false)
        detectorHelper.close()
        textReader.close()
        ttsManager.shutdown()
    }
}

// ======================================================================================
// COMPOSABLE UI COMPONENTS (Professional WCAG AA Dark Design)
// ======================================================================================

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TopBarSection(
    isWalkingModeActive: Boolean,
    language: String,
    flashlightMode: FlashlightMode,
    onToggleDebugOverlay: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val isHindi = language.equals("hi", ignoreCase = true)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status Pill with long-press to capture debug frame
        Box(
            modifier = Modifier
                .background(DarkSurfaceSemiOpaque, shape = RoundedCornerShape(24.dp))
                .combinedClickable(
                    onClick = { /* Informational */ },
                    onLongClick = onToggleDebugOverlay
                )
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .semantics {
                    contentDescription = "Status: Walking Mode ${if (isWalkingModeActive) "Active" else "Inactive"}, Language $language, Flashlight ${flashlightMode.name}. Long press to capture debug frame."
                }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(
                            color = if (isWalkingModeActive) WalkingGreen else Color.Gray,
                            shape = CircleShape
                        )
                )

                Text(
                    text = if (isWalkingModeActive) "WALKING" else "READY",
                    color = if (isWalkingModeActive) WalkingGreen else TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "•",
                    color = Color.DarkGray,
                    fontSize = 13.sp
                )

                Text(
                    text = language.uppercase(),
                    color = ElectricCyan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "•",
                    color = Color.DarkGray,
                    fontSize = 13.sp
                )

                Text(
                    text = flashlightMode.name,
                    color = ElectricAmber,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .size(48.dp)
                .background(DarkSurfaceSemiOpaque, shape = CircleShape)
                .semantics {
                    contentDescription = if (isHindi) "सेटिंग्स खोलें" else "Open settings"
                }
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                tint = ElectricCyan
            )
        }
    }
}

@Composable
private fun DisclaimerCard(
    isHindi: Boolean,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics {
                contentDescription = if (isHindi) {
                    "सावधानी: सीढ़ियां, गड्ढे और खुले नाले अभी डिटेक्ट नहीं होते। सफेद छड़ी का प्रयोग अवश्य करें।"
                } else {
                    "Safety Notice: Stairs, potholes, and open drains are not detected yet. Always use a white cane."
                }
            },
        colors = CardDefaults.cardColors(containerColor = DarkCard.copy(alpha = 0.95f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isHindi) {
                    "⚠️ सूचना: सीढ़ियां, गड्ढे व खुले नाले अभी डिटेक्ट नहीं होते। सफेद छड़ी का प्रयोग करें।"
                } else {
                    "⚠️ Notice: Stairs, potholes, and open drains are not detected yet. Does not replace a white cane."
                },
                color = ElectricAmber,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(40.dp)
                    .semantics {
                        contentDescription = if (isHindi) "सूचनाएं हटाएं" else "Dismiss notice"
                    }
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun PermissionRequestCard(
    isPermanentlyDenied: Boolean,
    isHindi: Boolean,
    onRequestPermissions: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceSemiOpaque),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isPermanentlyDenied) {
                    if (isHindi) "कैमरा और माइक्रोफ़ोन अनुमतियां अस्वीकृत हैं। कृपया सेटिंग्स खोलें।" else "Camera and microphone permissions were denied. Please open settings."
                } else {
                    if (isHindi) "लाइव बाधा पहचान के लिए कैमरा और माइक्रोफ़ोन अनुमतियां आवश्यक हैं।" else "Camera and microphone permissions are required for live obstacle detection."
                },
                color = TextPrimary,
                fontSize = 17.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Button(
                onClick = if (isPermanentlyDenied) onOpenSettings else onRequestPermissions,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .semantics {
                        contentDescription = if (isPermanentlyDenied) {
                            if (isHindi) "सेटिंग्स खोलें" else "Open Settings button"
                        } else {
                            if (isHindi) "अनुमति दें" else "Grant Permissions button"
                        }
                    },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isPermanentlyDenied) DangerRed else ElectricCyan
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = if (isPermanentlyDenied) {
                        if (isHindi) "सेटिंग्स खोलें" else "Open Settings"
                    } else {
                        if (isHindi) "अनुमति दें" else "Grant Permissions"
                    },
                    color = if (isPermanentlyDenied) Color.White else Color.Black,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BottomControlDock(
    isWalkingModeActive: Boolean,
    flashlightMode: FlashlightMode,
    currentLanguage: String,
    isReadingActive: Boolean,
    onToggleWalkingMode: () -> Unit,
    onLongClickWalkingMode: () -> Unit,
    onReadTextClicked: () -> Unit,
    onCycleFlashlight: () -> Unit,
    onToggleLanguage: () -> Unit,
    onSosClicked: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val isHindi = currentLanguage.equals("hi", ignoreCase = true)

    val walkingBtnColor by animateColorAsState(
        targetValue = if (isWalkingModeActive) DangerRed else WalkingGreen,
        animationSpec = tween(250),
        label = "walkingBtnColor"
    )

    val readBtnColor by animateColorAsState(
        targetValue = if (isReadingActive) DangerRed else DarkCard,
        animationSpec = tween(250),
        label = "readBtnColor"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceSemiOpaque),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(88.dp)
                    .background(walkingBtnColor, shape = RoundedCornerShape(20.dp))
                    .combinedClickable(
                        onClick = onToggleWalkingMode,
                        onLongClick = onLongClickWalkingMode
                    )
                    .semantics {
                        contentDescription = if (isWalkingModeActive) {
                            if (isHindi) "वॉकिंग मोड बंद करें। सक्रिय है।" else "Stop Walking Mode button. Active."
                        } else {
                            if (isHindi) "वॉकिंग मोड शुरू करें।" else "Start Walking Mode button."
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isWalkingModeActive) {
                        if (isHindi) "वॉकिंग मोड बंद करें" else "Stop Walking Mode"
                    } else {
                        if (isHindi) "वॉकिंग मोड शुरू करें" else "Start Walking Mode"
                    },
                    color = if (isWalkingModeActive) Color.White else Color.Black,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onCycleFlashlight,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(72.dp)
                        .semantics {
                            contentDescription = "Flashlight mode toggle, current is ${flashlightMode.name}"
                        },
                    colors = ButtonDefaults.buttonColors(containerColor = DarkCard),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = when (flashlightMode) {
                                FlashlightMode.AUTO -> Icons.Default.FlashAuto
                                FlashlightMode.ON -> Icons.Default.FlashOn
                                FlashlightMode.OFF -> Icons.Default.FlashOff
                            },
                            contentDescription = null,
                            tint = ElectricAmber,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = flashlightMode.name,
                            color = TextPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }

                Button(
                    onClick = onToggleLanguage,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(72.dp)
                        .semantics {
                            contentDescription = "Switch language, currently $currentLanguage"
                        },
                    colors = ButtonDefaults.buttonColors(containerColor = DarkCard),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isHindi) "हिंदी" else "EN",
                            color = TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }

                Button(
                    onClick = onReadTextClicked,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(72.dp)
                        .semantics {
                            contentDescription = if (isReadingActive) {
                                if (isHindi) "पढ़ना बंद करें" else "Stop reading text"
                            } else {
                                if (isHindi) "पाठ पढ़ें" else "Read visible text aloud"
                            }
                        },
                    colors = ButtonDefaults.buttonColors(containerColor = readBtnColor),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MenuBook,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isReadingActive) (if (isHindi) "रोकें" else "Stop") else (if (isHindi) "पढ़ें" else "Read"),
                            color = TextPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }

                Button(
                    onClick = onSosClicked,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(72.dp)
                        .semantics {
                            contentDescription = "SOS emergency assistance button"
                        },
                    colors = ButtonDefaults.buttonColors(containerColor = DarkCard),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = DangerRed,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "SOS",
                            color = DangerRed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }

                Button(
                    onClick = onOpenSettings,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(72.dp)
                        .semantics {
                            contentDescription = "Open Settings button"
                        },
                    colors = ButtonDefaults.buttonColors(containerColor = DarkCard),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "SET",
                            color = TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
