package com.example.netraai.cloud

import java.io.ByteArrayOutputStream

class MultipartBodyBuilder(val boundary: String = "----NetraBoundary" + System.currentTimeMillis()) {

    fun buildBody(
        imageBytes: ByteArray,
        language: String,
        question: String? = null
    ): ByteArray {
        val baos = ByteArrayOutputStream()

        // Field: language
        baos.write("--$boundary\r\n".toByteArray(Charsets.UTF_8))
        baos.write("Content-Disposition: form-data; name=\"language\"\r\n\r\n".toByteArray(Charsets.UTF_8))
        baos.write("$language\r\n".toByteArray(Charsets.UTF_8))

        // Optional field: question
        if (!question.isNullOrBlank()) {
            baos.write("--$boundary\r\n".toByteArray(Charsets.UTF_8))
            baos.write("Content-Disposition: form-data; name=\"question\"\r\n\r\n".toByteArray(Charsets.UTF_8))
            baos.write("$question\r\n".toByteArray(Charsets.UTF_8))
        }

        // File field: image
        baos.write("--$boundary\r\n".toByteArray(Charsets.UTF_8))
        baos.write("Content-Disposition: form-data; name=\"image\"; filename=\"scene.jpg\"\r\n".toByteArray(Charsets.UTF_8))
        baos.write("Content-Type: image/jpeg\r\n\r\n".toByteArray(Charsets.UTF_8))
        baos.write(imageBytes)
        baos.write("\r\n".toByteArray(Charsets.UTF_8))

        // Closing boundary
        baos.write("--$boundary--\r\n".toByteArray(Charsets.UTF_8))

        return baos.toByteArray()
    }

    fun getContentType(): String = "multipart/form-data; boundary=$boundary"
}
