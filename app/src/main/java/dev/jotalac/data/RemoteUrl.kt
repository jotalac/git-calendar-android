package dev.jotalac.data

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

/** Git remote URL with credentials split out, as shown in the calendar editor. */
data class RemoteAuth(val url: String, val username: String, val password: String)

/**
 * Credentials travel inside the remote URL (https://user:pass@host/repo.git), like the web client's
 * urlWithAuth/authFromUrl. A token without a username goes into the user-info's username part.
 */
object RemoteUrl {

    fun parse(value: String): URI? = runCatching {
        URI(value.trim()).takeIf { it.scheme in setOf("http", "https") && !it.host.isNullOrEmpty() }
    }.getOrNull()

    fun isGitRepo(value: String): Boolean = parse(value)?.path?.lowercase()?.endsWith(".git") == true

    fun withAuth(repoUrl: String, username: String, password: String): String {
        if (username.isEmpty() && password.isEmpty()) return repoUrl
        val uri = parse(repoUrl) ?: return repoUrl
        val userInfo = if (username.isEmpty()) encode(password) else "${encode(username)}:${encode(password)}"
        return URI(
            uri.scheme, null, uri.host, uri.port, null, null, null,
        ).toString().let { base ->
            // URI's multi-argument constructor re-encodes user info, so splice the encoded form in manually.
            val withUser = base.replaceFirst("://", "://$userInfo@")
            withUser + uri.rawPath.orEmpty() +
                (uri.rawQuery?.let { "?$it" } ?: "") + (uri.rawFragment?.let { "#$it" } ?: "")
        }
    }

    fun split(remoteUrl: String): RemoteAuth {
        val uri = parse(remoteUrl) ?: return RemoteAuth(remoteUrl, "", "")
        val raw = uri.rawUserInfo ?: return RemoteAuth(remoteUrl, "", "")
        val separator = raw.indexOf(':')
        val user = decode(if (separator < 0) raw else raw.substring(0, separator))
        val pass = if (separator < 0) "" else decode(raw.substring(separator + 1))
        val bare = remoteUrl.replaceFirst("$raw@", "")
        // A lone user-info component is a token, shown in the password field.
        return if (pass.isEmpty()) RemoteAuth(bare, "", user) else RemoteAuth(bare, user, pass)
    }

    private fun encode(value: String) = URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")
    private fun decode(value: String) = URLDecoder.decode(value, Charsets.UTF_8)
}
