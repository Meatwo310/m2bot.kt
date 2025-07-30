package io.github.meatwo310.m2bot.extensions.preferences

import dev.kord.rest.builder.message.allowedMentions
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.application.slash.converters.impl.stringChoice
import dev.kordex.core.commands.application.slash.publicSubCommand
import dev.kordex.core.commands.converters.impl.optionalBoolean
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.publicSlashCommand
import dev.kordex.core.i18n.toKey
import dev.kordex.core.i18n.withContext
import io.github.meatwo310.m2bot.i18n.Translations
import kotlinx.serialization.descriptors.elementNames

class PreferencesExtension : Extension() {
    override val name: String = "preferences"
    companion object {
        val preferencesStorage = PreferencesStorage()
    }

    override suspend fun setup() {
        publicSlashCommand {
            name = Translations.Commands.Preferences.name
            description = Translations.Commands.Preferences.description

            publicSubCommand(::PreferencesBoolArgs) {
                name = Translations.Commands.Preferences.Flag.name
                description = Translations.Commands.Preferences.Flag.description

                action {
                    val key = arguments.key
                    val newValue = arguments.value
                    val currentPreferences = preferencesStorage.getOrDefault(user.id)

                    respond {
                        if (newValue == null) {
                            val currentValue = currentPreferences.getFieldValue<Boolean>(key)
                            content = Translations.Commands.Preferences.print
                                .withContext(this@action)
                                .translateNamed("key" to key, "value" to currentValue.toString())
                        } else {
                            val updatedPreferences = currentPreferences.copyByFieldName(key, newValue)
                            preferencesStorage.set(updatedPreferences)
                            content = Translations.Commands.Preferences.update
                                .withContext(this@action)
                                .translateNamed("key" to key, "value" to newValue.toString())
                        }
                        allowedMentions {}
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
