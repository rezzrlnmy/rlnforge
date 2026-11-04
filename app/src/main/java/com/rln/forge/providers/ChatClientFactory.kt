package com.rln.forge.providers

import com.rln.forge.security.KeyStore
import kotlinx.coroutines.flow.Flow

/**
 * Builds the right client for a provider config and hands it the stored key.
 * Key lookup happens here and nowhere else — UI code never touches key values.
 */
class ChatClientFactory(
    private val keyStore: KeyStore,
    private val openAiClient: ChatClient = OpenAiCompatibleClient(),
    private val anthropicClient: ChatClient = AnthropicClient(),
    private val geminiClient: ChatClient = GeminiClient()
) {

    fun hasKey(config: ProviderConfig): Boolean = keyStore.hasKey(config.id)

    fun streamChat(config: ProviderConfig, messages: List<ChatMessage>): Flow<String> {
        val apiKey = keyStore.getKey(config.id)
            ?: throw ChatException("No API key saved for ${config.label}")
        return clientFor(config).streamChat(config, apiKey, messages)
    }

    suspend fun testConnection(config: ProviderConfig): Result<String> {
        val apiKey = keyStore.getKey(config.id)
            ?: return Result.failure(ChatException("No API key saved for ${config.label}"))
        return clientFor(config).testConnection(config, apiKey)
    }

    private fun clientFor(config: ProviderConfig): ChatClient = when (config.type.family) {
        ApiFamily.OPENAI_COMPATIBLE -> openAiClient
        ApiFamily.ANTHROPIC -> anthropicClient
        ApiFamily.GEMINI -> geminiClient
    }
}
