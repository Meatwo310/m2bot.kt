package io.github.meatwo310.m2bot.extensions.ai

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
import io.github.meatwo310.m2bot.extensions.preferences.isEnabledAI
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.jvm.optionals.getOrDefault
import kotlin.jvm.optionals.getOrNull

val logger = KotlinLogging.logger {}

class AiExtension : Extension() {
    override val name: String = "ai"

    companion object {
        private val googleApiKey = env("GOOGLE_API_KEY")

        val googleClient = AiClient.create(
            googleApiKey,
            config.ai.googleModel,
            Tool.builder()
                .googleSearch(GoogleSearch.builder().build())
                .urlContext(UrlContext.builder().build())
                .codeExecution(ToolCodeExecution.builder().build())
                .build()!!
        )
        val functionsClient = AiClient.create(
            googleApiKey,
            config.ai.functionsModel,
            Tool.builder()
                .functions(AiFunctions.getAvailableFunctions())
                .build()!!
        )
        val intentionClient = AiClient.create(
            googleApiKey,
            config.ai.intentionModel,
            thinkingBudget = null,
            useSystemInstruction = false
        )

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
                if (!event.message.author.isEnabledAI()) return@action
                val aiConfig = config.ai

                event.message.channel.withTyping {
                    val contents = mutableListOf(
                        Content.builder()
                            .role(Role.USER)
                            .parts(Part.fromText(event.message.content))
                            .build(),
                    ).apply {
                        var message = event.message
                        repeat(aiConfig.maxReplyChain) {
                            message = message.repliedMessageOrNull() ?: return@apply
                            val enabledAI = message.author.isEnabledAI()
                            val content = if (enabledAI) message.content else aiConfig.contentUnavailable
                            val role = if (message.author?.isSelf ?: return@apply) Role.MODEL else Role.USER
                            add(Content.builder()
                                .role(role)
                                .parts(Part.fromText(content))
                                .build()
                            )
                        }
                    }.reversed()

                    // 意図分析用のコンテンツを作成
                    val intentionContents = contents.toMutableList().apply {
                        add(Content.builder()
                            .role(Role.USER)
                            .parts(Part.fromText("# 指示\n以上のメッセージを踏まえて、ユーザーの意図から最適なモデルを選択し、モデル名のみを出力してください。"))
                            .build()
                        )
                    }

                    // intentionClientで意図を分析
                    val intentionResponse = intentionClient.generateContent(intentionContents)

                    // 分析結果に基づいてクライアントを選択
                    val isModelA = intentionResponse?.parts()?.firstOrNull()?.text()?.getOrNull()?.trim()
                        ?.contains("モデルA") ?: false
                    val client = if (isModelA) functionsClient else googleClient

                    // 選択されたクライアント名を決定
                    val clientName = aiConfig.run {
                        if (isModelA) functionsModel else googleModel
                    }.displayName

                    val response: GenerateContentResponse = client.generateContent(contents) ?: return@withTyping

                    event.message.reply {
                        val executedCodes = mutableListOf<Path>()
                        content = buildString {
                            // 選択されたクライアント情報を先頭に表示
                            appendLine("-# 🤖 $clientName")

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
                            if (it.length > aiConfig.maxLength) {
                                it.take(aiConfig.maxLength - aiConfig.ellipse.length) + aiConfig.ellipse
                            } else {
                                it
                            }
                        }.ifBlank { aiConfig.blank }

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
    val functionsConfig = config.ai.functions
    when {
        output.startsWith("Looking up information on Google Search.") -> { searchToolRegex
            .findAll(executableCodes.joinToString("\n"))
            .map { it.groupValues[1] }
            .ifEmpty { sequenceOf(functionsConfig.searchUnknown) }
            .forEach {
                appendLine(functionsConfig.searchFormat.format(it))
            }
        }

        output.startsWith("Browsing the web.") -> { urlContextToolRegex
            .findAll(executableCodes.joinToString("\n"))
            .map { Json.decodeFromString<List<String>>(it.groupValues[1]) }
            .flatten()
            .ifEmpty { sequenceOf(functionsConfig.browseUnknown) }
            .forEach {
                appendLine(functionsConfig.browseFormat.format(it))
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
                        functionsConfig.executionResultFormat.format(it)
                    }
                )
            }

            try {
                Files.createTempFile(tempDir, functionsConfig.executionFilePrefix, ".py").toFile().apply {
                    deleteOnExit()
                    writeText(attachmentText)
                    executedCodes.add(this.toPath())
                    appendLine(functionsConfig.executionFormat.format("$name"))
                }
            } catch (e: Exception) {
                logger.error { e }
                appendLine(functionsConfig.executionFormat.format(functionsConfig.executionFileUnknown))
            }
        }
    }
}

private fun StringBuilder.handleText(part: Part, text: String) {
    when {
        part.thought().getOrDefault(false) -> { text
            .lines()
            .filter { line -> line.startsWith("**") }
            .forEach { line -> appendLine(
                config.ai.functions.thinkingFormat.format(
                    line.removeSurrounding("**").trim()
                )
            ) }
        }
        else -> {
            appendLine()
            appendLine(text.trim())
        }
    }
}

