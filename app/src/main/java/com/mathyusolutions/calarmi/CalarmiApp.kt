package com.mathyusolutions.calarmi

import android.app.Application
import android.content.Context
import android.os.UserManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.mathyusolutions.calarmi.alarm.AlarmNotifications
import com.mathyusolutions.calarmi.alarm.AlarmPlanner
import com.mathyusolutions.calarmi.alarm.AlarmStateStore
import com.mathyusolutions.calarmi.billing.PlusStore
import com.mathyusolutions.calarmi.calendar.CalendarRepository
import com.mathyusolutions.calarmi.calendar.CalendarSyncWorker
import com.mathyusolutions.calarmi.data.AppDatabase
import com.mathyusolutions.calarmi.data.CategoryRepository
import com.mathyusolutions.calarmi.data.CountdownRepository
import com.mathyusolutions.calarmi.data.ImportedTonesStore
import com.mathyusolutions.calarmi.data.PhotoStore
import com.mathyusolutions.calarmi.data.ReminderRepository
import com.mathyusolutions.calarmi.data.SettingsStore
import com.mathyusolutions.calarmi.sharing.ShareNotifications
import com.mathyusolutions.calarmi.sharing.SharingService
import com.mathyusolutions.calarmi.widget.WidgetUpdater
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

class CalarmiApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        AlarmNotifications.createChannels(this)
        ShareNotifications.createChannels(this)
        container.start()
    }

    companion object {
        fun container(context: Context): AppContainer = (context.applicationContext as CalarmiApp).container
    }
}

/**
 * Las piezas compartidas de la app. Todo lo que guarda datos usa el
 * almacenamiento protegido por dispositivo, disponible antes del primer
 * desbloqueo: una alarma tiene que sonar aunque el teléfono se haya reiniciado.
 */
class AppContainer(val app: Application) {
    val storage: Context = app.createDeviceProtectedStorageContext()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: AppDatabase by lazy { AppDatabase.create(storage) }
    val settings: SettingsStore by lazy { SettingsStore(storage) }
    val alarmState: AlarmStateStore by lazy { AlarmStateStore(storage) }
    val photos: PhotoStore by lazy { PhotoStore(storage) }
    val importedTones: ImportedTonesStore by lazy { ImportedTonesStore(storage) }
    val calendar: CalendarRepository by lazy { CalendarRepository(app, settings) }
    val plus: PlusStore by lazy { PlusStore(app, storage, scope) }

    val planner: AlarmPlanner by lazy {
        AlarmPlanner(app, database.reminders(), database.categories(), settings, alarmState, calendar)
    }

    /** Lo compartido por Firebase (alarmas, cuentas regresivas, personas de confianza). */
    val sharing: SharingService by lazy { SharingService(this) }

    val reminders: ReminderRepository by lazy {
        ReminderRepository(database.reminders(), photos, hooks = { sharing.hooks }) { onDataChanged() }
    }
    val categories: CategoryRepository by lazy { CategoryRepository(database.categories(), database.reminders()) }
    val countdowns: CountdownRepository by lazy {
        CountdownRepository(database.countdowns(), photos, hooks = { sharing.hooks }) { WidgetUpdater.update(app) }
    }

    val isUserUnlocked: Boolean
        get() = app.getSystemService(UserManager::class.java).isUserUnlocked

    fun start() {
        scope.launch { planner.reschedule() }
        // Cambiar el interruptor general, el tono o la cuenta regresiva reprograma.
        scope.launch {
            settings.state
                .distinctUntilChangedBy { listOf(it.alarmsEnabled, it.alarmTone, it.countdown, it.calendarAlarmsEnabled, it.selectedCalendarIds, it.onlyAttendingEvents, it.defaultLeadTime, it.meetingLinksEnabled) }
                .drop(1)
                .collect { onDataChanged() }
        }
        if (isUserUnlocked) onUserUnlocked()
    }

    private val unlockedStarted = AtomicBoolean(false)

    /** Lo que necesita el almacenamiento normal (WorkManager, facturación, Firebase): tras el primer desbloqueo. */
    fun onUserUnlocked() {
        CalendarSyncWorker.schedule(app)
        plus.connect()
        if (unlockedStarted.compareAndSet(false, true)) {
            // Lo compartido se escucha solo con la app a la vista; con ella cerrada
            // llegan los push. El observador se registra en el hilo principal.
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
                    override fun onStart(owner: LifecycleOwner) = sharing.onForeground()
                    override fun onStop(owner: LifecycleOwner) = sharing.onBackground()
                })
            }
        }
    }

    suspend fun onDataChanged() {
        planner.reschedule()
        WidgetUpdater.update(app)
    }
}
