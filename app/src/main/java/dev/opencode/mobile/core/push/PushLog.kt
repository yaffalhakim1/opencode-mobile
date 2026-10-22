package dev.opencode.mobile.core.push

import android.util.Log

/**
 * One place for the push log tag.
 *
 * Push fails in ways that leave no trace in the UI — a token that never
 * arrived, a subscription that dropped, a report the server refused. Without a
 * log line each of those is indistinguishable from "push is off", so every
 * failure path here logs.
 */
internal fun logPush(message: String, error: Throwable? = null) {
    if (error == null) {
        Log.i(TAG, message)
    } else {
        Log.w(TAG, message, error)
    }
}

private const val TAG = "Push"
