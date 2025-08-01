package io.github.meatwo310.m2bot

import dev.kord.common.Locale
import dev.kord.common.entity.Snowflake
import dev.kordex.core.ExtensibleBot
import dev.kordex.core.utils.env
import io.github.meatwo310.m2bot.extensions.AdminExtension
import io.github.meatwo310.m2bot.extensions.RoleWatch
import io.github.meatwo310.m2bot.extensions.RoleWatchExtension
import io.github.meatwo310.m2bot.extensions.ai.Ai
import io.github.meatwo310.m2bot.extensions.ai.AiExtension
import io.github.meatwo310.m2bot.extensions.preferences.PreferencesExtension
import io.github.meatwo310.m2bot.extensions.reminder.ReminderExtension
import org.spongepowered.configurate.hocon.HoconConfigurationLoader
import org.spongepowered.configurate.kotlin.extensions.get
import org.spongepowered.configurate.objectmapping.ConfigSerializable
import kotlin.io.path.Path

private val TOKEN = env("TOKEN")

@ConfigSerializable
data class Config(
    val general: General = General(),
    val roleWatch: RoleWatch = RoleWatch(),
    val ai: Ai = Ai(),
)

@ConfigSerializable
data class General(
    val mainServerId: Long = 0L,
)

val loader = HoconConfigurationLoader.builder()
    .path(Path("local/config.conf"))
    .build()!!
val root = loader.load()!!
var config = root.get(Config::class)!!.also {
    loader.save(root)
}

fun Long.toSnowflake() = Snowflake(this)

suspend fun main() {
    val bot = ExtensibleBot(TOKEN) {
        chatCommands {
            defaultPrefix = "m2!"
            enabled = true

            prefix { default -> default }
        }

        extensions {
            add(::PreferencesExtension)

//            add(::TestExtension)
            add(::RoleWatchExtension)
            add(::ReminderExtension)
            add(::AdminExtension)
            add(::AiExtension)
        }

        i18n {
            applicationCommandLocale(Locale.JAPANESE)
            interactionGuildLocaleResolver()
        }
    }

    bot.start()
}
