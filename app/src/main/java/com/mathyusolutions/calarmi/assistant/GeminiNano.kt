package com.mathyusolutions.calarmi.assistant

import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Gemini Nano por la Prompt API de ML Kit: corre dentro del teléfono (AICore),
 * sin enviar nada a ningún servidor. Si el teléfono no lo tiene, el asistente
 * sigue funcionando con el parser («modo básico»).
 */
object OnDeviceModel {
    enum class Status { CHECKING, UNAVAILABLE, DOWNLOADING, AVAILABLE }

    private val _status = MutableStateFlow(Status.CHECKING)
    val status: StateFlow<Status> = _status.asStateFlow()

    // La descarga sigue aunque se cierre la pestaña: vive con la app, no con la pantalla.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // AICore atiende de a un pedido por app: los demás devolverían BUSY.
    private val mutex = Mutex()
    @Volatile private var client: GenerativeModel? = null
    @Volatile private var preparing = false

    /** Mira si el modelo está; si se puede descargar, lo descarga. Llamarlo de nuevo no repite nada. */
    fun prepare() {
        if (preparing || _status.value == Status.AVAILABLE) return
        preparing = true
        scope.launch {
            try {
                val model = client ?: Generation.getClient().also { client = it }
                when (model.checkStatus()) {
                    FeatureStatus.AVAILABLE -> ready(model)
                    FeatureStatus.DOWNLOADABLE, FeatureStatus.DOWNLOADING -> download(model)
                    else -> _status.value = Status.UNAVAILABLE
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Sin AICore, sin Play Services o un teléfono no compatible: modo básico.
                _status.value = Status.UNAVAILABLE
            } finally {
                preparing = false
            }
        }
    }

    private suspend fun download(model: GenerativeModel) {
        _status.value = Status.DOWNLOADING
        model.download().collect { progress ->
            when (progress) {
                is DownloadStatus.DownloadCompleted -> _status.value = Status.AVAILABLE
                is DownloadStatus.DownloadFailed -> _status.value = Status.UNAVAILABLE
                else -> _status.value = Status.DOWNLOADING
            }
        }
        if (_status.value == Status.AVAILABLE) ready(model)
    }

    private suspend fun ready(model: GenerativeModel) {
        _status.value = Status.AVAILABLE
        // Cargar el modelo antes del primer mensaje acorta esa primera respuesta.
        runCatching { model.warmup() }
    }

    /** El texto que genera el modelo, o `null` si no está, falla o tarda demasiado. Siempre fuera del hilo principal. */
    suspend fun generate(prompt: String): String? {
        val model = client ?: return null
        if (_status.value != Status.AVAILABLE) return null
        return mutex.withLock {
            withContext(Dispatchers.Default) {
                withTimeoutOrNull(GENERATION_TIMEOUT_MS) {
                    val request = generateContentRequest(TextPart(prompt)) {
                        // Poca temperatura: se busca un JSON fiel, no creatividad.
                        temperature = 0.1f
                        topK = 8
                        maxOutputTokens = 320
                    }
                    model.generateContent(request).candidates.firstOrNull()?.text
                }
            }
        }
    }

    private const val GENERATION_TIMEOUT_MS = 20_000L
}

/** `ModelInterpreter` sobre Gemini Nano: pide el JSON, lo valida con las reglas del parser. */
object GeminiNanoInterpreter : ModelInterpreter {
    override val isReady: Boolean get() = OnDeviceModel.status.value == OnDeviceModel.Status.AVAILABLE

    override suspend fun interpret(request: EngineRequest): Interpretation? {
        val modelRequest = request.copy(text = DraftRules.normalizeTimeRanges(request.text))
        val raw = OnDeviceModel.generate(ModelPrompt.build(modelRequest)) ?: return null
        val obj = ModelActionMapper.parseJson(raw) ?: return null
        // Las reglas leen el mensaje tal cual lo escribió la persona.
        val action = ModelActionMapper.map(obj, request) ?: return null
        return Interpretation(action, confident = true, source = EngineKind.ON_DEVICE_AI)
    }
}
