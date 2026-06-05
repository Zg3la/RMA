package com.motogp.fantasy.ui.common
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.motogp.fantasy.ui.calendar.CalendarScreen
import com.motogp.fantasy.ui.dashboard.DashboardScreen
import com.motogp.fantasy.ui.leagues.LeaguesScreen
import com.motogp.fantasy.ui.navigation.Tab
import com.motogp.fantasy.ui.profile.ProfileScreen
import com.motogp.fantasy.ui.teambuilder.TeamScreen

@Composable
fun LoadingScreen(msg: String = "Loading...") {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator()
            Text(msg, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ErrorScreen(msg: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(msg, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(onSignOut: () -> Unit) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.route
    Scaffold(bottomBar = {
        NavigationBar {
            Tab.all.forEach { tab ->
                NavigationBarItem(
                    selected = current == tab.route,
                    onClick = { nav.navigate(tab.route) { popUpTo(nav.graph.findStartDestination().id) { saveState=true }; launchSingleTop=true; restoreState=true } },
                    icon = { Icon(tab.icon, tab.label) },
                    label = { Text(tab.label) }
                )
            }
        }
    }) { pad ->
        NavHost(nav, Tab.Dashboard.route, Modifier.padding(pad)) {
            composable(Tab.Dashboard.route) { DashboardScreen() }
            composable(Tab.Team.route) { TeamScreen() }
            composable(Tab.Leagues.route) { LeaguesScreen() }
            composable(Tab.Calendar.route) { CalendarScreen() }
            composable(Tab.Profile.route) { ProfileScreen(onSignOut=onSignOut) }
        }
    }
}
