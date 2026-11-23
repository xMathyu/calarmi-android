package com.mathyusolutions.calarmi.challenge.arcade

import com.mathyusolutions.calarmi.model.WakeChallenge.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val DT = 1.0 / 60

class ArcadeKitTest {
    @Test fun splitMixIsDeterministicAndInRange() {
        val a = SplitMix64(42)
        val b = SplitMix64(42)
        repeat(1000) {
            val x = a.nextDouble()
            assertEquals(x, b.nextDouble(), 0.0)
            assertTrue(x >= 0 && x < 1)
            val r = a.nextDouble(-3.0, 5.0)
            b.nextDouble(-3.0, 5.0)
            assertTrue(r >= -3 && r <= 5)
            val i = a.nextInt(7)
            b.nextInt(7)
            assertTrue(i in 0 until 7)
        }
        assertNotEquals(SplitMix64(1).nextLong(), SplitMix64(2).nextLong())
    }

    @Test fun wrapIsAlwaysPositive() {
        assertEquals(1.0, arcadeWrap(-25.0, 26.0), 1e-9)
        assertEquals(4.0, arcadeWrap(30.0, 26.0), 1e-9)
        assertEquals(0.0, arcadeWrap(52.0, 26.0), 1e-9)
    }
}

class FlapGameTest {
    /** Un piloto sencillo: aletea cuando la abeja cae por debajo del centro del hueco que viene. */
    private fun autopilot(game: FlapGame) {
        val next = game.columns.firstOrNull { it.x + FlapGame.COLUMN_WIDTH > FlapGame.BEE_X - FlapGame.BEE_RADIUS }
        val center = if (next != null && next.x < FlapGame.BEE_X + 160) (next.gapTop + next.gapBottom) / 2 else FlapGame.GROUND_Y / 2
        if (game.beeY > center + 18 && game.beeVY > 0) game.press()
    }

    @Test fun difficultyScalesColumnsAndGaps() {
        val easy = FlapGame(Difficulty.EASY, 1)
        val medium = FlapGame(Difficulty.MEDIUM, 1)
        val hard = FlapGame(Difficulty.HARD, 1)
        assertEquals(listOf(5, 8, 12), listOf(easy.required, medium.required, hard.required))
        assertTrue(easy.gap > medium.gap && medium.gap > hard.gap)
        assertTrue(easy.speed < medium.speed && medium.speed < hard.speed)
    }

    @Test fun waitsForTheFirstTap() {
        val game = FlapGame(Difficulty.MEDIUM, 3)
        repeat(300) { assertTrue(game.step(DT).isEmpty()) }
        assertEquals(FlapGame.Phase.READY, game.phase)
        assertTrue(game.columns.isEmpty())
        game.press()
        assertEquals(FlapGame.Phase.PLAYING, game.phase)
        assertTrue(game.beeVY < 0)
    }

    @Test fun crashingKeepsProgressAndNeedsACooldown() {
        val game = FlapGame(Difficulty.EASY, 5)
        game.press()
        // Pasa un par de huecos y luego se deja caer.
        var crashed = false
        var t = 0.0
        while (game.passed < 2 && t < 60) {
            autopilot(game)
            game.step(DT)
            t += DT
        }
        assertEquals(2, game.passed)
        repeat(600) {
            if (game.step(DT).contains(FlapGame.Event.CRASHED)) crashed = true
        }
        assertTrue(crashed)
        assertEquals(FlapGame.Phase.CRASHED, game.phase)
        assertEquals(2, game.passed)
        // El aviso de seguir ya salió; un toque reanuda con los huecos conservados.
        assertTrue(game.canContinue)
        game.press()
        assertEquals(FlapGame.Phase.PLAYING, game.phase)
        assertEquals(2, game.passed)
        assertTrue(game.columns.isEmpty())
    }

    @Test fun tapDuringCooldownDoesNothing() {
        val game = FlapGame(Difficulty.EASY, 9)
        game.press()
        while (game.phase != FlapGame.Phase.CRASHED) game.step(DT)
        game.step(0.1)
        assertFalse(game.canContinue)
        game.press()
        assertEquals(FlapGame.Phase.CRASHED, game.phase)
    }

    @Test fun autopilotSolvesEveryDifficultyOnce() {
        for (difficulty in Difficulty.entries) {
            for (seed in 1L..4L) {
                val game = FlapGame(difficulty, seed)
                game.press()
                var solved = 0
                var crashes = 0
                var t = 0.0
                while (game.phase != FlapGame.Phase.DONE && t < 240) {
                    if (game.phase == FlapGame.Phase.CRASHED) {
                        if (game.canContinue) game.press()
                    } else {
                        autopilot(game)
                    }
                    for (event in game.step(DT)) {
                        if (event == FlapGame.Event.SOLVED) solved++
                        if (event == FlapGame.Event.CRASHED) crashes++
                    }
                    t += DT
                }
                assertEquals("$difficulty/$seed", FlapGame.Phase.DONE, game.phase)
                assertEquals(game.required, game.passed)
                assertEquals(1, solved)
                assertTrue("$difficulty/$seed: $crashes choques", crashes <= 3)
                // Terminado, ya no pasa nada más.
                repeat(120) { assertTrue(game.step(DT).isEmpty()) }
            }
        }
    }

