package com.motogp.fantasy.data.model

import com.motogp.fantasy.data.CURRENT_SEASON

data class Rider(
    val id: String = "",
    val name: String = "",
    val team: String = "",
    val number: Int = 0,
    val nationality: String = "",
    val imageUrl: String = "",
    val price: Double = 0.0,
    val points: Int = 0
)

data class Race(
    val id: String = "",
    val name: String = "",
    val circuit: String = "",
    val country: String = "",
    val date: String = "",
    val round: Int = 0,
    val status: String = "upcoming",
    val posterUrl: String? = null,
    val thumbUrl: String? = null
)

data class RaceSession(
    val name: String = "",
    val date: String = "",
    val time: String = "",
    val type: String = ""
)

data class RoundDetail(
    val race: Race,
    val sessions: List<RaceSession> = emptyList(),
    val eventId: String = ""
)

data class FirestoreResult(
    val raceName: String = "",
    val round: Int = 0,
    val sprintWinner: String = "",
    val sprintSecond: String = "",
    val sprintThird: String = "",
    val raceWinner: String = "",
    val raceSecond: String = "",
    val raceThird: String = "",
    val season: Int = CURRENT_SEASON,
    val processed: Boolean = false
)

data class User(
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val avatarUrl: String = "",
    val notifyRaceStart: Boolean = true,
    val notifyDeadline: Boolean = true,
    val notifyRivalOvertake: Boolean = true,
    val notifyResults: Boolean = true
)

data class League(
    val id: String = "",
    val name: String = "",
    val code: String = "",
    val isPublic: Boolean = false,
    val memberIds: List<String> = emptyList(),
    val createdBy: String = "",
    val season: Int = CURRENT_SEASON
)

data class LeaderboardEntry(
    val userId: String = "",
    val displayName: String = "",
    val avatarUrl: String = "",
    val totalPoints: Int = 0,
    val currentRank: Int = 0,
    val previousRank: Int = 0,
    val isCurrentUser: Boolean = false
)

data class Constructor(
    val id: String = "",
    val name: String = "",
    val price: Double = 0.0,
    val imageUrl: String = ""
)
