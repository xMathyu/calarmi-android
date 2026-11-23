package com.mathyusolutions.calarmi.challenge

import androidx.compose.ui.geometry.Offset
import com.mathyusolutions.calarmi.model.WakeChallenge.Difficulty
import java.text.Normalizer
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sqrt
import kotlin.random.Random

// Las reglas de los retos, sin nada de interfaz: lo que hay que comprobar con
// pruebas unitarias. Las cifras son las mismas que en iOS para que un reto
// cueste lo mismo en los dos teléfonos.

// MARK: - Cálculo

/**
 * Una operación con su resultado. Los operandos se eligen para que el
 * resultado sea siempre un entero no negativo, así el teclado no necesita
 * signo menos.
 */
data class MathProblem(val text: String, val answer: Int) {
    companion object {
        /** Tres operaciones seguidas. */
        const val TOTAL = 3

        /** Tope de cifras que admite el teclado. */
        const val MAX_DIGITS = 6

        fun random(difficulty: Difficulty, random: Random = Random.Default): MathProblem = when (difficulty) {
            Difficulty.EASY -> {
                // Suma o resta de dos cifras: 47 + 38, 83 − 29.
                val a = (10..99).random(random)
                val b = (10..99).random(random)
                if (random.nextBoolean()) {
                    MathProblem("$a + $b", a + b)
                } else {
                    val hi = maxOf(a, b)
                    val lo = minOf(a, b)
                    MathProblem("$hi − $lo", hi - lo)
                }
            }
            Difficulty.MEDIUM -> {
                // Multiplicación de una cifra por dos, o suma y resta encadenadas.
                if (random.nextBoolean()) {
                    val a = (3..9).random(random)
                    val b = (12..29).random(random)
                    MathProblem("$a × $b", a * b)
                } else {
                    val a = (20..99).random(random)
                    val b = (10..99).random(random)
                    val c = (1..minOf(a + b, 60)).random(random)
                    MathProblem("$a + $b − $c", a + b - c)
                }
            }
            Difficulty.HARD -> {
                // Multiplicación de dos cifras más un sumando: 23 × 7 + 45.
                val a = (12..29).random(random)
                val b = (3..9).random(random)
                val c = (10..99).random(random)
                MathProblem("$a × $b + $c", a * b + c)
            }
        }
    }
}

// MARK: - Memoria

/** Un «Simón» de 3×3: tres rondas, cada una con una casilla más. */
object MemoryRules {
    const val TOTAL_ROUNDS = 3
    const val TILES = 9

    fun startLength(difficulty: Difficulty): Int = when (difficulty) {
        Difficulty.EASY -> 3
        Difficulty.MEDIUM -> 4
        Difficulty.HARD -> 5
    }

    /** Casillas al azar sin dos iguales seguidas, que serían ambiguas al verlas. */
    fun makeSequence(length: Int, random: Random = Random.Default): List<Int> {
        val result = mutableListOf<Int>()
        while (result.size < length) {
            val next = random.nextInt(TILES)
            if (next != result.lastOrNull()) result += next
        }
        return result
    }
}

// MARK: - Ordenar números

/** Una tabla de Schulte: los números del 1 al N revueltos en una cuadrícula. */
object OrderRules {
    fun count(difficulty: Difficulty): Int = when (difficulty) {
        Difficulty.EASY -> 9
        Difficulty.MEDIUM -> 16
        Difficulty.HARD -> 20
    }

    fun columns(difficulty: Difficulty): Int = if (difficulty == Difficulty.EASY) 3 else 4

    /**
     * Ancho entre alto de la cuadrícula entera. Sale de las medidas de iOS
     * (360 pt de ancho y casillas de 96, 72 o 58 pt de alto) para que las cinco
     * filas de difícil quepan junto a la cabecera.
     */
    fun aspectRatio(difficulty: Difficulty): Float = when (difficulty) {
        Difficulty.EASY -> 1.18f
        Difficulty.MEDIUM -> 1.15f
        Difficulty.HARD -> 1.12f
    }

