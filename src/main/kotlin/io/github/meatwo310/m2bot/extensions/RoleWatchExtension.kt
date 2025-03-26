package io.github.meatwo310.m2bot.extensions

import dev.kord.common.entity.*
import dev.kord.core.behavior.channel.asChannelOfOrNull
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.event.guild.GuildAuditLogEntryCreateEvent
import dev.kord.rest.builder.message.allowedMentions
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.event
import io.github.meatwo310.m2bot.ANNOUNCEMENT_CHANNEL_ID
import io.github.meatwo310.m2bot.MAIN_SERVER_ID
import io.github.oshai.kotlinlogging.KotlinLogging

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

                val guild = kord.getGuild(MAIN_SERVER_ID)
                val channel = guild.getChannel(Snowflake(ANNOUNCEMENT_CHANNEL_ID.value))

                channel.asChannelOfOrNull<TextChannel>()?.let {
                    it.createMessage {
                        content = "<@${userid}> が <@&${targetRole.id}> になりました！"
                        allowedMentions {
                            add(AllowedMentionType.UserMentions)
                        }
                    }
                } ?: run {
                    logger.error { "Channel $ANNOUNCEMENT_CHANNEL_ID is not a text-based channel." }
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
