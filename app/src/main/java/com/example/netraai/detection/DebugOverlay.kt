package com.example.netraai.detection

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Debug overlay for testing and tuning: draws bounding boxes, classification labels,
 * confidence percentages, FPS, inference latency, motion angular speed, sharpness score, active profile, and gate status.
 */
@Composable
fun DebugOverlay(
    detections: List<DetectedObstacle>,
    fps: Int,
    inferenceTimeMs: Long,
    uprightWidth: Int,
    uprightHeight: Int,
    modifier: Modifier = Modifier,
    gateStatus: GateStatus = GateStatus.ACTIVE,
    angularSpeed: Float = 0f,
    sharpnessScore: Float = 0f,
    detectionProfile: DetectionProfile = DetectionProfile.BALANCED,
    lastWhyReason: String = ""
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (uprightWidth > 0 && uprightHeight > 0) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val scaleX = size.width / uprightWidth.toFloat()
                val scaleY = size.height / uprightHeight.toFloat()

                for (detection in detections) {
                    val box = detection.boundingBox ?: continue
                    val left = box.left * scaleX
                    val top = box.top * scaleY
                    val right = box.right * scaleX
                    val bottom = box.bottom * scaleY
                    val width = (right - left).coerceAtLeast(0f)
                    val height = (bottom - top).coerceAtLeast(0f)

                    val strokeColor = when (detection.priority) {
                        AlertPriority.PRIORITY_1 -> Color(0xFFFF1744) // Danger Red
                        AlertPriority.PRIORITY_2 -> Color(0xFFFFD600) // Warning Yellow
                        AlertPriority.PRIORITY_3 -> Color(0xFF00E5FF) // Electric Cyan
                    }

                    drawRect(
                        color = strokeColor,
                        topLeft = Offset(left, top),
                        size = Size(width, height),
                        style = Stroke(width = 3.dp.toPx())
                    )

                    val labelText = "${detection.label} ${String.format("%.0f%%", detection.confidence * 100)} (${detection.direction.name})"

                    drawContext.canvas.nativeCanvas.apply {
                        val paint = android.graphics.Paint().apply {
                            color = android.graphics.Color.WHITE
                            textSize = 36f
                            isFakeBoldText = true
                            setShadowLayer(4f, 1f, 1f, android.graphics.Color.BLACK)
                        }
                        drawText(labelText, left.coerceAtLeast(8f), (top - 8f).coerceAtLeast(36f), paint)
                    }
                }
            }
        }

        // HUD stats pill at top-start
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 16.dp, top = 88.dp)
                .background(Color.Black.copy(alpha = 0.85f), shape = RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Column {
                Text(
                    text = "DEBUG | Profile: ${detectionProfile.name} | FPS: $fps | Latency: ${inferenceTimeMs}ms",
                    color = Color(0xFF00E5FF),
                    fontSize = 12.sp
                )
                val statusText = when (gateStatus) {
                    GateStatus.ACTIVE -> "active"
                    GateStatus.PAUSED_MOVING -> "paused: moving"
                    GateStatus.PAUSED_BLURRY -> "paused: blurry"
                    GateStatus.PAUSED_SETTLING -> "paused: settling"
                }
                Text(
                    text = "Status: $statusText | Speed: ${String.format("%.2f", angularSpeed)} rad/s | Sharpness: ${String.format("%.1f", sharpnessScore)}",
                    color = if (gateStatus.isPaused) Color(0xFFFFD600) else Color(0xFF00E676),
                    fontSize = 11.sp
                )
                if (lastWhyReason.isNotEmpty()) {
                    Text(
                        text = "Why: $lastWhyReason",
                        color = Color(0xFFFF5252),
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}
