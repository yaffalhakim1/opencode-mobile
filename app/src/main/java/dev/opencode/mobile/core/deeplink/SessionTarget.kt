package dev.opencode.mobile.core.deeplink

import dev.opencode.mobile.data.remote.OpenCodeApi

/**
 * What a chat screen needs before it can open.
 *
 * A link carries only a session id; everything else comes from the session
 * itself, which is the difference between opening the right chat and opening a
 * blank one.
 */
data class SessionTarget(
    val sessionID: String,
    val title: String?,
    val agent: String?,
    val modelKey: String?,
    val directory: String?,
)

/**
 * Resolves a session id into the route a chat screen needs.
 *
 * Returns null when the session cannot be fetched — deleted, or belonging to a
 * server this phone is no longer connected to. A caller should land on the
 * session list rather than a chat that cannot load.
 */
suspend fun resolveSession(api: OpenCodeApi, sessionID: String): SessionTarget? =
    runCatching {
        val session = api.session(sessionID)
        SessionTarget(
            sessionID = session.id,
            title = session.title,
            agent = session.agent,
            modelKey = session.model?.let { "${it.providerID}/${it.id}" },
            directory = session.location?.directory,
        )
    }.getOrNull()
