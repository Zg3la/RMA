package com.motogp.fantasy.ui.navigation
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.*
import com.motogp.fantasy.ui.auth.AuthScreen
import com.motogp.fantasy.ui.auth.AuthViewModel
import com.motogp.fantasy.ui.common.MainScaffold

sealed class Screen(val route: String) {
    object Auth : Screen("auth")
    object Main : Screen("main")
}

sealed class Tab(val route: String, val label: String, val icon: ImageVector) {
    object Dashboard : Tab("dashboard", "Dashboard", Icons.Outlined.Dashboard)
    object Team : Tab("team", "My Team", Icons.Outlined.Group)
    object Leagues : Tab("leagues", "Leagues", Icons.Outlined.EmojiEvents)
    object Calendar : Tab("calendar", "Calendar", Icons.Outlined.CalendarMonth)
    object Profile : Tab("profile", "Profile", Icons.Outlined.Person)
    companion object { val all = listOf(Dashboard, Team, Leagues, Calendar, Profile) }
}

@Composable
fun AppNavHost() {
    val nav = rememberNavController()
    val authVm: AuthViewModel = hiltViewModel()
    val loggedIn by authVm.loggedIn.collectAsStateWithLifecycle()
    NavHost(nav, if (loggedIn) Screen.Main.route else Screen.Auth.route) {
        composable(Screen.Auth.route) {
            AuthScreen(onSuccess = { nav.navigate(Screen.Main.route) { popUpTo(Screen.Auth.route) { inclusive=true } } })
        }
        composable(Screen.Main.route) {
            MainScaffold(onSignOut = { nav.navigate(Screen.Auth.route) { popUpTo(Screen.Main.route) { inclusive=true } } })
        }
    }
}
