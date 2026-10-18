package ru.vladm.routefederal.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

// API Яндекс Расписаний: https://yandex.ru/dev/rasp/doc/ru/
interface RaspApi {

    // Рейсы между двумя точками. transfers=false — только прямые рейсы,
    // маршруты с пересадками приложение строит само (офлайн).
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
        @Query("format") format: String = "json",
        @Query("lang") lang: String = "ru_RU",
    ): SearchResponse

    companion object {
        const val BASE_URL = "https://api.rasp.yandex-net.ru/"
    }
}

// Ниже — только те поля ответа, которые нужны приложению (остальные игнорируются)

@Serializable
data class SearchResponse(
    val pagination: Pagination,
    val segments: List<Segment> = emptyList(),
)

@Serializable
data class Pagination(
    val total: Int,
    val limit: Int,
    val offset: Int,
)

@Serializable
data class Segment(
    val from: StationDto? = null,
    val to: StationDto? = null,
    val departure: String? = null,
    val arrival: String? = null,
    @SerialName("start_date") val startDate: String? = null,
    @SerialName("has_transfers") val hasTransfers: Boolean = false,
    val thread: ThreadDto? = null,
)

@Serializable
data class StationDto(
    val code: String,
    val title: String = "",
)

@Serializable
data class ThreadDto(
    val uid: String,
    val number: String = "",
    val title: String = "",
    @SerialName("transport_type") val transportType: String = "",
)
