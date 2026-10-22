package dev.opencode.mobile.core.push.dto

import kotlinx.serialization.Serializable

/**
 * One message from an ntfy subscription.
 *
 * The wire shape is ntfy's own, not the OpenCode server's, so it is modelled
 * separately from the app's other DTOs. Every field is optional because the
 * subscription also emits an `open` event that carries only the topic.
 *
 * The fields this app acts on are [title], [message] and [click]; the rest are
 * here because they are cheap and make a dropped message diagnosable.
 */
@Serializable
data class NtfyMessageDto(
    val id: String? = null,
    /** `open` for the subscription handshake, `message` for an actual message. */
    val event: String? = null,
    val topic: String? = null,
    val title: String? = null,
    val message: String? = null,
    val priority: Int? = null,
    val tags: List<String> = emptyList(),
    /** The deep link the sender wants opened on tap. */
    val click: String? = null,
    /** Epoch seconds. */
    val time: Long? = null,
)
