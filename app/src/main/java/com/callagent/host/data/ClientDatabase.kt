package com.callagent.host.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import java.util.UUID

class ClientDatabase(context: Context, databaseName: String) : SQLiteOpenHelper(context, databaseName, null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE gateway_state (
              singleton INTEGER PRIMARY KEY CHECK(singleton=1), gateway_id TEXT NOT NULL,
              device_name TEXT NOT NULL, online INTEGER NOT NULL, last_seen_at TEXT,
              mapping_revision INTEGER NOT NULL, root INTEGER, sip_registered INTEGER,
              battery_percent INTEGER, charging INTEGER
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE sim_lines (
              sim_id TEXT PRIMARY KEY, slot_index INTEGER NOT NULL, label TEXT NOT NULL,
              carrier_name TEXT, phone_number TEXT, state TEXT NOT NULL,
              mapping_revision INTEGER NOT NULL, identity_verified INTEGER NOT NULL,
              service_state TEXT
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE messages (
              local_id TEXT PRIMARY KEY, server_id TEXT UNIQUE, command_id TEXT,
              sim_id TEXT, mapping_revision INTEGER, direction TEXT NOT NULL,
              from_address TEXT, to_address TEXT, body TEXT NOT NULL,
              status TEXT NOT NULL, created_at TEXT NOT NULL, expires_at TEXT,
              part_count INTEGER, parts_json TEXT NOT NULL DEFAULT '[]',
              task_key TEXT UNIQUE, gateway_id TEXT, idempotency_body TEXT
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX messages_by_sim_time ON messages(sim_id, created_at)")
        db.execSQL("""
            CREATE TABLE drafts (
              sim_id TEXT PRIMARY KEY, recipient TEXT NOT NULL, body TEXT NOT NULL,
              updated_at TEXT NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE TABLE sync_state (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    @Synchronized
    fun saveGateway(gateway: GatewaySnapshot) {
        writableDatabase.insertWithOnConflict("gateway_state", null, ContentValues().apply {
            put("singleton", 1)
            put("gateway_id", gateway.gatewayId)
            put("device_name", gateway.deviceName)
            put("online", if (gateway.online) 1 else 0)
            put("last_seen_at", gateway.lastSeenAt)
            put("mapping_revision", gateway.mappingRevision)
            putNullable("root", gateway.root)
            putNullable("sip_registered", gateway.sipRegistered)
            putNullable("battery_percent", gateway.batteryPercent)
            putNullable("charging", gateway.charging)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    @Synchronized
    fun loadGateway(): GatewaySnapshot? = readableDatabase.query(
        "gateway_state", null, "singleton=1", null, null, null, null
    ).use { cursor -> if (cursor.moveToFirst()) cursor.toGateway() else null }

    @Synchronized
    fun replaceSims(lines: List<SimLine>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("sim_lines", null, null)
            lines.forEach { line ->
                db.insertOrThrow("sim_lines", null, ContentValues().apply {
                    put("sim_id", line.simId)
                    put("slot_index", line.slotIndex)
                    put("label", line.label)
                    put("carrier_name", line.carrierName)
                    put("phone_number", line.phoneNumber)
                    put("state", line.state)
                    put("mapping_revision", line.mappingRevision)
                    put("identity_verified", if (line.identityVerified) 1 else 0)
                    put("service_state", line.serviceState)
                })
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun loadSims(): List<SimLine> = readableDatabase.query(
        "sim_lines", null, null, null, null, null, "slot_index ASC"
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.toSimLine()) } }

    @Synchronized
    fun upsertRemoteMessage(message: SmsRecord) {
        upsertRemoteMessage(writableDatabase, message)
    }

    private fun upsertRemoteMessage(db: SQLiteDatabase, message: SmsRecord) {
        val existing = message.serverId?.let { findByServerId(db, it) }
        if (existing != null) {
            db.update("messages", ContentValues().apply {
                putRemoteFields(message)
            }, "local_id=?", arrayOf(existing.localId))
        } else {
            db.insertWithOnConflict("messages", null, ContentValues().apply {
                put("local_id", message.localId.ifBlank { UUID.randomUUID().toString() })
                putRemoteFields(message)
            }, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    @Synchronized
    fun insertOutbound(task: OutboundTask) {
        writableDatabase.insertOrThrow("messages", null, ContentValues().apply {
            put("local_id", task.localId)
            put("sim_id", task.simId)
            put("mapping_revision", task.mappingRevision)
            put("direction", "outbound")
            put("to_address", task.to)
            put("body", task.text)
            put("status", task.status)
            put("created_at", task.createdAt)
            put("parts_json", "[]")
            put("task_key", task.idempotencyKey)
            put("gateway_id", task.gatewayId)
            put("idempotency_body", task.retryEnvelope().requestBody())
        })
    }

    @Synchronized
    fun updateTask(localId: String, status: String, accepted: MessageAccepted? = null) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val linkedMessage = accepted?.let { findByServerId(db, it.messageId) }
            val fallbackRecipient = if (accepted == null) null else taskAddress(db, localId)
            if (linkedMessage != null && linkedMessage.localId != localId) {
                db.delete("messages", "local_id=?", arrayOf(linkedMessage.localId))
            }
            val merged = accepted?.let { mergeAcceptedTaskState(status, it, linkedMessage, fallbackRecipient) }
            db.update("messages", ContentValues().apply {
                put("status", merged?.status ?: status)
                if (accepted != null) {
                    put("server_id", accepted.messageId)
                    put("command_id", accepted.commandId)
                    putNullable("part_count", merged?.partCount)
                    put("expires_at", merged?.expiresAt)
                    put("parts_json", partsAsJson(merged?.parts.orEmpty()))
                    put("from_address", merged?.from)
                    put("to_address", merged?.to)
                }
            }, "local_id=?", arrayOf(localId))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun markInterruptedSubmissionsUnknown() {
        writableDatabase.execSQL(
            "UPDATE messages SET status=? WHERE status=? AND server_id IS NULL",
            arrayOf(SmsStatus.UNKNOWN, SmsStatus.SUBMITTING)
        )
    }

    @Synchronized
    fun updateRemoteStatus(messageId: String, status: String, parts: List<SmsPart>, partCount: Int?) {
        writableDatabase.update("messages", ContentValues().apply {
            put("status", status)
            put("parts_json", partsAsJson(parts))
            if (partCount != null) put("part_count", partCount)
        }, "server_id=?", arrayOf(messageId))
    }

    @Synchronized
    fun loadMessages(simId: String?): List<SmsRecord> {
        val selection = if (simId == null) "sim_id IS NULL" else "sim_id=?"
        val args = if (simId == null) null else arrayOf(simId)
        return readableDatabase.query(
            "messages", null, selection, args, null, null, "created_at ASC, local_id ASC"
        ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.toSmsRecord()) } }
    }

    @Synchronized
    fun loadTask(localId: String): SmsRecord? = readableDatabase.query(
        "messages", null, "local_id=?", arrayOf(localId), null, null, null
    ).use { cursor -> if (cursor.moveToFirst()) cursor.toSmsRecord() else null }

    @Synchronized
    fun loadPendingTasks(): List<SmsRecord> = readableDatabase.query(
        "messages", null, "task_key IS NOT NULL AND (status=? OR status=?)",
        arrayOf(SmsStatus.UNKNOWN, SmsStatus.SUBMITTING), null, null, "created_at ASC"
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.toSmsRecord()) } }

    @Synchronized
    fun loadOutboundTasks(): List<SmsRecord> = readableDatabase.query(
        "messages", null, "direction='outbound' AND task_key IS NOT NULL",
        null, null, null, "created_at ASC"
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.toSmsRecord()) } }

    @Synchronized
    fun loadDraft(simId: String): SmsDraft? = readableDatabase.query(
        "drafts", null, "sim_id=?", arrayOf(simId), null, null, null
    ).use { cursor -> if (cursor.moveToFirst()) cursor.toDraft() else null }

    @Synchronized
    fun saveDraft(draft: SmsDraft) {
        writableDatabase.insertWithOnConflict("drafts", null, ContentValues().apply {
            put("sim_id", draft.simId)
            put("recipient", draft.recipient)
            put("body", draft.text)
            put("updated_at", java.time.Instant.now().toString())
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    @Synchronized
    fun clearDraft(simId: String) {
        writableDatabase.delete("drafts", "sim_id=?", arrayOf(simId))
    }

    @Synchronized
    fun eventCursor(): String? = readableDatabase.query(
        "sync_state", arrayOf("value"), "key=?", arrayOf("events_cursor"), null, null, null
    ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

    @Synchronized
    fun saveEventCursor(cursorValue: String) {
        saveEventCursor(writableDatabase, cursorValue)
    }

    private fun saveEventCursor(db: SQLiteDatabase, cursorValue: String) {
        db.insertWithOnConflict("sync_state", null, ContentValues().apply {
            put("key", "events_cursor")
            put("value", cursorValue)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    /** True while the first event-history pass is intentionally silent. */
    @Synchronized
    fun isHistoricalBaseline(): Boolean = isHistoricalBaseline(writableDatabase)

    @Synchronized
    fun beginHistoricalBaseline() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            // Another ClientDatabase instance may have completed the initial
            // history pass after this caller observed the old state.
            if (!isHistoricalBaseline(db)) return
            saveSyncStateValue(db, BASELINE_IN_PROGRESS_KEY, "1")
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun finishHistoricalBaseline() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            saveSyncStateValue(db, BASELINE_IN_PROGRESS_KEY, "0")
            saveSyncStateValue(db, BOOTSTRAPPED_KEY, "1")
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Merge one messages snapshot page and journal newly discovered inbound IDs
     * in one transaction. During the initial historical baseline, the IDs are
     * remembered silently so the event replay does not alert on old mail.
     */
    @Synchronized
    fun applyRemoteSnapshotPage(
        messages: List<SmsRecord>,
        sessionIsCurrent: () -> Boolean = { true },
        forceHistoricalBaseline: Boolean = false
    ): List<String> {
        val db = writableDatabase
        db.beginTransaction()
        try {
            if (!sessionIsCurrent()) throw SessionChanged()
            val ids = messages.mapNotNull { it.serverId?.takeIf(String::isNotBlank) }.distinct()
            val previouslyCached = existingServerMessageIds(db, ids)
            messages.forEach { upsertRemoteMessage(db, it) }
            val uncached = ids.filterNot(previouslyCached::contains)
            val inboundIds = messages.asSequence()
                .filter { it.direction == "inbound" }
                .mapNotNull { it.serverId }
                .filterNot(previouslyCached::contains)
                .toList()
            val fresh = if (uncached.isEmpty()) emptyList() else recordBackgroundInboundIds(
                db, uncached, baseline = forceHistoricalBaseline || isHistoricalBaseline(db), inboundIds = inboundIds
            )
            if (!sessionIsCurrent()) throw SessionChanged()
            db.setTransactionSuccessful()
            return fresh
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Atomically merge an event page, record its inbound-alert journal, and
     * advance its cursor. The expected-cursor check fences stale pages when the
     * activity and foreground service each use their own SQLiteOpenHelper.
     */
    @Synchronized
    fun applyRemoteEventPage(
        messages: List<SmsRecord>,
        expectedCursor: String?,
        cursorValue: String?,
        sessionIsCurrent: () -> Boolean = { true }
    ): RemoteEventPageResult {
        val db = writableDatabase
        db.beginTransaction()
        try {
            if (!sessionIsCurrent()) throw SessionChanged()
            if (eventCursor(db) != expectedCursor) {
                db.setTransactionSuccessful()
                return RemoteEventPageResult(committed = false, newInboundNotificationIds = emptyList())
            }

            val ids = messages.mapNotNull { it.serverId?.takeIf(String::isNotBlank) }.distinct()
            val previouslyCached = existingServerMessageIds(db, ids)
            messages.forEach { upsertRemoteMessage(db, it) }

            val fresh = if (isHistoricalBaseline(db)) {
                recordBackgroundInboundIds(
                    db,
                    ids,
                    baseline = true,
                    inboundIds = messages.asSequence()
                        .filter { it.direction == "inbound" }
                        .mapNotNull { it.serverId }
                        .toList()
                )
            } else {
                val cachedIds = ids.filter(previouslyCached::contains)
                if (cachedIds.isNotEmpty()) recordBackgroundInboundIds(db, cachedIds, baseline = true)
                val uncachedIds = ids.filterNot(previouslyCached::contains)
                val inboundIds = messages.asSequence()
                    .filter { it.direction == "inbound" }
                    .mapNotNull { it.serverId }
                    .filterNot(previouslyCached::contains)
                    .toList()
                if (uncachedIds.isEmpty()) emptyList() else recordBackgroundInboundIds(
                    db, uncachedIds, baseline = false, inboundIds = inboundIds
                )
            }

            cursorValue?.let { saveEventCursor(db, it) }
            if (!sessionIsCurrent()) throw SessionChanged()
            db.setTransactionSuccessful()
            return RemoteEventPageResult(committed = true, newInboundNotificationIds = fresh)
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Mark inbound server message IDs as observed for background notification de-duplication.
     * The IDs live in the existing sync_state table so this does not change the database schema.
     * When baseline is true, IDs are recorded silently and no IDs are returned for notification.
     */
    @Synchronized
    fun recordBackgroundInboundIds(
        ids: List<String>,
        baseline: Boolean,
        inboundIds: List<String> = ids,
        limit: Int = 2048
    ): List<String> {
        if (ids.isEmpty()) return emptyList()
        val db = writableDatabase
        db.beginTransaction()
        try {
            val fresh = recordBackgroundInboundIds(db, ids, baseline, inboundIds, limit)
            db.setTransactionSuccessful()
            return fresh
        } finally {
            db.endTransaction()
        }
    }

    private fun recordBackgroundInboundIds(
        db: SQLiteDatabase,
        ids: List<String>,
        baseline: Boolean,
        inboundIds: List<String> = ids,
        limit: Int = 2048
    ): List<String> {
        if (ids.isEmpty()) return emptyList()
        val old = readSyncStateValue(db, BACKGROUND_SMS_SEEN_KEY)
        val seen = linkedSetOf<String>()
        runCatching {
            val json = JSONArray(old ?: "[]")
            for (index in 0 until json.length()) json.optString(index).takeIf { it.isNotBlank() }?.let(seen::add)
        }
        val fresh = if (baseline) emptyList() else inboundIds.filter { it in ids && it !in seen }.distinct()
        ids.forEach { id -> if (id.isNotBlank()) seen.add(id) }
        while (seen.size > limit.coerceAtLeast(1)) seen.remove(seen.first())
        val encoded = JSONArray().also { array -> seen.forEach { array.put(it) } }.toString()
        saveSyncStateValue(db, BACKGROUND_SMS_SEEN_KEY, encoded)
        if (fresh.isNotEmpty()) {
            val pending = linkedSetOf<String>()
            readSyncStateValue(db, BACKGROUND_SMS_PENDING_KEY)?.let { raw ->
                runCatching {
                    val json = JSONArray(raw)
                    for (index in 0 until json.length()) json.optString(index).takeIf { it.isNotBlank() }?.let(pending::add)
                }
            }
            fresh.forEach(pending::add)
            val pendingJson = JSONArray().also { array -> pending.forEach { array.put(it) } }.toString()
            saveSyncStateValue(db, BACKGROUND_SMS_PENDING_KEY, pendingJson)
        }
        return fresh
    }

    @Synchronized
    fun pendingBackgroundNotificationIds(): List<String> = syncStateValue("background_sms_pending_alerts")
        ?.let { raw ->
            runCatching {
                val json = JSONArray(raw)
                (0 until json.length()).mapNotNull { index -> json.optString(index).takeIf { it.isNotBlank() } }
            }.getOrDefault(emptyList())
        }
        .orEmpty()

    @Synchronized
    fun acknowledgeBackgroundNotificationIds(ids: List<String>) {
        if (ids.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            val pending = readSyncStateValue(db, "background_sms_pending_alerts")
                ?.let { raw -> runCatching { JSONArray(raw) }.getOrNull() }
            val remaining = linkedSetOf<String>()
            if (pending != null) {
                for (index in 0 until pending.length()) pending.optString(index).takeIf { it.isNotBlank() && it !in ids }?.let(remaining::add)
            }
            val encoded = JSONArray().also { array -> remaining.forEach { array.put(it) } }.toString()
            db.insertWithOnConflict("sync_state", null, ContentValues().apply {
                put("key", "background_sms_pending_alerts")
                put("value", encoded)
            }, SQLiteDatabase.CONFLICT_REPLACE)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun clearBackgroundNotificationQueue() {
        writableDatabase.insertWithOnConflict("sync_state", null, ContentValues().apply {
            put("key", "background_sms_pending_alerts")
            put("value", "[]")
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    @Synchronized
    fun existingServerMessageIds(ids: List<String>): Set<String> {
        if (ids.isEmpty()) return emptySet()
        return existingServerMessageIds(readableDatabase, ids)
    }

    private fun existingServerMessageIds(db: SQLiteDatabase, ids: List<String>): Set<String> {
        if (ids.isEmpty()) return emptySet()
        val found = linkedSetOf<String>()
        // Stay under SQLite's bind-parameter limit on older Android releases.
        ids.distinct().chunked(500).forEach { chunk ->
            val marks = chunk.joinToString(",") { "?" }
            db.query(
                "messages", arrayOf("server_id"), "server_id IN ($marks)", chunk.toTypedArray(), null, null, null
            ).use { cursor -> while (cursor.moveToNext()) found += cursor.getString(0) }
        }
        return found
    }

    @Synchronized
    fun syncStateValue(key: String): String? = readableDatabase.query(
        "sync_state", arrayOf("value"), "key=?", arrayOf(key), null, null, null
    ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

    @Synchronized
    fun saveSyncStateValue(key: String, value: String) {
        saveSyncStateValue(writableDatabase, key, value)
    }

    private fun saveSyncStateValue(db: SQLiteDatabase, key: String, value: String) {
        db.insertWithOnConflict("sync_state", null, ContentValues().apply {
            put("key", key)
            put("value", value)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun eventCursor(db: SQLiteDatabase): String? = readSyncStateValue(db, EVENT_CURSOR_KEY)

    private fun isHistoricalBaseline(db: SQLiteDatabase): Boolean =
        readSyncStateValue(db, BASELINE_IN_PROGRESS_KEY) == "1" ||
            (eventCursor(db) == null && readSyncStateValue(db, BOOTSTRAPPED_KEY) != "1")

    private fun readSyncStateValue(db: SQLiteDatabase, key: String): String? = db.query(
        "sync_state", arrayOf("value"), "key=?", arrayOf(key), null, null, null
    ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

    @Synchronized
    fun clearAll() {
        val db = writableDatabase
        db.delete("gateway_state", null, null)
        db.delete("sim_lines", null, null)
        db.delete("messages", null, null)
        db.delete("drafts", null, null)
        db.delete("sync_state", null, null)
    }

    private fun findByServerId(db: SQLiteDatabase, serverId: String): SmsRecord? = db.query(
        "messages", null, "server_id=?", arrayOf(serverId), null, null, null
    ).use { cursor -> if (cursor.moveToFirst()) cursor.toSmsRecord() else null }

    private fun taskAddress(db: SQLiteDatabase, localId: String): String? = db.query(
        "messages", arrayOf("to_address"), "local_id=?", arrayOf(localId), null, null, null
    ).use { cursor -> if (cursor.moveToFirst()) cursor.getNullableString("to_address") else null }

    private fun ContentValues.putRemoteFields(message: SmsRecord) {
        put("server_id", message.serverId)
        put("command_id", message.commandId)
        put("sim_id", message.simId)
        putNullable("mapping_revision", message.mappingRevision)
        put("direction", message.direction)
        put("from_address", message.from)
        put("to_address", message.to)
        put("body", message.text)
        put("status", message.status)
        put("created_at", message.createdAt)
        put("expires_at", message.expiresAt)
        putNullable("part_count", message.partCount)
        put("parts_json", partsAsJson(message.parts))
        put("gateway_id", message.gatewayId)
    }

    private fun ContentValues.putNullable(key: String, value: Long?) {
        if (value == null) putNull(key) else put(key, value)
    }

    private fun ContentValues.putNullable(key: String, value: Int?) {
        if (value == null) putNull(key) else put(key, value)
    }

    private fun ContentValues.putNullable(key: String, value: Boolean?) {
        if (value == null) putNull(key) else put(key, if (value) 1 else 0)
    }

    private fun Cursor.toGateway() = GatewaySnapshot(
        gatewayId = getString(getColumnIndexOrThrow("gateway_id")),
        deviceName = getString(getColumnIndexOrThrow("device_name")),
        online = getInt(getColumnIndexOrThrow("online")) != 0,
        lastSeenAt = getNullableString("last_seen_at"),
        mappingRevision = getLong(getColumnIndexOrThrow("mapping_revision")),
        root = getNullableInt("root")?.let { it != 0 },
        sipRegistered = getNullableInt("sip_registered")?.let { it != 0 },
        batteryPercent = getNullableInt("battery_percent"),
        charging = getNullableInt("charging")?.let { it != 0 }
    )

    private companion object {
        const val EVENT_CURSOR_KEY = "events_cursor"
        const val BOOTSTRAPPED_KEY = "background_events_bootstrapped"
        const val BASELINE_IN_PROGRESS_KEY = "background_events_baseline_in_progress"
        const val BACKGROUND_SMS_SEEN_KEY = "background_sms_seen"
        const val BACKGROUND_SMS_PENDING_KEY = "background_sms_pending_alerts"
    }

    private fun Cursor.toSimLine() = SimLine(
        simId = getString(getColumnIndexOrThrow("sim_id")),
        slotIndex = getInt(getColumnIndexOrThrow("slot_index")),
        label = getString(getColumnIndexOrThrow("label")),
        carrierName = getNullableString("carrier_name"),
        phoneNumber = getNullableString("phone_number"),
        state = getString(getColumnIndexOrThrow("state")),
        mappingRevision = getLong(getColumnIndexOrThrow("mapping_revision")),
        identityVerified = getInt(getColumnIndexOrThrow("identity_verified")) != 0,
        serviceState = getNullableString("service_state")
    )

    private fun Cursor.toSmsRecord(): SmsRecord {
        val partsText = getString(getColumnIndexOrThrow("parts_json"))
        return SmsRecord(
            localId = getString(getColumnIndexOrThrow("local_id")),
            serverId = getNullableString("server_id"),
            commandId = getNullableString("command_id"),
            simId = getNullableString("sim_id"),
            mappingRevision = getNullableLong("mapping_revision"),
            direction = getString(getColumnIndexOrThrow("direction")),
            from = getNullableString("from_address"),
            to = getNullableString("to_address"),
            text = getString(getColumnIndexOrThrow("body")),
            status = getString(getColumnIndexOrThrow("status")),
            createdAt = getString(getColumnIndexOrThrow("created_at")),
            expiresAt = getNullableString("expires_at"),
            partCount = getNullableInt("part_count"),
            parts = parseSmsParts(JSONArray(partsText)),
            taskKey = getNullableString("task_key"),
            gatewayId = getNullableString("gateway_id"),
            idempotencyBody = getNullableString("idempotency_body")
        )
    }

    private fun Cursor.toDraft() = SmsDraft(
        simId = getString(getColumnIndexOrThrow("sim_id")),
        recipient = getString(getColumnIndexOrThrow("recipient")),
        text = getString(getColumnIndexOrThrow("body"))
    )

    private fun Cursor.getNullableString(name: String): String? {
        val index = getColumnIndexOrThrow(name)
        return if (isNull(index)) null else getString(index)
    }

    private fun Cursor.getNullableInt(name: String): Int? {
        val index = getColumnIndexOrThrow(name)
        return if (isNull(index)) null else getInt(index)
    }

    private fun Cursor.getNullableLong(name: String): Long? {
        val index = getColumnIndexOrThrow(name)
        return if (isNull(index)) null else getLong(index)
    }
}

data class RemoteEventPageResult(
    val committed: Boolean,
    val newInboundNotificationIds: List<String>
)

data class MessageAccepted(
    val messageId: String,
    val commandId: String,
    val status: String,
    val expiresAt: String,
    val partCount: Int?
)
