package com.callagent.host.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerPushSignalTest {
    @Test
    fun aServerFirstSeenWithPushSignalsOnce() {
        val root = "https://signal-test.example/v1"
        ServerPushSignal.consumeChange()
        ServerPushSignal.observed(root, push = false)
        assertFalse(ServerPushSignal.advertises(root))
        assertFalse(ServerPushSignal.consumeChange())

        ServerPushSignal.observed(root, push = true)
        assertTrue(ServerPushSignal.advertises(root))
        assertTrue("the next registration pass skips its wait", ServerPushSignal.consumeChange())
        assertFalse(ServerPushSignal.consumeChange())

        ServerPushSignal.observed(root, push = true)
        assertFalse("seeing it again changes nothing", ServerPushSignal.consumeChange())
    }
}
