package com.rln.forge

import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import com.rln.forge.chat.ChatListScreen
import com.rln.forge.chat.ChatScreen
import com.rln.forge.chat.ChatViewModel
import com.rln.forge.projects.EditorScreen
import com.rln.forge.projects.ProjectListScreen
import com.rln.forge.projects.ProjectViewModel
import com.rln.forge.settings.SettingsScreen
import com.rln.forge.settings.SettingsViewModel
import com.rln.forge.ui.ForgeTheme

/**
 * rlnForge v0.2 — chats, projects (code editor), settings.
 * Bottom nav on top-level destinations; chat detail and the editor are
 * full screens without the bottom bar.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as ForgeApp
        setContent {
            ForgeTheme {
                val navController = rememberNavController()
                val chatViewModel: ChatViewModel = viewModel(
                    factory = ChatViewModel.Factory(
                        app.repository,
                        app.clientFactory,
                        app.projectRepository
                    )
                )
                val settingsViewModel: SettingsViewModel = viewModel(
                    factory = SettingsViewModel.Factory(
                        app.repository,
                        app.keyStore,
                        app.clientFactory
                    )
                )
                val projectViewModel: ProjectViewModel = viewModel(
                    factory = ProjectViewModel.Factory(app.projectRepository)
                )
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = backStackEntry?.destination?.route

                Scaffold(
                    bottomBar = {
                        if (currentRoute in TOP_LEVEL_ROUTES) {
                            NavigationBar {
                                TOP_LEVEL_ROUTES.forEach { route ->
                                    NavigationBarItem(
                                        selected = currentRoute == route,
                                        onClick = {
                                            navController.navigate(route) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        icon = { Text(topLevelLabel(route)) },
                                        label = { Text(topLevelLabel(route)) }
                                    )
                                }
                            }
                        }
                    }
                ) { padding ->
                    NavHost(
                        navController = navController,
                        startDestination = "chats",
                        modifier = androidx.compose.ui.Modifier.padding(padding)
                    ) {
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
                        composable("chat/{chatId}") { entry ->
                            val chatId = entry.arguments?.getString("chatId").orEmpty()
                            ChatScreen(
                                viewModel = chatViewModel,
                                projectViewModel = projectViewModel,
                                chatId = chatId,
                                onBack = { navController.popBackStack() },
                                onOpenSettings = { navController.navigate("settings") }
                            )
                        }
                        composable("projects") {
                            ProjectListScreen(
                                viewModel = projectViewModel,
                                onOpenEditor = { projectId, path ->
                                    navController.navigate(
                                        "editor/$projectId/${encodePath(path)}"
                                    )
                                }
                            )
                        }
                        composable("editor/{projectId}/{encodedPath}") { entry ->
                            val projectId = entry.arguments?.getString("projectId").orEmpty()
                            val path = decodePath(
                                entry.arguments?.getString("encodedPath").orEmpty()
                            )
                            EditorScreen(
                                viewModel = projectViewModel,
                                projectId = projectId,
                                path = path,
                                onBack = {
                                    projectViewModel.closeFile {
                                        navController.popBackStack()
                                    }
                                }
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

    companion object {
        private val TOP_LEVEL_ROUTES = listOf("chats", "projects", "settings")

        private fun topLevelLabel(route: String): String = when (route) {
            "chats" -> "Chats"
            "projects" -> "Projects"
            "settings" -> "Settings"
            else -> route
        }

        /** URL-safe Base64 so slashes in a file path never break the route. */
        fun encodePath(path: String): String =
            Base64.encodeToString(
                path.toByteArray(Charsets.UTF_8),
                Base64.URL_SAFE or Base64.NO_WRAP
            )

        fun decodePath(encoded: String): String = try {
            String(
                Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_WRAP),
                Charsets.UTF_8
            )
        } catch (e: IllegalArgumentException) {
            ""
        }
    }
}
