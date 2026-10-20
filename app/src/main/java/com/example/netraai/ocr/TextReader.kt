package com.example.netraai.ocr

import android.graphics.Bitmap
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.abs

data class TextLine(
    val text: String,
    val left: Int,
    val top: Int,
    val height: Int
)

class TextReader {
    private val latinRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val devanagariRecognizer = TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())

    suspend fun readText(bitmap: Bitmap, rotationDegrees: Int): String {
        val inputImage = InputImage.fromBitmap(bitmap, rotationDegrees)

        val latinResult = try { latinRecognizer.process(inputImage).awaitResult() } catch (_: Exception) { null }
        val devanagariResult = try { devanagariRecognizer.process(inputImage).awaitResult() } catch (_: Exception) { null }

        val latinText = latinResult?.text.orEmpty()
        val devanagariText = devanagariResult?.text.orEmpty()

        val chosenResult = if (devanagariText.replace(Regex("\\s+"), "").length > latinText.replace(Regex("\\s+"), "").length) {
            devanagariResult
        } else {
            latinResult
        }

        return formatRecognizedText(extractLines(chosenResult))
    }

    private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result ->
            continuation.resume(result)
        }
        addOnFailureListener { exception ->
            continuation.resumeWith(Result.failure(exception))
        }
    }

    fun close() {
        latinRecognizer.close()
        devanagariRecognizer.close()
    }

    companion object {
        fun extractLines(result: Text?): List<TextLine> {
            if (result == null) return emptyList()
            val textLines = mutableListOf<TextLine>()
            for (block in result.textBlocks) {
                for (line in block.lines) {
                    val box = line.boundingBox
                    if (box != null) {
                        textLines.add(
                            TextLine(
                                text = line.text,
                                left = box.left,
                                top = box.top,
                                height = box.height()
                            )
                        )
                    }
                }
            }
            if (textLines.isEmpty() && !result.text.isBlank()) {
                textLines.add(TextLine(text = result.text, left = 0, top = 0, height = 20))
            }
            return textLines
        }

        /**
         * Pure function taking a list of TextLine data class, sorting by top,
         * grouping into rows (tolerance = half of median line height), sorting each row by left,
         * and joining with spaces while cleaning extra whitespace.
         */
        fun formatRecognizedText(lines: List<TextLine>): String {
            if (lines.isEmpty()) return ""

            val cleanedLines = lines.map { line ->
                val cleanedText = line.text.replace(Regex("\\s+"), " ").trim()
                line.copy(text = cleanedText)
            }.filter { it.text.isNotEmpty() }

            if (cleanedLines.isEmpty()) return ""

            val sortedHeights = cleanedLines.map { it.height.coerceAtLeast(1) }.sorted()
            val medianHeight = sortedHeights[sortedHeights.size / 2]
            val tolerance = (medianHeight / 2).coerceAtLeast(5)

            val sortedByTop = cleanedLines.sortedBy { it.top }

            val rows = mutableListOf<MutableList<TextLine>>()
            for (line in sortedByTop) {
                var addedToRow = false
                for (row in rows) {
                    val rowTop = row.first().top
                    if (abs(line.top - rowTop) <= tolerance) {
                        row.add(line)
                        addedToRow = true
                        break
                    }
                }
                if (!addedToRow) {
                    rows.add(mutableListOf(line))
                }
            }

            val orderedTextBuilder = StringBuilder()
            for (row in rows) {
                val sortedRow = row.sortedBy { it.left }
                val rowText = sortedRow.joinToString(" ") { it.text }
                if (orderedTextBuilder.isNotEmpty()) {
                    orderedTextBuilder.append(" ")
                }
                orderedTextBuilder.append(rowText)
            }

            return orderedTextBuilder.toString().replace(Regex("\\s+"), " ").trim()
        }

        /**
         * Splits recognized text into chunks using a simple character loop ending after . ! ? । or \n,
         * ensuring no chunk exceeds maxChunkLength (default 200).
         */
        fun splitTextIntoChunks(text: String, maxChunkLength: Int = 200): List<String> {
            if (text.isBlank()) return emptyList()
            val resultChunks = mutableListOf<String>()
            val currentChunk = StringBuilder()
            val delimiters = setOf('.', '!', '?', '।', '\n')

            for (i in text.indices) {
                val ch = text[i]
                currentChunk.append(ch)
                if (ch in delimiters || currentChunk.length >= maxChunkLength) {
                    val chunkStr = currentChunk.toString().trim()
                    if (chunkStr.isNotEmpty()) {
                        if (chunkStr.length <= maxChunkLength) {
                            resultChunks.add(chunkStr)
                        } else {
                            var remaining = chunkStr
                            while (remaining.length > maxChunkLength) {
                                var splitIdx = remaining.lastIndexOf(' ', maxChunkLength)
                                if (splitIdx == -1 || splitIdx < maxChunkLength / 2) {
                                    splitIdx = maxChunkLength
                                }
                                resultChunks.add(remaining.substring(0, splitIdx).trim())
                                remaining = remaining.substring(splitIdx).trim()
                            }
                            if (remaining.isNotEmpty()) {
                                resultChunks.add(remaining)
                            }
                        }
                    }
                    currentChunk.clear()
                }
            }

            val leftover = currentChunk.toString().trim()
            if (leftover.isNotEmpty()) {
                if (leftover.length <= maxChunkLength) {
                    resultChunks.add(leftover)
                } else {
                    var remaining = leftover
                    while (remaining.length > maxChunkLength) {
                        var splitIdx = remaining.lastIndexOf(' ', maxChunkLength)
                        if (splitIdx == -1 || splitIdx < maxChunkLength / 2) {
                            splitIdx = maxChunkLength
                        }
                        resultChunks.add(remaining.substring(0, splitIdx).trim())
                        remaining = remaining.substring(splitIdx).trim()
                    }
                    if (remaining.isNotEmpty()) {
                        resultChunks.add(remaining)
                    }
                }
            }
            return resultChunks
        }
    }
}
