package com.wildanaizzaddin.ddltracker.service

internal fun hostPortFromJdbcUrl(url: String): String? {
    // Standard: jdbc:postgresql://host:port/db  jdbc:mysql://host:port/db  etc.
    try {
        val uri = java.net.URI(url.removePrefix("jdbc:"))
        if (uri.host != null && uri.port != -1) return "${uri.host}:${uri.port}"
        if (uri.host != null) return uri.host
    } catch (_: Exception) {}

    // Oracle new thin:  jdbc:oracle:thin:@//host:port/service
    Regex("""@//([^:/\s]+):(\d+)""").find(url)?.let {
        return "${it.groupValues[1]}:${it.groupValues[2]}"
    }

    // Oracle old thin with SID:  jdbc:oracle:thin:@host:port:SID
    Regex("""@([^:/\s(]+):(\d+):""").find(url)?.let {
        return "${it.groupValues[1]}:${it.groupValues[2]}"
    }

    // Oracle TNS descriptor:  (HOST=hostname)(PORT=1521)
    val host = Regex("""HOST=([^)]+)""", RegexOption.IGNORE_CASE).find(url)?.groupValues?.get(1)?.trim()
    val port = Regex("""PORT=([^)]+)""", RegexOption.IGNORE_CASE).find(url)?.groupValues?.get(1)?.trim()
    if (host != null && port != null) return "$host:$port"
    if (host != null) return host

    // General fallback: @host:port (no SID, e.g. @testdb:1521/service or @testdb:1521)
    Regex("""@([a-zA-Z0-9._-]+):(\d+)""").find(url)?.let {
        return "${it.groupValues[1]}:${it.groupValues[2]}"
    }

    return null
}
