package com.mathyusolutions.calarmi.challenge.arcade

import com.mathyusolutions.calarmi.model.WakeChallenge.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CannonGameTest {
    private val dt = 1.0 / 60

    /** El primer ángulo y fuerza de la rejilla que dan al despertador con el terreno de ahora. */
    private fun solution(game: CannonGame): Pair<Double, Double>? {
        var angle = 14.0
        while (angle <= 82) {
            var power = 0.08
            while (power <= 1.0001) {
                if (CannonGame.shotHits(game.terrain, game.cannonY, game.target, game.wind, angle, power)) return angle to power
                power += 0.01
            }
            angle += 1
        }
        return null
    }

    /** Avanza hasta que el disparo termina (vuelve a apuntar o gana). */
    private fun settle(game: CannonGame): List<CannonGame.Event> {
        val events = mutableListOf<CannonGame.Event>()
        var t = 0.0
        while ((game.phase == CannonGame.Phase.FLYING || game.phase == CannonGame.Phase.EXPLODING) && t < 20) {
            events += game.step(dt)
            t += dt
        }
        return events
    }

    @Test fun triangleWaveGoesUpAndDown() {
        assertEquals(0.0, CannonGame.triangle(0.0), 1e-9)
        assertEquals(0.5, CannonGame.triangle(0.5), 1e-9)
        assertEquals(1.0, CannonGame.triangle(1.0), 1e-9)
        assertEquals(0.5, CannonGame.triangle(1.5), 1e-9)
        assertEquals(0.0, CannonGame.triangle(2.0), 1e-9)
        assertEquals(0.25, CannonGame.triangle(2.25), 1e-9)
    }

    @Test fun difficultyScaling() {
        val easy = CannonGame(Difficulty.EASY, 1)
        val medium = CannonGame(Difficulty.MEDIUM, 1)
        val hard = CannonGame(Difficulty.HARD, 1)
        assertEquals(listOf(2, 3, 4), listOf(easy.required, medium.required, hard.required))
        assertTrue(easy.targetRadius > medium.targetRadius && medium.targetRadius > hard.targetRadius)
    }

    @Test fun everyNewTargetHasSeveralSolutions() {
        for (difficulty in Difficulty.entries) {
            for (seed in 1L..6L) {
                val game = CannonGame(difficulty, seed)
                val target = game.target
                assertTrue(target.x in CannonGame.TARGET_ZONE_MIN..CannonGame.TARGET_ZONE_MAX)
                // Descansa sobre el suelo.
                assertEquals(CannonGame.restingY(target.x, target.radius, game.terrain), target.y, 1e-9)
                assertTrue(CannonGame.solutionCount(game.terrain, game.cannonY, target, game.wind, enough = 4) >= 4)
            }
        }
    }

    @Test fun aimIsClampedToTheAllowedRange() {
        val game = CannonGame(Difficulty.MEDIUM, 2)
        assertFalse(game.hasAimed)
        game.aim(CannonGame.CANNON_X + 100, game.cannonY - 100)
        assertEquals(45.0, game.angle, 1e-9)
        assertTrue(game.hasAimed)
        game.aim(CannonGame.CANNON_X + 100, game.cannonY + 50)
        assertEquals(CannonGame.MIN_ANGLE, game.angle, 1e-9)
        game.aim(CannonGame.CANNON_X - 50, 0.0)
        assertEquals(CannonGame.MAX_ANGLE, game.angle, 1e-9)
    }

    @Test fun aBriefTouchDoesNotFire() {
        val game = CannonGame(Difficulty.EASY, 3)
        game.beginCharge()
        game.step(0.05)
        game.releaseCharge()
        assertEquals(CannonGame.Phase.AIMING, game.phase)
        assertNull(game.shell)
        assertNull(game.lastPower)
    }

    @Test fun holdingPumpsThePowerUpAndDown() {
        val game = CannonGame(Difficulty.EASY, 3)
        game.beginCharge()
        var t = 0.0
        while (t < CannonGame.CHARGE_PERIOD - 1e-9) {
            game.step(dt)
            t += dt
        }
        assertTrue(game.power > 0.95)
        while (t < CannonGame.CHARGE_PERIOD * 2 - 1e-9) {
            game.step(dt)
            t += dt
        }
        assertTrue(game.power < 0.05)
        game.releaseCharge()
        assertEquals(CannonGame.Phase.FLYING, game.phase)
        assertNotNull(game.lastPower)
    }

    @Test fun aMissKeepsTargetAndWindAndLeavesATrail() {
        val game = CannonGame(Difficulty.MEDIUM, 4)
        val target = game.target
        val wind = game.wind
        game.setAngle(80.0)
        game.fire(0.08)
        val events = settle(game)
        assertEquals(listOf(CannonGame.Event.MISS), events)
        assertEquals(CannonGame.Phase.AIMING, game.phase)
        assertEquals(target.x, game.target.x, 0.0)
        assertEquals(wind, game.wind, 0.0)
        assertTrue(game.trail.size > 2)
        assertEquals(0.08, game.lastPower!!, 1e-9)
    }

    @Test fun explosionsCarveCraters() {
        val game = CannonGame(Difficulty.EASY, 5)
        val before = game.terrain.copyOf()
        game.setAngle(60.0)
        game.fire(0.3)
        settle(game)
        val lowered = game.terrain.indices.count { game.terrain[it] > before[it] + 0.5 }
        assertTrue("cráter: $lowered muestras", lowered > 0)
        // Ningún cráter bajo el cañón ni por encima de su altura original.
        for (i in game.terrain.indices) {
            assertTrue(game.terrain[i] >= before[i])
            if (kotlin.math.abs(i * CannonGame.SAMPLE_STEP - CannonGame.CANNON_X) <= 24) assertEquals(before[i], game.terrain[i], 0.0)
        }
    }

    @Test fun aimingWellSolvesEveryDifficultyOnce() {
        for (difficulty in Difficulty.entries) {
            for (seed in 1L..4L) {
                val game = CannonGame(difficulty, seed)
                var solved = 0
                var shots = 0
                while (game.phase != CannonGame.Phase.DONE && shots < 20) {
                    val (angle, power) = solution(game) ?: error("$difficulty/$seed: sin solución")
                    val previousX = game.target.x
                    game.setAngle(angle)
                    game.fire(power)
                    shots++
                    val events = settle(game)
                    solved += events.count { it == CannonGame.Event.SOLVED }
                    assertTrue("$difficulty/$seed: $events", CannonGame.Event.HIT in events)
                    if (game.phase != CannonGame.Phase.DONE) {
                        // Tras un impacto el despertador se muda.
                        assertTrue(kotlin.math.abs(game.target.x - previousX) >= 36 || game.wind == 0.0)
                    }
                }
                assertEquals(CannonGame.Phase.DONE, game.phase)
                assertEquals(game.required, game.hits)
                assertEquals(game.required, shots)
                assertEquals(1, solved)
                assertTrue(game.step(dt).isEmpty())
            }
        }
    }
}
