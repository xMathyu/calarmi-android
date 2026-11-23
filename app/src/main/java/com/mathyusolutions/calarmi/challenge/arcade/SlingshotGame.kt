package com.mathyusolutions.calarmi.challenge.arcade

import com.mathyusolutions.calarmi.model.WakeChallenge
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * La partida del «Tirachinas», sin nada de Compose.
 *
 * Se estira la goma hacia atrás y se suelta para lanzar una bola contra torres
 * de tablones con despertadores encima. Un despertador cuenta como derribado
 * si cae, se desplaza, vuelca o recibe la bola de lleno. Los derribos se
 * acumulan entre torres: si se acaban los tiros sale una torre nueva, y lo
 * mismo al despejar una antes de llegar al total.
 *
 * Coordenadas como en la escena de SpriteKit de iOS: 360 × 280 con la y hacia
 * arriba. La gravedad de iOS es 5,2 m/s² a 150 puntos por metro.
 */
class SlingshotGame(val config: Config, seed: Long = Random.nextLong()) {
    constructor(difficulty: WakeChallenge.Difficulty, seed: Long = Random.nextLong()) : this(Config(difficulty), seed)

    class Config(difficulty: WakeChallenge.Difficulty) {
        val required: Int
        val shots: Int

        /** Segundos de trayectoria prevista que se dibujan al estirar. */
        val preview: Double
        val layouts: List<List<Piece>>

        init {
            when (difficulty) {
                WakeChallenge.Difficulty.EASY -> {
                    required = 3; shots = 4; preview = 0.55
                    layouts = listOf(Layouts.tower, Layouts.twins)
                }
                WakeChallenge.Difficulty.MEDIUM -> {
                    required = 4; shots = 3; preview = 0.3
                    layouts = listOf(Layouts.tower, Layouts.twoStory, Layouts.twins, Layouts.bunker)
                }
                WakeChallenge.Difficulty.HARD -> {
                    required = 6; shots = 3; preview = 0.0
                    layouts = listOf(Layouts.twoStoryPlus, Layouts.twinsPlus, Layouts.bunkerPlus)
                }
            }
        }
    }

    class Piece(val isTarget: Boolean, val centerX: Double, val centerY: Double, val width: Double, val height: Double) {
        companion object {
            fun post(x: Double, bottom: Double, height: Double = 46.0) = Piece(false, x, bottom + height / 2, 10.0, height)
            fun plank(x: Double, bottom: Double, width: Double) = Piece(false, x, bottom + 5, width, 10.0)
            fun target(x: Double, bottom: Double) = Piece(true, x, bottom + TARGET_SIZE / 2, TARGET_SIZE, TARGET_SIZE)
        }
    }

    /** Las torres. `G` es el suelo; cada pieza se apoya sobre la anterior. */
    object Layouts {
        private const val G = GROUND_Y

        /** Mesa con un despertador encima y otro en el suelo, detrás. */
        val tower = listOf(
            Piece.post(232.0, G), Piece.post(284.0, G),
            Piece.plank(258.0, G + 46, 72.0),
            Piece.target(258.0, G + 56),
            Piece.target(322.0, G),
        )

        /** Dos pisos: uno resguardado entre los postes de arriba y otro en lo alto. */
        val twoStory = listOf(
            Piece.post(230.0, G), Piece.post(290.0, G),
            Piece.plank(260.0, G + 46, 80.0),
            Piece.post(238.0, G + 56, 36.0), Piece.post(282.0, G + 56, 36.0),
            Piece.target(260.0, G + 56),
            Piece.plank(260.0, G + 92, 64.0),
            Piece.target(260.0, G + 102),
        )

        val twoStoryPlus = twoStory + Piece.target(330.0, G)

        /** Dos mesas de distinta altura. */
        val twins = listOf(
            Piece.post(206.0, G, 40.0), Piece.post(242.0, G, 40.0),
            Piece.plank(224.0, G + 40, 52.0),
            Piece.target(224.0, G + 50),
            Piece.post(290.0, G, 58.0), Piece.post(326.0, G, 58.0),
            Piece.plank(308.0, G + 58, 52.0),
            Piece.target(308.0, G + 68),
        )

        val twinsPlus = twins + Piece.target(266.0, G)

        /** Un despertador bajo techo y otro sobre el techo. */
        val bunker = listOf(
            Piece.post(238.0, G, 36.0), Piece.post(286.0, G, 36.0),
            Piece.target(262.0, G),
            Piece.plank(262.0, G + 36, 70.0),
            Piece.target(262.0, G + 46),
        )

        val bunkerPlus = bunker + listOf(Piece.post(206.0, G, 30.0), Piece.target(326.0, G))
    }

