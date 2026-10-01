package com.example.notificationmonitor.ui

sealed class Screen(val route: String, val label: String) {
    data object Home : Screen("home", "Home")
    data object History : Screen("history", "History")
    data object Apps : Screen("apps", "Apps")
    data object Republish : Screen("republish", "Republish")
    data object Settings : Screen("settings", "Settings")
    data object Detail : Screen("detail/{notificationId}", "Detail") {
        fun createRoute(id: Long) = "detail/$id"
    }
    data object RepublishApp : Screen("republish_app/{packageName}", "App republish") {
        fun createRoute(packageName: String) = "republish_app/${android.net.Uri.encode(packageName)}"
    }
    data object Workflows : Screen("workflows", "Workflows")
    data object WorkflowEdit : Screen("workflow/{workflowId}", "Workflow") {
        fun createRoute(id: Long) = "workflow/$id"
    }

    companion object {
        val bottomNavItems = listOf(Home, History, Apps, Republish, Settings)
    }
}
