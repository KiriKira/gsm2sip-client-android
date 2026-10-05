package com.callagent.host.backup

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.callagent.backup.SmsArchiveCodec
import com.callagent.backup.SmsArchiveRecord
import com.callagent.backup.SmsArchiveRecordProvider
import com.callagent.backup.SmsBackupActivity
import com.callagent.host.data.HostSession
import com.callagent.host.data.SessionChanged
import com.callagent.host.data.SessionStore
import com.callagent.host.data.clientDatabaseName
import com.callagent.host.data.sameSessionInstance
import java.io.IOException
import java.io.Closeable
import java.net.URI
import java.time.Instant
import java.time.OffsetDateTime
import java.util.concurrent.CancellationException
import java.util.NoSuchElementException

/** The only app-specific part of SMS backup. This provider is process scoped and holds no UI. */
class HostSmsBackupActivity : SmsBackupActivity() {
    override fun recordProvider(): SmsArchiveRecordProvider = HostSmsArchiveRecordProvider
}

internal object HostSmsArchiveRecordProvider : SmsArchiveRecordProvider {
    override fun records(context: Context): Sequence<SmsArchiveRecord> =
        HostSmsArchiveSnapshotSequence(context.applicationContext)
}

/** A single-use sequence whose explicit close releases a partially consumed SQLite snapshot. */
internal class HostSmsArchiveSnapshotSequence(context: Context) : Sequence<SmsArchiveRecord>, Closeable {
    private val app = context.applicationContext
    private val sessions = SessionStore(app)
    private var started = false
    private var closed = false
    private var database: SQLiteDatabase? = null
    private var messageCursor: Cursor? = null
    private var expectedSession: HostSession? = null
    private var source: String? = null
    private var observedAt = 0L

    override fun iterator(): Iterator<SmsArchiveRecord> {
        check(!started) { "An SMS archive snapshot can only be iterated once" }
        started = true
        try {
            openSnapshot()
        } catch (failure: Throwable) {
            close()
            throw failure
        }

        return object : Iterator<SmsArchiveRecord> {
            private var cached: SmsArchiveRecord? = null
            private var hasCached = false

            override fun hasNext(): Boolean {
                if (hasCached) return true
                if (closed) return false
                try {
                    cacheNext()
                    return hasCached
                } catch (failure: Throwable) {
                    close()
                    throw failure
                }
            }

            override fun next(): SmsArchiveRecord {
                try {
                    if (!hasNext()) throw NoSuchElementException()
                    ensureCurrent()
                } catch (failure: Throwable) {
                    close()
                    throw failure
                }
                hasCached = false
                return cached!!.also { cached = null }
            }

            private fun cacheNext() {
                ensureCurrent()
                val cursor = messageCursor ?: run {
                    close()
                    return
                }
                if (!cursor.moveToNext()) {
                    ensureCurrent()
                    close()
                    return
                }
                val boundSession = expectedSession ?: throw SessionChanged()
                val recordSource = source ?: throw SessionChanged()
                val localId = cursor.getString(cursor.getColumnIndexOrThrow("local_id"))
                val serverId = cursor.nullableString("server_id")?.takeIf(String::isNotBlank)
                val gatewayId = cursor.nullableString("gateway_id")
                val simId = cursor.nullableString("sim_id")
                cached = SmsArchiveRecord(
                    id = SmsArchiveCodec.stableId(
                        source = recordSource,
                        ownerId = boundSession.ownerId,
                        gatewayId = gatewayId,
                        nativeId = serverId ?: "local:$localId"
                    ),
                    source = recordSource,
                    ownerId = boundSession.ownerId,
                    gatewayId = gatewayId,
                    messageId = serverId,
                    simId = simId,
                    simLabel = cursor.nullableString("sim_label"),
                    direction = cursor.getString(cursor.getColumnIndexOrThrow("direction")),
                    from = cursor.nullableString("from_address"),
                    to = cursor.nullableString("to_address"),
                    body = cursor.getString(cursor.getColumnIndexOrThrow("body")),
                    createdAt = parseArchiveTime(cursor.getString(cursor.getColumnIndexOrThrow("created_at"))),
                    status = cursor.getString(cursor.getColumnIndexOrThrow("status")),
                    observedAt = observedAt,
                    slotIndex = cursor.nullableInt("sim_slot_index"),
                    subscriptionId = null,
                    partCount = cursor.nullableInt("part_count")
                )
                hasCached = true
            }
        }
    }

