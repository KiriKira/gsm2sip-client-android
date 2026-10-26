package com.callagent.host.data

import android.content.Context
import android.os.SystemClock
import java.io.Closeable
import java.io.IOException

data class HostSyncOutcome(val inboundNotificationCount: Int)

class SyncBudgetExhausted : IOException("Background sync time budget exhausted")

/**
 * A sync loop bound to one paired session and one database file. Durable
 * reception receipts are sent only after SQLite commit; it never submits SMS.
 */
class HostSyncEngine(
    context: Context,
    private val boundSession: HostSession,
    private val sessionIsCurrent: (HostSession) -> Boolean,
    private val persistInboundNotificationIds: (List<String>) -> Boolean = { false },
    /** Where [boundSession] lives: the active store, or an inactive mesh node's own store. */
    sessionStore: SessionStore = SessionStore(context.applicationContext)
) : Closeable {
    private val app = context.applicationContext
    private val database = ClientDatabase(app, boundSession.databaseName(), boundSession.nodeScope)
    private val api = ApiClient(
        boundSession.apiBaseUrl,
        sessionStore,
        connectTimeoutMillis = HTTP_TIMEOUT_MS,
        readTimeoutMillis = HTTP_TIMEOUT_MS
    )

    fun sync(deadlineElapsedRealtime: Long): HostSyncOutcome =
        SessionWorkLocks.withLock("sync:${boundSession.sessionInstanceId}") { syncLocked(deadlineElapsedRealtime) }

    private fun syncLocked(deadlineElapsedRealtime: Long): HostSyncOutcome {
        ensureCurrent()
        deliverPendingNotifications()
        val inboundCount = syncSms(deadlineElapsedRealtime)
        ensureCurrent()
        // Receipt failures are retried from the persisted cursor next round.
        // They never roll back local data or the separate notification journal.
        database.eventCursor()?.let { cursor ->
            if (database.syncCheckpoint(boundSession.sessionInstanceId, "acked") != cursor &&
                SystemClock.elapsedRealtime() + HTTP_TIMEOUT_MS + BUDGET_MARGIN_MS < deadlineElapsedRealtime) {
                runCatching {
                    api.acknowledgeEventCursor(cursor)
                    ensureCurrent()
                    database.saveSyncCheckpoint(boundSession.sessionInstanceId, "acked", cursor)
                }
                ensureCurrent()
            }
        }
        // Event delivery does not depend on a successful metadata refresh.
        try { refreshMetadata(deadlineElapsedRealtime) }
        catch (error: SessionChanged) { throw error }
        catch (error: SessionNeedsPairing) { throw error }
        catch (_: IOException) { /* Retry metadata next round, without undoing event delivery. */ }
        return HostSyncOutcome(inboundCount)
    }

    private fun refreshMetadata(deadline: Long) {
        val now = System.currentTimeMillis()
        val last = database.syncCheckpoint(boundSession.sessionInstanceId, "metadata_at")?.toLongOrNull() ?: 0L
        val environment = com.callagent.host.network.NetworkEnvironment.token
        val networkUnchanged = database.syncCheckpoint(boundSession.sessionInstanceId, "metadata_network") == environment
        val normal = com.callagent.host.BuildConfig.STANDBY_METADATA_MS
        val interval = if (com.callagent.host.network.NetworkEnvironment.stable) maxOf(normal, 600_000L) else normal
        if (networkUnchanged && database.loadGateway() != null && last > 0L && now >= last && now - last < interval) return
        ensureBudget(deadline)
        val gateway = api.listGateways().firstOrNull() ?: throw IOException("No paired gateway")
        ensureCurrent()
        ensureBudget(deadline)
        val (_, sims) = api.listSims(gateway.gatewayId)
        ensureCurrent()
        if (database.loadGateway() != gateway) database.saveGateway(gateway)
        if (database.loadSims() != sims) database.replaceSims(sims)
        database.saveSyncCheckpoint(boundSession.sessionInstanceId, "metadata_at", now.toString())
        database.saveSyncCheckpoint(boundSession.sessionInstanceId, "metadata_network", environment)
    }

    /**
     * Fold this node's message history into the shared profile database
     * without alerting, so a node that later becomes active only notifies
     * about messages no other node delivered. Returns true once caught up.
     */
    fun catchUpSilently(deadlineElapsedRealtime: Long): Boolean {
        ensureCurrent()
        syncSms(deadlineElapsedRealtime)
        return !database.isHistoricalBaseline()
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
