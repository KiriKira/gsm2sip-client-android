package com.callagent.host.data

import java.net.URI
import java.security.MessageDigest

fun clientDatabaseName(apiBaseUrl: String, ownerId: String, deviceId: String): String {
    val uri = URI(apiBaseUrl).normalize()
    require(uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank())
    val port = if (uri.port == -1 || uri.port == 443) "" else ":${uri.port}"
    val path = uri.rawPath.orEmpty().trimEnd('/')
    val normalizedBase = "https://${uri.host.lowercase()}$port$path"
    val scope = "$normalizedBase|$ownerId|$deviceId"
    val digest = MessageDigest.getInstance("SHA-256").digest(scope.toByteArray(Charsets.UTF_8))
        .take(16).joinToString("") { byte -> "%02x".format(byte) }
    return "host-$digest.db"
}
