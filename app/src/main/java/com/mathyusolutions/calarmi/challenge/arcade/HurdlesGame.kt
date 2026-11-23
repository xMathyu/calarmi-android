package com.mathyusolutions.calarmi.challenge.arcade

import com.mathyusolutions.calarmi.model.WakeChallenge
import kotlin.math.min
import kotlin.random.Random

/**
 * La partida de «Ovejas», sin nada de Compose: coordenadas lógicas de
 * 360 × 240. La x crece hacia la derecha; la altura del salto se mide hacia
 * arriba desde el suelo.
 *
 * Una oveja corre de noche hacia el amanecer y hay que hacerla saltar las
 * vallas. Un toque es un salto corto; mantener el dedo la eleva más, y las
 * vallas altas solo se pasan así. Tropezar no resta: tras un instante se toca
 * para seguir.
 */
class HurdlesGame(difficulty: WakeChallenge.Difficulty, seed: Long = Random.nextLong()) {
    enum class Phase { READY, PLAYING, CRASHED, DONE }

    enum class Event { CLEARED, CRASHED, SOLVED }

    enum class Kind {
        /** Se salta con un toque. */
        LOW,

        /** Hay que mantener el dedo. */
        TALL,

        /** Dos vallas bajas seguidas: también hay que mantener. */
        DOUBLE,
    }

    class Obstacle internal constructor(val id: Int, x: Double, val kind: Kind) {
        var x = x
            internal set
        var cleared = false
            internal set

        val height: Double get() = if (kind == Kind.TALL) TALL_HEIGHT else LOW_HEIGHT

        /** Dónde empieza cada valla del obstáculo. */
        val fenceXs: List<Double>
            get() = if (kind == Kind.DOUBLE) listOf(x, x + FENCE_WIDTH + DOUBLE_GAP) else listOf(x)

        val width: Double get() = if (kind == Kind.DOUBLE) FENCE_WIDTH * 2 + DOUBLE_GAP else FENCE_WIDTH
    }

    companion object {
        const val WIDTH = 360.0
        const val HEIGHT = 240.0
        const val GROUND_Y = 204.0
        const val SHEEP_X = 72.0

        /** Caja de choque de la oveja, más pequeña que el dibujo. */
        const val SHEEP_HALF_WIDTH = 12.0
        const val FENCE_WIDTH = 22.0
        const val DOUBLE_GAP = 24.0
        const val LOW_HEIGHT = 30.0

        /** Más alta de lo que llega un toque corto (unos 56): pide mantener. */
        const val TALL_HEIGHT = 64.0

        /** Lo que se perdona de cada valla: ni sus bordes ni su remate cuentan. */
        const val FENCE_FORGIVENESS = 4.0
        const val JUMP_SPEED = 600.0

        /** Gravedad mientras sube con el dedo apoyado: manteniendo llega a ~120. */
        const val HOLD_GRAVITY = 1500.0

        /** Gravedad al soltar o al caer: el salto corto es corto de verdad. */
        const val FALL_GRAVITY = 2000.0

        /** Al soltar pronto, la subida se recorta a esta velocidad. */
        const val RELEASE_SPEED = 300.0

        /** Un toque poco antes de aterrizar no se pierde: salta al tocar suelo. */
        const val JUMP_BUFFER = 0.12
        const val CRASH_COOLDOWN = 0.6
    }

    val required: Int
    val baseSpeed: Double

    /** Separación entre obstáculos, en segundos de carrera. */
    val spacingMin: Double
    val spacingMax: Double
    val tallChance: Double
    val doubleChance: Double

    init {
        when (difficulty) {
            WakeChallenge.Difficulty.EASY -> {
                required = 8; baseSpeed = 200.0; spacingMin = 1.25; spacingMax = 2.0; tallChance = 0.0; doubleChance = 0.0
            }
            WakeChallenge.Difficulty.MEDIUM -> {
                required = 12; baseSpeed = 225.0; spacingMin = 1.1; spacingMax = 1.7; tallChance = 0.3; doubleChance = 0.0
            }
            WakeChallenge.Difficulty.HARD -> {
                required = 16; baseSpeed = 250.0; spacingMin = 1.0; spacingMax = 1.5; tallChance = 0.35; doubleChance = 0.2
            }
        }
    }

    var phase = Phase.READY
        private set
    var cleared = 0
        private set

