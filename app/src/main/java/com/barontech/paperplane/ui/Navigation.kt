package com.barontech.paperplane.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.barontech.paperplane.notification.CurtisCommand
import com.barontech.paperplane.notification.CurtisDestination
import com.barontech.paperplane.notification.CurtisJson
import com.barontech.paperplane.notification.CurtisLinks
import com.barontech.paperplane.notification.CurtisPayload
import com.barontech.paperplane.notification.LocalNotificationManager
import com.barontech.paperplane.notification.NotificationRepublisher
import com.barontech.paperplane.repository.NotificationRepository
import com.barontech.paperplane.sync.SupabasePublisher
import com.barontech.paperplane.ui.apps.AppsScreen
import com.barontech.paperplane.ui.history.HistoryScreen
import com.barontech.paperplane.ui.history.NotificationDetailScreen
import com.barontech.paperplane.ui.home.HomeScreen
import com.barontech.paperplane.ui.republish.AppRepublishScreen
import com.barontech.paperplane.ui.republish.RepublishScreen
import com.barontech.paperplane.ui.republish.WorkflowEditScreen
import com.barontech.paperplane.ui.republish.WorkflowListScreen
import com.barontech.paperplane.ui.curtis.CurtisDetailScreen
import com.barontech.paperplane.ui.curtis.CurtisTargetScreen
import com.barontech.paperplane.ui.settings.SettingsScreen

@Composable
fun NotificationMonitorApp(
    repository: NotificationRepository,
    localNotificationManager: LocalNotificationManager,
    republisher: NotificationRepublisher,
    supabasePublisher: SupabasePublisher,
    pendingNotificationId: Long? = null,
    onPendingNotificationConsumed: () -> Unit = {},
    pendingCurtis: CurtisPayload? = null,
    curtisLaunchId: Int = 0,
    onPendingCurtisConsumed: () -> Unit = {},
    pendingCurtisCommand: CurtisCommand? = null,
    curtisCommandId: Int = 0,
    onPendingCurtisCommandConsumed: () -> Unit = {},
    onCurtisSnooze: (CurtisPayload, Int) -> Unit = { _, _ -> },
    onCurtisMarkDone: (CurtisPayload) -> Unit = {},
    onCurtisExternal: (String) -> Boolean = { false }
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = Screen.bottomNavItems.any { it.route == currentRoute }

    var detailJson by rememberSaveable {
        mutableStateOf(pendingCurtis?.raw?.let(CurtisJson::encodeStringMap))
    }
    val curtisPayload = detailJson
        ?.let(CurtisJson::decodeStringMap)
        ?.let(CurtisPayload::parse)

    LaunchedEffect(pendingNotificationId) {
        val id = pendingNotificationId ?: return@LaunchedEffect
        navController.navigate(Screen.Detail.createRoute(id))
        onPendingNotificationConsumed()
    }
    LaunchedEffect(curtisLaunchId) {
        val payload = pendingCurtis ?: return@LaunchedEffect
        detailJson = CurtisJson.encodeStringMap(payload.raw)
        navController.navigate(Screen.CurtisDetail.route) {
            launchSingleTop = true
        }
        onPendingCurtisConsumed()
    }
    LaunchedEffect(curtisCommandId) {
        val command = pendingCurtisCommand ?: return@LaunchedEffect
        when (val destination = CurtisLinks.resolve(command.payload, command.action)) {
            is CurtisDestination.InApp -> navController.navigate(destination.route) {
                launchSingleTop = true
            }
            is CurtisDestination.External -> if (!onCurtisExternal(destination.uri)) {
                detailJson = CurtisJson.encodeStringMap(command.payload.raw)
                navController.navigate(Screen.CurtisDetail.route) {
                    launchSingleTop = true
                }
            }
            is CurtisDestination.Unresolved -> {
                detailJson = CurtisJson.encodeStringMap(command.payload.raw)
                navController.navigate(Screen.CurtisDetail.route) {
                    launchSingleTop = true
                }
            }
        }
        onPendingCurtisCommandConsumed()
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
                    },
                    onOpenWorkflows = { navController.navigate(Screen.Workflows.route) }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    repository = repository,
                    localNotificationManager = localNotificationManager,
                    supabasePublisher = supabasePublisher
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
            composable(Screen.Workflows.route) {
                WorkflowListScreen(
                    repository = repository,
                    onBack = { navController.popBackStack() },
                    onCreate = { navController.navigate(Screen.WorkflowEdit.createRoute(0)) },
                    onOpen = { id -> navController.navigate(Screen.WorkflowEdit.createRoute(id)) }
                )
            }
            composable(Screen.CurtisDetail.route) {
                val payload = curtisPayload
                if (payload == null) {
                    CurtisTargetScreen(
                        title = "Notification",
                        id = "This notification is no longer available",
                        onBack = { navController.popBackStack() }
                    )
                } else {
                    CurtisDetailScreen(
                        payload = payload,
                        onBack = { navController.popBackStack() },
                        onSnooze = { minutes -> onCurtisSnooze(payload, minutes) },
                        onMarkDone = { onCurtisMarkDone(payload) },
                        onInApp = { route ->
                            navController.navigate(route) { launchSingleTop = true }
                        },
                        onExternal = onCurtisExternal
                    )
                }
            }
            composable(
                route = Screen.CurtisRun.route,
                arguments = listOf(navArgument("runId") { type = NavType.StringType })
            ) { entry ->
                val runId = entry.arguments?.getString("runId") ?: return@composable
                CurtisTargetScreen(
                    title = "Run",
                    id = runId,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Screen.CurtisChat.route,
                arguments = listOf(navArgument("conversationId") { type = NavType.StringType })
            ) { entry ->
                val conversationId = entry.arguments?.getString("conversationId") ?: return@composable
                CurtisTargetScreen(
                    title = "Chat",
                    id = conversationId,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Screen.WorkflowEdit.route,
                arguments = listOf(navArgument("workflowId") { type = NavType.LongType })
            ) { entry ->
                val id = entry.arguments?.getLong("workflowId") ?: return@composable
                WorkflowEditScreen(
                    workflowId = id,
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
