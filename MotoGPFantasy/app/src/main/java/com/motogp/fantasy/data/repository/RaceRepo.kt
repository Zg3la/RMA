package com.motogp.fantasy.data.repository

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.motogp.fantasy.data.model.Race
import com.motogp.fantasy.data.model.RaceSession
import com.motogp.fantasy.data.model.RoundDetail
import com.motogp.fantasy.data.remote.MotorsportApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

private const val CURRENT_SEASON = 2026
private const val MOTOGP_UNIQUE_STAGE_ID = 17

private data class FallbackRace(
    val id: String,
    val name: String,
    val circuit: String,
    val country: String,
    val date: String,
    val round: Int
)

private val FALLBACK_CALENDAR_2026 = listOf(
    FallbackRace("2026_01", "Grand Prix of Thailand", "Chang International Circuit", "Thailand", "2026-03-01", 1),
    FallbackRace("2026_02", "Grand Prix of Brazil", "Autodromo Internacional de Goiania - Ayrton Senna", "Brazil", "2026-03-22", 2),
    FallbackRace("2026_03", "Grand Prix of the United States", "Circuit Of The Americas", "United States", "2026-03-29", 3),
    FallbackRace("2026_04", "Grand Prix of Spain", "Circuito de Jerez - Angel Nieto", "Spain", "2026-04-26", 4),
    FallbackRace("2026_05", "Grand Prix de France", "Le Mans", "France", "2026-05-10", 5),
    FallbackRace("2026_06", "Grand Prix of Catalonia", "Circuit de Barcelona-Catalunya", "Spain", "2026-05-17", 6),
    FallbackRace("2026_07", "Grand Prix of Italy", "Autodromo Internazionale del Mugello", "Italy", "2026-05-31", 7),
    FallbackRace("2026_08", "Grand Prix of Hungary", "Balaton Park Circuit", "Hungary", "2026-06-07", 8),
    FallbackRace("2026_09", "Grand Prix of Czechia", "Automotodrom Brno", "Czechia", "2026-06-21", 9),
    FallbackRace("2026_10", "Grand Prix of the Netherlands", "TT Circuit Assen", "Netherlands", "2026-06-28", 10),
    FallbackRace("2026_11", "Grand Prix of Germany", "Sachsenring", "Germany", "2026-07-12", 11),
    FallbackRace("2026_12", "Grand Prix of Great Britain", "Silverstone Circuit", "United Kingdom", "2026-08-09", 12),
    FallbackRace("2026_13", "Grand Prix of Aragon", "MotorLand Aragon", "Spain", "2026-08-30", 13),
    FallbackRace("2026_14", "Grand Prix of San Marino", "Misano World Circuit Marco Simoncelli", "San Marino", "2026-09-13", 14),
    FallbackRace("2026_15", "Grand Prix of Austria", "Red Bull Ring - Spielberg", "Austria", "2026-09-20", 15),
    FallbackRace("2026_16", "Grand Prix of Japan", "Mobility Resort Motegi", "Japan", "2026-10-04", 16),
    FallbackRace("2026_17", "Grand Prix of Indonesia", "Pertamina Mandalika Circuit", "Indonesia", "2026-10-11", 17),
    FallbackRace("2026_18", "Grand Prix of Australia", "Phillip Island", "Australia", "2026-10-25", 18),
    FallbackRace("2026_19", "Grand Prix of Malaysia", "Petronas Sepang International Circuit", "Malaysia", "2026-11-01", 19),
    FallbackRace("2026_20", "Grand Prix of Qatar", "Lusail International Circuit", "Qatar", "2026-11-08", 20),
    FallbackRace("2026_21", "Grand Prix of Portugal", "Autodromo Internacional do Algarve", "Portugal", "2026-11-22", 21),
    FallbackRace("2026_22", "Grand Prix of Valencia", "Circuit Ricardo Tormo", "Spain", "2026-11-29", 22)
)

private fun defaultSessions(race: Race): List<RaceSession> = listOf(
    RaceSession("Free Practice 1", race.date, "09:00", "Practice 1"),
    RaceSession("Free Practice 2", race.date, "13:15", "Practice 2"),
    RaceSession("Sprint Qualifying", race.date, "10:10", "Sprint Qualifying"),
    RaceSession("Sprint Race", race.date, "15:00", "Sprint Race"),
    RaceSession("Qualifying", race.date, "10:15", "Qualifying"),
    RaceSession("Warm Up", race.date, "09:40", "Warm Up"),
    RaceSession("MotoGP Race", race.date, "14:00", "Race")
)

@Singleton
class RaceRepo @Inject constructor(private val api: MotorsportApi) {

    private val _races = MutableStateFlow(fallbackCalendar())
    val races: Flow<List<Race>> = _races.asStateFlow()

    suspend fun refresh() {
        val apiRaces = fetchMotorsportApiRaces()

        if (apiRaces.isNotEmpty()) {
            _races.value = apiRaces.sortedWith(
                compareBy<Race> { it.round.takeIf { round -> round > 0 } ?: Int.MAX_VALUE }
                    .thenBy { it.date }
            )
        }
    }

