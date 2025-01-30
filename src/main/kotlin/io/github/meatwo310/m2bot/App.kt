package io.github.meatwo310.m2bot

import dev.kord.common.Locale
import dev.kord.common.entity.Snowflake
import dev.kordex.core.ExtensibleBot
import dev.kordex.core.utils.env
import io.github.meatwo310.m2bot.extensions.TestExtension

val TEST_SERVER_ID = Snowflake(
    env("TEST_SERVER").toLong()  // Get the test server ID from the env vars or a .env file
)

private val TOKEN = env("TOKEN")   // Get the bot' token from the env vars or a .env file

suspend fun main() {
    val bot = ExtensibleBot(TOKEN) {
        chatCommands {
            defaultPrefix = "m2!"
            enabled = true

            prefix { default -> default }
        }

        extensions {
            add(::TestExtension)
        }

        i18n {
            applicationCommandLocale(Locale.JAPANESE)
            interactionGuildLocaleResolver()
        }
    }

    bot.start()
}
