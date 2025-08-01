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
import io.github.meatwo310.m2bot.config
import io.github.meatwo310.m2bot.extensions.preferences.PreferencesExtension
import kotlinx.serialization.json.Json
import kotlin.jvm.optionals.getOrDefault
import kotlin.jvm.optionals.getOrNull


class AiExtension : Extension() {
    override val name: String = "ai"

    companion object {
        private val googleApiKey = env("GOOGLE_API_KEY")
        val client = Client.builder()
            .apiKey(googleApiKey)
            .build()!!
        val instruction get() =
            Content.fromParts(Part.fromText(config.ai.instruction))!!
        val tool = Tool.builder()
            .googleSearch(GoogleSearch.builder().build())
            .urlContext(UrlContext.builder().build())
            .codeExecution(ToolCodeExecution.builder().build())
            .build()!!
        val functionsTool = Tool.builder()
            .functions(listOf(
                AiFunctions::class.java.getMethod("getLocalDateTime")
            ))
            .build()!!
        val thinkingConfig = ThinkingConfig.builder()
            .includeThoughts(true)
            .thinkingBudget(-1)
            .build()!!
        val contentConfig = GenerateContentConfig.builder()
            .systemInstruction(instruction)
            .tools(listOf(tool))
            .thinkingConfig(thinkingConfig)
            .maxOutputTokens(800)
            .build()!!

        val searchToolRegex = """concise_search\((?:query=)?"([^"]+)"(?:,.*)?\)""".toRegex()
        val urlContextToolRegex = """browse\((?:urls=)?(\[[^\[]+])(?:,.*)?\)""".toRegex()
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
                            val role = if (message.author?.isSelf ?: return@apply) "model" else "user"
                            add(Content.builder()
                                .role(role)
                                .parts(Part.fromText(message.content))
                                .build()
                            )
                        }
                    }.reversed()
                    val response: GenerateContentResponse = client.models.generateContent(
                        "gemini-2.5-flash",
                        contents,
                        contentConfig
                    ) ?: return@withTyping
                    event.message.reply {
                        content = buildString {
                            val executableCodes = mutableListOf<String>()
                            response.parts()?.forEach { part ->
                                part.executableCode().getOrNull()?.code()?.getOrNull()?.let { code ->
                                    executableCodes.add(code)
                                }
                                part.codeExecutionResult().getOrNull()?.output()?.getOrNull()?.let { output ->
                                    when {
                                        output.startsWith("Looking up information on Google Search.") -> {
                                            searchToolRegex
                                                .findAll(executableCodes.joinToString("\n"))
                                                .map { it.groupValues[1] }
                                                .ifEmpty { sequenceOf("<Unknown>") }
                                                .forEach {
                                                    appendLine("-# \uD83D\uDD0E $it")
                                                }
                                        }
                                        output.startsWith("Browsing the web.") -> {
                                            urlContextToolRegex
                                                .findAll(executableCodes.joinToString("\n"))
                                                .map { Json.decodeFromString<List<String>>(it.groupValues[1]) }
                                                .flatten()
                                                .ifEmpty { sequenceOf("<Unknown>") }
                                                .forEach {
                                                    appendLine("-# \uD83C\uDF10 <$it>")
                                                }
                                        }
                                        else -> {
                                            appendLine("```python")
                                            executableCodes.forEach {  code ->
                                                appendLine(code)
                                                appendLine()
                                            }
                                            appendLine(output
                                                .trimEnd()
                                                .lines()
                                                .joinToString("\n") { line -> "# $line" }
                                            )
                                            appendLine("```")
                                        }
                                    }
                                    executableCodes.clear()
                                }
                                part.text().getOrNull()?.let { text ->
                                    when {
                                        part.thought().getOrDefault(false) -> {
                                            text
                                                .lines()
                                                .filter { line -> line.startsWith("**") }
                                                .forEach { line ->
                                                    appendLine("-# 🧐 ${line.removeSurrounding("**")}")
                                                }
                                        }
                                        else -> {
                                            appendLine()
                                            appendLine(text.trim())
                                        }
                                    }
                                }
                            }
                        }.let {
                            if (it.length > 2000)
                                it.take(1997) + "..."
                            else
                                it
                        }.ifBlank { "レスポンスの生成に失敗" }
                        allowedMentions {}
                    }
                }
            }
        }
    }
}
