package com.rln.forge.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rln.forge.data.ChatEntity
import com.rln.forge.data.ChatRepository
import com.rln.forge.data.MessageEntity
import com.rln.forge.providers.ChatClientFactory
import com.rln.forge.providers.ChatException
import com.rln.forge.providers.ChatMessage
import com.rln.forge.providers.ProviderConfig
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ChatViewModel(
    private val repository: ChatRepository,
    private val clientFactory: ChatClientFactory
) : ViewModel() {

    val chats: Flow<List<ChatEntity>> = repository.observeChats()

    val providers: Flow<List<ProviderConfig>> = repository.observeProviders()

    fun chatFlow(chatId: String): Flow<ChatEntity?> = repository.observeChat(chatId)

    private val _selectedChatId = MutableStateFlow<String?>(null)
    val selectedChatId: StateFlow<String?> = _selectedChatId.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _sendError = MutableStateFlow<String?>(null)
    val sendError: StateFlow<String?> = _sendError.asStateFlow()

    private var streamJob: Job? = null

    init {
        viewModelScope.launch { repository.ensureSeeded() }
    }

    fun messagesFor(chatId: String): Flow<List<MessageEntity>> =
        repository.observeMessages(chatId)

    fun selectChat(chatId: String?) {
        stopStreaming()
        _selectedChatId.value = chatId
        _sendError.value = null
    }

    fun newChat() {
        viewModelScope.launch {
            val providers = repository.observeProviders().first()
            val default = providers.firstOrNull()
            val chat = repository.createChat(default?.id, default?.model.orEmpty())
            _selectedChatId.value = chat.id
        }
    }

    fun renameChat(chat: ChatEntity, title: String) {
        viewModelScope.launch { repository.renameChat(chat, title) }
    }

    fun deleteChat(chatId: String) {
        viewModelScope.launch {
            stopStreaming()
            repository.deleteChat(chatId)
            if (_selectedChatId.value == chatId) _selectedChatId.value = null
        }
    }

    fun updateChatModel(chat: ChatEntity, provider: ProviderConfig, model: String) {
        viewModelScope.launch {
            repository.updateChatModel(chat, provider.id, model.trim())
        }
    }

    fun send(text: String) {
        val prompt = text.trim()
        if (prompt.isEmpty() || _isStreaming.value) return
        val chatId = _selectedChatId.value ?: return
        viewModelScope.launch {
            val chat = repository.observeChat(chatId).first() ?: return@launch
            repository.addMessage(chatId, "user", prompt)
            runGeneration(chat)
        }
    }

    fun retry() {
        val chatId = _selectedChatId.value ?: return
        if (_isStreaming.value) return
        viewModelScope.launch {
            val chat = repository.observeChat(chatId).first() ?: return@launch
            val last = repository.getLastMessage(chatId)
            // Drop only the failed assistant message, then regenerate from history.
            if (last != null && last.role == "assistant" && last.error != null) {
                repository.deleteMessage(last.id)
            }
            runGeneration(chat)
        }
    }

    fun stopStreaming() {
        streamJob?.cancel()
        streamJob = null
        _isStreaming.value = false
    }

    private suspend fun runGeneration(chat: ChatEntity) {
        val chatId = chat.id
        val providers = repository.observeProviders().first()
        val provider = providers.firstOrNull { it.id == chat.providerId }
            ?: providers.firstOrNull()
        if (provider == null) {
            _sendError.value = "No provider configured. Add one in Settings."
            return
        }
        if (chat.model.isBlank()) {
            _sendError.value = "No model set for this chat. Pick a model first."
            return
        }
        if (!clientFactory.hasKey(provider)) {
            _sendError.value = "No API key saved for ${provider.label}. Add it in Settings."
            return
        }
        _sendError.value = null
        val history = repository.observeMessages(chatId).first()
            .filter { it.error == null }
            .map { ChatMessage(it.role, it.content) }
        val assistant = repository.addMessage(chatId, "assistant", "")
        _isStreaming.value = true
        val effectiveConfig = provider.copy(model = chat.model)
        streamJob = viewModelScope.launch {
            val buffer = StringBuilder()
            try {
                clientFactory.streamChat(effectiveConfig, history).collect { delta ->
                    buffer.append(delta)
                    repository.updateMessage(assistant.copy(content = buffer.toString()))
                }
                repository.updateMessage(assistant.copy(content = buffer.toString()))
            } catch (e: Exception) {
                val message = (e as? ChatException)?.message ?: (e.message ?: "Request failed")
                repository.updateMessage(
                    assistant.copy(content = buffer.toString(), error = message)
                )
            } finally {
                _isStreaming.value = false
            }
        }
    }

    class Factory(
        private val repository: ChatRepository,
        private val clientFactory: ChatClientFactory
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ChatViewModel(repository, clientFactory) as T
        }
    }
}