    /** Altura de los pies sobre el suelo. */
    var jumpHeight = 0.0
        private set
    private val _obstacles = mutableListOf<Obstacle>()
    val obstacles: List<Obstacle> get() = _obstacles
    var scroll = 0.0
        private set
    var crashElapsed = 0.0
        private set
    private var velocity = 0.0
    private var holding = false

    /** Si el dedo sigue apoyado (para el salto guardado). */
    private var pressed = false
    private var buffered = 0.0
    private var untilNext = 0.0
    private var nextId = 0
    private val rng = SplitMix64(seed)

    val canContinue: Boolean
        get() = phase == Phase.CRASHED && crashElapsed >= CRASH_COOLDOWN

    val isOnGround: Boolean
        get() = jumpHeight <= 0 && velocity <= 0

    /** Acelera un poco con cada valla, hasta un tope. */
    val speed: Double
        get() = baseSpeed + min(cleared * 3.0, 40.0)

    fun press() {
        pressed = true
        when (phase) {
            Phase.READY -> {
                phase = Phase.PLAYING
                untilNext = 0.0
                jump()
            }
            Phase.PLAYING -> if (isOnGround) jump() else buffered = JUMP_BUFFER
            Phase.CRASHED -> {
                if (!canContinue) return
                _obstacles.clear()
                jumpHeight = 0.0
                velocity = 0.0
                untilNext = 0.6
                phase = Phase.PLAYING
            }
            Phase.DONE -> Unit
        }
    }

    fun release() {
        pressed = false
        holding = false
        if (velocity > RELEASE_SPEED) velocity = RELEASE_SPEED
    }

    fun step(dt: Double): List<Event> {
        when (phase) {
            Phase.READY, Phase.DONE -> return emptyList()
            Phase.CRASHED -> {
                crashElapsed += dt
                applyGravity(dt)
                return emptyList()
            }
            Phase.PLAYING -> Unit
        }

        val events = mutableListOf<Event>()
        applyGravity(dt)
        if (buffered > 0) {
            buffered -= dt
            if (isOnGround) {
                buffered = 0.0
                jump()
                // Si ya soltó, es un salto corto.
                if (!pressed) release()
            }
        }

        val distance = speed * dt
        scroll += distance
        for (obstacle in _obstacles) obstacle.x -= distance
        _obstacles.removeAll { it.x + it.width < -10 }

        untilNext -= dt
        if (untilNext <= 0) spawn()

        val sheepLeft = SHEEP_X - SHEEP_HALF_WIDTH
        for (obstacle in _obstacles) {
            if (obstacle.cleared || obstacle.x + obstacle.width >= sheepLeft) continue
            obstacle.cleared = true
            cleared += 1
            events += Event.CLEARED
            if (cleared >= required) {
                phase = Phase.DONE
                events += Event.SOLVED
                return events
            }
        }

        if (collides()) {
            phase = Phase.CRASHED
            crashElapsed = 0.0
            holding = false
            buffered = 0.0
            velocity = min(velocity, 0.0)
            events += Event.CRASHED
        }
        return events
    }

    // MARK: - Interno

    private fun jump() {
        if (!isOnGround) return
        velocity = JUMP_SPEED
        holding = true
    }

    private fun applyGravity(dt: Double) {
        if (jumpHeight <= 0 && velocity <= 0) return
        val gravity = if (holding && velocity > 0) HOLD_GRAVITY else FALL_GRAVITY
        velocity -= gravity * dt
        jumpHeight += velocity * dt
        if (jumpHeight <= 0) {
            jumpHeight = 0.0
            velocity = 0.0
        }
    }

    private fun spawn() {
        val roll = rng.nextDouble()
        val kind = when {
            roll < doubleChance -> Kind.DOUBLE
            roll < doubleChance + tallChance -> Kind.TALL
            else -> Kind.LOW
        }
        _obstacles += Obstacle(nextId, WIDTH + 10, kind)
        nextId += 1
        var wait = rng.nextDouble(spacingMin, spacingMax)
        if (kind == Kind.DOUBLE) wait += 0.2
        untilNext = wait
    }

    private fun collides(): Boolean {
        val sheepLeft = SHEEP_X - SHEEP_HALF_WIDTH
        val sheepRight = SHEEP_X + SHEEP_HALF_WIDTH
        val margin = FENCE_FORGIVENESS
        for (obstacle in _obstacles) {
            for (fenceX in obstacle.fenceXs) {
                val overlapsX = fenceX + margin < sheepRight && fenceX + FENCE_WIDTH - margin > sheepLeft
                if (overlapsX && jumpHeight < obstacle.height - margin) return true
            }
        }
        return false
    }
}
