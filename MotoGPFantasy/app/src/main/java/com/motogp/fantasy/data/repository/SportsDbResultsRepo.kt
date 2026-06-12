package com.motogp.fantasy.data.repository

import com.motogp.fantasy.data.CURRENT_SEASON
import com.motogp.fantasy.data.model.Race
import com.motogp.fantasy.data.remote.SportsDbApi
import com.motogp.fantasy.data.remote.SportsDbEvent
import javax.inject.Inject
import javax.inject.Singleton

private const val MOTOGP_LEAGUE_ID = "4407"

data class MainRaceResultRow(
    val position: Int,
    val rider: String,
    val team: String,
    val gapOrTime: String
)

data class MainRaceResult(
    val eventId: String,
    val raceName: String,
    val round: Int,
    val date: String,
    val rows: List<MainRaceResultRow>
)

@Singleton
class SportsDbResultsRepo @Inject constructor(
    private val api: SportsDbApi
) {
    private var mainRaceEvents: List<SportsDbEvent>? = null
    private val resultCache = mutableMapOf<Int, MainRaceResult?>()

    suspend fun resultForRace(race: Race): MainRaceResult? {
        return resultForRound(race.round, searchCandidates(race))
    }

    private suspend fun resultForRound(round: Int, searchCandidates: List<String>): MainRaceResult? {
        if (resultCache.containsKey(round)) return resultCache[round]

        return try {
            val event = seasonEvents().firstOrNull { it.round?.toIntOrNull() == round }
                ?: searchCandidates.firstNotNullOfOrNull { candidate ->
                    api.searchEvents(candidate, CURRENT_SEASON.toString())
                        .allEvents()
                        .firstOrNull { it.isMainRace() && it.round?.toIntOrNull() == round }
                }
                ?: return null.also { resultCache[round] = null }

            val detailed = event.id
                ?.let { id -> api.eventDetails(id).allEvents().firstOrNull() }
                ?: event

            val rows = parseMainRaceRows(detailed.result.orEmpty())
            val result = if (rows.isEmpty()) {
                null
            } else {
                MainRaceResult(
                    eventId = detailed.id.orEmpty(),
                    raceName = detailed.name.orEmpty(),
                    round = detailed.round?.toIntOrNull() ?: round,
                    date = detailed.date.orEmpty(),
                    rows = rows
                )
            }
            resultCache[round] = result
            result
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun seasonEvents(): List<SportsDbEvent> {
        mainRaceEvents?.let { return it }

        val events = api.seasonEvents(MOTOGP_LEAGUE_ID, CURRENT_SEASON.toString()).events.orEmpty()
        return events
            .filter { it.isMainRace() && it.round?.toIntOrNull() != null }
            .sortedBy { it.round?.toIntOrNull() ?: Int.MAX_VALUE }
            .also { mainRaceEvents = it }
    }

    private fun parseMainRaceRows(raw: String): List<MainRaceResultRow> {
        val raceSection = raw
            .substringBefore("----------------------------------------------------")
            .substringBefore("Current Championship Standings")

        return raceSection
            .lineSequence()
            .mapNotNull { line -> parseResultLine(line) }
            .toList()
    }

    private fun parseResultLine(line: String): MainRaceResultRow? {
        val parts = line
            .split("/")
            .map { it.replace("\t", " ").trim() }
            .filter { it.isNotBlank() }

        if (parts.size < 4) return null

        val position = parts[0].split(Regex("\\s+")).firstOrNull()?.toIntOrNull() ?: return null
        val rider = parts.getOrNull(1).orEmpty()
        val team = parts.getOrNull(2).orEmpty()
        val gapOrTime = parts.lastOrNull().orEmpty()

        if (rider.isBlank() || team.isBlank() || gapOrTime.isBlank()) return null

        return MainRaceResultRow(
            position = position,
            rider = rider,
            team = team,
            gapOrTime = gapOrTime
        )
    }

    private fun SportsDbEvent.isMainRace(): Boolean {
        val name = name.orEmpty()
        return name.isNotBlank() &&
            !name.contains("sprint", ignoreCase = true) &&
            !name.contains("qualifying", ignoreCase = true) &&
            !name.contains("test", ignoreCase = true)
    }

    private fun com.motogp.fantasy.data.remote.SportsDbEventsResponse.allEvents(): List<SportsDbEvent> =
        events ?: event ?: emptyList()

    private fun searchCandidates(race: Race): List<String> {
        val name = race.name.lowercase()
        val country = race.country.lowercase()

        val canonical = when {
            "catalonia" in name || "catalunya" in name -> "Catalonia GP"
            "aragon" in name -> "Aragon GP"
            "san marino" in name || "misano" in name -> "San Marino GP"
            "valencia" in name -> "Valencia GP"
            "great britain" in name || "british" in name || "silverstone" in name -> "Great Britain GP"
            "united states" in name || "americas" in name || "austin" in name || country == "united states" -> "USA GP"
            "czech" in name || country == "czechia" -> "Czechia GP"
            "netherlands" in name || "dutch" in name || country == "netherlands" -> "Netherlands GP"
            "italy" in name || "italian" in name || country == "italy" -> "Italy GP"
            "spain" in name || "spanish" in name -> "Spain GP"
            "france" in name || "french" in name || country == "france" -> "France GP"
            race.country.isNotBlank() -> "${race.country} GP"
            else -> race.name
        }

        val cleanedName = race.name
            .replace("Grand Prix of the ", "", ignoreCase = true)
            .replace("Grand Prix of ", "", ignoreCase = true)
            .replace("Grand Prix de ", "", ignoreCase = true)
            .replace("Grand Prix", "GP", ignoreCase = true)
            .trim()

        return listOf(canonical, cleanedName, race.name)
            .filter { it.isNotBlank() }
            .distinct()
    }
}
