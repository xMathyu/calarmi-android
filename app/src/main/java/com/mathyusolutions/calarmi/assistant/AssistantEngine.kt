package com.mathyusolutions.calarmi.assistant

import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.CancellationException

data class EngineRequest(
    val text: String,
    val now: LocalDateTime,
    val zone: ZoneId,
    val lang: AssistantLang,
    val context: AssistantParser.Context = AssistantParser.Context(),
)

/** Convierte un mensaje en una acción. Dos implementaciones: el parser (siempre) y Gemini Nano. */
interface AssistantEngine {
    suspend fun interpret(request: EngineRequest): Interpretation
}

/** El parser determinista: sin modelo, sin red, al instante. */
class RuleEngine : AssistantEngine {
    override suspend fun interpret(request: EngineRequest): Interpretation =
        AssistantParser.parse(request.text, request.now, request.lang, request.context)
}

/** Un modelo en el teléfono que propone una acción (o `null` si no puede o no entendió). */
interface ModelInterpreter {
    val isReady: Boolean
    suspend fun interpret(request: EngineRequest): Interpretation?
}

/**
 * Primero el parser: si entendió todo, su respuesta es exacta e instantánea.
 * Solo cuando duda (falta la hora, sobran números, no entendió) se le pregunta
 * a la IA del teléfono, y lo que ella diga pasa por las mismas reglas. Si la IA
 * no está, falla o tarda, queda lo del parser.
 */
class HybridEngine(
    private val rules: AssistantEngine,
    private val model: ModelInterpreter?,
) : AssistantEngine {
    override suspend fun interpret(request: EngineRequest): Interpretation {
        val ruled = rules.interpret(request)
        if (ruled.confident || model == null || !model.isReady) return ruled
        val modeled = try {
            model.interpret(request)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        return modeled ?: ruled
    }
}
