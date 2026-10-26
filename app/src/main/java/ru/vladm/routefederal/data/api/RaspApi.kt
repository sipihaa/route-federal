package ru.vladm.routefederal.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface RaspApi {

    @GET("v3.0/search/")
    suspend fun search(
        @Header("Authorization") apiKey: String,
        @Query("from") from: String,
        @Query("to") to: String,
        @Query("date") date: String,
        @Query("transport_types") transportTypes: String,
        @Query("offset") offset: Int,
        @Query("limit") limit: Int = 100,
        @Query("transfers") transfers: Boolean = false,
    ): SearchResponse

    companion object {
        const val BASE_URL = "https://api.rasp.yandex-net.ru/"
    }
}


@Serializable
data class SearchResponse(
    val pagination: Pagination,
    val segments: List<Segment>,
)

@Serializable
data class Pagination(val total: Int)

@Serializable
data class Segment(
    val from: StationCode,
    val to: StationCode,
    val departure: String,
    val arrival: String,
    @SerialName("start_date") val startDate: String,
    val thread: Thread,
)

@Serializable
data class StationCode(val code: String)

@Serializable
data class Thread(
    val uid: String,
    val number: String = "",
    val title: String = "",
    @SerialName("transport_type") val transportType: String,
)
