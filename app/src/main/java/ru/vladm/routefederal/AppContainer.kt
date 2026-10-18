package ru.vladm.routefederal

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import ru.vladm.routefederal.data.CityRepository
import ru.vladm.routefederal.data.ScheduleRepository
import ru.vladm.routefederal.data.SettingsRepository
import ru.vladm.routefederal.data.StationRepository
import ru.vladm.routefederal.data.api.RaspApi
import java.io.File
import java.util.concurrent.TimeUnit

private val Context.dataStore by preferencesDataStore(name = "settings")

// Ручное внедрение зависимостей: все общие объекты создаются здесь один раз
// и раздаются ViewModel-ям. Обе вкладки получают один и тот же ScheduleRepository.
class AppContainer(context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    private val raspApi: RaspApi = Retrofit.Builder()
        .baseUrl(RaspApi.BASE_URL)
        .client(
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
        )
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(RaspApi::class.java)

    val cityRepository = CityRepository()
    val stationRepository = StationRepository()

    val scheduleRepository = ScheduleRepository(
        dir = File(context.filesDir, "schedule"),
        api = raspApi,
        json = json,
        stations = stationRepository.stations,
    )

    val settingsRepository = SettingsRepository(context.dataStore)
}
