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
        val instruction = Content.fromParts(Part.fromText(
            "あなたは親切でフレンドリーなAIアシスタントです。ユーザーの質問へ簡潔に答えてください。指示なき限り、LaTeXを使用せずに、日本語で回答してください。"
        ))!!
        val googleSearchTool = Tool.builder()
            .googleSearch(GoogleSearch.builder().build())
            .urlContext(UrlContext.builder().build())
            .codeExecution(ToolCodeExecution.builder().build())
            .build()!!
        val thinkingConfig = ThinkingConfig.builder()
            .includeThoughts(true)
            .thinkingBudget(-1)
            .build()!!
        val config = GenerateContentConfig.builder()
            .systemInstruction(instruction)
            .tools(listOf(googleSearchTool))
            .thinkingConfig(thinkingConfig)
            .maxOutputTokens(1024)
            .build()!!

        val searchToolRegex = """(?:print\()?concise_search\((?:query=)?"(.*?)"(?:,.*)?\)\)?""".toRegex()
        val urlContextToolRegex = """(?:print\()?browse\((?:urls=)?(\[.*?])\)\)?""".toRegex()
        val execResultRegex = """(?:Looking up information on Google Search\.|Browsing the web\.)\n?""".toRegex()
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
                        config
                    ) ?: return@withTyping
                    event.message.reply {
                        content = buildString {
                            response.parts()?.forEach {
                                it.executableCode().getOrNull()?.code()?.getOrNull()?.trim()?.let { code ->
                                    when {
                                        searchToolRegex.matches(code) -> {
                                            appendLine(searchToolRegex.replace(code, """-# 🔎 Google検索: `$1`"""))
//                                            appendLine()
                                        }
                                        urlContextToolRegex.matches(code) -> {
                                            appendLine("-# 🌐 Web閲覧:")
                                            appendLine(urlContextToolRegex.replace(code) {
                                                Json.decodeFromString<List<String>>(it.groupValues[1])
                                                    .joinToString("\n") { url -> "-# - <$url>" }
                                            })
//                                            appendLine()
                                        }
                                        else -> {
                                            appendLine("```python")
                                            appendLine(code)
                                        }
                                    }
                                }
                                it.codeExecutionResult().getOrNull()?.output()?.getOrNull()?.let { output ->
                                    if (execResultRegex.matches(output)) {
                                        return@let
                                    }

                                    appendLine()
                                    appendLine(output
                                        .trimEnd()
                                        .lines()
                                        .joinToString("\n") {
                                            line -> "# $line"
                                        }
                                    )
                                    appendLine("```")
                                }
                                it.text().getOrNull()?.let { text ->
                                    when {
                                        it.thought().getOrDefault(false) -> {
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
