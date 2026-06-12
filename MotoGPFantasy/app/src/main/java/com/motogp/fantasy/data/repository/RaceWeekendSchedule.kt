package com.motogp.fantasy.data.repository

import com.motogp.fantasy.data.model.Race
import com.motogp.fantasy.data.model.RaceSession
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object RaceWeekendSchedule {
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun sessionsFor(race: Race): List<RaceSession> {
        val friday = race.sessionDate(daysBeforeRace = 2)
        val saturday = race.sessionDate(daysBeforeRace = 1)

        return listOf(
            RaceSession("Free Practice 1", friday, "10:45", "Free Practice 1"),
            RaceSession("Practice", friday, "15:00", "Practice"),
            RaceSession("Free Practice 2", saturday, "10:10", "Free Practice 2"),
            RaceSession("Q1 (Qualifying 1)", saturday, "10:50", "Q1"),
            RaceSession("Q2 (Qualifying 2)", saturday, "11:15", "Q2"),
            RaceSession("Warm Up", race.date, "09:40", "Warm Up"),
            RaceSession("Race", race.date, "14:00", "Race")
        )
    }

    fun sessionStartAtTrack(race: Race, session: RaceSession): ZonedDateTime? {
        return try {
            ZonedDateTime.of(
                LocalDate.parse(session.date, dateFormatter),
                LocalTime.parse(session.time, timeFormatter),
                trackZone(race)
            )
        } catch (e: Exception) {
            null
        }
    }

    fun sessionStartAtDevice(race: Race, session: RaceSession): ZonedDateTime? {
        return sessionStartAtTrack(race, session)?.withZoneSameInstant(ZoneId.systemDefault())
    }

    fun trackZone(race: Race): ZoneId {
        val name = race.name.lowercase()
        val circuit = race.circuit.lowercase()
        val country = race.country.lowercase()

        val zone = when {
            "motegi" in circuit || country == "japan" -> "Asia/Tokyo"
            "mandalika" in circuit || country == "indonesia" -> "Asia/Makassar"
            country == "malaysia" -> "Asia/Kuala_Lumpur"
            country == "thailand" -> "Asia/Bangkok"
            country == "qatar" -> "Asia/Qatar"
            country == "australia" || "phillip island" in circuit -> "Australia/Melbourne"
            country == "brazil" -> "America/Sao_Paulo"
            country == "united states" || "americas" in name || "austin" in circuit -> "America/Chicago"
            country == "united kingdom" || "great britain" in name || "silverstone" in circuit -> "Europe/London"
            country == "portugal" -> "Europe/Lisbon"
            country == "spain" || "catalonia" in name || "aragon" in name || "valencia" in name -> "Europe/Madrid"
            country == "france" -> "Europe/Paris"
            country == "italy" || country == "san marino" -> "Europe/Rome"
            country == "hungary" -> "Europe/Budapest"
            country == "czechia" -> "Europe/Prague"
            country == "netherlands" -> "Europe/Amsterdam"
            country == "germany" -> "Europe/Berlin"
            country == "austria" -> "Europe/Vienna"
            else -> "UTC"
        }

        return ZoneId.of(zone)
    }

    private fun Race.sessionDate(daysBeforeRace: Long): String {
        return try {
            LocalDate.parse(date, dateFormatter)
                .minusDays(daysBeforeRace)
                .format(dateFormatter)
        } catch (e: Exception) {
            date
        }
    }
}
