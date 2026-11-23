package com.mathyusolutions.calarmi.challenge.arcade

import com.mathyusolutions.calarmi.model.WakeChallenge
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * La partida de «Cañón», sin nada de Compose: coordenadas lógicas de
 * 360 × 280 con la y hacia abajo.
 *
 * Artillería por turnos contra un despertador al otro lado de una colina. Se
 * apunta arrastrando el dedo por el campo y se dispara manteniendo el botón:
 * la fuerza sube y baja mientras se mantiene, y se suelta en el punto justo.
 * El viento desvía el disparo; cada explosión abre un cráter en el terreno. El
 * viento y el sitio del despertador solo cambian tras un impacto, así que
 * fallar sirve para afinar.
 *
 * El terreno es un mapa de alturas (la y de la superficie cada `SAMPLE_STEP`
 * puntos) y el proyectil se integra a paso fijo, el mismo que usa la búsqueda
 * que garantiza que cada blanco tiene solución.
 */
class CannonGame(difficulty: WakeChallenge.Difficulty, seed: Long = Random.nextLong()) {
    enum class Phase {
        AIMING,

        /** El dedo está en el botón: la fuerza sube y baja. */
        CHARGING,
        FLYING,
        EXPLODING,
        DONE,
    }

    enum class Event { HIT, MISS, SOLVED }

    data class Target(val x: Double, val y: Double, val radius: Double)

    class Shell(var x: Double, var y: Double, var vx: Double, var vy: Double)

    class Blast(val x: Double, val y: Double, val hit: Boolean) {
        var age = 0.0
            internal set
    }

    internal enum class Outcome { FLYING, IMPACT, LOST }