    private fun openSnapshot() {
        val boundSession = sessions.read() ?: run {
            close()
            return
        }
        expectedSession = boundSession
        source = canonicalArchiveSource(boundSession.apiBaseUrl)
        val databaseName = archiveDatabaseName(boundSession)
        ensureCurrent()

        val databaseFile = app.getDatabasePath(databaseName)
        if (!databaseFile.isFile) {
            ensureCurrent()
            close()
            return
        }

        // One read-only SELECT pins SQLite's implicit read transaction for the lifetime
        // of its cursor. Joining sim_lines in that statement keeps labels and messages in
        // exactly the same snapshot without loading message bodies into memory.
        val db = SQLiteDatabase.openDatabase(
            databaseFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
        )
        database = db
        ensureCurrent()
        observedAt = System.currentTimeMillis()
        messageCursor = db.rawQuery(
            """SELECT m.local_id, m.server_id, m.sim_id, m.direction,
               m.from_address, m.to_address, m.body, m.status, m.created_at,
               m.part_count, m.gateway_id, s.label AS sim_label, s.slot_index AS sim_slot_index
               FROM messages AS m
               LEFT JOIN sim_lines AS s
                 ON m.sim_id = s.sim_id AND m.mapping_revision = s.mapping_revision
               ORDER BY m.created_at ASC, m.local_id ASC""".trimIndent(),
            null
        )
    }

    private fun ensureCurrent() {
        if (Thread.currentThread().isInterrupted) throw CancellationException("SMS archive snapshot was cancelled")
        val expected = expectedSession ?: return
        val current = sessions.read()
        if (current == null || !current.sameSessionInstance(expected)) throw SessionChanged()
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        runCatching { messageCursor?.close() }
        messageCursor = null
        val db = database
        database = null
        if (db != null) runCatching { db.close() }
    }
}

/** Keep exported provenance useful without copying credentials, user-info, or URL parameters. */
internal fun canonicalArchiveSource(apiBaseUrl: String): String {
    val uri = try {
        URI(apiBaseUrl.trim()).normalize()
    } catch (failure: Exception) {
        throw IOException("The paired server URL is invalid", failure)
    }
    if (!uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrBlank() ||
        uri.userInfo != null || uri.query != null || uri.fragment != null
    ) {
        throw IOException("The paired server URL cannot be used as archive provenance")
    }

    val host = uri.host.lowercase()
    val authorityHost = if (host.contains(':') && !host.startsWith("[")) "[$host]" else host
    val port = if (uri.port == -1 || uri.port == 443) "" else ":${uri.port}"
    val rawPath = uri.rawPath.orEmpty().trimEnd('/')
    val path = if (rawPath == "/v1" || rawPath.endsWith("/v1")) rawPath else "$rawPath/v1"
    return "https://$authorityHost$port$path"
}

internal fun archiveDatabaseName(session: HostSession): String {
    canonicalArchiveSource(session.apiBaseUrl)
    return clientDatabaseName(session.apiBaseUrl, session.ownerId, session.deviceId)
}

private fun parseArchiveTime(value: String): Long = try {
    try {
        Instant.parse(value).toEpochMilli()
    } catch (_: Exception) {
        OffsetDateTime.parse(value).toInstant().toEpochMilli()
    }
} catch (failure: Exception) {
    throw IOException("A cached SMS has an invalid creation time", failure)
}

private fun Cursor.nullableString(name: String): String? {
    val column = getColumnIndexOrThrow(name)
    return if (isNull(column)) null else getString(column)
}

private fun Cursor.nullableInt(name: String): Int? {
    val column = getColumnIndexOrThrow(name)
    return if (isNull(column)) null else getInt(column)
}
