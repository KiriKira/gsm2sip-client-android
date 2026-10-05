package com.callagent.host.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class HostNavigationStateTest {
    @Test
    fun startsOnPhoneAndEachTabReturnsToItsHomeScreen() {
        val initial = HostNavigationState()
        assertEquals(HostTab.PHONE, initial.tab)
        assertEquals(HostDestination.Home, initial.destination)

        val dialer = initial.openDialer()
        assertEquals(HostTab.PHONE, dialer.tab)
        assertEquals(HostDestination.Dialer, dialer.destination)
        assertEquals(HostNavigationState(HostTab.MESSAGES), dialer.selectTab(HostTab.MESSAGES))
        assertEquals(HostNavigationState(HostTab.SETTINGS), dialer.selectTab(HostTab.SETTINGS))
    }

    @Test
    fun conversationKeepsSimAndPeerAndBackReturnsToMessages() {
        val detail = HostNavigationState().openConversation("sim-a", "+15550001001")
        assertEquals(HostTab.MESSAGES, detail.tab)
        assertEquals(HostDestination.Conversation("sim-a", "+15550001001"), detail.destination)
        assertEquals(HostNavigationState(HostTab.MESSAGES), detail.back())
    }

    @Test
    fun unknownSimThreadRemainsReadOnlyUntilUserChoosesALine() {
        val unknown = HostNavigationState().openConversation(null, "+15550001002")
        assertEquals(HostDestination.Conversation(null, "+15550001002"), unknown.destination)
        assertEquals(HostDestination.Conversation("sim-b", "+15550001002"),
            unknown.openConversation("sim-b", "+15550001002").destination)
    }
}
