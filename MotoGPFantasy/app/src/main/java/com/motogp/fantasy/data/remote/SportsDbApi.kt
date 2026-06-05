package com.motogp.fantasy.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

interface SportsDbApi {
    @GET("eventsseason.php")
    suspend fun seasonEvents(
        @Query("id") leagueId: String = "4407",
        @Query("s") season: String = "2026"
    ): SportsDbEventsResponse

    @GET("lookupevent.php")
    suspend fun eventDetails(@Query("id") eventId: String): SportsDbEventsResponse

    @GET("searchevents.php")
    suspend fun searchEvents(
        @Query("e") eventName: String,
        @Query("s") season: String = "2026"
    ): SportsDbEventsResponse
}

data class SportsDbEventsResponse(
    @SerializedName("events") val events: List<SportsDbEvent>? = null,
    @SerializedName("event") val event: List<SportsDbEvent>? = null
)

data class SportsDbEvent(
    @SerializedName("idEvent") val id: String?,
    @SerializedName("strEvent") val name: String?,
    @SerializedName("intRound") val round: String?,
    @SerializedName("dateEvent") val date: String?,
    @SerializedName("strVenue") val venue: String?,
    @SerializedName("strCountry") val country: String?,
    @SerializedName("strResult") val result: String?
)
