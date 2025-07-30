package io.github.meatwo310.m2bot.extensions.ai

import com.google.genai.Client
import com.google.genai.types.*
import dev.kord.core.behavior.channel.withTyping
import dev.kord.core.behavior.reply
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.rest.builder.message.allowedMentions
import dev.kordex.core.checks.isNotBot
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.event
import dev.kordex.core.utils.env
import dev.kordex.core.utils.repliedMessageOrNull
import io.github.meatwo310.m2bot.extensions.preferences.PreferencesExtension


class AiExtension : Extension() {
    override val name: String = "AiExtension"

    companion object {
        private val googleApiKey = env("GOOGLE_API_KEY")
        val client = Client.builder()
            .apiKey(googleApiKey)
            .build()
        val instruction = Content.fromParts(Part.fromText(
            "あなたは親切で、礼儀正しいAIアシスタントです。ユーザーの質問に答えてください。"
        ))!!
        var googleSearchTool = Tool.builder()
            .googleSearch(GoogleSearch.builder().build())
            .build()!!
        var config = GenerateContentConfig.builder()
            .systemInstruction(instruction)
            .tools(listOf(googleSearchTool))
            .build()!!

    }

    override suspend fun setup() {
        event<MessageCreateEvent> {
            check {
                isNotBot()
                failIfNot(event.message.mentionedUserIds.contains(kord.selfId))
            }

            action {
                val author = event.message.author ?: return@action
                if (!PreferencesExtension.preferencesStorage.getOrDefault(author.id).enableAI) {
                    return@action
                }
                event.message.channel.withTyping {
                    val contents = mutableListOf(
                        Content.builder()
                            .role("user")
                            .parts(Part.fromText(event.message.content))
                            .build(),
                    ).apply {
                        var message = event.message
                        repeat(10) {
                            message = message.repliedMessageOrNull() ?: return@apply
                            val role = message.author?.let {
                                if (it.isSelf) "model" else "user"
                            } ?: return@apply
                            add(Content.builder()
                                .role(role)
                                .parts(Part.fromText(message.content))
                                .build()
                            )
                        }
                    }.reversed()
                    val response: GenerateContentResponse = client.models.generateContent(
                        "gemini-2.5-flash-lite",
                        contents,
                        config
                    ) ?: return@withTyping
                    event.message.reply {
                        content = response.text() ?: "レスポンスの生成に失敗"
                        allowedMentions {}
                    }
                }

            }
        }
    }
}
