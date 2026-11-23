package com.mathyusolutions.calarmi.assistant

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mathyusolutions.calarmi.CalarmiApp
import com.mathyusolutions.calarmi.model.AlarmLeadTime
import com.mathyusolutions.calarmi.model.CustomCategory
import com.mathyusolutions.calarmi.model.Reminder
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * El estado del chat. Vive mientras la pantalla principal exista, así que
 * cambiar de pestaña no borra la conversación; «Nueva conversación» sí.
 */
class AssistantViewModel(app: Application) : AndroidViewModel(app) {
    private val container = CalarmiApp.container(app)
    private val replies = AssistantReplies(app)

    private val store = object : AssistantStore {
        override suspend fun all(): List<Reminder> = container.reminders.all()
        override suspend fun save(reminder: Reminder) = container.reminders.save(reminder)
        override suspend fun delete(id: String) = container.reminders.delete(id)
        override suspend fun customCategories(): List<CustomCategory> = container.categories.all()
        override fun defaultLeadTime(): AlarmLeadTime = container.settings.current.defaultLeadTime
    }

    private val engine = HybridEngine(RuleEngine(), GeminiNanoInterpreter)
    private var brain = newBrain()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    val modelStatus: StateFlow<OnDeviceModel.Status> = OnDeviceModel.status

    private var job: Job? = null

    init {
        OnDeviceModel.prepare()
    }

    private fun newBrain() = AssistantBrain(store, engine, defaultTitle = replies::defaultTitle)

    fun send(text: String, appLocale: Locale) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _busy.value) return
        _messages.update { it + ChatMessage(ChatMessage.nextId(), fromUser = true, text = trimmed) }
        _busy.value = true
        val appLang = AssistantLang.of(appLocale)
        val current = brain
        job = viewModelScope.launch {
            try {
                // El parser, el modelo y la base de datos trabajan fuera del hilo principal.
                val message = withContext(Dispatchers.Default) {
                    try {
                        replies.render(current.handle(trimmed, appLang))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        replies.error(LanguageDetector.detect(trimmed) ?: appLang)
                    }
                }
                _messages.update { it + message }
            } finally {
                if (brain === current) _busy.value = false
            }
        }
    }

    /** «Nueva conversación»: corta lo que esté en curso (incluido el modelo) y empieza de cero. */
    fun reset() {
        job?.cancel()
        job = null
        brain = newBrain()
        _messages.value = emptyList()
        _busy.value = false
    }

    override fun onCleared() {
        job?.cancel()
        super.onCleared()
    }
}
