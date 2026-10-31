package dev.opencode.mobile.data.chat

/**
 * A mention the user is typing.
 *
 * `@` mentions a file in the project and `/` opens the server's commands and
 * skills. The marker has to be the first character of a word, so an email
 * address or a path in the middle of a sentence is not mistaken for one.
 */
data class MentionTrigger(
    /** The character that opened it: `@` for a file, `/` for a command or skill. */
    val marker: Char,
    /** The text typed after the marker, used to filter the list. */
    val query: String,
    /** Where the marker sits in the draft. */
    val start: Int,
    /** Where the caret is: the end of the query. */
    val end: Int,
)

/** A mention inserted into the draft. */
data class AppliedMention(
    /** The draft with the mention in it. */
    val text: String,
    /** Where the caret goes: just after the mention. */
    val caret: Int,
    /** The span the mention occupies, for the server. */
    val start: Int,
    val end: Int,
)

/**
 * Where a mention sits in the prompt text.
 *
 * The server matches a mention by these offsets, so they have to be positions
 * in the text that is actually sent.
 */
data class MentionSpan(
    val start: Int,
    val end: Int,
    val text: String,
)

/** A file in the project that the user mentioned. */
data class MentionedFile(
    /** The absolute path on the server. */
    val path: String,
    /** The path relative to the project, as it appears in the prompt. */
    val relativePath: String,
    val span: MentionSpan,
)

/** A skill the user mentioned. */
data class MentionedSkill(
    val id: String,
    val name: String,
    val span: MentionSpan,
)

/**
 * Finding and placing mentions.
 *
 * Pure, so the rules that decide what counts as a mention are testable
 * without a keyboard.
 */
object Mentions {

    /** The character that mentions a file. */
    const val FILE_MARKER = '@'

    /** The character that mentions a skill. */
    const val SKILL_MARKER = '/'

    /**
     * The character that opens the `/` list: commands, and skills.
     *
     * The same key as [SKILL_MARKER] because the server models a skill as a
     * command. One name for the key that the composer looks for, so a rename
     * cannot leave the list opening for one and not the other.
     */
    const val SLASH_MARKER = SKILL_MARKER

    /**
     * Finds the mention being typed at the caret.
     *
     * The mention has to be a whole word: the caret must sit at the end of it,
     * and the marker must be the first character of the word. That is what
     * keeps `someone@example.com` and `src/main.kt` from opening a picker.
     */
    fun detect(text: String, caret: Int): MentionTrigger? {
        if (caret <= 0) return null
        if (caret > text.length) return null
        // The caret has to be at the end of the word, or half-typed text
        // would be replaced mid-word.
        if (caret < text.length && !text[caret].isWhitespace()) return null

        val before = text.substring(0, caret)
        val start = before.lastIndexOfAny(charArrayOf(' ', '\n', '\t', ',')) + 1
        val word = before.substring(start)
        val marker = word.firstOrNull() ?: return null
        if (marker != FILE_MARKER && marker != SKILL_MARKER) return null

        return MentionTrigger(
            marker = marker,
            query = word.substring(1),
            start = start,
            end = caret,
        )
    }

    /**
     * Replaces the mention being typed with the chosen one.
     *
     * The span is where the mention ends up in the text, which is what the
     * server uses to find it again.
     */
    fun apply(draft: String, trigger: MentionTrigger, mention: String): AppliedMention {
        val text = draft.substring(0, trigger.start) + mention + draft.substring(trigger.end)
        val start = trigger.start
        val end = start + mention.length
        return AppliedMention(text = text, caret = end, start = start, end = end)
    }

    /**
     * The `file:` URL for a path on the server.
     *
     * The server reads the attachment from its own filesystem, so this has to
     * be an absolute path, and a Windows path needs the extra leading slash or
     * the drive letter is read as the host.
     */
    fun fileUrl(path: String): String {
        val normalised = path.replace('\\', '/')
        val absolute = if (normalised.startsWith("/")) normalised else "/$normalised"
        return "file://$absolute"
    }

    /**
     * The absolute path of a file within the project.
     *
     * The search answers with paths relative to the directory that was
     * searched, and the attachment has to be absolute.
     */
    fun absolutePath(directory: String, relative: String): String {
        val base = directory.trimEnd('/', '\\')
        val suffix = relative.trimStart('/', '\\')
        if (base.isBlank()) return relative
        return "$base/$suffix"
    }

    /**
     * Moves the recorded mentions after text was inserted.
     *
     * The spans are positions in the prompt text, so text inserted in front of
     * a mention moves it. Without this, a second mention would leave the
     * first one pointing at the wrong characters and the server would attach
     * the file to the wrong place.
     */
    fun shiftAfter(span: MentionSpan, insertAt: Int, delta: Int): MentionSpan =
        if (span.start >= insertAt) {
            span.copy(start = span.start + delta, end = span.end + delta)
        } else {
            span
        }

    /** Moves every recorded mention after text was inserted. */
    fun shiftAfter(spans: List<MentionSpan>, insertAt: Int, delta: Int): List<MentionSpan> =
        spans.map { shiftAfter(it, insertAt, delta) }
}
