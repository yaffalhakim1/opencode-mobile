package dev.opencode.mobile.core.net

/** A transport or protocol failure carrying a message safe to show in the UI. */
class OpenCodeException(
    val status: Int?,
    override val message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {

    val isUnauthorized: Boolean get() = status == 401

    /**
     * True when the failure might not be true a moment later.
     *
     * A transport failure has no status. A server that answered with a status
     * will answer the same way again, so only the transport is worth retrying.
     */
    val isTransient: Boolean get() = status == null

    companion object {
        fun network(cause: Throwable): OpenCodeException {
            val detail = cause.message?.takeIf { it.isNotBlank() } ?: cause::class.simpleName.orEmpty()
            return OpenCodeException(null, "Cannot reach the server: $detail", cause)
        }

        fun unauthorized() = OpenCodeException(401, "Wrong password, or the pairing link expired.")

        fun pairingRejected() = OpenCodeException(
            401,
            "That pairing link is expired or already used. Generate a fresh QR code.",
        )

        fun notAPairingLink() = OpenCodeException(
            null,
            "That is not an OpenCode pairing link. It should look like https://host/auth/connect/CODE.",
        )

        fun unexpected(status: Int, body: String?): OpenCodeException {
            val hint = body?.takeIf { it.isNotBlank() }?.take(240)
            val message = if (hint == null) "Server returned HTTP $status." else "HTTP $status: $hint"
            return OpenCodeException(status, message)
        }
    }
}
