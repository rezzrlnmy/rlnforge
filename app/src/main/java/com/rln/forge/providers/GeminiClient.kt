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
 * Gemini API (Google AI). Streaming first:
 * POST {baseUrl}/v1beta/models/{model}:streamGenerateContent?alt=sse
 * If the streaming endpoint fails, one non-streaming fallback call to
 * :generateContent is tried before giving up. The API key travels in the
 * query string (Google's standard for this API) and is never logged.
 */
class GeminiClient(
    private val http: OkHttpClient = OpenAiCompatibleClient.defaultHttpClient()
) : ChatClient {

    override fun streamChat(
        config: ProviderConfig,
        apiKey: String,
        messages: List<ChatMessage>
    ): Flow<String> = callbackFlow {
        val baseUrl = config.cleanBaseUrl()
        val streamUrl = "$baseUrl/v1beta/models/${config.model}" +
            ":streamGenerateContent?alt=sse&key=$apiKey"
        val call = http.newCall(buildRequest(streamUrl, messages))
        try {
            val response = call.execute()
            if (response.isSuccessful) {
                response.body?.charStream()?.buffered()?.forEachLine { line ->
                    if (line.startsWith("data:")) {
                        geminiTexts(line.removePrefix("data:"))
                            .takeIf { text -> text.isNotEmpty() }
                            ?.let { text -> trySend(text) }
                    }
                }
                response.close()
                close()
            } else {
                response.body?.close()
                response.close()
                // One non-streaming fallback before giving up.
                val fallbackUrl = "$baseUrl/v1beta/models/${config.model}" +
                    ":generateContent?key=$apiKey"
                val fallback = http.newCall(buildRequest(fallbackUrl, messages)).execute()
                val raw = fallback.body?.string().orEmpty()
                val fallbackCode = fallback.code
                val fallbackOk = fallback.isSuccessful
                fallback.close()
                if (!fallbackOk) {
                    throw ChatException(
                        "HTTP $fallbackCode: ${errorMessage(raw, "Request failed")}",
                        fallbackCode
                    )
                }
                geminiTexts(raw).takeIf { text -> text.isNotEmpty() }?.let { text ->
                    trySend(text)
                }
                close()
            }
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
            .url("${config.cleanBaseUrl()}/v1beta/models?key=$apiKey&pageSize=1")
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
            "Connected"
        }
    }

    private fun buildRequest(url: String, messages: List<ChatMessage>): Request {
        val body = JSONObject().apply {
            put("contents", JSONArray().apply {
                messages.forEach { msg ->
                    put(JSONObject().apply {
                        put("role", if (msg.role == "assistant") "model" else "user")
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", msg.content) })
                        })
                    })
                }
            })
        }
        return Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
