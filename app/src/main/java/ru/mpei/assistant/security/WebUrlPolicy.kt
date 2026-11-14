package ru.mpei.assistant.security

import java.net.URI
import java.util.Locale

object WebUrlPolicy {
    // Only a same-host login form was verified. Do not add speculative SSO hosts.
    private val barsHosts = setOf("bars.mpei.ru")

    fun externalWebUrl(value: String?): URI? {
        if (value.isNullOrBlank() || value.any { it.isWhitespace() || it.isISOControl() || it == '\\' }) return null
        val uri = try { URI(value) } catch (_: Exception) { return null }
        if (uri.scheme?.lowercase(Locale.ROOT) !in setOf("https", "http")) return null
        if (uri.host.isNullOrBlank() || uri.rawUserInfo != null) return null
        if (uri.port != -1 && uri.port !in 1..65535) return null
        return uri
    }

    fun isBarsUrl(value: String?): Boolean {
        val uri = externalWebUrl(value) ?: return false
        return uri.scheme.equals("https", true) &&
            uri.host.lowercase(Locale.ROOT) in barsHosts && uri.port in setOf(-1, 443)
    }
}
