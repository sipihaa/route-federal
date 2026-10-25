package ru.vladm.routefederal.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import ru.vladm.routefederal.BuildConfig
import ru.vladm.routefederal.data.CityRepository
import ru.vladm.routefederal.data.ScheduleRepository
import ru.vladm.routefederal.data.StationRepository
import ru.vladm.routefederal.data.api.RaspApi
import java.io.File
import javax.inject.Singleton

// Модуль Hilt: «рецепты» объектов, которые Hilt не может создать сам
// (классы библиотек, объекты, которым нужен Context или ключ API, классы с параметрами по умолчанию).
// @Singleton — объект создаётся один раз на всё приложение и раздаётся всем, кто его попросит.
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json { ignoreUnknownKeys = true }

    @Provides
    @Singleton
    fun provideRaspApi(json: Json): RaspApi = Retrofit.Builder()
        .baseUrl(RaspApi.BASE_URL)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(RaspApi::class.java)

    @Provides
    @Singleton
    fun provideCityRepository(): CityRepository = CityRepository()

    @Provides
    @Singleton
    fun provideStationRepository(): StationRepository = StationRepository()

    // Один репозиторий расписания на обе вкладки: «Данные» скачивает, «Главная» читает
    @Provides
    @Singleton
    fun provideScheduleRepository(
        @ApplicationContext context: Context,
        api: RaspApi,
        json: Json,
        stationRepository: StationRepository,
    ): ScheduleRepository = ScheduleRepository(
        dir = File(context.filesDir, "schedule"),
        api = api,
        apiKey = BuildConfig.RASP_API_KEY,
        json = json,
        stations = stationRepository.stations,
    )
}
