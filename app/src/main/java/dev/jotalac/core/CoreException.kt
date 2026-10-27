package dev.jotalac.core

import kotlinx.serialization.Serializable

/** Stable error categories reported by the core (see core/pkg/errcode). */
enum class CoreErrorCode { NETWORK, AUTH, FORBIDDEN, RATELIMIT, VALIDATION, STORAGE }

/** An error returned by the Go core, decoded from the JSON message produced by the core's mobile binding. */
class CoreException(
    message: String,
    val code: CoreErrorCode? = null,
    val calendar: String? = null,
    cause: Throwable? = null,
) : Exception(message, cause) {

    companion object {
        @Serializable
        private data class Payload(val code: String? = null, val calendar: String? = null, val message: String = "")

        fun from(throwable: Throwable): CoreException {
            if (throwable is CoreException) return throwable
            val raw = throwable.message.orEmpty()
            val payload = runCatching { CoreJson.decodeFromString<Payload>(raw) }.getOrNull()
                ?: return CoreException(raw.ifEmpty { throwable.toString() }, cause = throwable)
            return CoreException(
                message = payload.message,
                code = CoreErrorCode.entries.firstOrNull { it.name == payload.code },
                calendar = payload.calendar?.takeIf { it.isNotBlank() },
                cause = throwable,
            )
        }
    }
}
