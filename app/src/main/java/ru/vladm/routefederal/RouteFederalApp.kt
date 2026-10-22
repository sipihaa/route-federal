package ru.vladm.routefederal

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

// Класс приложения (указан в AndroidManifest.xml). @HiltAndroidApp запускает Hilt:
// по этой аннотации Hilt генерирует код, который создаёт и раздаёт зависимости.
@HiltAndroidApp
class RouteFederalApp : Application()
