package app.tuneroam.radio.snapshot

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.tuneroam.radio.domain.model.AppSettings
import app.tuneroam.radio.domain.model.RefreshInterval
import app.tuneroam.radio.domain.model.SnapshotResult
import app.tuneroam.radio.domain.repository.SettingsRepository
import app.tuneroam.radio.domain.repository.SnapshotRepository
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** One background download of the offline station snapshot. */
class SnapshotRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {
    private val snapshot: SnapshotRepository by inject()

    override suspend fun doWork(): Result = when (snapshot.refresh()) {
        is SnapshotResult.Updated -> Result.success()
        // The catalog answered but with too little; trying again soon will not help.
        is SnapshotResult.TooFew -> Result.success()
        // Radio Browser unreachable (common from the mainland): back off and try later.
        is SnapshotResult.Failed -> if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.success()
    }

    private companion object {
        const val MAX_RETRIES = 3
    }
}

/**
 * Keeps the periodic snapshot download in line with settings: the interval, Wi-Fi only,
 * or off. WorkManager persists the schedule across reboots; this only re-applies it
 * when the settings change (and once per process start, which keeps the existing timing).
 */
class SnapshotScheduler(
    private val context: Context,
    private val settings: SettingsRepository,
) {
    fun start(scope: CoroutineScope) {
        scope.launch {
            settings.observe()
                .map { Schedule(it) }
                .distinctUntilChanged()
                .collect(::apply)
        }
    }

    private fun apply(schedule: Schedule) {
        val work = WorkManager.getInstance(context)
        if (schedule.interval == RefreshInterval.OFF) {
            work.cancelUniqueWork(WORK_NAME)
            return
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (schedule.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
        val request = PeriodicWorkRequestBuilder<SnapshotRefreshWorker>(schedule.interval.days.toLong(), TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()
        // UPDATE keeps the schedule's timing when only constraints or the period changed.
        work.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    private data class Schedule(val interval: RefreshInterval, val wifiOnly: Boolean) {
        constructor(settings: AppSettings) : this(settings.snapshotRefresh, settings.snapshotWifiOnly)
    }

    private companion object {
        const val WORK_NAME = "snapshot-refresh"
    }
}
