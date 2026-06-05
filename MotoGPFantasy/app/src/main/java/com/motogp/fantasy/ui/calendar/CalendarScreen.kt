package com.motogp.fantasy.ui.calendar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.motogp.fantasy.data.model.FirestoreResult
import com.motogp.fantasy.data.model.Race
import com.motogp.fantasy.data.model.RoundDetail
import com.motogp.fantasy.data.repository.RaceRepo
import com.motogp.fantasy.data.repository.ResultsRepo
import com.motogp.fantasy.data.repository.RiderRepo
import com.motogp.fantasy.ui.common.LoadingScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val CURRENT_SEASON = 2026

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val raceRepo: RaceRepo,
    private val resultsRepo: ResultsRepo,
    private val riderRepo: RiderRepo
) : ViewModel() {

    data class State(
        val races: List<Race> = emptyList(),
        val results: List<FirestoreResult> = emptyList(),
        val loading: Boolean = true,
        val selectedDetail: RoundDetail? = null
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            raceRepo.refresh()
            combine(raceRepo.races, resultsRepo.results(CURRENT_SEASON), riderRepo.riders()) { races, results, riders ->
                val mappedResults = results.map { r ->
                    r.copy(
                        sprintWinner = riders.find { it.id == r.sprintWinner }?.name ?: r.sprintWinner,
                        sprintSecond = riders.find { it.id == r.sprintSecond }?.name ?: r.sprintSecond,
                        sprintThird = riders.find { it.id == r.sprintThird }?.name ?: r.sprintThird,
                        raceWinner = riders.find { it.id == r.raceWinner }?.name ?: r.raceWinner,
                        raceSecond = riders.find { it.id == r.raceSecond }?.name ?: r.raceSecond,
                        raceThird = riders.find { it.id == r.raceThird }?.name ?: r.raceThird
                    )
                }
                _state.update { it.copy(races = races, results = mappedResults, loading = false) }
            }.collect()
        }
    }

    fun selectRace(race: Race) {
        _state.update { it.copy(selectedDetail = raceRepo.getRoundDetail(race)) }
    }

    fun getResultForRace(race: Race): FirestoreResult? {
        return _state.value.results.find {
            it.raceName.equals(race.name, ignoreCase = true) ||
            race.name.contains(it.raceName, ignoreCase = true) ||
            it.raceName.contains(race.name, ignoreCase = true)
        }
    }

    fun clearDetail() {
        _state.update { it.copy(selectedDetail = null) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(vm: CalendarViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    var showSheet by remember { mutableStateOf(false) }
    var sheetDetail by remember { mutableStateOf<RoundDetail?>(null) }

    LaunchedEffect(s.selectedDetail) {
        if (s.selectedDetail != null) {
            sheetDetail = s.selectedDetail
            showSheet = true
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("2026 MotoGP Calendar") })

        if (s.loading) {
            LoadingScreen()
        } else {
            val finished = s.races.filter { it.status == "finished" }.sortedByDescending { it.round }
            val upcoming = s.races.filter { it.status == "upcoming" }.sortedBy { it.round }

            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (upcoming.isNotEmpty()) {
                    item(key = "header_upcoming") {
                        Text("Upcoming", style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp))
                    }
                    itemsIndexed(upcoming) { _, race ->
                        RaceCard(race = race, result = null, onClick = { vm.selectRace(race) })
                    }
                }
                if (finished.isNotEmpty()) {
                    item(key = "header_results") {
                        Text("Results", style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp))
                    }
                    itemsIndexed(finished) { _, race ->
                        RaceCard(
                            race = race,
                            result = vm.getResultForRace(race),
                            onClick = { vm.selectRace(race) }
                        )
                    }
                }
            }
        }
    }

    if (showSheet && sheetDetail != null) {
        ModalBottomSheet(onDismissRequest = { showSheet = false; vm.clearDetail() }) {
            RoundDetailSheet(detail = sheetDetail!!)
        }
    }
}

@Composable
fun RaceCard(race: Race, result: FirestoreResult?, onClick: () -> Unit) {
    val statusColor = when (race.status) {
        "finished" -> MaterialTheme.colorScheme.outline
        "live"     -> MaterialTheme.colorScheme.error
        else       -> MaterialTheme.colorScheme.primary
    }
    Card(modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        val imageUrl = race.thumbUrl ?: race.posterUrl
        if (!imageUrl.isNullOrEmpty()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = race.name,
                modifier = Modifier.fillMaxWidth().height(140.dp),
                contentScale = ContentScale.Crop
            )
        }
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.width(44.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Rd", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${race.round}", style = MaterialTheme.typography.titleLarge, color = statusColor)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(race.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Icon(Icons.Outlined.LocationOn, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${race.circuit}, ${race.country}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(race.date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(shape = MaterialTheme.shapes.small, color = statusColor.copy(alpha = 0.12f)) {
                    Text(race.status.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall, color = statusColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
            if (result != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (result.sprintWinner.isNotEmpty()) {
                        Column(Modifier.weight(1f)) {
                            Text("Sprint Podium", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                            Text("🥇 ${result.sprintWinner}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                            if (result.sprintSecond.isNotEmpty()) Text("🥈 ${result.sprintSecond}", style = MaterialTheme.typography.bodySmall)
                            if (result.sprintThird.isNotEmpty()) Text("🥉 ${result.sprintThird}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (result.raceWinner.isNotEmpty()) {
                        Column(Modifier.weight(1f)) {
                            Text("Race Podium", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text("🥇 ${result.raceWinner}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                            if (result.raceSecond.isNotEmpty()) Text("🥈 ${result.raceSecond}", style = MaterialTheme.typography.bodySmall)
                            if (result.raceThird.isNotEmpty()) Text("🥉 ${result.raceThird}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            } else if (race.status == "finished") {
                Text("Results pending", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("Tap for full schedule ↓", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun RoundDetailSheet(detail: RoundDetail) {
    val sessionOrder = listOf("Practice 1","Practice 2","Sprint Qualifying","Sprint Race","Qualifying","Warm Up","Race")
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(detail.race.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.LocationOn, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${detail.race.circuit}, ${detail.race.country}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Round ${detail.race.round} · ${detail.race.date}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        Text("Weekend Schedule", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        val sorted = detail.sessions.sortedBy { s -> sessionOrder.indexOf(s.type).let { if (it == -1) 99 else it } }
        sorted.forEach { session ->
            val (containerColor, contentColor) = when (session.type) {
                "Race"             -> Pair(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                "Sprint Race"      -> Pair(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                "Qualifying", "Sprint Qualifying" -> Pair(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                else               -> Pair(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = containerColor)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(session.type, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = contentColor)
                        Text(session.date, style = MaterialTheme.typography.bodySmall, color = contentColor.copy(alpha = 0.7f))
                    }
                    Text(session.time, style = MaterialTheme.typography.labelLarge, color = contentColor)
                }
            }
        }
    }
}