    enum class Message {
        /** Antes del primer tiro: cómo se lanza. */
        AIM,
        CLEARED,
        OUT_OF_SHOTS,
    }

    enum class Event { KNOCKED, SOLVED }

    enum class BallState { EMPTY, LOADED, PULLING, FLYING }

    class Target internal constructor(val body: PhysicsBody) {
        var restX = 0.0
            internal set
        var restY = 0.0
            internal set
        var popped = false
            internal set

        /** Segundos desde que se derribó, para la animación de salida. */
        var popAge = 0.0
            internal set
    }

    companion object {
        const val WIDTH = 360.0
        const val HEIGHT = 280.0
        const val GROUND_Y = 28.0
        const val POUCH_X = 64.0
        const val POUCH_Y = 98.0

        /** Puntas de la horquilla, a ambos lados de la bola en reposo. */
        const val BACK_TIP_X = 50.0
        const val BACK_TIP_Y = 106.0
        const val FRONT_TIP_X = 78.0
        const val FRONT_TIP_Y = 106.0
        const val FORK_JOINT_X = 64.0
        const val FORK_JOINT_Y = 70.0
        const val MAX_PULL = 64.0
        const val MAX_SPEED = 560.0
        const val GRAVITY = 5.2 * 150
        const val BALL_RADIUS = 10.0
        const val TARGET_SIZE = 26.0

        /** Tiempo para que las torres se asienten antes de contar derribos. */
        const val SETTLE_TIME = 0.9
        const val POP_DURATION = 0.22
        const val FADE_DURATION = 0.2
        const val LOAD_DURATION = 0.15

        /** Paso fijo de la física: igual en pantallas de 60 y de 120 Hz. */
        const val PHYSICS_STEP = 1.0 / 120

        /** Lo que hay que estirar como mínimo para que el tiro cuente. */
        const val MIN_PULL = 10.0

        /** Una bola más lenta que esto al tocar un despertador no lo «golpea de lleno». */
        const val STRIKE_SPEED = 140.0

        fun launchVelocity(pullX: Double, pullY: Double) = Pt(-pullX / MAX_PULL * MAX_SPEED, -pullY / MAX_PULL * MAX_SPEED)
    }

    // MARK: - Estado visible

    val required: Int get() = config.required
    var knocked = 0
        private set
    var shotsLeft = config.shots
        private set
    var message: Message? = Message.AIM
        private set
    var isFinished = false
        private set
    var ballState = BallState.EMPTY
        private set
    var isArmed = false
        private set
    var pullX = 0.0
        private set
    var pullY = 0.0
        private set

    /** Segundos desde que apareció la bola en la goma (crece de pequeña). */
    var loadAge = 0.0
        private set

    /** La bola que se apaga tras un tiro, con su edad. */
    var ghost: Pt? = null
        private set
    var ghostAge = 0.0
        private set
    private val _trail = mutableListOf<Pt>()
    val trail: List<Pt> get() = _trail
    var previousTrail: List<Pt> = emptyList()
        private set
    private val _blocks = mutableListOf<PhysicsBody>()
    val blocks: List<PhysicsBody> get() = _blocks
    private val _targets = mutableListOf<Target>()
    val targets: List<Target> get() = _targets

    /** Qué torre está en juego (índice en `config.layouts`). */
    var layoutIndex = -1
        private set

    // MARK: - Estado interno

    internal val world = PhysicsWorld(gravity = -GRAVITY)
    private var ball: PhysicsBody? = null
    private val rng = SplitMix64(seed)
    private var levelAge = 0.0
    private var levelEnding = false
    private var flightTime = 0.0
    private var slowTime = 0.0
    private var trailClock = 0.0
    private var accumulator = 0.0
    private val pendingHits = mutableSetOf<PhysicsBody>()
    private var events = mutableListOf<Event>()

    // Las esperas que en iOS son acciones de SpriteKit con clave.
    private var reloadIn: Double? = null
    private var endingIn: Double? = null
    private var levelIn: Double? = null

    init {
        // El suelo físico acaba en el borde derecho: lo que se empuja más allá cae.
        val ground = PhysicsBody.box(560.0, 40.0, friction = 0.9, restitution = 0.2, isStatic = true)
        ground.x = (-200.0 + WIDTH) / 2
        ground.y = GROUND_Y - 20
        world.add(ground)
        world.onBeginContact = ::contactBegan
        buildLevel()
    }

    /** Dónde se dibuja la bola cargada o en vuelo. */
    val ballPosition: Pt?
        get() = when (ballState) {
            BallState.EMPTY -> null
            BallState.LOADED, BallState.PULLING -> Pt(POUCH_X + pullX, POUCH_Y + pullY)
            BallState.FLYING -> ball?.let { Pt(it.x, it.y) }
        }

