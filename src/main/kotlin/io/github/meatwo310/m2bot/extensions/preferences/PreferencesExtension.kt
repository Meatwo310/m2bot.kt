package io.github.meatwo310.m2bot.extensions.preferences

import dev.kord.core.behavior.UserBehavior
import dev.kord.rest.builder.message.allowedMentions
import dev.kordex.core.checks.isBotOwner
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.application.slash.ephemeralSubCommand
import dev.kordex.core.commands.application.slash.publicSubCommand
import dev.kordex.core.commands.converters.impl.optionalBoolean
import dev.kordex.core.commands.converters.impl.optionalString
import dev.kordex.core.commands.converters.impl.user
import dev.kordex.core.components.components
import dev.kordex.core.components.publicButton
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.publicSlashCommand
import dev.kordex.core.i18n.toKey
import dev.kordex.core.i18n.types.Key
import dev.kordex.core.i18n.withContext
import io.github.meatwo310.m2bot.i18n.Translations

private fun Boolean.toKey() : Key = Translations.Commands.Preferences.let {
    if (this) it.enabled else it.disabled
}

suspend fun UserBehavior?.isEnabledAI(): Boolean = this?.let {
    PreferencesExtension.preferencesStorage.getOrDefault(id).enableAI
} ?: false

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
                        if (newValue == null || newValue == currentPreferences.enableAI) {
                            content = Translations.Commands.Preferences.current
                                .withContext(this@action)
                                .translateNamed(
                                    "feature" to Translations.Commands.Preferences.Ai.name,
                                    "status" to currentPreferences.enableAI.toKey(),
                                )
                        } else if (newValue) {
                            currentPreferences.blockedAIBy.let {
                                if (it.isNotEmpty()) {
                                    content = it
                                    return@respond
                                }
                            }
                            content = """
                                AI機能を利用するには以下に同意してください:
                                - 送信したメッセージはGoogleのGemini APIに送信されます。
                                - メッセージはモデルの学習に使用される可能性があります。
                                """.trimIndent()
                            components {
                                publicButton {
                                    label = "同意".toKey()

                                    check {
                                        failIfNot(event.interaction.user == user)
                                    }

                                    action buttonAction@{
                                        respond {
                                            val updatedPreferences = currentPreferences
                                                .copy(enableAI = newValue)
                                                .also {
                                                    preferencesStorage.set(it)
                                                }
                                            preferencesStorage.set(updatedPreferences)
                                            content = Translations.Commands.Preferences.updated
                                                .withContext(this@buttonAction)
                                                .translateNamed(
                                                    "feature" to Translations.Commands.Preferences.Ai.name,
                                                    "status" to updatedPreferences.enableAI.toKey(),
                                                )
                                        }
                                    }
                                }
                            }
                        } else {
                            respond {
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

            ephemeralSubCommand(::BlockArgs) {
                name = "blockai".toKey()
                description = "AI機能をブロックする".toKey()

                check {
                    isBotOwner()
                }

                action {
                    val currentPrefences = preferencesStorage.getOrDefault(arguments.user.id)
                    val reason = arguments.reason ?: ""
                    val reasonString = reason.ifEmpty { "なし" }
                    if (currentPrefences.blockedAIBy == reason) {
                        respond {
                            content = "AIブロック状態は $reasonString から変更されませんでした"
                        }
                    } else {
                        preferencesStorage.set(currentPrefences.copy(blockedAIBy = reason))
                        respond {
                            content = "AIブロック状態を $reasonString に変更しました"
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

    inner class BlockArgs : Arguments() {
        val user by user {
            name = "user".toKey()
            description = "ブロックするユーザー".toKey()
        }
        val reason by optionalString {
            name = "reason".toKey()
            description = "ブロック理由".toKey()
        }
    }
}