    companion object {
        const val WIDTH = 360.0
        const val HEIGHT = 280.0
        const val SAMPLE_STEP = 2.0
        const val GRAVITY = 300.0
        const val MIN_SPEED = 90.0
        const val MAX_SPEED = 430.0
        const val CANNON_X = 36.0
        const val BARREL_LENGTH = 24.0
        const val SHELL_RADIUS = 4.0
        const val BLAST_RADIUS = 20.0
        const val BLAST_DURATION = 0.5

        /**
         * Segundos que tarda la fuerza en ir de 0 al máximo (y otros tantos en
         * volver): mantener el dedo sin más no da el disparo más fuerte.
         */
        const val CHARGE_PERIOD = 1.4

        /** Una pulsación más corta no dispara: así un roce no gasta el tiro. */
        const val MIN_CHARGE = 0.12
        const val MIN_ANGLE = 10.0
        const val MAX_ANGLE = 85.0
        const val FIXED_STEP = 1.0 / 120

        /** Zona donde puede aparecer el despertador. */
        const val TARGET_ZONE_MIN = 214.0
        const val TARGET_ZONE_MAX = 332.0

        fun speed(power: Double): Double = MIN_SPEED + (MAX_SPEED - MIN_SPEED) * power

        internal fun muzzle(pivotY: Double, radians: Double) =
            Pt(CANNON_X + cos(radians) * BARREL_LENGTH, pivotY - sin(radians) * BARREL_LENGTH)

        internal fun advance(s: Shell, wind: Double) {
            s.vx += wind * FIXED_STEP
            s.vy += GRAVITY * FIXED_STEP
            s.x += s.vx * FIXED_STEP
            s.y += s.vy * FIXED_STEP
        }

        internal fun outcome(s: Shell, terrain: DoubleArray, target: Target): Outcome {
            if (hypot(s.x - target.x, s.y - target.y) <= target.radius + SHELL_RADIUS) return Outcome.IMPACT
            if (s.x in 0.0..WIDTH && s.y >= top(terrain, s.x)) return Outcome.IMPACT
            if (s.x < -40 || s.x > WIDTH + 40 || s.y > HEIGHT + 40) return Outcome.LOST
            return Outcome.FLYING
        }

        internal fun isHit(x: Double, y: Double, target: Target): Boolean =
            hypot(x - target.x, y - target.y) <= target.radius + BLAST_RADIUS * 0.6

        /** El proyectil recién disparado con ese ángulo (en grados) y esa fuerza. */
        internal fun launch(cannonY: Double, angleDegrees: Double, power: Double): Shell {
            val radians = angleDegrees * PI / 180
            val speed = speed(power)
            val m = muzzle(cannonY, radians)
            return Shell(m.x, m.y, cos(radians) * speed, -sin(radians) * speed)
        }

        /**
         * Cuántas combinaciones de una rejilla de ángulos y fuerzas aciertan.
         * Para en cuanto llega a `enough`.
         */
        internal fun solutionCount(terrain: DoubleArray, cannonY: Double, target: Target, wind: Double, enough: Int): Int {
            var found = 0
            var angle = 14.0
            while (angle <= 82) {
                var power = 0.08
                while (power <= 1.0001) {
                    if (shotHits(terrain, cannonY, target, wind, angle, power)) {
                        found += 1
                        if (found >= enough) return found
                    }
                    power += 0.02
                }
                angle += 3
            }
            return found
        }

        /** Si ese disparo acierta (sin cráteres de por medio). */
        internal fun shotHits(terrain: DoubleArray, cannonY: Double, target: Target, wind: Double, angle: Double, power: Double): Boolean {
            val s = launch(cannonY, angle, power)
            repeat(1200) {
                advance(s, wind)
                when (outcome(s, terrain, target)) {
                    Outcome.FLYING -> Unit
                    Outcome.LOST -> return false
                    Outcome.IMPACT -> return isHit(s.x, s.y, target)
                }
            }
            return false
        }

        internal fun makeTerrain(hillMin: Double, hillMax: Double, rng: SplitMix64): DoubleArray {
            val count = (WIDTH / SAMPLE_STEP).toInt() + 1
            val phase1 = rng.nextDouble(0.0, 2 * PI)
            val phase2 = rng.nextDouble(0.0, 2 * PI)
            val hillX = rng.nextDouble(130.0, 185.0)
            val hillWidth = rng.nextDouble(28.0, 42.0)
            val hillHeight = rng.nextDouble(hillMin, hillMax)
            val terrain = DoubleArray(count) { i ->
                val x = i * SAMPLE_STEP
                var y = 222.0
                y -= 12 * sin(x / 43 + phase1) + 7 * sin(x / 19 + phase2)
                val d = (x - hillX) / hillWidth
                y -= hillHeight * exp(-d * d)
                y.coerceIn(70.0, 250.0)
            }
            // Explanada para el cañón, que se funde con el terreno de alrededor.
            val base = min(top(terrain, CANNON_X), 215.0)
            for (i in terrain.indices) {
                val d = abs(i * SAMPLE_STEP - CANNON_X)
                if (d <= 20) {
                    terrain[i] = base
                } else if (d < 34) {
                    val t = (d - 20) / 14
                    terrain[i] = base + (terrain[i] - base) * t
                }
            }
            return terrain
        }

        /** La y de la superficie en `x`, interpolada entre muestras. */
        fun top(terrain: DoubleArray, x: Double): Double {
            val position = (x / SAMPLE_STEP).coerceIn(0.0, (terrain.size - 1).toDouble())
            val i = position.toInt()
            val j = min(i + 1, terrain.size - 1)
            val t = position - i
            return terrain[i] + (terrain[j] - terrain[i]) * t
        }

        internal fun restingY(x: Double, radius: Double, terrain: DoubleArray): Double {
            var highest = Double.POSITIVE_INFINITY
            var sx = x - radius * 0.6
            while (sx <= x + radius * 0.6) {
                highest = min(highest, top(terrain, sx))
                sx += 1
            }
            return highest - radius
        }

        /** Onda triangular: 0 → 1 → 0 → 1… con periodo 2. */
        fun triangle(t: Double): Double {
            val m = arcadeWrap(t, 2.0)
            return if (m <= 1) m else 2 - m
        }
    }

