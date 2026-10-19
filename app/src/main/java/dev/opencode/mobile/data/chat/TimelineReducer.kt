package dev.opencode.mobile.data.chat

/**
 * Pure transformations over a session timeline.
 *
 * The event stream is live-only: a dropped connection silently loses events, so
 * every caller reconciles by refetching messages rather than trying to repair a
 * partial timeline. These functions only handle the live-append path.
 */
object TimelineReducer {

    fun upsert(entries: List<ChatEntry>, entry: ChatEntry): List<ChatEntry> {
        val index = entries.indexOfFirst { it.id == entry.id }
        return if (index == -1) {
            entries + entry
        } else {
            entries.toMutableList().apply { this[index] = entry }
        }
    }

    fun appendText(
        entries: List<ChatEntry>,
        messageID: String,
        ordinal: Int,
        delta: String,
    ): List<ChatEntry> = mapAssistant(entries, messageID) { assistant ->
        assistant.withBlock(
            match = { it is AssistantBlock.Text && it.ordinal == ordinal },
            create = { AssistantBlock.Text(text = delta, ordinal = ordinal, streaming = true) },
            update = { block ->
                (block as AssistantBlock.Text).copy(text = block.text + delta, streaming = true)
            },
        )
    }

    fun finishText(
        entries: List<ChatEntry>,
        messageID: String,
        ordinal: Int,
        text: String,
    ): List<ChatEntry> = mapAssistant(entries, messageID) { assistant ->
        assistant.withBlock(
            match = { it is AssistantBlock.Text && it.ordinal == ordinal },
            create = { AssistantBlock.Text(text = text, ordinal = ordinal, streaming = false) },
            update = { AssistantBlock.Text(text = text, ordinal = ordinal, streaming = false) },
        )
    }

    fun appendReasoning(
        entries: List<ChatEntry>,
        messageID: String,
        ordinal: Int,
        delta: String,
    ): List<ChatEntry> = mapAssistant(entries, messageID) { assistant ->
        assistant.withBlock(
            match = { it is AssistantBlock.Reasoning && it.ordinal == ordinal },
            create = { AssistantBlock.Reasoning(text = delta, ordinal = ordinal, streaming = true) },
            update = { block ->
                (block as AssistantBlock.Reasoning).copy(text = block.text + delta, streaming = true)
            },
        )
    }

    fun finishReasoning(
        entries: List<ChatEntry>,
        messageID: String,
        ordinal: Int,
        text: String,
    ): List<ChatEntry> = mapAssistant(entries, messageID) { assistant ->
        assistant.withBlock(
            match = { it is AssistantBlock.Reasoning && it.ordinal == ordinal },
            create = { AssistantBlock.Reasoning(text = text, ordinal = ordinal) },
            update = { block ->
                (block as AssistantBlock.Reasoning).copy(text = text, streaming = false)
            },
        )
    }

    fun startTool(
        entries: List<ChatEntry>,
        messageID: String,
        callId: String,
        name: String,
    ): List<ChatEntry> = mapAssistant(entries, messageID) { assistant ->
        if (assistant.blocks.any { it is AssistantBlock.Tool && it.callId == callId }) {
            assistant
        } else {
            assistant.copy(
                blocks = assistant.blocks + AssistantBlock.Tool(
                    callId = callId,
                    name = name,
                    status = ToolStatus.Streaming,
                ),
            )
        }
    }

    fun updateTool(
        entries: List<ChatEntry>,
        messageID: String,
        callId: String,
        status: ToolStatus? = null,
        input: String? = null,
        output: String? = null,
        error: String? = null,
    ): List<ChatEntry> = mapAssistant(entries, messageID) { assistant ->
        assistant.copy(
            blocks = assistant.blocks.map { block ->
                if (block !is AssistantBlock.Tool || block.callId != callId) return@map block
                block.copy(
                    status = status ?: block.status,
                    input = input ?: block.input,
                    output = output ?: block.output,
                    error = error ?: block.error,
                )
            },
        )
    }

    fun setStreaming(
        entries: List<ChatEntry>,
        messageID: String,
        streaming: Boolean,
    ): List<ChatEntry> = mapAssistant(entries, messageID) { it.copy(isStreaming = streaming) }

    fun toggleReasoning(
        entries: List<ChatEntry>,
        messageID: String,
        ordinal: Int,
    ): List<ChatEntry> = mapAssistant(entries, messageID) { assistant ->
        assistant.copy(
            blocks = assistant.blocks.map { block ->
                if (block is AssistantBlock.Reasoning && block.ordinal == ordinal) {
                    block.copy(expanded = !block.expanded)
                } else {
                    block
                }
            },
        )
    }

    /**
     * Applies [transform] to the assistant row with [messageID].
     *
     * If the row does not exist yet, one is created. That matters for the first
     * delta of a new turn: the message is only in the server's history *after*
     * it starts streaming, so a delta always arrives before any refetch can
     * introduce the row. Without this, the opening text of every reply was
     * silently dropped and the chat looked frozen until a manual refresh.
     */
    private fun mapAssistant(
        entries: List<ChatEntry>,
        messageID: String,
        transform: (ChatEntry.Assistant) -> ChatEntry.Assistant,
    ): List<ChatEntry> {
        val index = entries.indexOfFirst { it is ChatEntry.Assistant && it.id == messageID }
        if (index == -1) {
            val created = transform(
                ChatEntry.Assistant(
                    id = messageID,
                    blocks = emptyList(),
                    createdAt = System.currentTimeMillis(),
                    isStreaming = true,
                ),
            )
            // Drop a placeholder row if the timeline had one, so the empty
            // "Working" state does not linger above the real answer.
            return entries + created
        }
        return entries.toMutableList().apply { this[index] = transform(this[index] as ChatEntry.Assistant) }
    }

    private fun ChatEntry.Assistant.withBlock(
        match: (AssistantBlock) -> Boolean,
        create: () -> AssistantBlock,
        update: (AssistantBlock) -> AssistantBlock,
    ): ChatEntry.Assistant {
        val index = blocks.indexOfFirst(match)
        val next = if (index == -1) {
            blocks + create()
        } else {
            blocks.toMutableList().apply { this[index] = update(this[index]) }
        }
        return copy(blocks = next)
    }
}
