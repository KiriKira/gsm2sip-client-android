package com.callagent.host.ui

/** The three persistent destinations in the host app's bottom navigation. */
enum class HostTab {
    PHONE,
    MESSAGES,
    SETTINGS,
}

/** A home tab or one of its focused editing/detail screens. */
sealed class HostDestination {
    object Home : HostDestination()
    object Dialer : HostDestination()
    data class Conversation(
        val simId: String?,
        val peer: String,
        val isNew: Boolean = false,
    ) : HostDestination()
}

data class HostNavigationState(
    val tab: HostTab = HostTab.PHONE,
    val destination: HostDestination = HostDestination.Home,
) {
    fun selectTab(tab: HostTab) = copy(tab = tab, destination = HostDestination.Home)

    fun openDialer() = copy(tab = HostTab.PHONE, destination = HostDestination.Dialer)

    fun openConversation(simId: String?, peer: String, isNew: Boolean = false) =
        copy(tab = HostTab.MESSAGES, destination = HostDestination.Conversation(simId, peer, isNew))

    fun goHome() = copy(destination = HostDestination.Home)

    fun back(): HostNavigationState = if (destination == HostDestination.Home) {
        selectTab(HostTab.PHONE)
    } else {
        goHome()
    }
}
