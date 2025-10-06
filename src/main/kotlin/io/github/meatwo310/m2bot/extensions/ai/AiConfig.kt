package io.github.meatwo310.m2bot.extensions.ai

import com.google.genai.Client
import com.google.genai.types.*
import org.spongepowered.configurate.objectmapping.ConfigSerializable

@ConfigSerializable
data class Ai(
    val maxReplyChain: Int = 10,
    val maxLength: Int = 1990,
    val ellipse: String = "...",
    val blank: String = "レスポンスの生成に失敗",
    val contentUnavailable: String = "*Content not available due to user preferences.*",
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
        prompt = "# 指示\n以上のメッセージを踏まえて、ユーザーの意図から最適なモデルを選択し、モデル名のみを出力してください。"
    )
)

@ConfigSerializable
data class Model(
    val name: String = "gemini-2.5-flash",
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
    val prompt: String = "",
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

            val tools = tool?.let { listOf(it) } ?: emptyList()

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
                .role(Role.Companion.USER)
                .parts(Part.fromText(model.instruction))
                .build()
            listOf(instructionContent) + contents
        } else {
            contents
        }

        return client.models.generateContent(model.name, finalContents, contentConfig)
    }
}
