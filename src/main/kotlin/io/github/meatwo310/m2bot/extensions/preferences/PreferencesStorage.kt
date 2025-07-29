package io.github.meatwo310.m2bot.extensions.preferences

import dev.kord.common.entity.Snowflake
import io.github.meatwo310.m2bot.extensions.common.JsonStorage
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.reflect.full.declaredMemberProperties

@Serializable
data class PreferencesData(
    val userId: Snowflake,
    val enableAI: Boolean = false,
)

@Suppress("UNCHECKED_CAST")
inline fun <reified T> PreferencesData.getFieldValue(fieldName: String): T? {
    return try {
        this::class.declaredMemberProperties
            .find { it.name == fieldName }
            ?.call(this) as? T
    } catch (_: Exception) {
        // KClassCastExceptionなどをキャッチ
        null
    }
}


/**
 * 指定されたフィールドの値を変更した新しい PreferencesData インスタンスを返します。
 * この関数はリフレクションを使用し、data classの 'copy' メソッドを動的に呼び出します。
 *
 * @param V 変更する値の型。
 * @param fieldName 変更するフィールドの名前。
 * @param value 新しい値。
 * @return 指定されたフィールドが更新された新しい PreferencesData インスタンス。
 * @throws IllegalArgumentException フィールド名が存在しない場合、または値の型が一致しない場合。
 * @throws UnsupportedOperationException レシーバーが 'copy' メソッドを持たない（data classではない）場合。
 */
fun <V : Any> PreferencesData.copyByFieldName(fieldName: String, value: V): PreferencesData {
    val parameter = this::copy.parameters.find { it.name == fieldName }
        ?: throw IllegalArgumentException("Field '$fieldName' not found in ${this::class.simpleName}.")

    try {
        return this::copy.callBy(mapOf(
            parameter to value,
        ))
    } catch (e: IllegalArgumentException) {
        throw IllegalArgumentException(
            "Type mismatch for field '$fieldName'. Expected '${parameter.type}' but got '${value::class.qualifiedName}'.",
            e
        )
    }
}

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

    suspend fun getPreferencesOrDefault(userId: Snowflake): PreferencesData {
        return getPreferences(userId) ?: PreferencesData(userId)
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
