package com.motogp.fantasy.ui.leagues

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.motogp.fantasy.data.model.League
import com.motogp.fantasy.data.model.LeaderboardEntry
import com.motogp.fantasy.data.repository.FantasyScoringRepo
import com.motogp.fantasy.data.repository.LeagueRepo
import com.motogp.fantasy.ui.common.LoadingScreen
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LeaguesViewModel @Inject constructor(
    private val repo: LeagueRepo,
    private val scoringRepo: FantasyScoringRepo
) : ViewModel() {

    data class State(
        val mine: List<League> = emptyList(),
        val public: List<League> = emptyList(),
        val selected: League? = null,
        val board: List<LeaderboardEntry> = emptyList(),
        val loading: Boolean = true,
        val boardLoading: Boolean = false,
        val msg: String? = null,
        val err: String? = null,
        val currentUserId: String = ""
    )

    private val uid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    private val _state = MutableStateFlow(State(currentUserId = uid))
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch { repo.fixMyScoreEntries() }
        viewModelScope.launch { scoringRepo.updateCurrentUserScore() }

        viewModelScope.launch {
            combine(repo.myLeagues(), repo.publicLeagues()) { mine, pub ->
                _state.update { it.copy(mine = mine, public = pub, loading = false) }
            }.catch { e ->
                _state.update { it.copy(loading = false, err = e.message) }
            }.collect()
        }
    }

    fun select(league: League) {
        _state.update { it.copy(selected = league, boardLoading = true) }
        viewModelScope.launch {
            repo.leaderboard(league.id)
                .catch { _state.update { s -> s.copy(boardLoading = false) } }
                .collect { entries -> _state.update { it.copy(board = entries, boardLoading = false) } }
        }
    }

    fun create(name: String, pub: Boolean) {
        viewModelScope.launch {
            try {
                val l = repo.createLeague(name, pub)
                _state.update { it.copy(msg = "League created! Code: ${l.code}") }
            } catch (e: Exception) {
                _state.update { it.copy(err = e.message) }
            }
        }
    }

    fun join(code: String) {
        viewModelScope.launch {
            repo.joinLeague(code).fold(
                onSuccess = { l -> _state.update { it.copy(msg = "Joined '${l.name}'!") } },
                onFailure = { e -> _state.update { it.copy(err = e.message) } }
            )
        }
    }

    fun delete(league: League) {
        viewModelScope.launch {
            try {
                repo.deleteLeague(league.id)
                _state.update { it.copy(msg = "League deleted") }
                if (_state.value.selected?.id == league.id) {
                    _state.update { it.copy(selected = null) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(err = e.message) }
            }
        }
    }

    fun clearMsg() { _state.update { it.copy(msg = null, err = null) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaguesScreen(vm: LeaguesViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showCreate by remember { mutableStateOf(false) }
    var showJoin by remember { mutableStateOf(false) }

    LaunchedEffect(s.msg) { s.msg?.let { scope.launch { snack.showSnackbar(it) }; vm.clearMsg() } }
    LaunchedEffect(s.err) { s.err?.let { scope.launch { snack.showSnackbar("Error: $it") }; vm.clearMsg() } }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Leagues") }) },
        snackbarHost = { SnackbarHost(snack) }
    ) { pad ->
        if (s.loading) { LoadingScreen(); return@Scaffold }
        LazyColumn(
            Modifier.padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showCreate = true }, Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Add, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Create")
                    }
                    Button(onClick = { showJoin = true }, Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Login, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Join")
                    }
                }
            }

            if (s.mine.isEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Outlined.EmojiEvents, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            Text("No leagues yet", style = MaterialTheme.typography.titleMedium)
                            Text("Create a league or join with an invite code", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                item { Text("My leagues (${s.mine.size})", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                itemsIndexed(s.mine, key = { _, l -> l.id }) { _, league ->
                    LeagueCard(
                        league = league,
                        currentUserId = s.currentUserId,
                        isSelected = s.selected?.id == league.id,
                        onClick = { vm.select(league) },
                        onDelete = if (league.createdBy == s.currentUserId) { { vm.delete(league) } } else null
                    )
                }
            }

            if (s.selected != null) {
                item { Text("Leaderboard — ${s.selected!!.name}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (s.boardLoading) {
                    item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                } else if (s.board.isEmpty()) {
                    item { Text("No scores yet. Points update automatically when race results are added.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else {
                    itemsIndexed(s.board, key = { _, e -> e.userId }) { i, entry ->
                        LeaderboardRow(rank = i + 1, entry = entry)
                    }
                }
            }

            if (s.public.isNotEmpty()) {
                item { Text("Discover", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                itemsIndexed(s.public.filter { pub -> s.mine.none { it.id == pub.id } }, key = { _, l -> l.id }) { _, league ->
                    LeagueCard(
                        league = league,
                        currentUserId = s.currentUserId,
                        isSelected = false,
                        onClick = { vm.select(league) }
                    )
                }
            }
        }
    }

    if (showCreate) CreateDialog(onDismiss = { showCreate = false }, onCreate = { n, p -> vm.create(n, p); showCreate = false })
    if (showJoin) JoinDialog(onDismiss = { showJoin = false }, onJoin = { c -> vm.join(c); showJoin = false })
}

@Composable
fun LeagueCard(league: League, currentUserId: String, isSelected: Boolean, onClick: () -> Unit, onDelete: (() -> Unit)? = null) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(league.name, style = MaterialTheme.typography.bodyLarge)
                Text("${league.memberIds.size} members · Code: ${league.code}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (league.isPublic) AssistChip(onClick = {}, label = { Text("Public") })
                if (onDelete != null && league.createdBy == currentUserId) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Outlined.Delete, "Delete League", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
fun LeaderboardRow(rank: Int, entry: LeaderboardEntry) {
    Card(modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = if (entry.isCurrentUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("$rank", style = MaterialTheme.typography.titleMedium,
                color = if (rank == 1) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(24.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.displayName, style = MaterialTheme.typography.bodyMedium)
                if (entry.isCurrentUser) Text("You", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Text("${entry.totalPoints} pts", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun CreateDialog(onDismiss: () -> Unit, onCreate: (String, Boolean) -> Unit) {
    var name by remember { mutableStateOf("") }
    var pub by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Create league") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("League name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Public", Modifier.weight(1f)); Switch(checked = pub, onCheckedChange = { pub = it }) }
            }
        },
        confirmButton = { Button(onClick = { if (name.isNotBlank()) onCreate(name, pub) }, enabled = name.isNotBlank()) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun JoinDialog(onDismiss: () -> Unit, onJoin: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Join league") },
        text = { OutlinedTextField(value = code, onValueChange = { code = it.uppercase().take(6) }, label = { Text("Invite code") }, placeholder = { Text("ABC123") }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { Button(onClick = { if (code.length == 6) onJoin(code) }, enabled = code.length == 6) { Text("Join") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
