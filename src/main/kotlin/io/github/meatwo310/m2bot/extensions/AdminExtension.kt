package io.github.meatwo310.m2bot.extensions

import dev.kordex.core.checks.isBotOwner
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.publicSlashCommand
import dev.kordex.core.extensions.slashCommandCheck
import dev.kordex.core.i18n.withContext
import io.github.meatwo310.m2bot.Config
import io.github.meatwo310.m2bot.config
import io.github.meatwo310.m2bot.i18n.Translations
import io.github.meatwo310.m2bot.loader
import io.github.meatwo310.m2bot.root
import org.spongepowered.configurate.kotlin.extensions.get
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

        publicSlashCommand {
            name = Translations.Commands.Reload.name
            description = Translations.Commands.Reload.description

            action {
                root = loader.load()!!
                config = root.get(Config::class)!!
                loader.save(root)

                respond {
                    content = Translations.Commands.Reload.success
                        .withContext(this@action)
                        .translate()
                }
            }
        }
    }
}
