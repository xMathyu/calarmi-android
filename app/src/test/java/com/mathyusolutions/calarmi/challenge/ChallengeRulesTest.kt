package com.mathyusolutions.calarmi.challenge

import androidx.compose.ui.geometry.Offset
import com.mathyusolutions.calarmi.model.WakeChallenge.Difficulty
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChallengeRulesTest {
    private val random = Random(42)

    // MARK: - Cálculo

    /** Evalúa el texto del problema (sin precedencia rara: × antes que + y −). */
    private fun evaluate(text: String): Int {
        val tokens = text.split(" ")
        val terms = mutableListOf<Int>()
        val signs = mutableListOf(1)
        var i = 0
        var current = tokens[0].toInt()
        while (i + 2 <= tokens.lastIndex) {
            val op = tokens[i + 1]
            val value = tokens[i + 2].toInt()
            when (op) {
                "×" -> current *= value
                "+", "−" -> {
                    terms += current
                    signs += if (op == "+") 1 else -1
                    current = value
                }
                else -> error("Operador desconocido: $op")
            }
            i += 2
        }
        terms += current
        return terms.indices.sumOf { terms[it] * signs[it] }
    }

    @Test fun mathAnswersMatchTheTextAndAreNeverNegative() {
        for (difficulty in Difficulty.entries) {
            repeat(500) {
                val problem = MathProblem.random(difficulty, random)
                assertEquals(problem.text, evaluate(problem.text), problem.answer)
                assertTrue(problem.answer >= 0)
                assertTrue(problem.answer.toString().length <= MathProblem.MAX_DIGITS)
            }
        }
    }

    @Test fun hardMathIsAlwaysMultiplicationPlusAddend() {
        repeat(100) {
            val text = MathProblem.random(Difficulty.HARD, random).text
            assertTrue(text, Regex("""\d+ × \d \+ \d+""").matches(text))
        }
    }

    // MARK: - Memoria

    @Test fun memorySequencesNeverRepeatATileTwiceInARow() {
        repeat(200) {
            val length = MemoryRules.startLength(Difficulty.HARD) + 2
            val sequence = MemoryRules.makeSequence(length, random)
            assertEquals(length, sequence.size)
            assertTrue(sequence.all { it in 0 until MemoryRules.TILES })
            assertTrue(sequence.zipWithNext().none { (a, b) -> a == b })
        }
    }

    // MARK: - Ordenar

    @Test fun reshuffleKeepsTappedNumbersInPlace() {
        val numbers = (1..20).shuffled(random)
        val next = 8
        val shuffled = OrderRules.reshuffleRemaining(numbers, next, random)
        assertEquals(numbers.sorted(), shuffled.sorted())
        numbers.forEachIndexed { i, n -> if (n < next) assertEquals(n, shuffled[i]) }
    }

    // MARK: - Parejas

    @Test fun pairsDealEveryEmojiExactlyTwice() {
        for (difficulty in Difficulty.entries) {
            val cards = PairsRules.deal(difficulty, random)
            assertEquals(PairsRules.pairCount(difficulty) * 2, cards.size)
            assertTrue(cards.groupingBy { it }.eachCount().values.all { it == 2 })
        }
    }

    // MARK: - Colores

    @Test fun stroopWordAndInkDifferAndBothAreOptions() {
        var previous: StroopRound? = null
        repeat(300) {
            val count = if (it % 2 == 0) 4 else 6
            val round = StroopRound.random(count, previous, random)
            assertNotEquals(round.word, round.ink)
            assertEquals(count, round.options.size)
            assertEquals(count, round.options.toSet().size)
            assertTrue(round.ink in round.options && round.word in round.options)
            if (previous != null) assertNotEquals(previous!!.ink, round.ink)
            previous = round
        }
    }

    // MARK: - El distinto

    @Test fun oddRoundsChangePairAndKeepTheOddInsideTheGrid() {
        var previous: OddRound? = null
        repeat(200) {
            val round = OddRules.newRound(it + 1, previous?.common, 25, random)
            assertNotEquals(round.common, round.odd)
            assertTrue(round.oddIndex in 0 until 25)
            previous?.let { p -> assertTrue(round.common != p.common && round.odd != p.common) }
            previous = round
        }
    }

    // MARK: - Escribir

    @Test fun typingMarksTheFirstMismatch() {
        val phrase = "Hoy va a ser un gran día"
        assertNull(TypingRules.mismatchIndex(phrase, ""))
        assertNull(TypingRules.mismatchIndex(phrase, "Hoy va"))
        assertEquals(0, TypingRules.mismatchIndex(phrase, "hoy"))
        assertEquals(22, TypingRules.mismatchIndex(phrase, "Hoy va a ser un gran dia"))
        // Escribir de más también es un error.
        assertEquals(phrase.length, TypingRules.mismatchIndex(phrase, "$phrase!"))
    }

    @Test fun typingIgnoresTrailingSpacesAndDecomposedAccents() {
        val phrase = "Sonríe, ya es de día"
        assertTrue(TypingRules.matches(phrase, "$phrase  "))
        // «í» escrita como «i» + tilde combinante.
        val decomposed = "Sonríe, ya es de día"
        assertTrue(TypingRules.matches(phrase, decomposed))
        assertNull(TypingRules.mismatchIndex(phrase, decomposed))
        assertFalse(TypingRules.matches(phrase, "sonríe, ya es de día"))
    }

    // MARK: - Sacudir

    @Test fun oneShakeCountsOnceAndNeedsCalmBeforeTheNext() {
        val detector = ShakeDetector()
        // Un meneo largo: varias lecturas fuertes seguidas cuentan una sola vez.
        assertTrue(detector.process(2.2, 0.00))
        assertFalse(detector.process(2.4, 0.02))
        assertFalse(detector.process(0.1, 0.30))
        assertEquals(1, detector.shakes)
        // Calma y otra sacudida pasado el rebote: cuenta.
        assertFalse(detector.process(1.0, 0.40))
        assertTrue(detector.process(2.0, 0.60))
        assertEquals(2, detector.shakes)
        // Calma pero demasiado pronto: no cuenta.
        assertFalse(detector.process(1.0, 0.65))
        assertFalse(detector.process(2.0, 0.70))
        assertEquals(2, detector.shakes)
    }

    // MARK: - Caminar

    @Test fun peakDetectorCountsSteadyWalkingAndIgnoresStillness() {
        val walking = StepPeakDetector()
        // 10 s a 50 Hz caminando a 2 pasos por segundo (±0,3 g).
        for (i in 0 until 500) {
            val t = i / 50.0
            walking.process(1.0 + 0.3 * sin(2 * PI * 2.0 * t), t)
        }
        assertTrue("pasos: ${walking.steps}", walking.steps in 18..21)

        // Paso suave (±0,15 g), con el teléfono quieto en la mano: también cuenta.
        val gentle = StepPeakDetector()
        for (i in 0 until 500) {
            val t = i / 50.0
            gentle.process(1.0 + 0.15 * sin(2 * PI * 1.8 * t), t)
        }
        assertTrue("pasos: ${gentle.steps}", gentle.steps in 16..19)

        val still = StepPeakDetector()
        for (i in 0 until 500) still.process(1.0 + 0.02 * sin(i.toDouble()), i / 50.0)
        assertEquals(0, still.steps)
    }

    // MARK: - Laberinto

    private fun path(maze: TiltMazeGrid): List<Pair<Int, Int>>? {
        val start = maze.start
        val previous = mutableMapOf(start to start)
        val queue = ArrayDeque(listOf(start))
        while (queue.isNotEmpty()) {
            val cell = queue.removeFirst()
            if (cell == maze.exit) {
                val result = mutableListOf(cell)
                var c = cell
                while (c != start) {
                    c = previous.getValue(c)
                    result += c
                }
                return result.reversed()
            }
            val (r, c) = cell
            for (next in listOf(r - 1 to c, r + 1 to c, r to c - 1, r to c + 1)) {
                if (!maze.isWall(next.first, next.second) && next !in previous) {
                    previous[next] = cell
                    queue += next
                }
            }
        }
        return null
    }

    @Test fun everyMazeHasAPathFromStartToExit() {
        for (difficulty in Difficulty.entries) {
            for (lines in TiltMazes.all(difficulty)) {
                val maze = TiltMazeGrid(lines)
                assertTrue(lines.all { it.length == maze.size })
                assertNotNull(lines.joinToString("\n"), path(maze))
            }
        }
    }

    @Test fun draggingAlongThePathReachesTheExit() {
        for (difficulty in Difficulty.entries) {
            for (lines in TiltMazes.all(difficulty)) {
                val maze = TiltMazeGrid(lines)
                var ball = TiltMazeBall.at(maze.start.first, maze.start.second)
                for ((r, c) in path(maze)!!) ball = TiltPhysics.drag(ball, c + 0.5, r + 0.5, maze)
                assertTrue(lines.joinToString("\n"), maze.isExit(ball))
            }
        }
    }

    @Test fun gravityNeverPushesTheBallThroughWalls() {
        for (lines in TiltMazes.all(Difficulty.HARD)) {
            val maze = TiltMazeGrid(lines)
            var ball = TiltMazeBall.at(maze.start.first, maze.start.second)
            // Teléfono muy inclinado cambiando de lado cada segundo, a 60 Hz.
            for (i in 0 until 60 * 20) {
                val phase = (i / 60) % 4
                val gx = if (phase == 0) 1.0 else if (phase == 2) -1.0 else 0.3
                val gy = if (phase == 1) 1.0 else if (phase == 3) -1.0 else -0.3
                ball = TiltPhysics.step(ball, gx, gy, 1.0 / 60, maze).ball
                val r = TiltMazeBall.RADIUS
                for (x in listOf(ball.x - r, ball.x + r)) {
                    for (y in listOf(ball.y - r, ball.y + r)) {
                        assertFalse("bola en pared: $ball", maze.isWall(y.toInt(), x.toInt()))
                    }
                }
            }
        }
    }

    @Test fun flatPhoneLetsTheBallStop() {
        val maze = TiltMazeGrid(TiltMazes.all(Difficulty.EASY).first())
        var ball = TiltMazeBall.at(maze.start.first, maze.start.second).copy(vx = 2.0)
        repeat(240) { ball = TiltPhysics.step(ball, 0.01, -0.01, 1.0 / 60, maze).ball }
        assertTrue(kotlin.math.abs(ball.vx) < 0.05 && kotlin.math.abs(ball.vy) < 0.05)
    }

    // MARK: - Atrapa

    @Test fun reflexSpotsStayInsideAndApartFromOthers() {
        val occupied = mutableListOf<Offset>()
        repeat(100) {
            val spot = ReflexRules.freeSpot(900f, 800f, 160f, 30f, occupied.takeLast(2), random)!!
            assertTrue(spot.x in 95f..805f && spot.y in 95f..705f)
            occupied.takeLast(2).forEach { assertTrue((it - spot).getDistance() >= 190f) }
            occupied += spot
        }
        assertNull(ReflexRules.freeSpot(100f, 100f, 160f, 30f, emptyList(), random))
    }
}
