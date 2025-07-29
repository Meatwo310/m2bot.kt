package io.github.meatwo310.m2bot.extensions.preferences

import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.application.slash.converters.impl.stringChoice
import dev.kordex.core.commands.application.slash.publicSubCommand
import dev.kordex.core.commands.converters.impl.optionalBoolean
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.publicSlashCommand
import dev.kordex.core.i18n.toKey
import kotlinx.serialization.descriptors.elementNames

class PreferencesExtension : Extension() {
    override val name: String = "preferences"
    private val preferencesStorage = PreferencesStorage()

    override suspend fun setup() {
        publicSlashCommand {
            name = "preferences".toKey()
            description = "Manage your preferences".toKey()

            publicSubCommand(::PreferencesBoolArgs) {
                name = "flag".toKey()
                description = "Turn a preference on or off".toKey()

                action {
                    val member = this.member
                    if (member == null) {
                        respond { content = "エラー：コマンドを実行したメンバー情報が見つかりませんでした。" }
                        return@action
                    }

                    val key = arguments.key
                    val newValue = arguments.value
                    val currentPreferences = preferencesStorage.getPreferencesOrDefault(member.id)

                    respond {
                        if (newValue == null) {
                            val currentValue = currentPreferences.getFieldValue<Boolean>(key)
                            content = "設定項目 `${key}` は `${currentValue}` です"
                        } else {
                            val updatedPreferences = currentPreferences.copyByFieldName(key, newValue)
                            preferencesStorage.setPreferences(updatedPreferences)
                            content = "設定項目 `${key}` を `${newValue}` へ更新しました"
                        }
                    }
                }
            }
        }
    }

    inner class PreferencesBoolArgs : Arguments() {
        val key by stringChoice {
            name = "key".toKey()
            description = "The preference to manage".toKey()

            PreferencesData.serializer().descriptor.elementNames
                .filter { it != "userId" }
                .filter { it.startsWith("enable") || it.startsWith("disable") }
                .forEach {
                    choice(it.toKey(), it)
                }
        }

        val value by optionalBoolean {
            name = "value".toKey()
            description = "The value to set for the preference".toKey()
        }
    }
}
