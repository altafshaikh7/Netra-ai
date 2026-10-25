package com.example.netraai.cloud

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class BackendSceneDescriber(
    private val baseUrl: String
) : SceneDescriber {

    override suspend fun describeScene(
        imageBytes: ByteArray,
        deviceId: String,
        language: String,
        question: String?
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/describe")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 15000
                doOutput = true
                setRequestProperty("X-Device-Id", deviceId)

                val builder = MultipartBodyBuilder()
                val body = builder.buildBody(imageBytes, language, question)
                setRequestProperty("Content-Type", builder.getContentType())
                setRequestProperty("Content-Length", body.size.toString())

                outputStream.use { it.write(body) }
            }

            val statusCode = connection.responseCode
            if (statusCode == 200) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseText)
                val text = json.optString("text", "")
                Result.success(text)
            } else if (statusCode == 429) {
                Result.failure(Exception("RATE_LIMITED"))
            } else if (statusCode == 502 || statusCode == 503) {
                Result.failure(Exception("SERVICE_UNAVAILABLE"))
            } else {
                Result.failure(Exception("HTTP_$statusCode"))
            }
        } catch (e: java.net.SocketTimeoutException) {
            Result.failure(Exception("TIMEOUT"))
        } catch (e: java.io.IOException) {
            Result.failure(Exception("NO_INTERNET"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
