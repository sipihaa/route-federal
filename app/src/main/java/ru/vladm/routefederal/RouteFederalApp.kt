package ru.vladm.routefederal

import android.app.Application
import com.yandex.mapkit.MapKitFactory
import dagger.hilt.android.HiltAndroidApp

// Класс приложения (указан в AndroidManifest.xml). @HiltAndroidApp запускает Hilt:
// по этой аннотации Hilt генерирует код, который создаёт и раздаёт зависимости.
@HiltAndroidApp
class RouteFederalApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Карта Яндекса: ключ из local.properties, инициализация один раз при запуске
        MapKitFactory.setApiKey(BuildConfig.MAPKIT_API_KEY)
        MapKitFactory.initialize(this)
    }
}
