package com.callagent.host.mesh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InstallIdWaitTest {
    private fun await(vararg rounds: List<String>, expected: String?): Triple<String?, Int, Int> {
        var fetches = 0
        var waits = 0
        val result = HostNodes.awaitInstallId(
            fetch = { rounds[minOf(fetches++, rounds.lastIndex)] },
            expected = expected,
            onWaiting = { waits++ },
            timeoutMs = 50,
            pauseMs = 1,
            sleep = { Thread.sleep(it) }
        )
        return Triple(result, fetches, waits)
    }

    @Test
    fun aGatewayThatReportsAMomentLaterIsAccepted() {
        val (id, fetches, waits) = await(emptyList(), emptyList(), listOf("phone-a"), expected = "phone-a")
        assertEquals("phone-a", id)
        assertEquals(3, fetches)
        assertEquals("the user is told once that we are waiting", 1, waits)
    }

    @Test
    fun aGatewayAlreadyThereIsAcceptedWithoutWaiting() {
        val (id, fetches, waits) = await(listOf("phone-a"), expected = "phone-a")
        assertEquals("phone-a", id)
        assertEquals(1, fetches)
        assertEquals(0, waits)
    }

    @Test
    fun anotherPhoneOrNoneIsReportedAfterTheWait() {
        assertEquals("phone-b", await(listOf("phone-b"), expected = "phone-a").first)
        assertNull(await(emptyList(), expected = "phone-a").first)
        assertEquals("the primary takes whichever phone it reports", "phone-a", await(emptyList(), listOf("phone-a"), expected = null).first)
    }
}