    /** Revuelve solo los números que faltan; los ya tocados se quedan en su sitio. */
    fun reshuffleRemaining(numbers: List<Int>, next: Int, random: Random = Random.Default): List<Int> {
        val positions = numbers.indices.filter { numbers[it] >= next }
        val values = positions.map { numbers[it] }.shuffled(random)
        val result = numbers.toMutableList()
        positions.forEachIndexed { i, position -> result[position] = values[i] }
        return result
    }
}

// MARK: - Parejas

/** El juego de memoria con cartas: parejas de emojis boca abajo. */
object PairsRules {
    val pool = listOf(
        "🌞", "🍳", "☕️", "🐣", "🚀", "🎧", "🧠", "🏃‍♀️",
        "🍋", "🪐", "🎸", "🧃", "🌈", "🦄", "🍩", "🛼",
    )

    fun pairCount(difficulty: Difficulty): Int = when (difficulty) {
        Difficulty.EASY -> 3
        Difficulty.MEDIUM -> 4
        Difficulty.HARD -> 6
    }

    fun columns(difficulty: Difficulty): Int = if (difficulty == Difficulty.EASY) 3 else 4

    /** Las cartas ya barajadas: cada emoji elegido, dos veces. */
    fun deal(difficulty: Difficulty, random: Random = Random.Default): List<String> {
        val chosen = pool.shuffled(random).take(pairCount(difficulty))
        return (chosen + chosen).shuffled(random)
    }
}

// MARK: - Colores (Stroop)

/** Los seis colores del juego. El nombre y la tinta los pone la interfaz. */
enum class StroopColor { RED, GREEN, BLUE, YELLOW, PURPLE, ORANGE }

/** Una ronda: la palabra, la tinta con la que se pinta y las muestras a elegir. */
data class StroopRound(val word: StroopColor, val ink: StroopColor, val options: List<StroopColor>) {
    companion object {
        /**
         * Palabra y tinta siempre distintas. La tinta cambia respecto a la ronda
         * anterior para que no valga repetir la misma muestra. Entre las opciones
         * están siempre la tinta y el color de la palabra (el señuelo), y el resto
         * se rellena al azar; todo barajado para que la posición no delate nada.
         */
        fun random(optionCount: Int, avoiding: StroopRound?, random: Random = Random.Default): StroopRound {
            val all = StroopColor.entries
            val ink = all.filter { it != avoiding?.ink }.random(random)
            val word = all.filter { it != ink }.random(random)
            val others = all.filter { it != ink && it != word }.shuffled(random)
            val options = listOf(ink, word) + others.take(maxOf(0, optionCount - 2))
            return StroopRound(word, ink, options.shuffled(random))
        }
    }
}

object ColorsRules {
    fun totalRounds(difficulty: Difficulty): Int = when (difficulty) {
        Difficulty.EASY -> 5
        Difficulty.MEDIUM -> 8
        Difficulty.HARD -> 12
    }

    fun optionCount(difficulty: Difficulty): Int = if (difficulty == Difficulty.HARD) 6 else 4
}

// MARK: - El distinto

/** Una cuadrícula del mismo emoji con uno muy parecido colado en `oddIndex`. */
data class OddRound(val round: Int, val common: String, val odd: String, val oddIndex: Int)

object OddRules {
    /** Parejas que se confunden a simple vista. Se usan en cualquier orientación. */
    val pairs: List<Pair<String, String>> = listOf(
        "😀" to "😃", "🐶" to "🐺", "🍎" to "🍅", "🌝" to "🌚", "⚽️" to "🏐",
        "🟢" to "🟩", "🐱" to "🐯", "🍪" to "🥯", "🌷" to "🌹", "🦋" to "🐛",
        "🚗" to "🚕", "🧁" to "🍰", "🐭" to "🐹", "🌙" to "🌛", "🍊" to "🍑",
    )

    fun totalRounds(difficulty: Difficulty): Int = when (difficulty) {
        Difficulty.EASY -> 4
        Difficulty.MEDIUM -> 6
        Difficulty.HARD -> 8
    }

    fun columns(difficulty: Difficulty): Int = when (difficulty) {
        Difficulty.EASY -> 3
        Difficulty.MEDIUM -> 4
        Difficulty.HARD -> 5
    }

