package ru.vladm.routefederal.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

// Настройки приложения в DataStore. Сейчас это только API-ключ.
// @Inject constructor: Hilt сам создаст этот класс, передав DataStore из AppModule
class SettingsRepository @Inject constructor(private val dataStore: DataStore<Preferences>) {

    val apiKey: Flow<String?> = dataStore.data.map { it[API_KEY] }

    suspend fun saveApiKey(key: String) {
        dataStore.edit { it[API_KEY] = key }
    }

    private companion object {
        val API_KEY = stringPreferencesKey("api_key")
    }
}
