package com.mathyusolutions.calarmi.challenge.arcade

import com.mathyusolutions.calarmi.model.WakeChallenge.Difficulty
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val STEP = 1.0 / 120

class ArcadePhysicsTest {
    private fun worldWithGround(): Pair<PhysicsWorld, PhysicsBody> {
        val world = PhysicsWorld(gravity = -780.0)
        val ground = PhysicsBody.box(800.0, 40.0, friction = 0.9, isStatic = true)
        ground.x = 0.0
        ground.y = -20.0
        world.add(ground)
        return world to ground
    }

    private fun run(world: PhysicsWorld, seconds: Double) {
        var t = 0.0
        while (t < seconds) {
            world.step(STEP)
            t += STEP
        }
    }

    @Test fun aBoxComesToRestOnTheGround() {
        val (world, _) = worldWithGround()
        val box = PhysicsBody.box(30.0, 20.0, friction = 0.8, restitution = 0.05)
        box.x = 10.0
        box.y = 40.0
        world.add(box)
        run(world, 3.0)
        assertEquals(10.0, box.y, 0.6)
        assertEquals(10.0, box.x, 0.5)
        assertEquals(0.0, box.angle, 0.01)
        assertTrue("se duerme", world.isSleeping)
    }

    @Test fun aTiltedPostTopplesAndAnUprightOneStands() {
        val (world, _) = worldWithGround()
        val upright = PhysicsBody.box(10.0, 46.0, friction = 0.8)
        upright.x = -100.0
        upright.y = 23.5
        val tilted = PhysicsBody.box(10.0, 46.0, friction = 0.8)
        tilted.x = 100.0
        tilted.y = 24.0
        tilted.angle = 0.35
        world.add(upright)
        world.add(tilted)
        run(world, 4.0)
        assertEquals(0.0, upright.angle, 0.02)
        assertEquals(23.0, upright.y, 0.6)
        val lying = abs(atan2(sin(tilted.angle), cos(tilted.angle)))
        assertTrue("ángulo: $lying", lying > 1.3 && lying < 1.85)
        assertEquals(5.0, tilted.y, 1.0)
    }

    @Test fun aStackOfBoxesStaysPut() {
        val (world, _) = worldWithGround()
        val boxes = (0 until 5).map { i ->
            PhysicsBody.box(20.0, 20.0, friction = 0.8, restitution = 0.05).also {
                it.x = 0.0
                it.y = 10.5 + i * 20.5
                world.add(it)
            }
        }
        run(world, 10.0)
        boxes.forEachIndexed { i, b ->
            assertEquals(0.0, b.x, 0.5)
            assertEquals(10.0 + i * 20, b.y, 1.5)
            assertEquals(0.0, b.angle, 0.02)
        }
    }

    @Test fun aBallBouncesAndRollsOnTheGround() {
        val (world, _) = worldWithGround()
        val ball = PhysicsBody.circle(10.0, density = 4.0, friction = 0.6, restitution = 0.3, linearDamping = 0.0, angularDamping = 0.5)
        ball.x = 0.0
        ball.y = 100.0
        ball.vx = 80.0
        world.add(ball)
        var maxUpAfterContact = 0.0
        var touched = false
        var t = 0.0
        while (t < 3) {
            world.step(STEP)
            if (ball.y < 11) touched = true
            if (touched) maxUpAfterContact = maxOf(maxUpAfterContact, ball.vy)
            t += STEP
        }
        assertTrue(touched)
        assertTrue("rebota: $maxUpAfterContact", maxUpAfterContact > 50)
        assertEquals(10.0, ball.y, 1.0)
        assertTrue(ball.x > 100)
    }

    @Test fun aFastBallKnocksAPostOver() {
        val (world, _) = worldWithGround()
        val post = PhysicsBody.box(10.0, 46.0, friction = 0.8, restitution = 0.05, angularDamping = 0.4)
        post.x = 100.0
        post.y = 23.5
        world.add(post)
        run(world, 0.5)
        val ball = PhysicsBody.circle(10.0, density = 4.0, friction = 0.6, restitution = 0.3, linearDamping = 0.0)
        ball.x = 0.0
        ball.y = 38.0
        ball.vx = 500.0
        world.add(ball)
        var began = 0
        world.onBeginContact = { a, b -> if (a === ball || b === ball) began++ }
        run(world, 3.0)
        assertTrue(began > 0)
        assertTrue("volcó: ${post.angle}", abs(post.angle) > 1.0 || post.x > 130)
    }

