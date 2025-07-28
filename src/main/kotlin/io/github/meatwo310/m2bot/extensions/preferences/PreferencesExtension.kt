package io.github.meatwo310.m2bot.extensions.preferences

import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.application.slash.converters.impl.stringChoice
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.publicSlashCommand
import dev.kordex.core.i18n.toKey
import kotlinx.serialization.descriptors.elementNames

class PreferencesExtension : Extension() {
    override val name: String = "preferences"
    private val preferencesStorage = PreferencesStorage()

    override suspend fun setup() {
        publicSlashCommand(::PreferencesCommandArgs) {
            name = "preferences".toKey()
            description = "Manage your preferences".toKey()

            action {
                respond {
                    content = "Testing"
                }
            }
        }
    }

    inner class PreferencesCommandArgs : Arguments() {
        val key by stringChoice {
            name = "key".toKey()
            description = "The preference to manage".toKey()

            PreferencesData.serializer().descriptor.elementNames
                .filter { it != "userId" }
                .forEach {
                    choice(it.toKey(), it)
                }
        }
    }
}
