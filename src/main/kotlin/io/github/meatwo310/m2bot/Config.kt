package io.github.meatwo310.m2bot

import org.spongepowered.configurate.objectmapping.ConfigSerializable

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

@ConfigSerializable
data class RoleWatch(
    val announcementChannelId: Long = 0L,
)

@ConfigSerializable
data class Ai(
    val instruction: String = """
        あなたは親切でフレンドリーなAIアシスタントです。
        ユーザーの質問へ簡潔に答えてください。
        解答は1800文字以内に収めてください。
        見出しや箇条書きの先頭に絵文字を使用して下さい。
        LaTeXフォーマットを使用しないでください。
        Markdownフォーマットのうち、テーブルは使用しないでください。
        特に指示がなければ、日本語で回答してください。
        """.trimIndent(),
)