    @Test fun gapsStayInsideTheField() {
        val game = FlapGame(Difficulty.HARD, 11)
        game.press()
        var t = 0.0
        while (t < 60 && game.phase != FlapGame.Phase.DONE) {
            if (game.phase == FlapGame.Phase.CRASHED && game.canContinue) game.press() else autopilot(game)
            game.step(DT)
            for (c in game.columns) {
                assertTrue(c.gapTop >= FlapGame.GAP_MARGIN - 1e-9)
                assertTrue(c.gapBottom <= FlapGame.GROUND_Y - FlapGame.GAP_MARGIN + 1e-9)
                assertEquals(game.gap, c.gapBottom - c.gapTop, 1e-9)
            }
            t += DT
        }
    }
}

class HurdlesGameTest {
    /** Altura máxima de un salto con el dedo apoyado `hold` segundos. */
    private fun peak(hold: Double): Double {
        val game = HurdlesGame(Difficulty.EASY, 1)
        game.press()
        var t = 0.0
        var released = false
        var best = 0.0
        while (t < 2) {
            if (!released && t >= hold) {
                game.release()
                released = true
            }
            game.step(DT)
            best = maxOf(best, game.jumpHeight)
            t += DT
            if (t > hold && game.isOnGround) break
        }
        return best
    }

    @Test fun tapClearsLowFencesButHoldIsNeededForTallOnes() {
        val tap = peak(0.04)
        val hold = peak(0.4)
        val reachLow = HurdlesGame.LOW_HEIGHT - HurdlesGame.FENCE_FORGIVENESS
        val reachTall = HurdlesGame.TALL_HEIGHT - HurdlesGame.FENCE_FORGIVENESS
        assertTrue("toque: $tap", tap > reachLow && tap < reachTall)
        assertTrue("mantener: $hold", hold > reachTall)
    }

    @Test fun difficultyScaling() {
        val easy = HurdlesGame(Difficulty.EASY, 1)
        val hard = HurdlesGame(Difficulty.HARD, 1)
        assertEquals(8, easy.required)
        assertEquals(12, HurdlesGame(Difficulty.MEDIUM, 1).required)
        assertEquals(16, hard.required)
        assertEquals(0.0, easy.tallChance, 0.0)
        assertTrue(hard.doubleChance > 0)
    }

    @Test fun standingStillTripsAndKeepsProgress() {
        val game = HurdlesGame(Difficulty.EASY, 2)
        game.press()
        game.release()
        var crashed = false
        var t = 0.0
        while (!crashed && t < 10) {
            crashed = game.step(DT).contains(HurdlesGame.Event.CRASHED)
            t += DT
        }
        assertTrue(crashed)
        assertEquals(0, game.cleared)
        game.press()
        assertEquals("sin esperar no sigue", HurdlesGame.Phase.CRASHED, game.phase)
        while (!game.canContinue) game.step(DT)
        game.press()
        assertEquals(HurdlesGame.Phase.PLAYING, game.phase)
        assertTrue(game.obstacles.isEmpty())
    }

    @Test fun autopilotSolvesEveryDifficultyOnce() {
        for (difficulty in Difficulty.entries) {
            for (seed in 1L..4L) {
                val game = HurdlesGame(difficulty, seed)
                game.press()
                game.release()
                var holdLeft = 0.0
                var solved = 0
                var crashes = 0
                var t = 0.0
                var lastJumped = -1
                while (game.phase != HurdlesGame.Phase.DONE && t < 240) {
                    if (game.phase == HurdlesGame.Phase.CRASHED) {
                        if (game.canContinue) {
                            game.press()
                            game.release()
                        }
                    } else {
                        val next = game.obstacles.firstOrNull { !it.cleared && it.x + it.width > HurdlesGame.SHEEP_X - HurdlesGame.SHEEP_HALF_WIDTH }
                        if (next != null && next.id != lastJumped && game.isOnGround) {
                            val lead = when (next.kind) {
                                HurdlesGame.Kind.LOW -> 0.08
                                HurdlesGame.Kind.TALL -> 0.2
                                HurdlesGame.Kind.DOUBLE -> 0.1
                            }
                            if (next.x - (HurdlesGame.SHEEP_X + 8) <= game.speed * lead) {
                                game.press()
                                lastJumped = next.id
                                holdLeft = if (next.kind == HurdlesGame.Kind.LOW) 0.06 else 0.35
                            }
                        }
                    }
                    for (event in game.step(DT)) {
                        if (event == HurdlesGame.Event.SOLVED) solved++
                        if (event == HurdlesGame.Event.CRASHED) crashes++
                    }
                    if (holdLeft > 0) {
                        holdLeft -= DT
                        if (holdLeft <= 0) game.release()
                    }
                    t += DT
                }
                assertEquals("$difficulty/$seed", HurdlesGame.Phase.DONE, game.phase)
                assertEquals(game.required, game.cleared)
                assertEquals(1, solved)
                assertEquals("$difficulty/$seed", 0, crashes)
            }
        }
    }
}
