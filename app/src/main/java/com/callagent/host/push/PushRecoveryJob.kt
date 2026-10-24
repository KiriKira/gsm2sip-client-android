package com.callagent.host.push

import android.app.NotificationManager
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import com.callagent.host.background.HostBackgroundRuntime
import com.callagent.host.background.sessionStamp
import com.callagent.host.calls.SimLabels
import com.callagent.host.data.*
import com.callagent.host.notify.HostNotificationChannels
import com.callagent.host.notify.InboundSmsNotifications
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** System-owned recovery for dropped/downgraded hints. Not a real-time call scheduler. */
class PushRecoveryJob : JobService() {
    private val worker = Executors.newSingleThreadExecutor()
    private var cancelled = AtomicBoolean(false)

    override fun onStartJob(params: JobParameters): Boolean {
        val stopped = AtomicBoolean(false)
        cancelled = stopped
        worker.execute {
            var retry = false
            try {
                if (HostBackgroundRuntime.restoreAllowed(this)) {
                    val bound = SessionStore(this).read()
                    if (bound != null) {
                        val current = {
                            !stopped.get() && HostBackgroundRuntime.restoreAllowed(this) &&
                                SessionStore(this).read()?.sameSessionInstance(bound) == true
                        }
                        StandbyDiagnostics.withCause("push") {
                            HostSyncEngine(this, bound, { current() }, { ids ->
                                if (!current()) throw SessionChanged()
                                ClientDatabase(this, bound.databaseName(), bound.nodeScope).use { db ->
                                    HostNotificationChannels.ensure(this)
                                    val manager = getSystemService(NotificationManager::class.java)
                                    if (!manager.areNotificationsEnabled() ||
                                        !HostNotificationChannels.channelAllowed(this, HostNotificationChannels.INBOUND_SMS)) {
                                        false
                                    } else {
                                        val records = db.loadMessagesByServerIds(ids)
                                        val tag = sessionStamp(bound)
                                        val posts = InboundSmsNotifications.compose(this, tag, SUMMARY_ID, records) {
                                            SimLabels.describe(this, it)
                                        }
                                        if (posts.isEmpty()) false else {
                                            if (!current()) throw SessionChanged()
                                            posts.forEach { manager.notify(tag, it.id, it.notification) }
                                            true
                                        }
                                    }
                                }
                            }).use { it.sync(SystemClock.elapsedRealtime() + 55_000L) }
                        }
                    }
                }
            } catch (_: SessionChanged) {
                // Old pairing work must not be replayed against a new pairing.
            } catch (_: SessionNeedsPairing) {
                // User must pair again; retry cannot restore authorization.
            } catch (_: Exception) {
                retry = true
            } finally {
                if (!stopped.get()) jobFinished(params, retry)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        cancelled.set(true)
        return true
    }

    override fun onDestroy() {
        cancelled.set(true)
        worker.shutdownNow()
        super.onDestroy()
    }

    companion object {
        private const val JOB_ID = 0x4d4801
        private const val SUMMARY_ID = 7102

        fun schedule(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java)
            // Preserve an already queued job's backoff instead of resetting it on every hint.
            if (scheduler.getPendingJob(JOB_ID) != null) return
            scheduler.schedule(JobInfo.Builder(JOB_ID, ComponentName(context, PushRecoveryJob::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setBackoffCriteria(30_000L, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .build())
        }
    }
}
