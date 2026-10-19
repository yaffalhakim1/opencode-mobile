package dev.opencode.mobile.data.chat

/** One rendered row of a session timeline. */
sealed interface ChatEntry {
    val id: String

    data class User(
        override val id: String,
        val text: String,
        val createdAt: Long,
    ) : ChatEntry

    data class Assistant(
        override val id: String,
        val blocks: List<AssistantBlock>,
        val createdAt: Long,
        val isStreaming: Boolean,
        val modelLabel: String? = null,
        val errorText: String? = null,
    ) : ChatEntry

    /** System, skill, shell and compaction messages collapse into a single quiet row. */
    data class Notice(
        override val id: String,
        val text: String,
        val kind: NoticeKind,
        val createdAt: Long,
    ) : ChatEntry
}

enum class NoticeKind { System, Skill, Shell, Compaction }

sealed interface AssistantBlock {
    data class Text(
        val text: String,
        val ordinal: Int = 0,
        val streaming: Boolean = false,
    ) : AssistantBlock

    data class Reasoning(
        val text: String,
        val ordinal: Int = 0,
        val streaming: Boolean = false,
        val expanded: Boolean = false,
    ) : AssistantBlock

    data class Tool(
        val callId: String,
        val name: String,
        val status: ToolStatus,
        val input: String? = null,
        val output: String? = null,
        val error: String? = null,
    ) : AssistantBlock
}

enum class ToolStatus { Streaming, Running, Completed, Failed }
