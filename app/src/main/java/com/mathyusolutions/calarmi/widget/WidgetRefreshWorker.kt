package com.mathyusolutions.calarmi.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * El refresco programado de los widgets (medianoche, o cuando pasa la próxima
 * alarma). Un trabajo único que se reprograma a sí mismo: no hace falta
 * despertar el teléfono cada media hora con `updatePeriodMillis`.
 */
class WidgetRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        WidgetUpdater.refresh(applicationContext, fromWorker = true)
        return Result.success()
    }

    companion object {
        private const val NAME = "widget-refresh"

        fun schedule(context: Context, at: Long, fromWorker: Boolean) {
            val delay = (at - System.currentTimeMillis()).coerceAtLeast(0L)
            val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()
            // Desde la app, la nueva hora reemplaza a la anterior. Desde el propio
            // trabajo, REPLACE lo cancelaría mientras termina: el siguiente va detrás.
            val policy = if (fromWorker) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, policy, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}
