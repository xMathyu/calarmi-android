package com.mathyusolutions.calarmi.challenge.arcade

import com.mathyusolutions.calarmi.model.WakeChallenge
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * La partida de «Vuelo», sin nada de Compose: coordenadas lógicas de
 * 360 × 360 con la y hacia abajo.
 *
 * Una abeja que cae sola y sube un poco con cada toque. Hay que pasarla por
 * los huecos de las columnas que llegan desde la derecha. Chocar no resta:
 * tras un instante se toca para seguir y los huecos ya pasados se conservan,
 * así que el reto siempre se puede terminar.
 */
class FlapGame(difficulty: WakeChallenge.Difficulty, seed: Long = Random.nextLong()) {
    enum class Phase {
        /** La abeja flota esperando el primer toque. */
        READY,
        PLAYING,

        /** Chocó: cae al suelo y, pasado `CRASH_COOLDOWN`, un toque sigue. */
        CRASHED,
        DONE,
    }

    enum class Event { PASSED, CRASHED, SOLVED }

    class Column internal constructor(val id: Int, x: Double, val gapTop: Double, val gapBottom: Double) {
        var x = x
            internal set
        var passed = false
            internal set
    }

    companion object {
        const val WIDTH = 360.0
        const val HEIGHT = 360.0
        const val GROUND_Y = 330.0
        const val BEE_X = 92.0

        /** Radio de choque de la abeja: algo menor que el dibujo, para perdonar los roces. */
        const val BEE_RADIUS = 12.0
        const val COLUMN_WIDTH = 56.0
        const val GRAVITY = 1350.0
        const val FLAP_SPEED = -400.0
        const val MAX_FALL_SPEED = 620.0
        const val CRASH_COOLDOWN = 0.6

        /** Margen mínimo entre el hueco y el techo o el suelo. */
        const val GAP_MARGIN = 34.0

        internal fun circleHits(cx: Double, cy: Double, radius: Double, left: Double, top: Double, right: Double, bottom: Double): Boolean {
            val nearestX = min(max(cx, left), right)
            val nearestY = min(max(cy, top), bottom)
            return hypot(cx - nearestX, cy - nearestY) < radius
        }
    }

    val required: Int
    val gap: Double
    val speed: Double
    val spacing: Double

    /** Cuánto puede moverse el centro del hueco de una columna a la siguiente. */
    val maxShift: Double

    init {
        when (difficulty) {
            WakeChallenge.Difficulty.EASY -> {
                required = 5; gap = 150.0; speed = 125.0; spacing = 205.0; maxShift = 100.0
            }
            WakeChallenge.Difficulty.MEDIUM -> {
                required = 8; gap = 132.0; speed = 145.0; spacing = 195.0; maxShift = 130.0
            }
            WakeChallenge.Difficulty.HARD -> {
                required = 12; gap = 116.0; speed = 165.0; spacing = 190.0; maxShift = 150.0
            }
        }
    }

    var phase = Phase.READY
        private set
    var passed = 0
        private set
    var beeY = GROUND_Y / 2
        private set
    var beeVY = 0.0
        private set
    private val _columns = mutableListOf<Column>()
    val columns: List<Column> get() = _columns

    /** Distancia recorrida, para desplazar el suelo y las nubes. */
    var scroll = 0.0
        private set
    private var time = 0.0
    private var crashElapsed = 0.0
    private var nextId = 0
    private var lastGapCenter = GROUND_Y / 2
    private val rng = SplitMix64(seed)

    val canContinue: Boolean
        get() = phase == Phase.CRASHED && crashElapsed >= CRASH_COOLDOWN

    /** Un toque: arranca, aletea o, tras un choque, vuelve a empezar. */
    fun press() {
        when (phase) {
            Phase.READY -> {
                phase = Phase.PLAYING
                beeVY = FLAP_SPEED
            }
            Phase.PLAYING -> beeVY = FLAP_SPEED
            Phase.CRASHED -> if (canContinue) restart()
            Phase.DONE -> Unit
        }
    }

    fun step(dt: Double): List<Event> {
        time += dt
        when (phase) {
            Phase.READY -> {
                beeY = GROUND_Y / 2 + sin(time * 3) * 6
                scroll += speed * 0.5 * dt
                return emptyList()
            }
            Phase.CRASHED -> {
                crashElapsed += dt
                val floor = GROUND_Y - BEE_RADIUS
                if (beeY < floor) {
                    beeVY = min(beeVY + GRAVITY * dt, MAX_FALL_SPEED)
                    beeY = min(beeY + beeVY * dt, floor)
                }
                return emptyList()
            }
            Phase.DONE -> return emptyList()
            Phase.PLAYING -> Unit
        }

        val events = mutableListOf<Event>()
        beeVY = min(beeVY + GRAVITY * dt, MAX_FALL_SPEED)
        beeY += beeVY * dt
        // El techo no mata: solo frena.
        if (beeY < BEE_RADIUS) {
            beeY = BEE_RADIUS
            beeVY = max(beeVY, 0.0)
        }

        scroll += speed * dt
        for (column in _columns) column.x -= speed * dt
        _columns.removeAll { it.x + COLUMN_WIDTH < -10 }
        spawnIfNeeded()

        for (column in _columns) {
            if (column.passed || column.x + COLUMN_WIDTH >= BEE_X - BEE_RADIUS) continue
            column.passed = true
            passed += 1
            events += Event.PASSED
            if (passed >= required) {
                phase = Phase.DONE
                events += Event.SOLVED
                return events
            }
        }

        if (collides()) {
            phase = Phase.CRASHED
            crashElapsed = 0.0
            beeVY = max(beeVY, 0.0)
            events += Event.CRASHED
        }
        return events
    }

    // MARK: - Interno

    private fun restart() {
        _columns.clear()
        beeY = GROUND_Y / 2
        lastGapCenter = beeY
        phase = Phase.PLAYING
        beeVY = FLAP_SPEED
    }

    private fun spawnIfNeeded() {
        val last = _columns.lastOrNull()
        val x = if (last != null) {
            if (last.x + spacing > WIDTH + 20) return
            last.x + spacing
        } else {
            WIDTH + 60
        }
        val half = gap / 2
        val lowest = GAP_MARGIN + half
        val highest = GROUND_Y - GAP_MARGIN - half
        val low = max(lowest, lastGapCenter - maxShift)
        val high = min(highest, lastGapCenter + maxShift)
        val center = rng.nextDouble(low, high)
        lastGapCenter = center
        _columns += Column(nextId, x, center - half, center + half)
        nextId += 1
    }

    private fun collides(): Boolean {
        if (beeY + BEE_RADIUS >= GROUND_Y) return true
        for (column in _columns) {
            val right = column.x + COLUMN_WIDTH
            if (circleHits(BEE_X, beeY, BEE_RADIUS, column.x, -1000.0, right, column.gapTop) ||
                circleHits(BEE_X, beeY, BEE_RADIUS, column.x, column.gapBottom, right, 1000.0)
            ) {
                return true
            }
        }
        return false
    }
}