    val ballAngle: Double get() = ball?.angle ?: 0.0

    // MARK: - Lanzamiento

    /** Empieza a estirar si hay bola cargada y las torres ya se asentaron. */
    fun beginPull(): Boolean {
        if (ballState != BallState.LOADED || !isArmed || isFinished || levelEnding) return false
        ballState = BallState.PULLING
        return true
    }

    /** Estira la goma: el desplazamiento es cuánto se aleja la bola de su sitio. */
    fun movePull(dx: Double, dy: Double) {
        if (ballState != BallState.PULLING) return
        val length = hypot(dx, dy)
        val factor = if (length > MAX_PULL) MAX_PULL / length else 1.0
        pullX = dx * factor
        pullY = dy * factor
    }

    /** Suelta: con poca tensión la bola vuelve a su sitio sin gastar el tiro. */
    fun endPull() {
        if (ballState != BallState.PULLING) return
        if (hypot(pullX, pullY) < MIN_PULL) {
            ballState = BallState.LOADED
            pullX = 0.0
            pullY = 0.0
            return
        }
        val body = PhysicsBody.circle(
            BALL_RADIUS,
            density = 4.0,
            friction = 0.6,
            restitution = 0.3,
            // Sin amortiguación lineal: la trayectoria prevista es exacta.
            linearDamping = 0.0,
            angularDamping = 0.5,
        )
        body.x = POUCH_X + pullX
        body.y = POUCH_Y + pullY
        val v = launchVelocity(pullX, pullY)
        body.vx = v.x
        body.vy = v.y
        world.add(body)
        ball = body
        ballState = BallState.FLYING
        pullX = 0.0
        pullY = 0.0
        flightTime = 0.0
        slowTime = 0.0
        trailClock = 0.0
        shotsLeft -= 1
        message = null
        previousTrail = _trail.toList()
        _trail.clear()
    }

    /** La trayectoria prevista mientras se estira (vacía si la dificultad no la da). */
    fun previewPoints(count: Int = 10): List<Pt> {
        if (config.preview <= 0 || ballState != BallState.PULLING || hypot(pullX, pullY) < MIN_PULL) return emptyList()
        val v = launchVelocity(pullX, pullY)
        val startX = POUCH_X + pullX
        val startY = POUCH_Y + pullY
        return List(count) { i ->
            val t = config.preview * (i + 1) / count
            Pt(startX + v.x * t, startY + v.y * t - 0.5 * GRAVITY * t * t)
        }
    }

    // MARK: - Bucle

    /** Avanza la partida; el orden es el de SpriteKit: lógica, esperas, física y comprobaciones. */
    fun step(dt: Double): List<Event> {
        events = mutableListOf()
        update(dt)
        runTimers(dt)
        animate(dt)
        accumulator += dt
        var steps = 0
        while (accumulator >= PHYSICS_STEP && steps < MAX_SUBSTEPS) {
            world.step(PHYSICS_STEP)
            accumulator -= PHYSICS_STEP
            steps++
        }
        if (steps == MAX_SUBSTEPS) accumulator = 0.0
        dropFallenBlocks()
        checkTargets()
        return events
    }

    private fun update(dt: Double) {
        levelAge += dt
        if (!isArmed && levelAge >= SETTLE_TIME) {
            isArmed = true
            for (target in _targets) {
                target.restX = target.body.x
                target.restY = target.body.y
            }
        }
        val b = ball
        if (ballState != BallState.FLYING || b == null) return
        flightTime += dt
        trailClock += dt
        if (trailClock >= 0.04) {
            trailClock = 0.0
            if (_trail.size < 120) _trail += Pt(b.x, b.y)
        }
        slowTime = if (b.speed < 14) slowTime + dt else 0.0
        val gone = b.x < -30 || b.x > WIDTH + 30 || b.y < -30
        if (gone || slowTime > 0.5 || flightTime > 7) endShot()
    }

    private fun runTimers(dt: Double) {
        reloadIn?.let { left ->
            val now = left - dt
            if (now <= 0) {
                reloadIn = null
                loadBall()
            } else {
                reloadIn = now
            }
        }
        endingIn?.let { left ->
            val now = left - dt
            if (now <= 0) {
                endingIn = null
                outOfShots()
            } else {
                endingIn = now
            }
        }
        levelIn?.let { left ->
            val now = left - dt
            if (now <= 0) {
                levelIn = null
                nextLevel()
            } else {
                levelIn = now
            }
        }
    }

    private fun animate(dt: Double) {
        if (ballState == BallState.LOADED || ballState == BallState.PULLING) loadAge += dt
        if (ghost != null) {
            ghostAge += dt
            if (ghostAge >= FADE_DURATION) ghost = null
        }
        for (target in _targets) if (target.popped) target.popAge += dt
    }

