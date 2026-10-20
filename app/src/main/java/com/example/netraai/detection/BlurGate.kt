package com.example.netraai.detection

import androidx.camera.core.ImageProxy

/**
 * Calculates a fast image sharpness score based on Laplacian variance on the luminance (Y) plane
 * to drop motion-blurred or out-of-focus camera frames.
 */
object BlurGate {

    const val DEFAULT_SHARPNESS_THRESHOLD = 80.0f // Variance of Laplacian threshold

    /**
     * Calculates the variance of Laplacian on the Y plane of an [ImageProxy].
     * Uses adjacent pixels (offset of 1 pixel) on a downscaled sample grid.
     * Higher score = sharper image. Lower score = blurry image.
     */
    fun calculateSharpness(imageProxy: ImageProxy, step: Int = 8): Float {
        val planes = imageProxy.planes
        if (planes.isEmpty()) return 200.0f

        val yPlane = planes[0]
        val buffer = yPlane.buffer
        val rowStride = yPlane.rowStride
        val pixelStride = yPlane.pixelStride
        val width = imageProxy.width
        val height = imageProxy.height

        var sum = 0.0
        var sqSum = 0.0
        var count = 0

        // 3x3 Laplacian kernel approximation using adjacent pixels (1 pixel offset):
        // L(x,y) = I(x+1,y) + I(x-1,y) + I(x,y+1) + I(x,y-1) - 4*I(x,y)
        for (row in 1 until height - 1 step step) {
            val rowOffset = row * rowStride
            val rowAboveOffset = (row - 1) * rowStride
            val rowBelowOffset = (row + 1) * rowStride

            for (col in 1 until width - 1 step step) {
                val idx = rowOffset + col * pixelStride
                if (idx + pixelStride >= buffer.limit()) continue

                val center = buffer.get(idx).toInt() and 0xFF
                val left = buffer.get(rowOffset + (col - 1) * pixelStride).toInt() and 0xFF
                val right = buffer.get(rowOffset + (col + 1) * pixelStride).toInt() and 0xFF
                val top = buffer.get(rowAboveOffset + col * pixelStride).toInt() and 0xFF
                val bottom = buffer.get(rowBelowOffset + col * pixelStride).toInt() and 0xFF

                val laplacian = (left + right + top + bottom - 4 * center).toDouble()
                sum += laplacian
                sqSum += laplacian * laplacian
                count++
            }
        }

        if (count == 0) return 200.0f
        val mean = sum / count
        val variance = (sqSum / count) - (mean * mean)
        return variance.toFloat()
    }

    fun isBlurry(sharpnessScore: Float, threshold: Float = DEFAULT_SHARPNESS_THRESHOLD): Boolean {
        return sharpnessScore < threshold
    }
}
