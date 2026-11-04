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

/**
 * Anthropic Messages API. POST https://api.anthropic.com/v1/messages,
 * streaming SSE with content_block_delta events.
 */
class AnthropicClient(
    private val http: OkHttpClient = OpenAiCompatibleClient.defaultHttpClient()
) : ChatClient {

    override fun streamChat(
        config: ProviderConfig,
        apiKey: String,
        messages: List<ChatMessage>
    ): Flow<String> = callbackFlow {
        val body = JSONObject().apply {
            put("model", config.model)
            put("max_tokens", 4096)
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
            .url("${config.cleanBaseUrl()}/v1/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
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
                        anthropicDelta(line.removePrefix("data:"))?.let { delta ->
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
        val request = Request.Builder()
            .url("${config.cleanBaseUrl()}/v1/models")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
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

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
