package com.callagent.host.data

import android.content.Context
import android.os.SystemClock
import com.callagent.host.background.HostBackgroundPolicy
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
        val bootstrapped = database.syncStateValue(BOOTSTRAPPED_KEY) == "1"
        val baselineInProgress = database.syncStateValue(BASELINE_IN_PROGRESS_KEY) == "1"
        val baseline = HostBackgroundPolicy.isHistoricalBaseline(existingCursor != null, bootstrapped, baselineInProgress)
        if (baseline) database.saveSyncStateValue(BASELINE_IN_PROGRESS_KEY, "1")
        if (existingCursor == null) snapshotMessages(deadline)

        var cursor = existingCursor
        var pageCount = 0
        var newInboundCount = 0
        do {
            ensureBudget(deadline)
            val page = api.listEvents(cursor)
            ensureCurrent()
            if (page.resyncRequired) snapshotMessages(deadline)

            val records = page.messages
            val ids = records.mapNotNull { it.serverId?.takeIf { id -> id.isNotBlank() } }
            val previouslyCached = database.existingServerMessageIds(ids)
            records.forEach { record ->
                ensureCurrent()
                database.upsertRemoteMessage(record)
            }

            if (baseline || page.resyncRequired) {
                database.recordBackgroundInboundIds(
                    ids,
                    baseline = true,
                    inboundIds = records.asSequence().filter { it.direction == "inbound" }.mapNotNull { it.serverId }.toList()
                )
            } else {
                val cachedIds = ids.filter { it in previouslyCached }
                if (cachedIds.isNotEmpty()) database.recordBackgroundInboundIds(cachedIds, baseline = true)
                val uncachedIds = ids.filterNot(previouslyCached::contains)
                val inboundIds = records.asSequence()
                    .filter { it.direction == "inbound" }
                    .mapNotNull { it.serverId }
                    .filterNot(previouslyCached::contains)
                    .toList()
                val newInboundIds = database.recordBackgroundInboundIds(
                    uncachedIds,
                    baseline = false,
                    inboundIds = inboundIds
                )
                newInboundCount += HostBackgroundPolicy.newInboundNotificationIds(records, newInboundIds.toSet()).size
            }

            val advance = page.nextCursor ?: page.lastItemCursor
            if (advance != null) {
                ensureCurrent()
                database.saveEventCursor(advance)
                cursor = if (page.nextCursor != null && page.nextCursor != cursor) page.nextCursor else null
            } else {
                cursor = null
            }
            deliverPendingNotifications()
            pageCount++
        } while (cursor != null && pageCount < MAX_EVENT_PAGES)
        if (baseline) {
            if (cursor != null) {
                database.saveSyncStateValue(BASELINE_IN_PROGRESS_KEY, "1")
            } else {
                database.saveSyncStateValue(BASELINE_IN_PROGRESS_KEY, "0")
                database.saveSyncStateValue(BOOTSTRAPPED_KEY, "1")
            }
        }
        return newInboundCount
    }

    private fun snapshotMessages(deadline: Long) {
        var snapshotCursor: String? = null
        var pages = 0
        do {
            ensureBudget(deadline)
            val page = api.listMessages(cursor = snapshotCursor)
            ensureCurrent()
            page.items.forEach { record ->
                ensureCurrent()
                database.upsertRemoteMessage(record)
            }
            val inboundIds = page.items.asSequence()
                .filter { it.direction == "inbound" }
                .mapNotNull { it.serverId }
                .toList()
            database.recordBackgroundInboundIds(inboundIds, baseline = true)
            snapshotCursor = page.nextCursor
            pages++
        } while (snapshotCursor != null && pages < MAX_SNAPSHOT_PAGES)
    }

    private fun ensureCurrent() {
        if (!sessionIsCurrent(boundSession)) throw SessionChanged()
    }

    private fun deliverPendingNotifications() {
        val pending = database.pendingBackgroundNotificationIds()
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
        private const val BOOTSTRAPPED_KEY = "background_events_bootstrapped"
        private const val BASELINE_IN_PROGRESS_KEY = "background_events_baseline_in_progress"
    }
}
