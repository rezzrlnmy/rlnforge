package com.rln.forge.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rln.forge.data.ChatRepository
import com.rln.forge.providers.ChatClientFactory
import com.rln.forge.providers.ProviderConfig
import com.rln.forge.providers.ProviderType
import com.rln.forge.security.KeyStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface TestState {
    data object Idle : TestState
    data object Testing : TestState
    data class Ok(val message: String) : TestState
    data class Failed(val message: String) : TestState
}

class SettingsViewModel(
    private val repository: ChatRepository,
    private val keyStore: KeyStore,
    private val clientFactory: ChatClientFactory
) : ViewModel() {

    val providers: Flow<List<ProviderConfig>> = repository.observeProviders()

    private val _testStates = MutableStateFlow<Map<String, TestState>>(emptyMap())
    val testStates: StateFlow<Map<String, TestState>> = _testStates.asStateFlow()

    init {
        viewModelScope.launch { repository.ensureSeeded() }
    }

    fun hasKey(config: ProviderConfig): Boolean = keyStore.hasKey(config.id)

    fun saveProvider(config: ProviderConfig, apiKey: String?) {
        viewModelScope.launch {
            repository.saveProvider(config)
            if (apiKey != null) {
                if (apiKey.isBlank()) keyStore.deleteKey(config.id)
                else keyStore.saveKey(config.id, apiKey.trim())
            }
        }
    }

    fun deleteProvider(config: ProviderConfig) {
        viewModelScope.launch {
            keyStore.deleteKey(config.id)
            repository.deleteProvider(config)
        }
    }

    fun testConnection(config: ProviderConfig) {
        viewModelScope.launch {
            _testStates.value = _testStates.value + (config.id to TestState.Testing)
            val result = clientFactory.testConnection(config)
            _testStates.value = _testStates.value + (
                config.id to result.fold(
                    onSuccess = { TestState.Ok(it) },
                    onFailure = { TestState.Failed(it.message ?: "Connection failed") }
                )
            )
        }
    }

    fun newConfig(type: ProviderType): ProviderConfig = ProviderConfig(
        type = type,
        label = type.displayName,
        baseUrl = type.defaultBaseUrl.orEmpty(),
        model = type.defaultModel.orEmpty()
    )

    class Factory(
        private val repository: ChatRepository,
        private val keyStore: KeyStore,
        private val clientFactory: ChatClientFactory
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(repository, keyStore, clientFactory) as T
        }
    }
}
