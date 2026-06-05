package com.motogp.fantasy.ui.teambuilder

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.motogp.fantasy.data.model.Constructor
import com.motogp.fantasy.data.model.Rider
import com.motogp.fantasy.data.repository.ConstructorRepo
import com.motogp.fantasy.data.repository.FantasyScoringRepo
import com.motogp.fantasy.data.repository.RaceRepo
import com.motogp.fantasy.data.repository.RiderRepo
import com.motogp.fantasy.data.repository.TeamRepo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

private const val BUDGET = 100.0
private const val MAX_RIDERS = 5

@HiltViewModel
class TeamViewModel @Inject constructor(
    private val riderRepo: RiderRepo,
    private val constructorRepo: ConstructorRepo,
    private val teamRepo: TeamRepo,
    private val raceRepo: RaceRepo,
    private val scoringRepo: FantasyScoringRepo
) : ViewModel() {

    data class State(
        val riders: List<Rider> = emptyList(),
        val constructors: List<Constructor> = emptyList(),
        val selectedRiderIds: Set<String> = emptySet(),
        val selectedConstructorId: String? = null,
        val saving: Boolean = false,
        val saved: Boolean = false,
        val tab: Int = 0,
        val teamLocked: Boolean = false
    ) {
        val riderBudget get() = riders.filter { it.id in selectedRiderIds }.sumOf { it.price }
        val constructorBudget get() = constructors.find { it.id == selectedConstructorId }?.price ?: 0.0
        val totalUsed get() = riderBudget + constructorBudget
        val remaining get() = BUDGET - totalUsed
        val canAddRider get() = selectedRiderIds.size < MAX_RIDERS
    }

    private val _selRiders = MutableStateFlow<Set<String>>(emptySet())
    private val _selConstructor = MutableStateFlow<String?>(null)
    private val _saving = MutableStateFlow(false)
    private val _saved = MutableStateFlow(false)
    private val _tab = MutableStateFlow(0)

    private val _teamLocked = raceRepo.races.map { races ->
        val today = LocalDate.now()
        val nextRace = races.firstOrNull { it.status == "upcoming" || it.status == "live" }
        var isLocked = false
        if (nextRace != null) {
            try {
                val raceDate = LocalDate.parse(nextRace.date, DateTimeFormatter.ISO_LOCAL_DATE)
                if (!today.isBefore(raceDate.minusDays(2)) && !today.isAfter(raceDate)) {
                    isLocked = true
                }
            } catch (e: Exception) {}
        }
        isLocked
    }

    val state: StateFlow<State> = combine(
        riderRepo.riders(),
        constructorRepo.constructors(),
        _selRiders,
        _selConstructor,
        combine(_tab, _teamLocked, ::Pair)
    ) { riders, constructors, selR, selC, tabAndLocked ->
        State(
            riders = riders,
            constructors = constructors,
            selectedRiderIds = selR,
            selectedConstructorId = selC,
            tab = tabAndLocked.first,
            teamLocked = tabAndLocked.second
        )
    }.combine(_saving) { s, saving -> s.copy(saving = saving) }
     .combine(_saved) { s, saved -> s.copy(saved = saved) }
     .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), State())

    init {
        viewModelScope.launch {
            teamRepo.myTeam().collect { team ->
                team?.let {
                    _selRiders.value = it.riderIds.toSet()
                    _selConstructor.value = it.constructorId
                }
            }
        }
    }

    fun setTab(t: Int) { _tab.value = t }

    fun toggleRider(rider: Rider) {
        val cur = _selRiders.value
        if (rider.id in cur) { _selRiders.value = cur - rider.id; return }
        val s = state.value
        if (!s.canAddRider || s.totalUsed + rider.price > BUDGET) return
        _selRiders.value = cur + rider.id
    }

    fun selectConstructor(c: Constructor) {
        if (_selConstructor.value == c.id) { _selConstructor.value = null; return }
        val s = state.value
        if (s.riderBudget + c.price > BUDGET) return
        _selConstructor.value = c.id
    }

    fun save() {
        viewModelScope.launch {
            _saving.value = true
            teamRepo.saveTeam(
                riderIds = _selRiders.value.toList(),
                budget = state.value.totalUsed,
                constructorId = _selConstructor.value
            )
            scoringRepo.updateCurrentUserScore()
            _saving.value = false
            _saved.value = true
        }
    }

    fun clearSaved() { _saved.value = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamScreen(vm: TeamViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(s.saved) {
        if (s.saved) {
            scope.launch { snack.showSnackbar("Team saved to Firebase!") }
            vm.clearSaved()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("My Team") }) },
        snackbarHost = { SnackbarHost(snack) }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {

            if (s.teamLocked) {
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Lock, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(Modifier.width(8.dp))
                        Text("Team is locked for the race weekend.", color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }

            
            Card(Modifier.fillMaxWidth().padding(12.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Budget left", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "${"%.1f".format(s.remaining)}M",
                            style = MaterialTheme.typography.titleLarge,
                            color = if (s.remaining < 10) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Riders", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${s.selectedRiderIds.size}/$MAX_RIDERS", style = MaterialTheme.typography.titleLarge)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Constructor", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            if (s.selectedConstructorId != null) "✓" else "—",
                            style = MaterialTheme.typography.titleLarge,
                            color = if (s.selectedConstructorId != null) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = { vm.save() },
                        enabled = s.selectedRiderIds.isNotEmpty() && !s.saving && !s.teamLocked
                    ) {
                        if (s.saving) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        else Text("Save")
                    }
                }
            }

            
            TabRow(selectedTabIndex = s.tab) {
                Tab(selected = s.tab == 0, onClick = { vm.setTab(0) }, text = { Text("Riders (${s.riders.size})") })
                Tab(selected = s.tab == 1, onClick = { vm.setTab(1) }, text = { Text("Constructors (${s.constructors.size})") })
            }

            when (s.tab) {
                0 -> {
                    if (s.riders.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(s.riders, key = { it.id }) { rider ->
                                val sel = rider.id in s.selectedRiderIds
                                RiderCard(
                                    rider = rider,
                                    isSelected = sel,
                                    canSelect = s.canAddRider || sel,
                                    onToggle = { vm.toggleRider(rider) }
                                )
                            }
                        }
                    }
                }
                1 -> {
                    if (s.constructors.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(s.constructors, key = { it.id }) { c ->
                                ConstructorCard(
                                    constructor = c,
                                    isSelected = s.selectedConstructorId == c.id,
                                    onToggle = { vm.selectConstructor(c) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RiderCard(rider: Rider, isSelected: Boolean, canSelect: Boolean, onToggle: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { if (canSelect || isSelected) onToggle() },
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSelected -> MaterialTheme.colorScheme.primaryContainer
                !canSelect -> MaterialTheme.colorScheme.surfaceVariant
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (rider.imageUrl.isNotEmpty()) {
                AsyncImage(
                    model = rider.imageUrl,
                    contentDescription = rider.name,
                    modifier = Modifier.size(52.dp)
                )
            } else {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("#${rider.number}", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            Column(Modifier.weight(1f)) {
                Text(rider.name, style = MaterialTheme.typography.bodyMedium)
                Text(rider.team, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(rider.nationality, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("${rider.price}M", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            if (isSelected) Icon(Icons.Outlined.Check, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun ConstructorCard(constructor: Constructor, isSelected: Boolean, onToggle: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onToggle,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                             else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (constructor.imageUrl.isNotEmpty()) {
                AsyncImage(
                    model = constructor.imageUrl,
                    contentDescription = constructor.name,
                    modifier = Modifier.size(52.dp)
                )
            }
            Column(Modifier.weight(1f)) {
                Text(constructor.name, style = MaterialTheme.typography.bodyMedium)
                Text("Constructor · ${constructor.price}M", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("${constructor.price}M", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            if (isSelected) Icon(Icons.Outlined.Check, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}
