package com.callagent.host.data

import android.content.Context
import android.os.SystemClock
import java.io.Closeable
import java.io.IOException

data class HostSyncOutcome(val inboundNotificationCount: Int)

class SyncBudgetExhausted : IOException("Background sync time budget exhausted")

/**
 * A read-only-to-the-server sync loop bound to one paired session and one database file.
 * It only writes authoritative gateway/SIM/message snapshots and event cursors. It never submits SMS.
 */
class HostSyncEngine(
    context: Context,
    private val boundSession: HostSession,
    private val sessionIsCurrent: (HostSession) -> Boolean,
    private val persistInboundNotificationIds: (List<String>) -> Boolean = { false }
) : Closeable {
    private val app = context.applicationContext
    private val sessionStore = SessionStore(app)
    private val database = ClientDatabase(
        app,
        clientDatabaseName(boundSession.apiBaseUrl, boundSession.ownerId, boundSession.deviceId)
    )
    private val api = ApiClient(
        boundSession.apiBaseUrl,
        sessionStore,
        connectTimeoutMillis = HTTP_TIMEOUT_MS,
        readTimeoutMillis = HTTP_TIMEOUT_MS
    )

    fun sync(deadlineElapsedRealtime: Long): HostSyncOutcome {
        ensureCurrent()
        deliverPendingNotifications()
        ensureBudget(deadlineElapsedRealtime)
        val remoteGateways = api.listGateways()
        ensureCurrent()
        val gateway = remoteGateways.firstOrNull()
            ?: throw IOException("No gateway is paired with this account")

        ensureBudget(deadlineElapsedRealtime)
        val (_, remoteSims) = api.listSims(gateway.gatewayId)
        ensureCurrent()
        database.saveGateway(gateway)
        database.replaceSims(remoteSims)

        val inboundCount = syncSms(deadlineElapsedRealtime)
        ensureCurrent()
        return HostSyncOutcome(inboundCount)
    }

    private fun syncSms(deadline: Long): Int {
        val existingCursor = database.eventCursor()
        if (database.isHistoricalBaseline()) database.beginHistoricalBaseline()
        var newInboundCount = 0
        if (existingCursor == null) newInboundCount += snapshotMessages(deadline)

        var cursor = existingCursor
        var pageCount = 0
        do {
            ensureBudget(deadline)
            val page = api.listEvents(cursor)
            ensureCurrent()
            if (page.resyncRequired) newInboundCount += snapshotMessages(deadline)

            val advance = page.nextCursor ?: page.lastItemCursor
            val result = database.applyRemoteEventPage(
                messages = page.messages,
                expectedCursor = cursor,
                cursorValue = advance,
                sessionIsCurrent = { sessionIsCurrent(boundSession) }
            )
            ensureCurrent()
            if (!result.committed) {
                // A foreground sync or another service helper committed this
                // page first. Re-read its cursor and fetch from that point;
                // never replay a stale page into the notification journal.
                cursor = database.eventCursor()
            } else {
                newInboundCount += result.newInboundNotificationIds.size
                cursor = if (page.nextCursor != null && page.nextCursor != cursor) page.nextCursor else null
            }
            deliverPendingNotifications()
            pageCount++
        } while (cursor != null && pageCount < MAX_EVENT_PAGES)
        if (cursor == null && database.isHistoricalBaseline()) database.finishHistoricalBaseline()
        return newInboundCount
    }

    private fun snapshotMessages(deadline: Long): Int {
        // Pin whether this whole paged read belongs to the first history pass;
        // another helper may finish that pass while this network request runs.
        val historicalSnapshot = database.isHistoricalBaseline()
        var snapshotCursor: String? = null
        var pages = 0
        var newInboundCount = 0
        do {
            ensureBudget(deadline)
            val page = api.listMessages(cursor = snapshotCursor)
            ensureCurrent()
            newInboundCount += database.applyRemoteSnapshotPage(
                messages = page.items,
                sessionIsCurrent = { sessionIsCurrent(boundSession) },
                forceHistoricalBaseline = historicalSnapshot
            ).size
            snapshotCursor = page.nextCursor
            pages++
        } while (snapshotCursor != null && pages < MAX_SNAPSHOT_PAGES)
        return newInboundCount
    }

    private fun ensureCurrent() {
        if (!sessionIsCurrent(boundSession)) throw SessionChanged()
    }

    private fun deliverPendingNotifications() {
        val pending = database.pendingBackgroundNotificationIds().take(MAX_COALESCED_NOTIFICATION_IDS)
        if (pending.isEmpty()) return
        ensureCurrent()
        if (persistInboundNotificationIds(pending)) {
            database.acknowledgeBackgroundNotificationIds(pending)
        }
    }

    private fun ensureBudget(deadline: Long) {
        // Each HTTPS call has a 10 second timeout. Leave a margin before the wake-lock deadline.
        if (SystemClock.elapsedRealtime() + HTTP_TIMEOUT_MS + BUDGET_MARGIN_MS >= deadline) {
            throw SyncBudgetExhausted()
        }
    }

    override fun close() = database.close()

    companion object {
        private const val HTTP_TIMEOUT_MS = 10_000
        private const val BUDGET_MARGIN_MS = 1_000L
        private const val MAX_SNAPSHOT_PAGES = 4
        private const val MAX_EVENT_PAGES = 5
        private const val MAX_COALESCED_NOTIFICATION_IDS = 99
    }
}
