package com.example.notificationmonitor.ui

sealed class Screen(val route: String, val label: String) {
    data object Home : Screen("home", "Home")
    data object History : Screen("history", "History")
    data object Apps : Screen("apps", "Apps")
    data object Settings : Screen("settings", "Settings")
    data object Detail : Screen("detail/{notificationId}", "Detail") {
        fun createRoute(id: Long) = "detail/$id"
    }

    companion object {
        val bottomNavItems = listOf(Home, History, Apps, Settings)
    }
}
