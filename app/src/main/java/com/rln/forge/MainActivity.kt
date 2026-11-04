package com.rln.forge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.rln.forge.chat.ChatListScreen
import com.rln.forge.chat.ChatScreen
import com.rln.forge.chat.ChatViewModel
import com.rln.forge.settings.SettingsScreen
import com.rln.forge.settings.SettingsViewModel
import com.rln.forge.ui.ForgeTheme

/**
 * rlnForge v0.1 — chat + providers.
 * Navigation: chat list -> chat -> providers. Agent mode lands in v0.2.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as ForgeApp
        setContent {
            ForgeTheme {
                val navController = rememberNavController()
                val chatViewModel: ChatViewModel = viewModel(
                    factory = ChatViewModel.Factory(app.repository, app.clientFactory)
                )
                val settingsViewModel: SettingsViewModel = viewModel(
                    factory = SettingsViewModel.Factory(
                        app.repository,
                        app.keyStore,
                        app.clientFactory
                    )
                )
                NavHost(navController = navController, startDestination = "chats") {
                    composable("chats") {
                        ChatListScreen(
                            viewModel = chatViewModel,
                            onOpenChat = { chatId ->
                                chatViewModel.selectChat(chatId)
                                navController.navigate("chat/$chatId")
                            },
                            onOpenSettings = { navController.navigate("settings") }
                        )
                    }
                    composable("chat/{chatId}") { backStackEntry ->
                        val chatId = backStackEntry.arguments?.getString("chatId").orEmpty()
                        ChatScreen(
                            viewModel = chatViewModel,
                            chatId = chatId,
                            onBack = { navController.popBackStack() },
                            onOpenSettings = { navController.navigate("settings") }
                        )
                    }
                    composable("settings") {
                        SettingsScreen(
                            viewModel = settingsViewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
