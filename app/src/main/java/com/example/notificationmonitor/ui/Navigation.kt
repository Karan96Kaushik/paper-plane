package com.example.notificationmonitor.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.notificationmonitor.notification.LocalNotificationManager
import com.example.notificationmonitor.notification.NotificationRepublisher
import com.example.notificationmonitor.repository.NotificationRepository
import com.example.notificationmonitor.ui.apps.AppsScreen
import com.example.notificationmonitor.ui.history.HistoryScreen
import com.example.notificationmonitor.ui.history.NotificationDetailScreen
import com.example.notificationmonitor.ui.home.HomeScreen
import com.example.notificationmonitor.ui.republish.AppRepublishScreen
import com.example.notificationmonitor.ui.republish.RepublishScreen
import com.example.notificationmonitor.ui.settings.SettingsScreen

@Composable
fun NotificationMonitorApp(
    repository: NotificationRepository,
    localNotificationManager: LocalNotificationManager,
    republisher: NotificationRepublisher,
    pendingNotificationId: Long? = null,
    onPendingNotificationConsumed: () -> Unit = {}
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = Screen.bottomNavItems.any { it.route == currentRoute }

    LaunchedEffect(pendingNotificationId) {
        val id = pendingNotificationId ?: return@LaunchedEffect
        navController.navigate(Screen.Detail.createRoute(id))
        onPendingNotificationConsumed()
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    Screen.bottomNavItems.forEach { screen ->
                        NavigationBarItem(
                            selected = currentRoute == screen.route,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = screen.icon(),
                                    contentDescription = screen.label
                                )
                            },
                            label = { Text(screen.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(repository = repository)
            }
            composable(Screen.History.route) {
                HistoryScreen(
                    repository = repository,
                    republisher = republisher,
                    onOpenDetail = { id ->
                        navController.navigate(Screen.Detail.createRoute(id))
                    }
                )
            }
            composable(Screen.Apps.route) {
                AppsScreen(repository = repository)
            }
            composable(Screen.Republish.route) {
                RepublishScreen(
                    repository = repository,
                    republisher = republisher,
                    onOpenApp = { packageName ->
                        navController.navigate(Screen.RepublishApp.createRoute(packageName))
                    }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    repository = repository,
                    localNotificationManager = localNotificationManager
                )
            }
            composable(
                route = Screen.Detail.route,
                arguments = listOf(navArgument("notificationId") { type = NavType.LongType })
            ) { entry ->
                val id = entry.arguments?.getLong("notificationId") ?: return@composable
                NotificationDetailScreen(
                    notificationId = id,
                    repository = repository,
                    republisher = republisher,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Screen.RepublishApp.route,
                arguments = listOf(navArgument("packageName") { type = NavType.StringType })
            ) { entry ->
                val packageName = entry.arguments?.getString("packageName") ?: return@composable
                AppRepublishScreen(
                    packageName = packageName,
                    repository = repository,
                    republisher = republisher,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

private fun Screen.icon(): ImageVector = when (this) {
    Screen.Home -> Icons.Filled.Home
    Screen.History -> Icons.Filled.History
    Screen.Apps -> Icons.Filled.Apps
    Screen.Republish -> Icons.Filled.Campaign
    Screen.Settings -> Icons.Filled.Settings
    else -> Icons.Filled.Home
}