    val required: Int
    val targetRadius: Double
    private val windMin: Double
    private val windMax: Double

    var phase = Phase.AIMING
        private set
    var hits = 0
        private set
    val terrain: DoubleArray
    val cannonY: Double
    var target: Target
        private set

    /** Aceleración horizontal del viento, en puntos/s² (positiva: hacia la derecha). */
    var wind = 0.0
        private set
    var angle = 45.0
        private set
    var power = 0.0
        private set
    var lastPower: Double? = null
        private set
    var hasAimed = false
        private set
    var shell: Shell? = null
        private set
    private val _trail = mutableListOf<Pt>()
    val trail: List<Pt> get() = _trail
    var previousTrail: List<Pt> = emptyList()
        private set
    var blast: Blast? = null
        private set
    private var chargeElapsed = 0.0
    private var accumulator = 0.0
    private var flightSteps = 0
    private val rng = SplitMix64(seed)

    init {
        val hillMin: Double
        val hillMax: Double
        when (difficulty) {
            WakeChallenge.Difficulty.EASY -> {
                required = 2; targetRadius = 18.0; windMin = 0.0; windMax = 20.0; hillMin = 35.0; hillMax = 55.0
            }
            WakeChallenge.Difficulty.MEDIUM -> {
                required = 3; targetRadius = 15.0; windMin = 10.0; windMax = 45.0; hillMin = 50.0; hillMax = 75.0
            }
            WakeChallenge.Difficulty.HARD -> {
                required = 4; targetRadius = 12.0; windMin = 25.0; windMax = 65.0; hillMin = 65.0; hillMax = 95.0
            }
        }
        terrain = makeTerrain(hillMin, hillMax, rng)
        cannonY = top(terrain, CANNON_X) - 10
        target = Target(280.0, 0.0, targetRadius)
        relocate(avoiding = null)
    }

    /** Fuerza del viento en la escala que se enseña (0 a 7). */
    val windStrength: Int
        get() = (abs(wind) / 10).roundToInt()

    // MARK: - Controles

    /** Apunta hacia un punto del campo (coordenadas lógicas). */
    fun aim(x: Double, y: Double) {
        if (phase != Phase.AIMING && phase != Phase.CHARGING) return
        val dx = max(x - CANNON_X, 1.0)
        val dy = cannonY - y
        val degrees = atan2(dy, dx) * 180 / PI
        angle = degrees.coerceIn(MIN_ANGLE, MAX_ANGLE)
        hasAimed = true
    }

    /** Fija el ángulo directamente (para las pruebas). */
    internal fun setAngle(degrees: Double) {
        angle = degrees.coerceIn(MIN_ANGLE, MAX_ANGLE)
        hasAimed = true
    }

    fun beginCharge() {
        if (phase != Phase.AIMING) return
        phase = Phase.CHARGING
        chargeElapsed = 0.0
        power = 0.0
    }

    fun releaseCharge() {
        if (phase != Phase.CHARGING) return
        if (chargeElapsed < MIN_CHARGE) {
            phase = Phase.AIMING
            power = 0.0
            return
        }
        fire()
    }

    // MARK: - Simulación

    fun step(dt: Double): List<Event> {
        val events = mutableListOf<Event>()
        when (phase) {
            Phase.CHARGING -> {
                chargeElapsed += dt
                power = triangle(chargeElapsed / CHARGE_PERIOD)
            }
            Phase.FLYING -> {
                accumulator += dt
                while (accumulator >= FIXED_STEP && phase == Phase.FLYING) {
                    accumulator -= FIXED_STEP
                    advanceShell()?.let { events += it }
                }
            }
            Phase.EXPLODING -> {
                val current = blast
                if (current == null) {
                    phase = Phase.AIMING
                } else {
                    current.age += dt
                    if (current.age >= BLAST_DURATION) {
                        blast = null
                        if (current.hit) {
                            if (hits >= required) {
                                phase = Phase.DONE
                                events += Event.SOLVED
                            } else {
                                relocate(avoiding = target.x)
                                phase = Phase.AIMING
                            }
                        } else {
                            phase = Phase.AIMING
                        }
                    }
                }
            }
            Phase.AIMING, Phase.DONE -> Unit
        }
        return events
    }

