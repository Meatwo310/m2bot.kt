package io.github.meatwo310.m2bot.extensions

import dev.kordex.core.checks.isBotOwner
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.publicSlashCommand
import dev.kordex.core.extensions.slashCommandCheck
import dev.kordex.core.i18n.withContext
import io.github.meatwo310.m2bot.i18n.Translations
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.system.exitProcess

class AdminExtension : Extension() {
    override val name = "admin"

    override suspend fun setup() {
        slashCommandCheck {
            isBotOwner()
        }

        publicSlashCommand {
            name = Translations.Commands.Update.name
            description = Translations.Commands.Update.description

            action {
                val process = ProcessBuilder("git", "pull")
                    .redirectErrorStream(true)
                    .start()
                val output = BufferedReader(InputStreamReader(process.inputStream)).readText().trim()
                process.waitFor()

                when {
                    process.exitValue() != 0 -> {
                        respond {
                            content = Translations.Commands.Update.error
                                .withContext(this@action)
                                .translate() + "\n```\n${output}\n```"
                        }
                        return@action
                    }
                    output.endsWith("Already up to date.") -> {
                        respond {
                            content = Translations.Commands.Update.uptodate
                                .withContext(this@action)
                                .translate()
                        }
                        return@action
                    }
                }

                respond {
                    content = "```\n${output}\n```" + Translations.Commands.Update.restart
                        .withContext(this@action)
                        .translate()
                }

                Thread.sleep(3000)
                exitProcess(0)
            }
        }
    }
}
