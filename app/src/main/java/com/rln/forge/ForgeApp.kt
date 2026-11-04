package com.rln.forge

import android.app.Application
import android.content.Context
import com.rln.forge.data.AppDatabase
import com.rln.forge.data.ChatRepository
import com.rln.forge.projects.ProjectFileStore
import com.rln.forge.projects.ProjectRepository
import com.rln.forge.providers.ChatClientFactory
import com.rln.forge.security.KeyStore
import java.io.File

/**
 * Manual dependency container. No DI framework on purpose:
 * one place, easy to read, nothing hidden.
 */
class ForgeApp : Application() {

    lateinit var repository: ChatRepository
        private set

    lateinit var projectRepository: ProjectRepository
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
        val fileStore = ProjectFileStore(File(filesDir, "projects"))
        val prefs = getSharedPreferences("rlnforge_prefs", Context.MODE_PRIVATE)
        projectRepository = ProjectRepository(db, fileStore, prefs)
    }
}
