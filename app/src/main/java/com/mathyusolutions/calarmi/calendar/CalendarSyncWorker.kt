package com.mathyusolutions.calarmi.calendar

import android.content.Context
import android.provider.CalendarContract
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mathyusolutions.calarmi.CalarmiApp
import java.util.concurrent.TimeUnit

/**
 * Reprograma cuando cambia el calendario del teléfono (un evento nuevo, uno
 * movido) y, por si acaso, cada pocas horas.
 */
class CalendarSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = CalarmiApp.container(applicationContext)
        container.calendar.notifyChanged()
        container.planner.reschedule()
        if (tags.contains(TAG_OBSERVER)) enqueueObserver(applicationContext)
        return Result.success()
    }

    companion object {
        private const val TAG_OBSERVER = "calendar-observer"
        private const val NAME_OBSERVER = "calendar-observer"
        private const val NAME_PERIODIC = "calendar-periodic"

        fun schedule(context: Context) {
            val wm = WorkManager.getInstance(context)
            wm.enqueueUniquePeriodicWork(
                NAME_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<CalendarSyncWorker>(3, TimeUnit.HOURS).build(),
            )
            enqueueObserver(context)
        }

        /** Un trabajo que corre cuando cambia el proveedor de calendario; se vuelve a poner al terminar. */
        private fun enqueueObserver(context: Context) {
            val constraints = Constraints.Builder()
                .addContentUriTrigger(CalendarContract.Events.CONTENT_URI, true)
                .addContentUriTrigger(CalendarContract.Instances.CONTENT_URI, true)
                .setTriggerContentUpdateDelay(5, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                NAME_OBSERVER,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<CalendarSyncWorker>().setConstraints(constraints).addTag(TAG_OBSERVER).build(),
            )
        }
    }
}