    /**
     * Elige una pareja distinta de la anterior, una orientación al azar y la
     * posición del distinto.
     */
    fun newRound(round: Int, previousCommon: String?, cellCount: Int, random: Random = Random.Default): OddRound {
        val candidates = pairs.filter { it.first != previousCommon && it.second != previousCommon }
        val pair = candidates.ifEmpty { pairs }.random(random)
        val flipped = random.nextBoolean()
        return OddRound(
            round = round,
            common = if (flipped) pair.second else pair.first,
            odd = if (flipped) pair.first else pair.second,
            oddIndex = random.nextInt(cellCount),
        )
    }
}

// MARK: - Escribir

object TypingRules {
    fun total(difficulty: Difficulty): Int = when (difficulty) {
        Difficulty.EASY -> 1
        Difficulty.MEDIUM -> 2
        Difficulty.HARD -> 3
    }

    /**
     * Sin espacios ni saltos de línea al final (un espacio tras la última
     * palabra no cuenta como error) y en forma NFC: algunos teclados mandan la
     * tilde como carácter aparte y «í» tiene que valer igual que «í».
     */
    fun normalize(text: String): String = Normalizer.normalize(text.trimEnd(), Normalizer.Form.NFC)

    /**
     * Posición del primer carácter escrito que no coincide con la frase
     * (también si se escribió de más), o null si todo va bien hasta ahora.
     */
    fun mismatchIndex(phrase: String, input: String): Int? {
        val target = normalize(phrase)
        val typed = normalize(input)
        for (i in typed.indices) {
            if (i >= target.length || target[i] != typed[i]) return i
        }
        return null
    }

    fun typedLength(input: String): Int = normalize(input).length

    fun matches(phrase: String, input: String): Boolean = normalize(input) == normalize(phrase)
}

// MARK: - Sacudir

object ShakeRules {
    fun required(difficulty: Difficulty): Int = when (difficulty) {
        Difficulty.EASY -> 20
        Difficulty.MEDIUM -> 35
        Difficulty.HARD -> 50
    }
}

/**
 * Cuenta sacudidas con el acelerómetro. Una sacudida es un pico en el que el
 * módulo de la aceleración se aleja más de 0,8 g de 1 g (la gravedad en
 * reposo). Tras contar una, hay que volver a la calma (menos de 0,4 g) y dejar
 * pasar 150 ms antes de la siguiente: así un solo meneo no cuenta varias veces.
 */
class ShakeDetector {
    var shakes = 0
        private set
    private var lastShake = Double.NEGATIVE_INFINITY
    private var armed = true

    /** `magnitude` en g, `time` en segundos. Devuelve si contó una sacudida. */
    fun process(magnitude: Double, time: Double): Boolean {
        val deviation = kotlin.math.abs(magnitude - 1)
        if (deviation < REARM) {
            armed = true
            return false
        }
        if (!armed || deviation <= THRESHOLD || time - lastShake < DEBOUNCE) return false
        armed = false
        lastShake = time
        shakes += 1
        return true
    }

    companion object {
        const val THRESHOLD = 0.8
        const val REARM = 0.4
        const val DEBOUNCE = 0.15

        fun magnitude(x: Float, y: Float, z: Float): Double = sqrt((x * x + y * y + z * z).toDouble())
    }
}

// MARK: - Caminar

object StepsRules {
    fun required(difficulty: Difficulty): Int = when (difficulty) {
        Difficulty.EASY -> 15
        Difficulty.MEDIUM -> 30
        Difficulty.HARD -> 50
    }
}

/**
 * Pasos a partir del acelerómetro (sin permisos: el detector de pasos del
 * sistema pediría el de actividad física). Al caminar con el teléfono en la mano el
 * módulo de la aceleración sube y baja unas dos veces por segundo: se suaviza
 * para quitar el temblor y cada vez que cruza hacia arriba 1,08 g cuenta un
 * paso; para contar otro tiene que bajar antes de 0,97 g y pasar al menos
 * 300 ms (nadie da más de tres pasos por segundo).
 */
