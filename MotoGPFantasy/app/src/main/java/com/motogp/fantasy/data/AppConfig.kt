package com.motogp.fantasy.data

const val CURRENT_SEASON = 2026
const val PREVIOUS_TEAM_SEASON = CURRENT_SEASON - 1

fun teamDocumentId(userId: String, season: Int = CURRENT_SEASON): String = "${userId}_$season"