    private fun contactBegan(a: PhysicsBody, b: PhysicsBody) {
        val current = ball ?: return
        val other = when {
            a === current -> b
            b === current -> a
            else -> return
        }
        // Un roce lento no cuenta; un golpe de lleno sí, aunque no lo mueva.
        if (current.speed > STRIKE_SPEED) pendingHits += other
    }

    /** Lo que cae por el borde derecho sale de la simulación (si no, el mundo nunca se dormiría). */
    private fun dropFallenBlocks() {
        val iterator = _blocks.iterator()
        while (iterator.hasNext()) {
            val block = iterator.next()
            if (block.y < -80) {
                world.remove(block)
                iterator.remove()
            }
        }
    }

    private fun checkTargets() {
        if (!isArmed || isFinished) return
        for (target in _targets) {
            if (target.popped) continue
            val body = target.body
            val fell = target.restY - body.y > 14
            val shifted = abs(body.x - target.restX) > 22
            val rotation = atan2(sin(body.angle), cos(body.angle))
            val tipped = abs(rotation) > 0.7
            val out = body.y < -10 || body.x > WIDTH + 20 || body.x < -20
            val struck = body in pendingHits
            if (fell || shifted || tipped || out || struck) {
                pop(target)
                if (isFinished) return
            }
        }
        pendingHits.clear()
    }

    // MARK: - Niveles

    private fun buildLevel() {
        endingIn = null
        reloadIn = null
        for (block in _blocks) world.remove(block)
        for (target in _targets) world.remove(target.body)
        _blocks.clear()
        _targets.clear()
        pendingHits.clear()
        ball?.let { world.remove(it) }
        ball = null
        ballState = BallState.EMPTY
        levelAge = 0.0
        isArmed = false
        levelEnding = false

        val layouts = config.layouts
        var index = rng.nextInt(layouts.size)
        if (layouts.size > 1 && index == layoutIndex) index = (index + 1) % layouts.size
        layoutIndex = index

        for (piece in layouts[index]) {
            val body = if (piece.isTarget) {
                PhysicsBody.box(
                    TARGET_SIZE - 4, TARGET_SIZE - 2,
                    density = 0.5, friction = 0.8, restitution = 0.05, linearDamping = 0.1, angularDamping = 0.4,
                )
            } else {
                PhysicsBody.box(
                    piece.width, piece.height,
                    density = 1.0, friction = 0.8, restitution = 0.05, linearDamping = 0.1, angularDamping = 0.4,
                )
            }
            // Un hueco mínimo para que cada pieza caiga y se asiente sola.
            body.x = piece.centerX
            body.y = piece.centerY + 0.5
            world.add(body)
            if (piece.isTarget) _targets += Target(body) else _blocks += body
        }

        shotsLeft = config.shots
        loadBall()
    }

    private fun loadBall() {
        if (isFinished || ballState != BallState.EMPTY || shotsLeft <= 0) return
        ballState = BallState.LOADED
        pullX = 0.0
        pullY = 0.0
        loadAge = 0.0
    }

    // MARK: - Derribos y tiros

    private fun pop(target: Target) {
        target.popped = true
        target.popAge = 0.0
        world.remove(target.body)
        knocked += 1
        events += Event.KNOCKED
        if (knocked >= config.required) {
            finish()
            events += Event.SOLVED
            return
        }
        if (_targets.all { it.popped }) {
            levelEnding = true
            message = Message.CLEARED
            endingIn = null
            levelIn = 1.0
        }
    }

    private fun endShot() {
        val b = ball ?: return
        ball = null
        ballState = BallState.EMPTY
        world.remove(b)
        ghost = Pt(b.x, b.y)
        ghostAge = 0.0
        if (isFinished || levelEnding) return
        if (shotsLeft > 0) {
            reloadIn = 0.25
        } else {
            // Deja que lo que se tambalea acabe de caer antes de rendirse.
            levelEnding = true
            endingIn = 1.2
        }
    }

    private fun outOfShots() {
        if (isFinished) return
        // Lo último cayó durante la espera: `pop` ya preparó la torre siguiente.
        if (_targets.all { it.popped }) return
        message = Message.OUT_OF_SHOTS
        levelIn = 1.2
    }

    private fun nextLevel() {
        if (isFinished) return
        message = null
        _trail.clear()
        previousTrail = emptyList()
        buildLevel()
    }

    private fun finish() {
        isFinished = true
        reloadIn = null
        endingIn = null
        levelIn = null
    }
}

/** Como mucho estos pasos de física por fotograma (el fotograma ya viene acotado a 1/30 s). */
private const val MAX_SUBSTEPS = 5
