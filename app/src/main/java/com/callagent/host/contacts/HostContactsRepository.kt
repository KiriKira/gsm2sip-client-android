package com.callagent.host.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/** One locally stored phone number, suitable for a dial candidate or local history label. */
data class ContactEntry(
    /** Contacts data-row ID. A contact with several numbers has one entry per number. */
    val id: Long,
    val contactId: Long,
    val name: String,
    val number: String,
    val normalizedNumber: String?
)

/** Reads names and phone numbers from this device's Contacts Provider only. */
class HostContactsRepository private constructor(
    private val worker: Executor,
    private val callbackExecutor: Executor,
    private val hasReadPermission: () -> Boolean,
    private val readContacts: (String?) -> List<ContactEntry>
) {
    constructor(context: Context) : this(
        sharedWorker,
        mainThread,
        { context.applicationContext.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED },
        { query -> queryProvider(context.applicationContext, query) }
    )

    /** Internal seam keeps permission and executor behavior covered without reading real user contacts. */
    internal constructor(
        @Suppress("UNUSED_PARAMETER") context: Context,
        worker: Executor,
        callbackExecutor: Executor,
        hasReadPermission: () -> Boolean,
        readContacts: (String?) -> List<ContactEntry>
    ) : this(worker, callbackExecutor, hasReadPermission, readContacts)

    /** Runs the provider query on a worker and delivers its result on the main thread. */
    fun queryAsync(query: String? = null, callback: (Result<List<ContactEntry>>) -> Unit) {
        worker.execute {
            val result = runCatching {
                if (!hasReadPermission()) emptyList()
                else try {
                    readContacts(query)
                } catch (_: SecurityException) {
                    // Permission may be revoked between the check and the provider query.
                    emptyList()
                }
            }
            callbackExecutor.execute { callback(result) }
        }
    }

    private companion object {
        private val sharedWorker = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "host-local-contacts").apply { isDaemon = true }
        }
        private val mainThread = Executor { command -> Handler(Looper.getMainLooper()).post(command) }

        private fun queryProvider(context: Context, query: String?): List<ContactEntry> {
            val columns = arrayOf(
                ContactsContract.CommonDataKinds.Phone._ID,
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER
            )
            val needle = query?.trim()?.takeIf { it.isNotEmpty() }
            val selectionArgs = mutableListOf<String>()
            val selection = needle?.let {
                val clauses = mutableListOf(
                    "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                    "${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?"
                )
                selectionArgs += "%$it%"
                selectionArgs += "%$it%"
                val digits = it.filter { char -> char.isDigit() || char == '+' }
                if (digits.any { char -> char.isDigit() }) {
                    clauses += "${ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER} LIKE ?"
                    selectionArgs += "%$digits%"
                }
                clauses.joinToString(" OR ")
            }

            return context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                columns,
                selection,
                selectionArgs.takeIf { needle != null }?.toTypedArray(),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE LOCALIZED ASC, " +
                    "${ContactsContract.CommonDataKinds.Phone._ID} ASC"
            )?.use(::readEntries).orEmpty()
        }

        private fun readEntries(cursor: Cursor): List<ContactEntry> {
            val idColumn = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone._ID)
            val contactIdColumn = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameColumn = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberColumn = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val normalizedColumn = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER)
            if (idColumn < 0 || contactIdColumn < 0 || nameColumn < 0 || numberColumn < 0) return emptyList()

            val entries = ArrayList<ContactEntry>()
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameColumn)?.trim().orEmpty()
                val number = cursor.getString(numberColumn)?.trim().orEmpty()
                if (name.isBlank() || number.isBlank()) continue
                entries += ContactEntry(
                    id = cursor.getLong(idColumn),
                    contactId = cursor.getLong(contactIdColumn),
                    name = name,
                    number = number,
                    normalizedNumber = normalizedColumn.takeIf { it >= 0 }
                        ?.let { cursor.getString(it)?.takeIf(String::isNotBlank) }
                )
            }
            return entries
        }
    }
}
