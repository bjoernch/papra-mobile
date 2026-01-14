package app.papra.mobile.ui

import java.net.URI

fun normalizeBaseUrl(rawUrl: String): String {
    val trimmed = rawUrl.trim().removeSuffix("/")
    val knownTail = listOf(
        "/api/organizations",
        "/api/documents",
        "/api/api-keys/current",
        "/api"
    )
    val tail = knownTail.firstOrNull { trimmed.contains(it) }
    val base = if (tail != null) {
        trimmed.substring(0, trimmed.indexOf(tail) + "/api".length)
    } else {
        trimmed
    }
    var normalized = base
    while (normalized.endsWith("/api/api")) {
        normalized = normalized.removeSuffix("/api")
    }
    return if (normalized.endsWith("/api")) normalized else "$normalized/api"
}

fun buildHelpUrl(rawUrl: String): String {
    val trimmed = rawUrl.trim().removeSuffix("/")
    val base = if (trimmed.contains("/api")) {
        trimmed.substring(0, trimmed.indexOf("/api"))
    } else {
        trimmed
    }
    return "$base/api-keys/create"
}

fun isValidBaseUrl(rawUrl: String): Boolean {
    return try {
        val normalized = rawUrl.trim()
        val uri = URI(normalized)
        val host = uri.host ?: return false
        val hasScheme = uri.scheme == "https" || uri.scheme == "http"
        val hasTld = host.contains(".") && host.substringAfterLast('.').length >= 2
        hasScheme && hasTld
    } catch (e: Exception) {
        false
    }
}

fun parseScheme(rawUrl: String): String {
    return try {
        val uri = URI(rawUrl.trim())
        uri.scheme ?: "http"
    } catch (e: Exception) {
        "http"
    }
}
