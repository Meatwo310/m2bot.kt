package io.github.meatwo310.m2bot.extensions.ai

import com.google.genai.Client
import com.google.genai.types.*
import dev.kord.core.behavior.channel.withTyping
import dev.kord.core.behavior.reply
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.rest.builder.message.addFile
import dev.kord.rest.builder.message.allowedMentions
import dev.kordex.core.checks.isNotBot
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.event
import dev.kordex.core.utils.env
import dev.kordex.core.utils.repliedMessageOrNull
import io.github.meatwo310.m2bot.config
import io.github.meatwo310.m2bot.extensions.ai.AiExtension.Companion.searchToolRegex
import io.github.meatwo310.m2bot.extensions.ai.AiExtension.Companion.urlContextToolRegex
import io.github.meatwo310.m2bot.extensions.preferences.PreferencesExtension
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import org.spongepowered.configurate.objectmapping.ConfigSerializable
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.jvm.optionals.getOrDefault
import kotlin.jvm.optionals.getOrNull

@ConfigSerializable
data class Ai(
    val instruction: String = """
        あなたは親切でフレンドリーなAIアシスタントです。
        ユーザーの質問へ簡潔に答えてください。
        回答は1800文字以内に収めてください。
        見出しや箇条書きの先頭に絵文字を使用して下さい。
        LaTeXフォーマットを使用しないでください。
        Markdownフォーマットのうち、テーブルは使用しないでください。
        特に指示がなければ、日本語で回答してください。
        """.trimIndent(),
    val model: String = "gemini-2.5-flash",
    val maxReplyChain: Int = 10,
    val maxLength: Int = 1990,
    val ellipse: String = "...",
    val blank: String = "レスポンスの生成に失敗",
    val functions: FunctionsConfig = FunctionsConfig(),
)

@ConfigSerializable
data class FunctionsConfig(
    val thinkingFormat: String = "-# \uD83E\uDDD0 %s", // 🧐
    val searchFormat: String = "-# \uD83D\uDD0E %s",   // 🔎
    val searchUnknown: String = "<Unknown>",
    val browseFormat: String = "-# \uD83C\uDF10 <%s>", // 🌐
    val browseUnknown: String = "<Unknown>",
    val executionFormat: String = "-# \uD83D\uDC0D %s", // 🐍
    val executionFilePrefix: String = "exec",
    val executionFileUnknown: String = "unknown.py",
    val executionResultFormat: String = "# %s",
)

val logger = KotlinLogging.logger {}

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

        val tempDir = Path("./local/aitemp/").createDirectories()
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
                        repeat(config.ai.maxReplyChain) {
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
                        config.ai.model,
                        contents,
                        contentConfig
                    ) ?: return@withTyping

                    event.message.reply {
                        val executedCodes = mutableListOf<Path>()
                        content = buildString {
                            val executableCodes = mutableListOf<String>()
                            response.parts()?.forEach { part ->
                                part.executableCode().getOrNull()?.code()?.getOrNull()?.let { code ->
                                    executableCodes.add(code)
                                }
                                part.codeExecutionResult().getOrNull()?.output()?.getOrNull()?.let { output ->
                                    handleExecutionResult(executableCodes, executedCodes, tempDir, output)
                                    executableCodes.clear()
                                }
                                part.text().getOrNull()?.let { text ->
                                    handleText(part, text)
                                }
                            }
                        }.let {
                            if (it.length > config.ai.maxLength) {
                                it.take(config.ai.maxLength - config.ai.ellipse.length) + config.ai.ellipse
                            } else {
                                it
                            }
                        }.ifBlank { config.ai.blank }

                        allowedMentions {}
                        executedCodes.forEach {
                            addFile(it)
                        }
                    }
                }
            }
        }
    }
}

private fun StringBuilder.handleExecutionResult(executableCodes: MutableList<String>, executedCodes: MutableList<Path>, tempDir: Path, output: String) {
    when {
        output.startsWith("Looking up information on Google Search.") -> { searchToolRegex
            .findAll(executableCodes.joinToString("\n"))
            .map { it.groupValues[1] }
            .ifEmpty { sequenceOf(config.ai.functions.searchUnknown) }
            .forEach {
                appendLine(config.ai.functions.searchFormat.format(it))
            }
        }

        output.startsWith("Browsing the web.") -> { urlContextToolRegex
            .findAll(executableCodes.joinToString("\n"))
            .map { Json.decodeFromString<List<String>>(it.groupValues[1]) }
            .flatten()
            .ifEmpty { sequenceOf(config.ai.functions.browseUnknown) }
            .forEach {
                appendLine(config.ai.functions.browseFormat.format(it))
            }
        }
        else -> {
            val attachmentText = buildString {
                executableCodes.forEach {
                    appendLine(it)
                    appendLine()
                }
                appendLine(output
                    .trimEnd()
                    .lines()
                    .joinToString("\n") {
                        config.ai.functions.executionResultFormat.format(it)
                    }
                )
            }

            try {
                Files.createTempFile(tempDir, config.ai.functions.executionFilePrefix, ".py").toFile().apply {
                    deleteOnExit()
                    writeText(attachmentText)
                    executedCodes.add(this.toPath())
                    appendLine(config.ai.functions.executionFormat.format(
                        "${name}"
                    ))
                }
            } catch (e: Exception) {
                logger.error { e }
                appendLine(config.ai.functions.executionFormat.format(
                    "${config.ai.functions.executionFileUnknown}"
                ))
            }
        }
    }
}

private fun StringBuilder.handleText(part: Part, text: String) {
    when {
        part.thought().getOrDefault(false) -> { text
            .lines()
            .filter { line -> line.startsWith("**") }
            .forEach { line ->
                appendLine(
                    config.ai.functions.thinkingFormat.format(
                        line.removeSurrounding("**").trim()
                    )
                )
            }
        }
        else -> {
            appendLine()
            appendLine(text.trim())
        }
    }
}
