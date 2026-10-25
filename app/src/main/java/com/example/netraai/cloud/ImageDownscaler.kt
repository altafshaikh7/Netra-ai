package com.example.netraai.cloud

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import kotlin.math.max

object ImageDownscaler {
    const val MAX_LONGEST_SIDE = 1280
    const val JPEG_QUALITY = 80
    const val MAX_BYTES = 1887436 // 1.8 MB

    fun calculateTargetDimensions(width: Int, height: Int, maxLongestSide: Int = MAX_LONGEST_SIDE): Pair<Int, Int> {
        if (width <= 0 || height <= 0) return Pair(1, 1)
        val longestSide = max(width, height)
        if (longestSide <= maxLongestSide) return Pair(width, height)

        val scale = maxLongestSide.toFloat() / longestSide.toFloat()
        val targetW = (width * scale).toInt().coerceAtLeast(1)
        val targetH = (height * scale).toInt().coerceAtLeast(1)
        return Pair(targetW, targetH)
    }

    fun downscaleImage(imageBytes: ByteArray): ByteArray {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options)

        val (targetW, targetH) = calculateTargetDimensions(options.outWidth, options.outHeight)
        val original = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size) ?: return imageBytes

        val scaled = if (original.width != targetW || original.height != targetH) {
            Bitmap.createScaledBitmap(original, targetW, targetH, true)
        } else {
            original
        }

        val stream = ByteArrayOutputStream()
        var quality = JPEG_QUALITY
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        var result = stream.toByteArray()

        while (result.size > MAX_BYTES && quality > 30) {
            quality -= 10
            stream.reset()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            result = stream.toByteArray()
        }

        if (scaled != original) {
            scaled.recycle()
        }
        original.recycle()

        return result
    }
}