class StepPeakDetector {
    var steps = 0
        private set
    private var smoothed = 1.0
    private var above = false
    private var lastStep = Double.NEGATIVE_INFINITY

    /** `magnitude` en g, `time` en segundos. Devuelve si contó un paso. */
    fun process(magnitude: Double, time: Double): Boolean {
        smoothed += ALPHA * (magnitude - smoothed)
        if (!above && smoothed > HIGH) {
            above = true
            if (time - lastStep >= MIN_INTERVAL) {
                lastStep = time
                steps += 1
                return true
            }
        } else if (above && smoothed < LOW) {
            above = false
        }
        return false
    }

    companion object {
        const val ALPHA = 0.25
        const val HIGH = 1.08
        const val LOW = 0.97
        const val MIN_INTERVAL = 0.3
    }
}

// MARK: - Laberinto

/**
 * La bola, en coordenadas de casilla (el centro de la casilla (0,0) es
 * (0,5, 0,5)). Mutable como un `struct` de Swift: se copia, se mueve la copia
 * y se publica.
 */
data class TiltMazeBall(var x: Double, var y: Double, var vx: Double = 0.0, var vy: Double = 0.0) {
    /**
     * Desplaza en X y, si la caja de la bola entra en una pared, la deja pegada
     * a ella. Devuelve si chocó. Como el paso es menor que una casilla, solo
     * puede haber entrado en la columna siguiente en el sentido del movimiento,
     * y se comprueba en todas las filas que la caja cubre (así tampoco se cuela
     * por una esquina).
     */
    fun moveX(dx: Double, maze: TiltMazeGrid): Boolean {
        if (dx == 0.0) return false
        x += dx
        val rows = floor(y - RADIUS).toInt()..floor(y + RADIUS).toInt()
        if (dx > 0) {
            val col = floor(x + RADIUS).toInt()
            if (rows.any { maze.isWall(it, col) }) {
                x = col - RADIUS - EPSILON
                return true
            }
        } else {
            val col = floor(x - RADIUS).toInt()
            if (rows.any { maze.isWall(it, col) }) {
                x = col + 1 + RADIUS + EPSILON
                return true
            }
        }
        return false
    }

    fun moveY(dy: Double, maze: TiltMazeGrid): Boolean {
        if (dy == 0.0) return false
        y += dy
        val cols = floor(x - RADIUS).toInt()..floor(x + RADIUS).toInt()
        if (dy > 0) {
            val row = floor(y + RADIUS).toInt()
            if (cols.any { maze.isWall(row, it) }) {
                y = row - RADIUS - EPSILON
                return true
            }
        } else {
            val row = floor(y - RADIUS).toInt()
            if (cols.any { maze.isWall(row, it) }) {
                y = row + 1 + RADIUS + EPSILON
                return true
            }
        }
        return false
    }

    companion object {
        /**
         * Radio de colisión en casillas: menor que media casilla para que la bola
         * quepa por los pasillos y pueda doblar las esquinas.
         */
        const val RADIUS = 0.3

        /**
         * Holgura con la que se deja la bola al chocar: justo fuera de la pared,
         * nunca sobre el borde exacto de la casilla.
         */
        private const val EPSILON = 0.001

        fun at(row: Int, col: Int) = TiltMazeBall(x = col + 0.5, y = row + 0.5)
    }
}

/**
 * Un laberinto cuadrado de casillas: `#` pared, `.` pasillo, `S` salida de la
 * bola y `E` meta. Fuera del tablero todo es pared.
 */
class TiltMazeGrid(val lines: List<String>) {
    val size: Int = lines.size
    private val walls: List<List<Boolean>> = lines.map { line -> line.map { it == '#' } }
    val start: Pair<Int, Int> = find('S')
    val exit: Pair<Int, Int> = find('E')

    private fun find(mark: Char): Pair<Int, Int> {
        lines.forEachIndexed { row, line ->
            val col = line.indexOf(mark)
            if (col >= 0) return row to col
        }
        return 0 to 0
    }