    @Test fun simulationIsDeterministic() {
        fun build(): List<PhysicsBody> {
            val (world, _) = worldWithGround()
            val bodies = listOf(
                PhysicsBody.box(10.0, 46.0).also { it.x = 0.0; it.y = 23.5 },
                PhysicsBody.box(60.0, 10.0).also { it.x = 20.0; it.y = 52.0; it.angle = 0.1 },
                PhysicsBody.circle(10.0).also { it.x = -80.0; it.y = 60.0; it.vx = 300.0 },
            )
            bodies.forEach(world::add)
            run(world, 2.0)
            return bodies
        }
        val a = build()
        val b = build()
        for (i in a.indices) {
            assertEquals(a[i].x, b[i].x, 0.0)
            assertEquals(a[i].y, b[i].y, 0.0)
            assertEquals(a[i].angle, b[i].angle, 0.0)
        }
    }
}

class SlingshotGameTest {
    private val dt = 1.0 / 60

    private fun advance(game: SlingshotGame, seconds: Double, events: MutableList<SlingshotGame.Event>? = null) {
        var t = 0.0
        while (t < seconds) {
            val e = game.step(dt)
            events?.addAll(e)
            t += dt
        }
    }

    /**
     * Con la goma ya agarrada, estira al máximo en la dirección que, según
     * la parábola, pasa por el punto: el tiro más tenso de los posibles.
     */
    private fun shoot(game: SlingshotGame, x: Double, y: Double) {
        var best = Double.MAX_VALUE
        var bestPull = 0.0 to 0.0
        var degrees = -20.0
        while (degrees <= 75) {
            val a = Math.toRadians(degrees)
            val pullX = -cos(a) * SlingshotGame.MAX_PULL
            val pullY = -sin(a) * SlingshotGame.MAX_PULL
            val v = SlingshotGame.launchVelocity(pullX, pullY)
            val sx = SlingshotGame.POUCH_X + pullX
            val sy = SlingshotGame.POUCH_Y + pullY
            val t = (x - sx) / v.x
            if (t > 0) {
                val yAt = sy + v.y * t - 0.5 * SlingshotGame.GRAVITY * t * t
                val error = abs(yAt - y)
                if (error < best) {
                    best = error
                    bestPull = pullX to pullY
                }
            }
            degrees += 0.25
        }
        game.movePull(bestPull.first, bestPull.second)
        game.endPull()
    }

    @Test fun difficultyScaling() {
        val easy = SlingshotGame.Config(Difficulty.EASY)
        val medium = SlingshotGame.Config(Difficulty.MEDIUM)
        val hard = SlingshotGame.Config(Difficulty.HARD)
        assertEquals(listOf(3, 4, 6), listOf(easy.required, medium.required, hard.required))
        assertEquals(listOf(4, 3, 3), listOf(easy.shots, medium.shots, hard.shots))
        assertEquals(0.0, hard.preview, 0.0)
        assertTrue(easy.preview > medium.preview)
    }

    @Test fun everyTowerStandsOnItsOwn() {
        for (difficulty in Difficulty.entries) {
            val seen = mutableSetOf<Int>()
            var seed = 0L
            while (seen.size < SlingshotGame.Config(difficulty).layouts.size && seed < 200) {
                val game = SlingshotGame(difficulty, seed++)
                if (!seen.add(game.layoutIndex)) continue
                val events = mutableListOf<SlingshotGame.Event>()
                advance(game, 12.0, events)
                assertTrue("$difficulty torre ${game.layoutIndex}: $events", events.isEmpty())
                assertEquals(0, game.knocked)
                for (target in game.targets) {
                    assertFalse(target.popped)
                    assertEquals(target.restX, target.body.x, 2.0)
                    assertEquals(target.restY, target.body.y, 2.0)
                }
                for (block in game.blocks) assertEquals(0.0, block.angle, 0.05)
                assertTrue("$difficulty torre ${game.layoutIndex} se duerme", game.world.isSleeping)
            }
            assertEquals(SlingshotGame.Config(difficulty).layouts.size, seen.size)
        }
    }

