package io.github.meatwo310.m2bot

import dev.kord.common.Locale
import dev.kord.common.entity.Snowflake
import dev.kordex.core.ExtensibleBot
import dev.kordex.core.utils.env
import io.github.meatwo310.m2bot.extensions.ReminderExtension
import io.github.meatwo310.m2bot.extensions.RoleWatchExtension

private val TOKEN = env("TOKEN")
val MAIN_SERVER_ID = Snowflake(env("MAIN_SERVER").toLong())
val ANNOUNCEMENT_CHANNEL_ID = Snowflake(env("ANNOUNCEMENT_CHANNEL").toLong())

suspend fun main() {
    val bot = ExtensibleBot(TOKEN) {
        chatCommands {
            defaultPrefix = "m2!"
            enabled = true

            prefix { default -> default }
        }

        extensions {
//            add(::TestExtension)
            add(::RoleWatchExtension)
            add(::ReminderExtension)
        }

        i18n {
            applicationCommandLocale(Locale.JAPANESE)
            interactionGuildLocaleResolver()
        }
    }

    bot.start()
}
