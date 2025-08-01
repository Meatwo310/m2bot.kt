package io.github.meatwo310.m2bot.extensions

import dev.kord.common.entity.AllowedMentionType
import dev.kord.common.entity.AuditLogChange
import dev.kord.common.entity.AuditLogEvent
import dev.kord.common.entity.DiscordPartialRole
import dev.kord.core.behavior.channel.asChannelOfOrNull
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.entity.channel.GuildMessageChannel
import dev.kord.core.event.guild.GuildAuditLogEntryCreateEvent
import dev.kord.rest.builder.message.allowedMentions
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.event
import io.github.meatwo310.m2bot.config
import io.github.meatwo310.m2bot.toSnowflake
import io.github.oshai.kotlinlogging.KotlinLogging
import org.spongepowered.configurate.objectmapping.ConfigSerializable

@ConfigSerializable
data class RoleWatch(
    val announcementChannelId: Long = 0L,
)

class RoleWatchExtension : Extension() {
    override val name = "rolewatch"
    val logger = KotlinLogging.logger {}

    override suspend fun setup() {
        event<GuildAuditLogEntryCreateEvent> {
            action {
                if (event.auditLogEntry.actionType != AuditLogEvent.MemberRoleUpdate) return@action

                val userid = event.auditLogEntry.targetId
                val oldRoles = extractRoles(event.auditLogEntry.changes, "\$remove")
                val oldRolesMapped = oldRoles.map { it.name.value ?: "?" }
                val newRoles = extractRoles(event.auditLogEntry.changes, "\$add")
                val newRolesMapped = newRoles.map { it.name.value ?: "?" }

                logger.info {
                    "User $userid: Added $newRolesMapped, Removed $oldRolesMapped"
                }

                val targetRoles = newRoles
                    .filter { it.name.value?.matches("^.{1,2}髪$".toRegex()) == true }
                if (targetRoles.isEmpty()) return@action
                if (targetRoles.size > 1) {
                    logger.warn { "More than one role matched for user $userid: $targetRoles" }
                }
                val targetRole = targetRoles.first()

                val guild = kord.getGuild(config.general.mainServerId.toSnowflake())
                val channel = guild.getChannel(config.roleWatch.announcementChannelId.toSnowflake())

                channel.asChannelOfOrNull<GuildMessageChannel>()?.let {
                    it.createMessage {
                        content = "<@${userid}> が <@&${targetRole.id}> になりました！"
                        allowedMentions {
                            add(AllowedMentionType.UserMentions)
                        }
                    }
                } ?: run {
                    logger.error { "Channel ${config.roleWatch.announcementChannelId} is not a text-based channel." }
                    return@action
                }
            }
        }
    }

    private fun extractRoles(changes: List<AuditLogChange<*>>, key: String): List<DiscordPartialRole> {
        return changes
            .filter { it.key.name == key }
            .flatMap { it.new as? List<*> ?: emptyList() }
            .filterIsInstance<DiscordPartialRole>()
    }
}
