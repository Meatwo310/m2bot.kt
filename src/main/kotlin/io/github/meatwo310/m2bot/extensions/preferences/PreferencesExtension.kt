package io.github.meatwo310.m2bot.extensions.preferences

import dev.kord.rest.builder.message.allowedMentions
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.application.slash.publicSubCommand
import dev.kordex.core.commands.converters.impl.optionalBoolean
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.publicSlashCommand
import dev.kordex.core.i18n.types.Key
import dev.kordex.core.i18n.withContext
import io.github.meatwo310.m2bot.i18n.Translations

private fun Boolean.toKey() : Key {
    return Translations.Commands.Preferences.let {
        if (this) it.enabled else it.disabled
    }
}

private fun Boolean.translate() : String {
    return this.toKey().translate()
}

class PreferencesExtension : Extension() {
    override val name: String = "preferences"
    companion object {
        val preferencesStorage = PreferencesStorage()
    }

    override suspend fun setup() {
        publicSlashCommand {
            name = Translations.Commands.Preferences.name
            description = Translations.Commands.Preferences.description

            publicSubCommand(::BoolArgs) {
                name = Translations.Commands.Preferences.Ai.name
                description = Translations.Commands.Preferences.Ai.description

                action {
                    val newValue = arguments.value
                    val currentPreferences = preferencesStorage.getOrDefault(user.id)

                    respond {
                        allowedMentions {}
                        if (newValue == null) {
                            content = Translations.Commands.Preferences.current
                                .withContext(this@action)
                                .translateNamed(
                                    "feature" to Translations.Commands.Preferences.Ai.name,
                                    "status" to currentPreferences.enableAI.toKey(),
                                )
                        } else {
                            val updatedPreferences = currentPreferences
                                .copy(enableAI = newValue)
                                .also {
                                    preferencesStorage.set(it)
                                }
                            preferencesStorage.set(updatedPreferences)
                            content = Translations.Commands.Preferences.updated
                                .withContext(this@action)
                                .translateNamed(
                                    "feature" to Translations.Commands.Preferences.Ai.name,
                                    "status" to updatedPreferences.enableAI.toKey(),
                                )
                        }
                    }
                }
            }
        }
    }

    inner class BoolArgs : Arguments() {
        val value by optionalBoolean {
            name = Translations.Commands.Preferences.Arguments.name
            description = Translations.Commands.Preferences.Arguments.description
        }
    }
}
