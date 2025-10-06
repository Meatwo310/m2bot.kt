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
import io.github.meatwo310.m2bot.extensions.preferences.isEnabledAI
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
    val maxReplyChain: Int = 10,
    val maxLength: Int = 1990,
    val ellipse: String = "...",
    val blank: String = "レスポンスの生成に失敗",
    val functions: FunctionsConfig = FunctionsConfig(),
    val googleModel: Model = Model(name = "gemini-2.5-flash"),
    val functionsModel: Model = Model(name = "gemini-2.5-flash", displayName = "%s (Function Calling)"),
    val intentionModel: Model = Model(
        name = "gemma-3-27b-it",
        instruction = """
# 指示
あなたは、ユーザーからの質問の意図を分析し、最適な処理を行う専門家（モデルA または モデルB）にタスクを割り振るAIディスパッチャーです。
以下の説明を読み、ユーザーの質問に最適なモデル名のみを出力してください。

# 各モデルの専門分野

## モデルA：特化型ツール実行エキスパート (Specialized Tool User)
- **役割**: 特定の目的に特化したAPIや内部ツールを呼び出し、専門的なタスクを実行するエキスパートです。
- **現在実行可能なタスクリスト**
  - リマインダーの登録
  - リマインダーの確認
- **具体例**
  - 「明日の朝9時にリマインドして」
  - 「今日のリマインダーを全部教えて」

---

## モデルB：汎用リサーチ＆分析エキスパート (General Researcher & Analyst)
- **役割**: Google検索のような汎用ツールを駆使し、Web上にある広範な公開情報から答えを探し出したり、プログラムを実行して複雑な分析を行ったりするエキスパートです。
- **能力と使い分け**:
  - **Google検索**: 最新情報、一般的な知識、手順の解説、評判など、**Web上の非構造化情報**を探します。
  - **URL読解**: 特定のWebページの内容を正確に読み取ります。
  - **Code Execution**: 数学計算やデータ分析など、**厳密な論理処理**を実行します。
- **具体例**
  - [Google検索]「明日の東京の天気は？」
  - [URL読解]「このニュース記事を3行で要約して: https://example.com/ 」
  - [Code Execution]「256の平方根を計算して」

# 判断ロジック
1.  まず、ユーザーの質問が「モデルAの実行可能タスクリスト」にある、**特定のツールで処理できる具体的な操作**に合致するかを最優先で確認します。
2.  合致する場合は、**「モデルA」**を選択します。
3.  合致しない場合、または質問が広範な調査、一般的な知識、手順の解説、厳密な計算などを求めるものであれば、すべて**「モデルB」**を選択します。
        """.trimIndent(),
    )
)

@ConfigSerializable
data class Model(
    val name: String = "gemini-2.5-flash-lite",
    val displayName: String = "%s",
    val instruction: String = """
あなたは親切でフレンドリーなAIアシスタントです。
- ユーザーの質問へ簡潔に答えてください。
- 回答は1800文字以内に収めてください。
- 見出しや箇条書きの先頭に絵文字を使用して下さい。
- LaTeXフォーマットを使用しないでください。
- Markdownフォーマットのうち、テーブルは使用しないでください。代わりに、箇条書きを使用してください。

特に指示がなければ:
- 日本語で回答してください。
- 日付・時刻に日本標準時(UTC+9)を使用してください。
        """.trimIndent(),
    val maxOutputTokens: Int = 8000,
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

data class AiClient(
    val client: Client,
    val model: Model,
    val tool: Tool?,
    val thinkingConfig: ThinkingConfig?,
    val contentConfig: GenerateContentConfig,
    val useSystemInstruction: Boolean
) {
    companion object {
        fun create(
            apiKey: String,
            model: Model,
            tool: Tool? = null,
            thinkingBudget: Int? = 24576,
            useSystemInstruction: Boolean = true
        ): AiClient {
            val client = Client.builder()
                .apiKey(apiKey)
                .build()!!

            val thinkingConfig = thinkingBudget?.let { ThinkingConfig.builder()
                .includeThoughts(true)
                .thinkingBudget(thinkingBudget)
                .build()!!
            }

            val tools = if (tool != null) listOf(tool) else emptyList()

            val contentConfig = GenerateContentConfig.builder().apply {
                if (useSystemInstruction) {
                    systemInstruction(Content.fromParts(Part.fromText(model.instruction))!!)
                }
                if (thinkingBudget != null) {
                    thinkingConfig(thinkingConfig)
                }
                tools(tools)
                maxOutputTokens(model.maxOutputTokens)
            }.build()!!

            return AiClient(client, model, tool, thinkingConfig, contentConfig, useSystemInstruction)
        }
    }

    fun generateContent(contents: List<Content>): GenerateContentResponse? {
        val finalContents = if (!useSystemInstruction) {
            val instructionContent = Content.builder()
                .role("user")
                .parts(Part.fromText(model.instruction))
                .build()
            listOf(instructionContent) + contents
        } else {
            contents
        }

        return client.models.generateContent(model.name, finalContents, contentConfig)
    }
}

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
                            val enabledAI = message.author.isEnabledAI()
                            val content = if (enabledAI) message.content else "*Content not available due to user preferences.*"
                            val role = if (message.author?.isSelf ?: return@apply) "model" else "user"
                            add(Content.builder()
                                .role(role)
                                .parts(Part.fromText(message.content))
                                .build()
                            )
                        }
                    }.reversed()

                    // 意図分析用のコンテンツを作成
                    val intentionContents = contents.toMutableList().apply {
                        add(Content.builder()
                            .role("user")
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
                    val clientName = config.ai.run {
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
