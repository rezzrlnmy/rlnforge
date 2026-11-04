package com.rln.forge.providers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * OpenAI-compatible chat completions (OpenAI, OpenRouter, DSN Relay, Custom).
 * POST {baseUrl}/v1/chat/completions with stream=true, SSE deltas.
 */
class OpenAiCompatibleClient(
    private val http: OkHttpClient = defaultHttpClient()
) : ChatClient {

    override fun streamChat(
        config: ProviderConfig,
        apiKey: String,
        messages: List<ChatMessage>
    ): Flow<String> = callbackFlow {
        val baseUrl = requireBaseUrl(config)
        val body = JSONObject().apply {
            put("model", config.model)
            put("stream", true)
            put("messages", JSONArray().apply {
                messages.forEach { msg ->
                    put(JSONObject().apply {
                        put("role", msg.role)
                        put("content", msg.content)
                    })
                }
            })
        }
        val request = Request.Builder()
            .url("$baseUrl/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .apply {
                if (config.type == ProviderType.OPENROUTER) {
                    header("HTTP-Referer", "https://github.com/rezzylrn/rlnforge")
                    header("X-Title", "rlnForge")
                }
            }
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        val call = http.newCall(request)
        try {
            val response = call.execute()
            response.use {
                if (!it.isSuccessful) {
                    val raw = it.body?.string().orEmpty()
                    throw ChatException(
                        "HTTP ${it.code}: ${errorMessage(raw, "Request failed")}",
                        it.code
                    )
                }
                it.body?.charStream()?.buffered()?.forEachLine { line ->
                    if (line.startsWith("data:")) {
                        openAiDelta(line.removePrefix("data:"))?.let { delta ->
                            trySend(delta)
                        }
                    }
                }
            }
            close()
        } catch (e: Exception) {
            if (e is ChatException) throw e
            close(e)
        }
        awaitClose { call.cancel() }
    }.flowOn(Dispatchers.IO)

    override suspend fun testConnection(
        config: ProviderConfig,
        apiKey: String
    ): Result<String> = runCatching {
        val baseUrl = requireBaseUrl(config)
        val request = Request.Builder()
            .url("$baseUrl/v1/models")
            .header("Authorization", "Bearer $apiKey")
            .get()
            .build()
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw ChatException(
                    "HTTP ${response.code}: ${errorMessage(raw, "Connection failed")}",
                    response.code
                )
            }
            val count = countModels(raw)
            if (count != null) "Connected — $count models listed" else "Connected"
        }
    }

    private fun requireBaseUrl(config: ProviderConfig): String {
        val baseUrl = config.cleanBaseUrl()
        require(baseUrl.isNotEmpty()) { "Base URL is empty for ${config.label}" }
        return baseUrl
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
