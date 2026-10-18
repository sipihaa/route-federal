package ru.vladm.routefederal

import android.app.Application

// Создаётся системой один раз при запуске приложения (указан в AndroidManifest.xml)
class RouteFederalApp : Application() {
    val container by lazy { AppContainer(this) }
}