    fun isWall(row: Int, col: Int): Boolean {
        val cells = walls.getOrNull(row) ?: return true
        return cells.getOrNull(col) ?: true
    }

    fun isExit(ball: TiltMazeBall): Boolean =
        floor(ball.x).toInt() == exit.second && floor(ball.y).toInt() == exit.first
}

/** Resultado de un paso de la simulación: la bola movida y si chocó con ganas. */
data class TiltStep(val ball: TiltMazeBall, val bumpSpeed: Double)

object TiltPhysics {
    /** Aceleración en casillas/s² con el teléfono de canto (gravedad entera). */
    const val GRAVITY_GAIN = 15.0

    /** Rozamiento: la velocidad decae a exp(-friction · t). */
    const val FRICTION = 2.2

    /** Tope en casillas/s: con él un fotograma nunca atraviesa una pared. */
    const val MAX_SPEED = 6.0

    /** Cuánto rebota la bola al chocar (0 = se queda pegada). */
    const val RESTITUTION = 0.2

    /** Por debajo de esta inclinación el teléfono cuenta como plano. */
    const val DEAD_ZONE = 0.03

    /** Paso máximo por iteración al arrastrar con el dedo, en casillas. */
    const val DRAG_STEP = 0.2

    /**
     * Un paso de la simulación. `gx` y `gy` son la gravedad en g con la
     * convención de iOS: x hacia la derecha de la pantalla, y hacia arriba (en
     * vertical `gy` es negativa y la bola cae hacia abajo).
     */
    fun step(ball: TiltMazeBall, gx: Double, gy: Double, dt: Double, maze: TiltMazeGrid): TiltStep {
        val b = ball.copy()
        val ax = if (kotlin.math.abs(gx) < DEAD_ZONE) 0.0 else gx
        val ay = if (kotlin.math.abs(gy) < DEAD_ZONE) 0.0 else gy
        b.vx += ax * GRAVITY_GAIN * dt
        b.vy += -ay * GRAVITY_GAIN * dt

        val damping = exp(-FRICTION * dt)
        b.vx *= damping
        b.vy *= damping

        val speed = hypot(b.vx, b.vy)
        if (speed > MAX_SPEED) {
            b.vx *= MAX_SPEED / speed
            b.vy *= MAX_SPEED / speed
        }

        var bump = 0.0
        if (b.moveX(b.vx * dt, maze)) {
            bump = maxOf(bump, kotlin.math.abs(b.vx))
            b.vx = -b.vx * RESTITUTION
        }
        if (b.moveY(b.vy * dt, maze)) {
            bump = maxOf(bump, kotlin.math.abs(b.vy))
            b.vy = -b.vy * RESTITUTION
        }
        return TiltStep(b, bump)
    }

    /**
     * Sin sensores: la bola sigue al dedo en pasitos cortos, resolviendo las
     * paredes en cada uno, así no salta a través de ellas.
     */
    fun drag(ball: TiltMazeBall, targetX: Double, targetY: Double, maze: TiltMazeGrid): TiltMazeBall {
        val b = ball.copy(vx = 0.0, vy = 0.0)
        repeat(64) {
            val dx = targetX - b.x
            val dy = targetY - b.y
            if (kotlin.math.abs(dx) < 0.01 && kotlin.math.abs(dy) < 0.01) return b
            val before = b.copy()
            b.moveX(dx.coerceIn(-DRAG_STEP, DRAG_STEP), maze)
            b.moveY(dy.coerceIn(-DRAG_STEP, DRAG_STEP), maze)
            // Bloqueada por una pared en ambos ejes: hasta aquí llega.
            if (b == before) return b
        }
        return b
    }
}

object TiltMazes {
    fun random(difficulty: Difficulty, random: Random = Random.Default): TiltMazeGrid =
        TiltMazeGrid(all(difficulty).random(random))

    fun all(difficulty: Difficulty): List<List<String>> = when (difficulty) {
        Difficulty.EASY -> easy
        Difficulty.MEDIUM -> medium
        Difficulty.HARD -> hard
    }

    // Los mismos de iOS. Todos comprobados (también en las pruebas): la meta
    // siempre se alcanza desde la salida.

