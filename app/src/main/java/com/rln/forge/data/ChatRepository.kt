package com.rln.forge.data

import com.rln.forge.providers.ProviderConfig
import com.rln.forge.providers.ProviderType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * Single access point for Room. Providers' non-secret fields live here;
 * API keys live in [com.rln.forge.security.KeyStore], never in this database.
 */
class ChatRepository(private val db: AppDatabase) {

    // ---- Chats ----

    fun observeChats(): Flow<List<ChatEntity>> = db.chatDao().observeChats()

    fun observeChat(id: String): Flow<ChatEntity?> = db.chatDao().observeChat(id)

    fun observeMessages(chatId: String): Flow<List<MessageEntity>> =
        db.messageDao().observeMessages(chatId)

    suspend fun createChat(providerId: String?, model: String): ChatEntity {
        val now = System.currentTimeMillis()
        val chat = ChatEntity(
            id = UUID.randomUUID().toString(),
            title = "New chat",
            providerId = providerId,
            model = model,
            createdAt = now,
            updatedAt = now
        )
        db.chatDao().upsert(chat)
        return chat
    }

    suspend fun renameChat(chat: ChatEntity, title: String) {
        db.chatDao().update(chat.copy(title = title))
    }

    suspend fun updateChatModel(chat: ChatEntity, providerId: String?, model: String) {
        db.chatDao().update(
            chat.copy(providerId = providerId, model = model, updatedAt = System.currentTimeMillis())
        )
    }

    suspend fun deleteChat(chatId: String) {
        db.messageDao().deleteForChat(chatId)
        db.chatDao().deleteChat(chatId)
    }

    suspend fun addMessage(
        chatId: String,
        role: String,
        content: String,
        error: String? = null
    ): MessageEntity {
        val message = MessageEntity(
            id = UUID.randomUUID().toString(),
            chatId = chatId,
            role = role,
            content = content,
            error = error,
            createdAt = System.currentTimeMillis()
        )
        db.messageDao().upsert(message)
        db.chatDao().getChat(chatId)?.let { chat ->
            val title = if (role == "user" && chat.title == "New chat") {
                content.trim().take(60).replace("\n", " ")
            } else {
                chat.title
            }
            db.chatDao().update(chat.copy(title = title, updatedAt = System.currentTimeMillis()))
        }
        return message
    }

    suspend fun updateMessage(message: MessageEntity) {
        db.messageDao().upsert(message)
    }

    suspend fun getLastMessage(chatId: String): MessageEntity? =
        db.messageDao().getLastMessage(chatId)

    suspend fun deleteMessage(messageId: String) {
        db.messageDao().deleteMessage(messageId)
    }

    // ---- Providers ----

    fun observeProviders(): Flow<List<ProviderConfig>> =
        db.providerDao().observeProviders().map { list -> list.map { it.toConfig() } }

    suspend fun getProvider(id: String): ProviderConfig? =
        db.providerDao().getProvider(id)?.toConfig()

    suspend fun saveProvider(config: ProviderConfig) {
        db.providerDao().upsert(config.toEntity())
    }

    suspend fun deleteProvider(config: ProviderConfig) {
        db.providerDao().delete(config.toEntity())
    }

    /**
     * First-run seed: one template per provider type. The first provider in
     * the list (Custom, empty) is the default for new chats — no paid model
     * is ever pre-selected.
     *
     * DSN Relay is PARKED (rezz, 2026-11-04): the enum stays in code, but no
     * template row is seeded and it cannot be picked in the UI.
     */
    suspend fun ensureSeeded() {
        if (db.providerDao().count() > 0) return
        val now = System.currentTimeMillis()
        ProviderType.entries.filter { it != ProviderType.DSN_RELAY }
            .forEachIndexed { index, type ->
            db.providerDao().upsert(
                ProviderEntity(
                    id = UUID.randomUUID().toString(),
                    type = type.name,
                    label = type.displayName,
                    baseUrl = type.defaultBaseUrl.orEmpty(),
                    model = type.defaultModel.orEmpty(),
                    createdAt = now + index
                )
            )
        }
    }

    private fun ProviderEntity.toConfig(): ProviderConfig = ProviderConfig(
        id = id,
        type = ProviderType.valueOf(type),
        label = label,
        baseUrl = baseUrl,
        model = model
    )

    private fun ProviderConfig.toEntity(): ProviderEntity = ProviderEntity(
        id = id,
        type = type.name,
        label = label,
        baseUrl = baseUrl,
        model = model,
        createdAt = System.currentTimeMillis()
    )
}
