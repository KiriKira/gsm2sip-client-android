package com.callagent.host.ui

/**
 * Whether this phone's last attempt to reach the server succeeded. Only a
 * fresh answer from the server can say the old phone is offline; while the
 * server itself is unreachable the old phone's state is unknown.
 */
sealed class ServerLink {
    object Checking : ServerLink()
    object Connected : ServerLink()
    data class Unreachable(val reason: String) : ServerLink()
}