    @Test fun pullingNeedsSettledTowersAndAShortPullIsFree() {
        val game = SlingshotGame(Difficulty.MEDIUM, 3)
        assertEquals(SlingshotGame.BallState.LOADED, game.ballState)
        assertFalse("las torres aún se asientan", game.beginPull())
        advance(game, SlingshotGame.SETTLE_TIME + 0.1)
        assertTrue(game.beginPull())
        game.movePull(-5.0, -3.0)
        game.endPull()
        assertEquals(SlingshotGame.BallState.LOADED, game.ballState)
        assertEquals(game.config.shots, game.shotsLeft)
        assertEquals(SlingshotGame.Message.AIM, game.message)
        // Estirar de más se queda en el máximo.
        assertTrue(game.beginPull())
        game.movePull(-300.0, 0.0)
        assertEquals(-SlingshotGame.MAX_PULL, game.pullX, 1e-9)
        assertTrue(game.previewPoints().isNotEmpty())
        game.endPull()
        assertEquals(SlingshotGame.BallState.FLYING, game.ballState)
        assertEquals(game.config.shots - 1, game.shotsLeft)
        assertEquals(null, game.message)
    }

    @Test fun runningOutOfShotsBringsANewTower() {
        val game = SlingshotGame(Difficulty.MEDIUM, 5)
        advance(game, 1.0)
        val shots = game.config.shots
        var sawOut = false
        repeat(shots) {
            // Hacia atrás: la bola sale por la izquierda sin tocar nada.
            var waited = 0.0
            while (game.ballState != SlingshotGame.BallState.LOADED && waited < 5) {
                advance(game, dt)
                waited += dt
            }
            assertTrue(game.beginPull())
            game.movePull(60.0, -10.0)
            game.endPull()
            advance(game, 0.1)
        }
        assertEquals(0, game.shotsLeft)
        var t = 0.0
        while (t < 8 && game.shotsLeft == 0) {
            advance(game, dt)
            if (game.message == SlingshotGame.Message.OUT_OF_SHOTS) sawOut = true
            t += dt
        }
        assertTrue(sawOut)
        assertEquals(shots, game.shotsLeft)
        assertEquals(0, game.knocked)
        assertEquals(SlingshotGame.BallState.LOADED, game.ballState)
    }

    @Test fun aimingAtTheClocksSolvesEveryDifficultyOnce() {
        for (difficulty in Difficulty.entries) {
            for (seed in 1L..3L) {
                val game = SlingshotGame(difficulty, seed)
                val events = mutableListOf<SlingshotGame.Event>()
                var t = 0.0
                var shots = 0
                while (!game.isFinished && t < 180) {
                    if (game.ballState == SlingshotGame.BallState.LOADED && game.isArmed) {
                        // Primero los de arriba, que suelen arrastrar a los demás.
                        val target = game.targets.filter { !it.popped }.maxByOrNull { it.body.y }
                        if (target != null && game.beginPull()) {
                            shoot(game, target.body.x, target.body.y)
                            shots++
                        }
                    }
                    events += game.step(dt)
                    t += dt
                }
                assertTrue("$difficulty/$seed: ${game.knocked} en ${t}s", game.isFinished)
                assertEquals(game.required, game.knocked)
                assertEquals(1, events.count { it == SlingshotGame.Event.SOLVED })
                assertEquals(game.required, events.count { it == SlingshotGame.Event.KNOCKED })
                assertTrue("$difficulty/$seed: $shots tiros", shots <= game.required * 3)
                // Terminado, ya no se puede tirar ni pasa nada más.
                advance(game, 3.0, events)
                assertEquals(1, events.count { it == SlingshotGame.Event.SOLVED })
                assertFalse(game.beginPull())
            }
        }
    }
}
