package com.callagent.host.data

import java.net.URI
import java.security.MessageDigest

fun clientDatabaseName(apiBaseUrl: String, ownerId: String, deviceId: String): String {
    val uri = URI(apiBaseUrl).normalize()
    val scheme = uri.scheme?.lowercase()
    require((scheme == "https" || scheme == "http") && !uri.host.isNullOrBlank())
    // HTTPS scopes keep their original form so existing installs keep their database;
    // an http:// base of the same host is a distinct server origin and scope.
    val defaultPort = if (scheme == "https") 443 else 80
    val port = if (uri.port == -1 || uri.port == defaultPort) "" else ":${uri.port}"
    val path = uri.rawPath.orEmpty().trimEnd('/')
    val normalizedBase = "$scheme://${uri.host.lowercase()}$port$path"
    val scope = "$normalizedBase|$ownerId|$deviceId"
    val digest = MessageDigest.getInstance("SHA-256").digest(scope.toByteArray(Charsets.UTF_8))
        .take(16).joinToString("") { byte -> "%02x".format(byte) }
    return "host-$digest.db"
}
