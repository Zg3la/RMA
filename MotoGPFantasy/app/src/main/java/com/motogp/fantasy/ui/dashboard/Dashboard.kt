package com.motogp.fantasy.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.motogp.fantasy.data.repository.MainRaceResult
import com.motogp.fantasy.data.model.Race
import com.motogp.fantasy.data.model.User
import com.motogp.fantasy.data.repository.RaceRepo
import com.motogp.fantasy.data.repository.SportsDbResultsRepo
import com.motogp.fantasy.data.repository.UserRepo
import com.motogp.fantasy.ui.common.LoadingScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val raceRepo: RaceRepo,
    private val userRepo: UserRepo,
    private val sportsDbResultsRepo: SportsDbResultsRepo
) : ViewModel() {

    private data class ResultState(
        val selectedRaceName: String? = null,
        val selectedResult: MainRaceResult? = null,
        val resultLoading: Boolean = false,
        val resultError: String? = null
    )

    data class State(
        val user: User? = null,
        val next: Race? = null,
        val past: List<Race> = emptyList(),
        val loading: Boolean = true,
        val selectedRaceName: String? = null,
        val selectedResult: MainRaceResult? = null,
        val resultLoading: Boolean = false,
        val resultError: String? = null
    )

    private val resultState = MutableStateFlow(ResultState())

    val state: StateFlow<State> = combine(
        userRepo.currentUser(),
        raceRepo.races,
        resultState
    ) { user, races, result ->
        val upcoming = races.filter { it.status == "upcoming" }.sortedBy { it.round }
        val past = races.filter { it.status == "finished" }
            .sortedByDescending { it.round }
            .take(5)
        State(
            user = user,
            next = upcoming.firstOrNull(),
            past = past,
            loading = false,
            selectedRaceName = result.selectedRaceName,
            selectedResult = result.selectedResult,
            resultLoading = result.resultLoading,
            resultError = result.resultError
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), State())

    init {
        viewModelScope.launch {
            raceRepo.refresh()
        }
    }

    fun openResult(race: Race) {
        viewModelScope.launch {
            resultState.value = ResultState(selectedRaceName = race.name, resultLoading = true)
            val result = sportsDbResultsRepo.resultForRace(race)
            resultState.value = if (result != null) {
                ResultState(selectedRaceName = race.name, selectedResult = result)
            } else {
                ResultState(
                    selectedRaceName = race.name,
                    resultError = "Main race result is not available yet."
                )
            }
        }
    }

    fun closeResult() {
        resultState.value = ResultState()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(vm: DashboardViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    if (s.loading) { LoadingScreen(); return }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "Welcome back${s.user?.displayName?.let { ", ${it.substringBefore(" ")}" } ?: ""}!",
                style = MaterialTheme.typography.headlineSmall
            )
        }

        s.next?.let { race ->
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Outlined.Flag, null, tint = MaterialTheme.colorScheme.primary)
                            Text("NEXT RACE - Round ${race.round}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        Text(race.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Outlined.LocationOn, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${race.circuit}, ${race.country}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(race.date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        if (s.past.isNotEmpty()) {
            item {
                Text("Recent Results", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            items(s.past) { item ->
                Card(
                    Modifier
                        .fillMaxWidth()
                        .clickable { vm.openResult(item) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(item.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)) {
                                Text("Rd ${item.round}", style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    color = MaterialTheme.colorScheme.outline)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Outlined.EmojiEvents, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Text("Tap for main race results", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    if (s.selectedRaceName != null || s.resultLoading || s.selectedResult != null || s.resultError != null) {
        ModalBottomSheet(onDismissRequest = vm::closeResult) {
            MainRaceResultSheet(
                title = s.selectedRaceName.orEmpty(),
                result = s.selectedResult,
                loading = s.resultLoading,
                error = s.resultError
            )
        }
    }
}

@Composable
private fun MainRaceResultSheet(
    title: String,
    result: MainRaceResult?,
    loading: Boolean,
    error: String?
) {
    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(title.ifBlank { "Main Race Result" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        when {
            loading -> {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Loading main race classification...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            error != null -> {
                Text(error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            result != null -> {
                if (result.date.isNotBlank()) {
                    Text(result.date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                result.rows.forEach { row ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("${row.position}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(28.dp))
                        Column(Modifier.weight(1f)) {
                            Text(row.rider, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(row.team, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(row.gapOrTime, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}
