package com.rln.forge

import android.app.Application
import com.rln.forge.data.AppDatabase
import com.rln.forge.data.ChatRepository
import com.rln.forge.providers.ChatClientFactory
import com.rln.forge.security.KeyStore

/**
 * Manual dependency container. No DI framework in v0.1 on purpose:
 * one place, easy to read, nothing hidden.
 */
class ForgeApp : Application() {

    lateinit var repository: ChatRepository
        private set

    lateinit var keyStore: KeyStore
        private set

    lateinit var clientFactory: ChatClientFactory
        private set

    override fun onCreate() {
        super.onCreate()
        val db = AppDatabase.getInstance(this)
        keyStore = KeyStore(this)
        clientFactory = ChatClientFactory(keyStore)
        repository = ChatRepository(db)
    }
}