    private val easy = listOf(
        listOf(
            "#######",
            "#S..#.#",
            "#.#.#.#",
            "#.#...#",
            "#.###.#",
            "#....E#",
            "#######",
        ),
        listOf(
            "#######",
            "#...#E#",
            "#.#.#.#",
            "#.#...#",
            "#.#####",
            "#S....#",
            "#######",
        ),
        listOf(
            "#######",
            "#E#...#",
            "#.#.#.#",
            "#...#.#",
            "###.#.#",
            "#S....#",
            "#######",
        ),
    )

    private val medium = listOf(
        listOf(
            "#########",
            "#S..#...#",
            "#.#.#.#.#",
            "#.#...#.#",
            "#.#####.#",
            "#...#...#",
            "###.#.###",
            "#...#..E#",
            "#########",
        ),
        listOf(
            "#########",
            "#...#..E#",
            "#.#.#.#.#",
            "#.#...#.#",
            "#.###.#.#",
            "#...#.#.#",
            "#.#.#...#",
            "#S#.....#",
            "#########",
        ),
        listOf(
            "#########",
            "#E..#...#",
            "###.#.#.#",
            "#...#.#.#",
            "#.###.#.#",
            "#.....#.#",
            "#.###.#.#",
            "#S..#...#",
            "#########",
        ),
    )

    private val hard = listOf(
        listOf(
            "###########",
            "#S..#.....#",
            "#.#.#.###.#",
            "#.#...#...#",
            "#.#####.#.#",
            "#...#...#.#",
            "###.#.###.#",
            "#...#.#...#",
            "#.###.#.###",
            "#.....#..E#",
            "###########",
        ),
        listOf(
            "###########",
            "#...#.....#",
            "#.#.#.###.#",
            "#.#...#.#.#",
            "#.###.#.#.#",
            "#...#...#.#",
            "#.#.#####.#",
            "#.#.....#.#",
            "#.#####.#.#",
            "#S....#..E#",
            "###########",
        ),
        listOf(
            "###########",
            "#E#.......#",
            "#.#.#####.#",
            "#...#...#.#",
            "#.###.#.#.#",
            "#.#...#...#",
            "#.#.#####.#",
            "#.#.#.....#",
            "#.#.#.###.#",
            "#...#...#S#",
            "###########",
        ),
    )
}

// MARK: - Atrapa

object ReflexRules {
    val emojis = listOf("🐣", "☕️", "🌞", "⏰", "🥐", "🚀")

    fun required(difficulty: Difficulty): Int = when (difficulty) {
        Difficulty.EASY -> 10
        Difficulty.MEDIUM -> 15
        Difficulty.HARD -> 20
    }

    /** Milisegundos que un objetivo se deja ver antes de esconderse. */
    fun lifetimeMillis(difficulty: Difficulty): Int = when (difficulty) {
        Difficulty.EASY -> 1600
        Difficulty.MEDIUM -> 1200
        Difficulty.HARD -> 900
    }

    fun maxVisible(difficulty: Difficulty): Int = if (difficulty == Difficulty.HARD) 2 else 1

    /**
     * Un punto al azar con el objetivo entero dentro del escenario y sin pisar
     * los puntos a evitar. Si tras muchos intentos no hay sitio limpio (solo
     * pasaría en un escenario minúsculo) devuelve el último candidato: antes
     * solapar que dejar al jugador sin nada que tocar. Medidas en píxeles.
     */
    fun freeSpot(
        width: Float,
        height: Float,
        size: Float,
        gap: Float,
        occupied: List<Offset>,
        random: Random = Random.Default,
    ): Offset? {
        val inset = size / 2 + gap / 2
        if (width <= inset * 2 || height <= inset * 2) return null
        val minDistance = size + gap
        var candidate = Offset(width / 2, height / 2)
        repeat(40) {
            candidate = Offset(
                inset + random.nextFloat() * (width - inset * 2),
                inset + random.nextFloat() * (height - inset * 2),
            )
            if (occupied.all { (it - candidate).getDistance() >= minDistance }) return candidate
        }
        return candidate
    }
}
