package com.mathyusolutions.calarmi.sharing

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.mathyusolutions.calarmi.CalarmiApp
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch

/**
 * Los avisos push de lo compartido: mensajes de DATOS (sin notificación) de
 * prioridad alta que mandan las Cloud Functions cuando algo compartido cambia.
 *
 * `onMessageReceived` tiene pocos segundos y no debe esperar a la red; el
 * trabajo (leer Firestore, aplicar, reprogramar) va en un trabajo acelerado de
 * WorkManager, que la prioridad alta deja arrancar aunque la app esté cerrada.
 */
class ShareMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        if (data.isEmpty()) return
        ShareSyncWorker.enqueue(this, data)
    }

    // FCM 26 marca el token como obsoleto en favor de `register()`/`onRegistered`, pero las
    // Cloud Functions mandan a tokens de registro (`users/{uid}.tokens`): se sigue con
    // el token hasta cambiar el contrato en los tres lados.
    @Deprecated("FCM 26: register()/onRegistered; el contrato usa tokens de registro")
    @Suppress("DEPRECATION")
    override fun onNewToken(token: String) {
        val container = CalarmiApp.container(this)
        if (!container.isUserUnlocked) return
        container.scope.launch { container.sharing.registerPushToken(token) }
    }
}

/** Aplica un push de lo compartido (ver `SharingService.handlePush`) y reprograma. */
class ShareSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = CalarmiApp.container(applicationContext)
        if (!container.isUserUnlocked) return Result.retry()
        val data = inputData.keyValueMap.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap()
        return try {
            container.sharing.handlePush(data)
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo aplicar el push $data", e)
            // Sin red un momento: se reintenta (y si no, el listener lo pone al día al abrir la app).
            if (runAttemptCount < 4) Result.retry() else Result.failure()
        }
    }

    /**
     * Solo se usa en Android 11 o anterior, donde un trabajo acelerado corre como
     * servicio en primer plano. Sin tipo a propósito: ahí no hace falta, y declarar
     * `dataSync` obligaría a justificarlo en Play para Android 14+, donde nunca se usa.
     */
    override suspend fun getForegroundInfo(): ForegroundInfo =
        ForegroundInfo(ShareNotifications.ID_SYNC, ShareNotifications.syncing(applicationContext))

    companion object {
        private const val TAG = "ShareSyncWorker"
        private val KEYS = setOf("type", "event", "id", "principal", "kind", "title", "oldDate", "newDate", "actorName", "revision")

        fun enqueue(context: Context, data: Map<String, String>) {
            val input = Data.Builder().apply { data.filterKeys { it in KEYS }.forEach { (k, v) -> putString(k, v) } }.build()
            val request = OneTimeWorkRequestBuilder<ShareSyncWorker>()
                .setInputData(input)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                // Con la app en segundo plano Android le puede cortar la red; un trabajo que
                // declara que la necesita la recibe (visto en el emulador: sin esto, «Unable to
                // resolve host» y el cambio llegaba recién al abrir la app).
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .build()
            // Varios avisos del mismo share se encadenan: cada uno lee el estado más nuevo.
            WorkManager.getInstance(context).enqueueUniqueWork(
                "share-push-${data["type"]}-${data["id"]}",
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request,
            )
        }
    }
}
