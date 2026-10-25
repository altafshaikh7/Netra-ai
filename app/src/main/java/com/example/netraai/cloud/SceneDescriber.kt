package com.example.netraai.cloud

interface SceneDescriber {
    suspend fun describeScene(
        imageBytes: ByteArray,
        deviceId: String,
        language: String,
        question: String? = null
    ): Result<String>
}
