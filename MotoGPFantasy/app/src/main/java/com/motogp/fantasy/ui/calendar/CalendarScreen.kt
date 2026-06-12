package com.motogp.fantasy.ui.calendar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.motogp.fantasy.data.model.Race
import com.motogp.fantasy.data.model.RoundDetail
import com.motogp.fantasy.data.repository.RaceRepo
import com.motogp.fantasy.data.repository.RaceWeekendSchedule
import com.motogp.fantasy.data.repository.SportsDbResultsRepo
import com.motogp.fantasy.ui.common.LoadingScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

private enum class ScheduleTimeMode {
    LocalTime,
    YourTime
}

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val raceRepo: RaceRepo,
    private val sportsDbResultsRepo: SportsDbResultsRepo
) : ViewModel() {

    data class State(
        val races: List<Race> = emptyList(),
        val raceWinners: Map<Int, String> = emptyMap(),
        val loading: Boolean = true,
        val selectedDetail: RoundDetail? = null
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    private val requestedWinnerRounds = mutableSetOf<Int>()

    init {
        viewModelScope.launch {
            raceRepo.refresh()
            raceRepo.races.collect { races ->
                _state.update { it.copy(races = races, loading = false) }
                loadRaceWinners(races)
            }
        }
    }

    private fun loadRaceWinners(races: List<Race>) {
        races
            .filter { it.status == "finished" && requestedWinnerRounds.add(it.round) }
            .forEach { race ->
                viewModelScope.launch {
                    val winner = sportsDbResultsRepo.resultForRace(race)
                        ?.rows
                        ?.firstOrNull { it.position == 1 }
                        ?.rider
                        .orEmpty()

                    if (winner.isNotBlank()) {
                        _state.update { current ->
                            current.copy(raceWinners = current.raceWinners + (race.round to winner))
                        }
                    }
                }
            }
    }

    fun selectRace(race: Race) {
        _state.update { it.copy(selectedDetail = raceRepo.getRoundDetail(race)) }
    }

    fun getRaceWinner(race: Race): String? {
        return _state.value.raceWinners[race.round]
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
                        RaceCard(race = race, raceWinner = null, onClick = { vm.selectRace(race) })
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
                            raceWinner = vm.getRaceWinner(race),
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
fun RaceCard(race: Race, raceWinner: String?, onClick: () -> Unit) {
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
            if (!raceWinner.isNullOrBlank()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                Column {
                    Text("Race Winner", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text(raceWinner, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                }
            } else if (race.status == "finished") {
                Text("Results pending", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("Tap for full schedule ↓", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoundDetailSheet(detail: RoundDetail) {
    var timeMode by remember { mutableStateOf(ScheduleTimeMode.LocalTime) }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("EEE, dd MMM") }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val selectedZone = when (timeMode) {
        ScheduleTimeMode.YourTime -> ZoneId.systemDefault()
        ScheduleTimeMode.LocalTime -> RaceWeekendSchedule.trackZone(detail.race)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
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

        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = timeMode == ScheduleTimeMode.LocalTime,
                onClick = { timeMode = ScheduleTimeMode.LocalTime },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                label = { Text("Local time") }
            )
            SegmentedButton(
                selected = timeMode == ScheduleTimeMode.YourTime,
                onClick = { timeMode = ScheduleTimeMode.YourTime },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                label = { Text("Your time") }
            )
        }

        Text(selectedZone.id, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        detail.sessions.forEach { session ->
            val start = when (timeMode) {
                ScheduleTimeMode.YourTime -> RaceWeekendSchedule.sessionStartAtDevice(detail.race, session)
                ScheduleTimeMode.LocalTime -> RaceWeekendSchedule.sessionStartAtTrack(detail.race, session)
            }
            val dateText = start?.format(dateFormatter) ?: session.date
            val timeText = start?.format(timeFormatter) ?: session.time
            val (containerColor, contentColor) = when (session.type) {
                "Race"             -> Pair(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                "Q1", "Q2"         -> Pair(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                else               -> Pair(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = containerColor)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(session.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = contentColor)
                        Text(dateText, style = MaterialTheme.typography.bodySmall, color = contentColor.copy(alpha = 0.7f))
                    }
                    Text(timeText, style = MaterialTheme.typography.labelLarge, color = contentColor)
                }
            }
        }
    }
}
