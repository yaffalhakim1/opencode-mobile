package dev.opencode.mobile.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Splits a pairing payload into the server it belongs to and the code to redeem.
 *
 * Two shapes arrive in practice, and they are not interchangeable:
 *
 * - **The QR code** carries JSON: `{"code":"…","urls":["https://…"]}`. This is
 *   what the desktop app actually renders.
 * - **The "Copy link" button** carries a URL: `https://host/auth/connect/CODE`.
 *
 * Users paste either, and links get pasted with trailing slashes, query strings
 * or surrounding whitespace, so this is deliberately forgiving.
 */
data class PairingLink(val baseUrl: String, val code: String) {

    companion object {
        private const val MARKER = "/auth/connect/"
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        fun parse(raw: String): PairingLink? {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return null

            // JSON first: a QR payload is JSON and never contains the URL marker.
            return fromJson(trimmed) ?: fromUrl(trimmed)
        }

        private fun fromJson(raw: String): PairingLink? {
            if (!raw.startsWith("{")) return null
            val obj = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return null

            val code = obj["code"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            if (code.isEmpty()) return null

            // `urls` is an array in current builds; tolerate a bare string too.
            val urls = when (val element = obj["urls"]) {
                is JsonArray -> element.mapNotNull { it.primitiveOrNull() }
                is JsonPrimitive -> listOfNotNull(element.contentOrNull)
                else -> emptyList()
            }

            val base = urls.firstOrNull { it.startsWith("https://", true) }
                ?: urls.firstOrNull()
                ?: return null

            return PairingLink(base.trimEnd('/'), code)
        }

        private fun fromUrl(raw: String): PairingLink? {
            val index = raw.indexOf(MARKER, ignoreCase = true)
            if (index <= 0) return null

            val base = raw.substring(0, index).trimEnd('/')
            val code = raw.substring(index + MARKER.length)
                .substringBefore('?')
                .substringBefore('#')
                .trim()
                .trimEnd('/')

            if (base.isBlank() || code.isBlank()) return null
            return PairingLink(base, code)
        }

        private fun kotlinx.serialization.json.JsonElement.primitiveOrNull(): String? =
            (this as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
    }
}