    private suspend fun fetchMotorsportApiRaces(): List<Race> {
        return try {
            val seasonId = getMotorsportSeasonId() ?: return emptyList()
            api.stageSubstages(seasonId)
                .findObjects("stages")
                .mapIndexedNotNull { index, stage -> stage.asJsonObjectOrNull()?.toRace(index + 1) }
                .filterNot { it.name.contains("test", ignoreCase = true) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun getMotorsportSeasonId(): Int? {
        return api.uniqueStageSeasons(MOTOGP_UNIQUE_STAGE_ID)
            .findObjects("seasons")
            .firstOrNull { season ->
                season.asJsonObjectOrNull()?.stringValue("year") == CURRENT_SEASON.toString()
            }
            ?.asJsonObjectOrNull()
            ?.intValue("id")
    }

    private fun determineStatus(dateStr: String?): String {
        if (dateStr.isNullOrEmpty()) return "upcoming"
        return try {
            val date = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE)
            val today = LocalDate.now()
            when {
                date.isBefore(today) -> "finished"
                date.isEqual(today) -> "live"
                else -> "upcoming"
            }
        } catch (e: Exception) {
            "upcoming"
        }
    }

    private fun normalizeStatus(apiStatus: String?, date: String): String {
        return when (apiStatus?.trim()?.lowercase()) {
            "finished", "completed", "complete", "closed" -> "finished"
            "live", "in-progress", "in progress", "inprogress", "running" -> "live"
            "notstarted", "not-started", "not started", "upcoming", "scheduled", "pending" -> determineStatus(date)
            else -> determineStatus(date)
        }
    }

    private fun fallbackCalendar(): List<Race> {
        return FALLBACK_CALENDAR_2026.map {
            Race(
                id = it.id,
                name = it.name,
                circuit = it.circuit,
                country = it.country,
                date = it.date,
                round = it.round,
                status = determineStatus(it.date)
            )
        }
    }

    private fun JsonObject.toRace(fallbackRound: Int): Race? {
        val date = dateValue().orEmpty()
        val name = stringValue("name", "description").orEmpty()
        if (name.isBlank()) return null

        val info = get("info")?.asJsonObjectOrNull()
        val status = get("status")?.asJsonObjectOrNull()?.stringValue("type", "description")

        return Race(
            id = stringValue("id").orEmpty(),
            name = name,
            circuit = info?.stringValue("circuit").orEmpty(),
            country = nestedStringValue("country", "name") ?: info?.stringValue("circuitCountry").orEmpty(),
            date = date,
            round = info?.intValue("stageRound") ?: fallbackRound,
            status = normalizeStatus(status, date),
            posterUrl = null,
            thumbUrl = null
        )
    }

    private fun JsonObject.dateValue(): String? {
        stringValue("date_end", "dateEnd", "date", "dateEvent", "event_date", "start_date")?.let { return it }
        return longValue("endDateTimestamp", "startDateTimestamp")?.toDateString()
    }

    private fun Long.toDateString(): String {
        return Instant.ofEpochSecond(this)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .format(DateTimeFormatter.ISO_LOCAL_DATE)
    }

    private fun JsonObject.stringValue(vararg keys: String): String? {
        for (key in keys) {
            val value = get(key) ?: continue
            if (!value.isJsonPrimitive) continue

            val primitive = value.asJsonPrimitive
            if (primitive.isString || primitive.isNumber) {
                return primitive.asString.takeIf { it.isNotBlank() }
            }
        }
        return null
    }

    private fun JsonObject.nestedStringValue(parent: String, key: String): String? {
        return get(parent)?.asJsonObjectOrNull()?.stringValue(key)
    }

    private fun JsonObject.intValue(vararg keys: String): Int? {
        for (key in keys) {
            val value = get(key) ?: continue
            if (!value.isJsonPrimitive) continue

            val primitive = value.asJsonPrimitive
            if (primitive.isNumber) return primitive.asInt
            if (primitive.isString) return primitive.asString.toIntOrNull()
        }
        return null
    }

    private fun JsonObject.longValue(vararg keys: String): Long? {
        for (key in keys) {
            val value = get(key) ?: continue
            if (!value.isJsonPrimitive) continue

            val primitive = value.asJsonPrimitive
            if (primitive.isNumber) return primitive.asLong
            if (primitive.isString) return primitive.asString.toLongOrNull()
        }
        return null
    }

    private fun JsonElement.asJsonObjectOrNull(): JsonObject? =
        takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonElement.findObjects(vararg preferredKeys: String): List<JsonElement> {
        return when {
            isJsonArray -> asJsonArray.toList()
            isJsonObject -> {
                val obj = asJsonObject
                val keys = preferredKeys.toList() + listOf("stages", "seasons", "events", "data", "response", "results", "items", "content")
                val directArray = keys.firstNotNullOfOrNull { key -> obj.get(key)?.takeIf { it.isJsonArray } }
                directArray?.asJsonArray?.toList()
                    ?: obj.entrySet().firstNotNullOfOrNull { (_, value) ->
                        value.takeIf { it.isJsonArray }?.asJsonArray?.toList()
                    }
                    ?: listOf(this)
            }
            else -> emptyList()
        }
    }

    fun getRoundDetail(race: Race): RoundDetail = RoundDetail(
        race = race,
        sessions = defaultSessions(race),
        eventId = race.id
    )
}