    /** Dispara ya con la fuerza indicada (para las pruebas). */
    internal fun fire(withPower: Double) {
        if (phase != Phase.AIMING && phase != Phase.CHARGING) return
        power = withPower
        fire()
    }

    private fun fire() {
        lastPower = power
        val s = launch(cannonY, angle, power)
        shell = s
        previousTrail = _trail.toList()
        _trail.clear()
        _trail += Pt(s.x, s.y)
        accumulator = 0.0
        flightSteps = 0
        phase = Phase.FLYING
    }

    private fun advanceShell(): Event? {
        val s = shell ?: return null
        advance(s, wind)
        flightSteps += 1
        if (flightSteps % 3 == 0 && s.y >= -20) _trail += Pt(s.x, s.y)
        return when (outcome(s, terrain, target)) {
            Outcome.FLYING -> null
            Outcome.LOST -> {
                shell = null
                power = 0.0
                phase = Phase.AIMING
                Event.MISS
            }
            Outcome.IMPACT -> {
                shell = null
                _trail += Pt(s.x, s.y)
                val hit = isHit(s.x, s.y, target)
                carve(s.x, s.y)
                settleTarget()
                blast = Blast(s.x, s.y, hit)
                power = 0.0
                phase = Phase.EXPLODING
                if (hit) {
                    hits += 1
                    Event.HIT
                } else {
                    Event.MISS
                }
            }
        }
    }

    /**
     * Abre un cráter: la superficie baja donde la alcanza el círculo de la
     * explosión. Nunca bajo el cañón ni hasta el fondo del todo.
     */
    private fun carve(cx: Double, cy: Double) {
        val r = BLAST_RADIUS
        for (i in terrain.indices) {
            val x = i * SAMPLE_STEP
            val dx = x - cx
            if (abs(dx) >= r || abs(x - CANNON_X) <= 24) continue
            val h = sqrt(r * r - dx * dx)
            if (cy - h <= terrain[i] && terrain[i] < cy + h) {
                terrain[i] = min(cy + h, HEIGHT - 8)
            }
        }
    }

    /** El despertador descansa sobre lo más alto del suelo que tiene debajo. */
    private fun settleTarget() {
        target = target.copy(y = restingY(target.x, target.radius, terrain))
    }

    /**
     * Nuevo sitio y nuevo viento para el despertador, con la garantía de que
     * hay varias combinaciones de ángulo y fuerza que le dan.
     */
    private fun relocate(avoiding: Double?) {
        repeat(40) {
            val x = rng.nextDouble(TARGET_ZONE_MIN, TARGET_ZONE_MAX)
            if (avoiding != null && abs(x - avoiding) < 36) return@repeat
            val magnitude = rng.nextDouble(windMin, windMax)
            val candidateWind = if (rng.nextBoolean()) magnitude else -magnitude
            val candidate = Target(x, restingY(x, targetRadius, terrain), targetRadius)
            if (solutionCount(terrain, cannonY, candidate, candidateWind, enough = 4) >= 4) {
                target = candidate
                wind = candidateWind
                return
            }
        }
        // Muy improbable: sin viento y en el centro de la zona siempre hay tiro.
        wind = 0.0
        val x = (TARGET_ZONE_MIN + TARGET_ZONE_MAX) / 2
        target = Target(x, restingY(x, targetRadius, terrain), targetRadius)
    }
}
