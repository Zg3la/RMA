package com.motogp.fantasy.data.remote

import com.google.gson.JsonElement
import retrofit2.http.GET
import retrofit2.http.Path

interface MotorsportApi {

    @GET("api/motorsport/search/{term}")
    suspend fun search(@Path("term") term: String): JsonElement

    @GET("api/motorsport/unique-stage/{uniqueStageId}/season")
    suspend fun uniqueStageSeasons(@Path("uniqueStageId") uniqueStageId: Int): JsonElement

    @GET("api/motorsport/stage/{stageId}/substages")
    suspend fun stageSubstages(@Path("stageId") stageId: Int): JsonElement

    @GET("api/motorsport/stage/{stageId}/highlights")
    suspend fun stageHighlights(@Path("stageId") stageId: Int): JsonElement
}
