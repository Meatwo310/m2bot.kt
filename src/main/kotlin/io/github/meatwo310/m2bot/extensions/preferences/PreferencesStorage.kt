package io.github.meatwo310.m2bot.extensions.preferences

import dev.kord.common.entity.Snowflake
import io.github.meatwo310.m2bot.extensions.common.JsonStorage
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class PreferencesData(
    val userId: Snowflake,
    val enableAI: Boolean = false,
)

class PreferencesStorage : JsonStorage<PreferencesData>() {
    override val storageFile get() = File("local/preferences.json")
    override val errorMessageLoad = "設定データの読み込みに失敗しました"
    override val errorMessageSave = "設定データの保存に失敗しました"

    override fun decodeFromString(content: String): List<PreferencesData> {
        return Json.decodeFromString(content)
    }

    override fun encodeToString(data: List<PreferencesData>): String {
        val json = Json { prettyPrint = true }
        return json.encodeToString(data)
    }

    suspend fun getPreferences(userId: Snowflake): PreferencesData? {
        return readDataLock { data ->
            data.find { it.userId == userId }
        }
    }

    suspend fun setPreferences(preferences: PreferencesData) {
        withDataLock { data ->
            val index = data.indexOfFirst { it.userId == preferences.userId }
            if (index >= 0) {
                data[index] = preferences
            } else {
                data.add(preferences)
            }
        }
    }

    suspend fun removePreferences(userId: Snowflake) {
        withDataLock { data ->
            data.removeAll { it.userId == userId }
        }
    }
}
