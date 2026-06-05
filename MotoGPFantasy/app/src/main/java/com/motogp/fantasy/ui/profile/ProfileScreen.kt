package com.motogp.fantasy.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.motogp.fantasy.data.model.User
import com.motogp.fantasy.data.repository.AuthRepo
import com.motogp.fantasy.data.repository.UserRepo
import com.motogp.fantasy.ui.common.LoadingScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userRepo: UserRepo,
    private val authRepo: AuthRepo
) : ViewModel() {
    val user: StateFlow<User?> = userRepo.currentUser()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun updatePref(field: String, v: Boolean) {
        viewModelScope.launch { userRepo.updatePref(field, v) }
    }

    fun signOut(onDone: () -> Unit) {
        viewModelScope.launch { authRepo.signOut(); onDone() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(onSignOut: () -> Unit, vm: ProfileViewModel = hiltViewModel()) {
    val user by vm.user.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(topBar = { TopAppBar(title = { Text("Profile") }) }, snackbarHost = { SnackbarHost(snack) }) { pad ->
        if (user == null) { LoadingScreen(); return@Scaffold }
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

            
            item {
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (user!!.avatarUrl.isNotEmpty()) {
                            AsyncImage(model = user!!.avatarUrl, contentDescription = null, modifier = Modifier.size(64.dp).clip(CircleShape))
                        } else {
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(64.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(user!!.displayName.take(2).uppercase(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                            }
                        }
                        Column {
                            Text(user!!.displayName, style = MaterialTheme.typography.titleLarge)
                            Text(user!!.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            
            item { Text("Notifications", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column {
                        listOf(
                            Triple("Race start alerts", "notifyRaceStart", user!!.notifyRaceStart),
                            Triple("Team lock deadline", "notifyDeadline", user!!.notifyDeadline),
                            Triple("Rival overtakes you", "notifyRivalOvertake", user!!.notifyRivalOvertake),
                            Triple("Race results", "notifyResults", user!!.notifyResults)
                        ).forEachIndexed { i, (label, field, checked) ->
                            Row(Modifier.fillMaxWidth().padding(horizontal=16.dp, vertical=12.dp), verticalAlignment=Alignment.CenterVertically) {
                                Text(label, Modifier.weight(1f), style=MaterialTheme.typography.bodyMedium)
                                Switch(checked=checked, onCheckedChange={ v ->
                                    vm.updatePref(field, v)
                                    scope.launch { snack.showSnackbar("Saved to Firebase") }
                                })
                            }
                            if (i < 3) HorizontalDivider(thickness=0.5.dp)
                        }
                    }
                }
            }

            
            item { Text("Firebase", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
                            Text("Authentication active", style = MaterialTheme.typography.bodyMedium)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
                            Text("Firestore syncing", style = MaterialTheme.typography.bodyMedium)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Outlined.Notifications, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Text("FCM token: ${user!!.fcmToken.take(20)}...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            
            item {
                Button(
                    onClick = { vm.signOut(onSignOut) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
                ) {
                    Icon(Icons.Outlined.Logout, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Sign out")
                }
            }
        }
    }
}
