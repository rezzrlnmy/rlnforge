package com.rln.forge.providers

import kotlinx.coroutines.flow.Flow
import java.util.UUID

/** Which wire protocol a provider speaks. */
enum class ApiFamily {
    OPENAI_COMPATIBLE,
    ANTHROPIC,
    GEMINI
}

enum class ProviderType(
    val displayName: String,
    val family: ApiFamily,
    val defaultBaseUrl: String?,
    val defaultModel: String?
) {
    DSN_RELAY(
        "DSN Relay",
        ApiFamily.OPENAI_COMPATIBLE,
        null, // Set when the relay is live; decided by rlnmy, not guessed here.
        null
    ),
    ANTHROPIC(
        "Anthropic",
        ApiFamily.ANTHROPIC,
        "https://api.anthropic.com",
        "claude-sonnet-4-5"
    ),
    OPENAI(
        "OpenAI",
        ApiFamily.OPENAI_COMPATIBLE,
        "https://api.openai.com/v1",
        "gpt-4o-mini"
    ),
    GEMINI(
        "Gemini",
        ApiFamily.GEMINI,
        "https://generativelanguage.googleapis.com",
        "gemini-2.0-flash"
    ),
    OPENROUTER(
        "OpenRouter",
        ApiFamily.OPENAI_COMPATIBLE,
        "https://openrouter.ai/api/v1",
        "openrouter/auto"
    ),
    CUSTOM(
        "Custom",
        ApiFamily.OPENAI_COMPATIBLE,
        "",
        ""
    )
}

/** Provider config without the secret. The API key lives in KeyStore. */
data class ProviderConfig(
    val id: String = UUID.randomUUID().toString(),
    val type: ProviderType,
    val label: String,
    val baseUrl: String,
    val model: String
) {
    /** Effective base URL with trailing slash removed. Empty when not set. */
    fun cleanBaseUrl(): String = baseUrl.trim().trimEnd('/')
}

data class ChatMessage(val role: String, val content: String)

/** HTTP/API failure surfaced to the user verbatim (status + provider body). */
class ChatException(
    message: String,
    val statusCode: Int? = null
) : Exception(message)

interface ChatClient {
    /** Streams response deltas (text chunks) for a chat completion. */
    fun streamChat(
        config: ProviderConfig,
        apiKey: String,
        messages: List<ChatMessage>
    ): Flow<String>

    /** Lightweight check (list models / tiny request). Returns a human summary. */
    suspend fun testConnection(config: ProviderConfig, apiKey: String): Result<String>
}
