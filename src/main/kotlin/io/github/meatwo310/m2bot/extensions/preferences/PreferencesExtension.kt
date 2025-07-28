package io.github.meatwo310.m2bot.extensions.preferences

import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.converters.impl.string
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.publicSlashCommand
import dev.kordex.core.i18n.toKey
import dev.kordex.core.utils.suggestStringCollection
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
        // TODO: Switch to enum
        val key by string {
            name = "key".toKey()
            description = "The preference to manage".toKey()

            autoComplete {
                suggestStringCollection(
                    PreferencesData.serializer().descriptor.elementNames
                        .filter { it != "userId" }
                        .toList()
                )
            }
        }
    }
}
