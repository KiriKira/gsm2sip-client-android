package com.callagent.host.ui

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.callagent.host.data.ApiClient
import com.callagent.host.data.ApiFailure
import com.callagent.host.data.ClientPreferences
import com.callagent.host.data.HostSession
import com.callagent.host.data.SessionStore
import java.util.concurrent.Executors

/** Keeps an unfinished pairing form through Activity configuration changes without retaining a View. */
class PairingFormViewModel : ViewModel() {
    @Volatile
    var serverUrl: String = ""
        private set
    @Volatile
    var deviceName: String = ""
        private set
    @Volatile
    var pairingCode: String = ""
        private set
    private val operationLock = Any()
    private val pairingExecutor = Executors.newSingleThreadExecutor()
    private var pairingClaim: (Context, String, String, String) -> HostSession = { appContext, server, code, name ->
        ApiClient(server, SessionStore(appContext)).pair(code, name)
    }
    private val mutablePairingOperation = MutableLiveData(PairingOperationState())
    val pairingOperation: LiveData<PairingOperationState> = mutablePairingOperation
    private var pairingInProgress = false
    private var nextPairingOperationId = 0L
    private var pendingOutcomeId: Long? = null
    @Volatile private var initialized = false

    val hasPendingOrRunningPairing: Boolean
        get() = synchronized(operationLock) { pairingInProgress || pendingOutcomeId != null }

    fun initialize(
        defaultServerUrl: String,
        defaultDeviceName: String,
        restoredServerUrl: String? = null,
        restoredDeviceName: String? = null,
    ) {
        synchronized(operationLock) {
            if (initialized) return
            serverUrl = restoredServerUrl ?: defaultServerUrl
            deviceName = restoredDeviceName ?: defaultDeviceName
            pairingCode = ""
            initialized = true
        }
    }

    fun updateServerUrl(value: String) {
        synchronized(operationLock) { serverUrl = value }
    }

    fun updateDeviceName(value: String) {
        synchronized(operationLock) { deviceName = value }
    }

    fun updatePairingCode(value: String) {
        synchronized(operationLock) { pairingCode = value }
    }

    fun clearPairingCode() {
        synchronized(operationLock) { pairingCode = "" }
    }

    /** The pairing job belongs to the retained ViewModel, so rotating the Activity cannot cancel it. */
    fun startPairing(context: Context, serverUrl: String, code: String, deviceName: String): Boolean {
        val requestId = synchronized(operationLock) {
            if (pairingInProgress || pendingOutcomeId != null) return false
            pairingInProgress = true
            ++nextPairingOperationId
        }
        mutablePairingOperation.value = PairingOperationState(isRunning = true)
        val appContext = context.applicationContext
        val claim = pairingClaim
        pairingExecutor.execute {
            var pairedSession: HostSession? = null
            val failure = try {
                pairedSession = claim(appContext, serverUrl, code, deviceName)
                ClientPreferences(appContext).apiBaseUrl = serverUrl
                null
            } catch (error: Exception) {
                error
            }
            synchronized(operationLock) {
                pairingInProgress = false
                pendingOutcomeId = requestId
                if (failure == null) {
                    clear()
                } else if (failure is ApiFailure && failure.code == "INVALID_PAIRING_CODE") {
                    pairingCode = ""
                }
            }
            mutablePairingOperation.postValue(
                PairingOperationState(
                    isRunning = false,
                    outcome = PairingOperationOutcome(requestId, pairedSession, failure),
                )
            )
        }
        return true
    }

    internal fun setPairingClaimForTest(claim: (Context, String, String, String) -> HostSession) {
        synchronized(operationLock) {
            check(!pairingInProgress && pendingOutcomeId == null) { "Cannot replace the pairing claim while it is active" }
            pairingClaim = claim
        }
    }

    fun consumeOutcome(requestId: Long) {
        synchronized(operationLock) {
            if (pendingOutcomeId != requestId) return
            pendingOutcomeId = null
        }
        mutablePairingOperation.value = PairingOperationState()
    }

    fun clear() {
        synchronized(operationLock) {
            serverUrl = ""
            deviceName = ""
            pairingCode = ""
            initialized = false
        }
    }

    override fun onCleared() {
        pairingExecutor.shutdownNow()
        super.onCleared()
    }
}

data class PairingOperationState(
    val isRunning: Boolean = false,
    val outcome: PairingOperationOutcome? = null,
)

data class PairingOperationOutcome(
    val requestId: Long,
    val session: HostSession?,
    val failure: Exception?,
)
