package com.mathyusolutions.calarmi.challenge.arcade

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// Física 2D mínima para el tirachinas: cajas orientadas y círculos rígidos con
// gravedad, rozamiento y un poco de rebote. En iOS lo hace SpriteKit (que por
// dentro es Box2D); aquí se sigue el mismo diseño que «Box2D Lite»: contactos
// por pares (hasta dos puntos entre cajas), impulsos acumulados con arranque
// en caliente y corrección de penetración por sesgo de velocidad. Es lo
// mínimo que deja torres de tablones de pie y las hace volcar de forma
// creíble. Todo es determinista (paso fijo, sin azar) para poder probarlo.
//
// Unidades: puntos del escenario con la y hacia arriba, segundos y radianes
// (positivos en sentido antihorario).

/** Un cuerpo rígido: caja (medio ancho y medio alto) o círculo (radio en `halfWidth`). */
class PhysicsBody private constructor(
    val isCircle: Boolean,
    val halfWidth: Double,
    val halfHeight: Double,
    density: Double,
    isStatic: Boolean,
    val friction: Double,
    val restitution: Double,
    val linearDamping: Double,
    val angularDamping: Double,
) {
    /** Lo asigna el mundo; ordena los pares para que la normal siempre vaya del primero al segundo. */
    internal var id = -1

    var x = 0.0
    var y = 0.0
    var angle = 0.0
    var vx = 0.0
    var vy = 0.0
    var angularVelocity = 0.0

    val mass: Double
    val invMass: Double
    val inertia: Double
    val invInertia: Double

    /** Radio de un círculo que lo envuelve: descarta pares lejanos sin calcular nada más. */
    internal val boundRadius: Double = if (isCircle) halfWidth else hypot(halfWidth, halfHeight)

    val radius: Double get() = halfWidth
    val isStatic: Boolean get() = invMass == 0.0
    val speed: Double get() = hypot(vx, vy)

    init {
        if (isStatic) {
            mass = 0.0; invMass = 0.0; inertia = 0.0; invInertia = 0.0
        } else if (isCircle) {
            mass = density * PI * halfWidth * halfWidth
            inertia = mass * halfWidth * halfWidth / 2
            invMass = 1 / mass; invInertia = 1 / inertia
        } else {
            val w = halfWidth * 2
            val h = halfHeight * 2
            mass = density * w * h
            inertia = mass * (w * w + h * h) / 12
            invMass = 1 / mass; invInertia = 1 / inertia
        }
    }

    companion object {
        fun box(
            width: Double,
            height: Double,
            density: Double = 1.0,
            friction: Double = 0.2,
            restitution: Double = 0.2,
            linearDamping: Double = 0.1,
            angularDamping: Double = 0.1,
            isStatic: Boolean = false,
        ) = PhysicsBody(false, width / 2, height / 2, density, isStatic, friction, restitution, linearDamping, angularDamping)

        fun circle(
            radius: Double,
            density: Double = 1.0,
            friction: Double = 0.2,
            restitution: Double = 0.2,
            linearDamping: Double = 0.1,
            angularDamping: Double = 0.1,
        ) = PhysicsBody(true, radius, radius, density, false, friction, restitution, linearDamping, angularDamping)
    }
}

/** Un punto de contacto entre dos cuerpos; la normal va del primero al segundo. */
internal class Contact {
    var px = 0.0
    var py = 0.0
    var nx = 0.0
    var ny = 0.0

    /** Negativa si se solapan. */
    var separation = 0.0

    /** Qué aristas lo forman: identifica el mismo punto de un paso al siguiente. */
    var feature = 0

    // Impulsos acumulados (normal y tangente) y datos precalculados del paso.
    var pn = 0.0
    var pt = 0.0
    var r1x = 0.0
    var r1y = 0.0
    var r2x = 0.0
    var r2y = 0.0
    var massNormal = 0.0
    var massTangent = 0.0
    var bias = 0.0

    fun setGeometry(px: Double, py: Double, nx: Double, ny: Double, separation: Double, feature: Int) {
        this.px = px; this.py = py; this.nx = nx; this.ny = ny
        this.separation = separation; this.feature = feature
    }
}

/** Los contactos de un par de cuerpos, que se conservan mientras se tocan. */
internal class Arbiter(val b1: PhysicsBody, val b2: PhysicsBody) {
    val contacts = arrayOf(Contact(), Contact())
    var count = 0
    private val friction = sqrt(b1.friction * b2.friction)
    private val restitution = max(b1.restitution, b2.restitution)

    /**
     * Sustituye la geometría por la nueva y, para los puntos que siguen siendo
     * los mismos, conserva el impulso del paso anterior (arranque en caliente):
     * es lo que deja una pila quieta en lugar de temblando.
     */
    fun update(fresh: Array<Contact>, n: Int) {
        var pn0 = 0.0
        var pt0 = 0.0
        var pn1 = 0.0
        var pt1 = 0.0
        for (i in 0 until n) {
            for (j in 0 until count) {
                if (contacts[j].feature == fresh[i].feature) {
                    if (i == 0) { pn0 = contacts[j].pn; pt0 = contacts[j].pt } else { pn1 = contacts[j].pn; pt1 = contacts[j].pt }
                    break
                }
            }
        }
        for (i in 0 until n) {
            val f = fresh[i]
            contacts[i].setGeometry(f.px, f.py, f.nx, f.ny, f.separation, f.feature)
            contacts[i].pn = if (i == 0) pn0 else pn1
            contacts[i].pt = if (i == 0) pt0 else pt1
        }
        count = n
    }

    fun preStep(invDt: Double) {
        for (k in 0 until count) {
            val c = contacts[k]
            c.r1x = c.px - b1.x; c.r1y = c.py - b1.y
            c.r2x = c.px - b2.x; c.r2y = c.py - b2.y
            val rn1 = c.r1x * c.nx + c.r1y * c.ny
            val rn2 = c.r2x * c.nx + c.r2y * c.ny
            val kNormal = b1.invMass + b2.invMass +
                b1.invInertia * (c.r1x * c.r1x + c.r1y * c.r1y - rn1 * rn1) +
                b2.invInertia * (c.r2x * c.r2x + c.r2y * c.r2y - rn2 * rn2)
            c.massNormal = 1 / kNormal

            val tx = c.ny
            val ty = -c.nx
            val rt1 = c.r1x * tx + c.r1y * ty
            val rt2 = c.r2x * tx + c.r2y * ty
            val kTangent = b1.invMass + b2.invMass +
                b1.invInertia * (c.r1x * c.r1x + c.r1y * c.r1y - rt1 * rt1) +
                b2.invInertia * (c.r2x * c.r2x + c.r2y * c.r2y - rt2 * rt2)
            c.massTangent = 1 / kTangent

            // Corrección de penetración, con un margen para que lo que reposa no tiemble.
            c.bias = -BIAS_FACTOR * invDt * min(0.0, c.separation + ALLOWED_PENETRATION)
            // Rebote solo en choques de verdad: un objeto apoyado no salta.
            val vn = relativeVelocityX(c) * c.nx + relativeVelocityY(c) * c.ny
            if (vn < -RESTITUTION_THRESHOLD) c.bias = max(c.bias, -restitution * vn)

            // Arranque en caliente.
            val px = c.pn * c.nx + c.pt * tx
            val py = c.pn * c.ny + c.pt * ty
            applyImpulse(c, px, py)
        }
    }

    fun solve() {
        for (k in 0 until count) {
            val c = contacts[k]
            // Normal: nunca tira, solo empuja.
            var dvx = relativeVelocityX(c)
            var dvy = relativeVelocityY(c)
            val vn = dvx * c.nx + dvy * c.ny
            var dPn = c.massNormal * (-vn + c.bias)
            val pn0 = c.pn
            c.pn = max(pn0 + dPn, 0.0)
            dPn = c.pn - pn0
            applyImpulse(c, dPn * c.nx, dPn * c.ny)

            // Rozamiento: limitado por la fuerza normal acumulada.
            dvx = relativeVelocityX(c)
            dvy = relativeVelocityY(c)
            val tx = c.ny
            val ty = -c.nx
            val vt = dvx * tx + dvy * ty
            var dPt = c.massTangent * -vt
            val maxPt = friction * c.pn
            val pt0 = c.pt
            c.pt = (pt0 + dPt).coerceIn(-maxPt, maxPt)
            dPt = c.pt - pt0
            applyImpulse(c, dPt * tx, dPt * ty)
        }
    }

    private fun relativeVelocityX(c: Contact) = b2.vx - b2.angularVelocity * c.r2y - b1.vx + b1.angularVelocity * c.r1y

    private fun relativeVelocityY(c: Contact) = b2.vy + b2.angularVelocity * c.r2x - b1.vy - b1.angularVelocity * c.r1x

    private fun applyImpulse(c: Contact, px: Double, py: Double) {
        b1.vx -= b1.invMass * px
        b1.vy -= b1.invMass * py
        b1.angularVelocity -= b1.invInertia * (c.r1x * py - c.r1y * px)
        b2.vx += b2.invMass * px
        b2.vy += b2.invMass * py
        b2.angularVelocity += b2.invInertia * (c.r2x * py - c.r2y * px)
    }

    private companion object {
        const val BIAS_FACTOR = 0.2
        const val ALLOWED_PENETRATION = 0.3
        const val RESTITUTION_THRESHOLD = 60.0
    }
}

/** El mundo: cuerpos, gravedad y el paso de simulación. */
class PhysicsWorld(private val gravity: Double, private val iterations: Int = 10) {
    private val _bodies = mutableListOf<PhysicsBody>()
    val bodies: List<PhysicsBody> get() = _bodies
    private val arbiters = LinkedHashMap<Long, Arbiter>()
    private val scratch = arrayOf(Contact(), Contact())
    private val collider = Collider()
    private var nextId = 0
    private var quietTime = 0.0

    /**
     * Con todo quieto un rato el mundo se duerme y deja de simular: las torres
     * no se arrastran con el tiempo y no se gasta batería. Lo único que puede
     * moverlas es un cuerpo nuevo o uno que se quita, y eso lo despierta.
     */
    var isSleeping = false
        private set

    /** Se llama cuando dos cuerpos empiezan a tocarse, antes de resolver el choque. */
    var onBeginContact: ((PhysicsBody, PhysicsBody) -> Unit)? = null

    fun add(body: PhysicsBody) {
        body.id = nextId++
        _bodies += body
        wake()
    }

    fun remove(body: PhysicsBody) {
        if (!_bodies.remove(body)) return
        arbiters.values.removeAll { it.b1 === body || it.b2 === body }
        wake()
    }

    fun contains(body: PhysicsBody): Boolean = _bodies.contains(body)

    fun wake() {
        isSleeping = false
        quietTime = 0.0
    }

    fun step(dt: Double) {
        if (isSleeping || dt <= 0) return
        val invDt = 1 / dt
        broadPhase()

        for (b in _bodies) {
            if (b.isStatic) continue
            b.vy += gravity * dt
            val linear = 1 / (1 + dt * b.linearDamping)
            b.vx *= linear
            b.vy *= linear
            b.angularVelocity *= 1 / (1 + dt * b.angularDamping)
        }

        for (arbiter in arbiters.values) arbiter.preStep(invDt)
        repeat(iterations) {
            for (arbiter in arbiters.values) arbiter.solve()
        }

        for (b in _bodies) {
            if (b.isStatic) continue
            b.x += b.vx * dt
            b.y += b.vy * dt
            b.angle += b.angularVelocity * dt
        }
        updateSleep(dt)
    }

    private fun broadPhase() {
        val n = _bodies.size
        for (i in 0 until n) {
            for (j in i + 1 until n) {
                var b1 = _bodies[i]
                var b2 = _bodies[j]
                if (b1.isStatic && b2.isStatic) continue
                if (b1.id > b2.id) { val t = b1; b1 = b2; b2 = t }
                val key = (b1.id.toLong() shl 32) or b2.id.toLong()
                val reach = b1.boundRadius + b2.boundRadius + 1
                val dx = b2.x - b1.x
                val dy = b2.y - b1.y
                val count = if (dx * dx + dy * dy <= reach * reach) collider.collide(b1, b2, scratch) else 0
                if (count > 0) {
                    val existing = arbiters[key]
                    if (existing != null) {
                        existing.update(scratch, count)
                    } else {
                        val arbiter = Arbiter(b1, b2)
                        arbiter.update(scratch, count)
                        arbiters[key] = arbiter
                        onBeginContact?.invoke(b1, b2)
                    }
                } else {
                    arbiters.remove(key)
                }
            }
        }
    }

    private fun updateSleep(dt: Double) {
        var calm = true
        for (b in _bodies) {
            if (b.isStatic) continue
            if (b.vx * b.vx + b.vy * b.vy > SLEEP_LINEAR * SLEEP_LINEAR || abs(b.angularVelocity) > SLEEP_ANGULAR) {
                calm = false
                break
            }
        }
        if (!calm) {
            quietTime = 0.0
            return
        }
        quietTime += dt
        if (quietTime >= SLEEP_TIME) {
            isSleeping = true
            for (b in _bodies) {
                b.vx = 0.0; b.vy = 0.0; b.angularVelocity = 0.0
            }
        }
    }

    private companion object {
        const val SLEEP_LINEAR = 2.5
        const val SLEEP_ANGULAR = 0.05
        const val SLEEP_TIME = 0.5
    }
}

/**
 * Detección de contactos entre pares. Caja contra caja: ejes separadores y
 * recorte de la arista incidente contra la cara de referencia (como Box2D
 * Lite), que da hasta dos puntos; círculo contra caja: el punto más cercano.
 */
internal class Collider {
    private class ClipVertex {
        var x = 0.0
        var y = 0.0
        var inEdge1 = 0
        var outEdge1 = 0
        var inEdge2 = 0
        var outEdge2 = 0

        fun copyFrom(o: ClipVertex) {
            x = o.x; y = o.y
            inEdge1 = o.inEdge1; outEdge1 = o.outEdge1; inEdge2 = o.inEdge2; outEdge2 = o.outEdge2
        }

        fun key(flip: Boolean): Int = if (flip) {
            inEdge2 or (outEdge2 shl 8) or (inEdge1 shl 16) or (outEdge1 shl 24)
        } else {
            inEdge1 or (outEdge1 shl 8) or (inEdge2 shl 16) or (outEdge2 shl 24)
        }
    }

    private val incident = arrayOf(ClipVertex(), ClipVertex())
    private val clip1 = arrayOf(ClipVertex(), ClipVertex())
    private val clip2 = arrayOf(ClipVertex(), ClipVertex())

    /** Rellena `out` con los contactos de `a` contra `b` (normal de `a` hacia `b`) y dice cuántos hay. */
    fun collide(a: PhysicsBody, b: PhysicsBody, out: Array<Contact>): Int = when {
        !a.isCircle && !b.isCircle -> boxes(a, b, out)
        a.isCircle && !b.isCircle -> circleBox(a, b, out, circleFirst = true)
        !a.isCircle && b.isCircle -> circleBox(b, a, out, circleFirst = false)
        else -> circles(a, b, out)
    }

    private fun circles(a: PhysicsBody, b: PhysicsBody, out: Array<Contact>): Int {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val d = hypot(dx, dy)
        val r = a.radius + b.radius
        if (d > r) return 0
        val nx = if (d > 1e-9) dx / d else 0.0
        val ny = if (d > 1e-9) dy / d else 1.0
        out[0].setGeometry(a.x + nx * a.radius, a.y + ny * a.radius, nx, ny, d - r, 0)
        return 1
    }

    private fun circleBox(circle: PhysicsBody, box: PhysicsBody, out: Array<Contact>, circleFirst: Boolean): Int {
        val c = cos(box.angle)
        val s = sin(box.angle)
        val dx = circle.x - box.x
        val dy = circle.y - box.y
        // El centro del círculo en el sistema de la caja.
        val lx = c * dx + s * dy
        val ly = -s * dx + c * dy
        val hx = box.halfWidth
        val hy = box.halfHeight
        val r = circle.radius
        val nlx: Double
        val nly: Double
        val qx: Double
        val qy: Double
        val separation: Double
        if (abs(lx) <= hx && abs(ly) <= hy) {
            // Centro dentro de la caja: sale por la cara más cercana.
            val penX = hx - abs(lx)
            val penY = hy - abs(ly)
            if (penX < penY) {
                nlx = if (lx >= 0) 1.0 else -1.0; nly = 0.0
                qx = nlx * hx; qy = ly
                separation = -penX - r
            } else {
                nlx = 0.0; nly = if (ly >= 0) 1.0 else -1.0
                qx = lx; qy = nly * hy
                separation = -penY - r
            }
        } else {
            qx = lx.coerceIn(-hx, hx)
            qy = ly.coerceIn(-hy, hy)
            val ex = lx - qx
            val ey = ly - qy
            val d = hypot(ex, ey)
            if (d > r) return 0
            nlx = ex / d; nly = ey / d
            separation = d - r
        }
        // De vuelta al mundo; la normal sale de la caja hacia el círculo.
        val nx = c * nlx - s * nly
        val ny = s * nlx + c * nly
        val px = box.x + c * qx - s * qy
        val py = box.y + s * qx + c * qy
        if (circleFirst) {
            out[0].setGeometry(px, py, -nx, -ny, separation, 0)
        } else {
            out[0].setGeometry(px, py, nx, ny, separation, 0)
        }
        return 1
    }

    private fun boxes(a: PhysicsBody, b: PhysicsBody, out: Array<Contact>): Int {
        val hAx = a.halfWidth
        val hAy = a.halfHeight
        val hBx = b.halfWidth
        val hBy = b.halfHeight
        val cA = cos(a.angle)
        val sA = sin(a.angle)
        val cB = cos(b.angle)
        val sB = sin(b.angle)
        val dpx = b.x - a.x
        val dpy = b.y - a.y
        // La distancia entre centros vista desde cada caja.
        val dAx = cA * dpx + sA * dpy
        val dAy = -sA * dpx + cA * dpy
        val dBx = cB * dpx + sB * dpy
        val dBy = -sB * dpx + cB * dpy
        // C = RA^T · RB, en valor absoluto.
        val a11 = abs(cA * cB + sA * sB)
        val a12 = abs(-cA * sB + sA * cB)
        val a21 = abs(-sA * cB + cA * sB)
        val a22 = abs(sA * sB + cA * cB)

        // Ejes de las caras de A y de B: si alguno separa, no hay contacto.
        val faceAx = abs(dAx) - hAx - (a11 * hBx + a12 * hBy)
        val faceAy = abs(dAy) - hAy - (a21 * hBx + a22 * hBy)
        if (faceAx > 0 || faceAy > 0) return 0
        val faceBx = abs(dBx) - (a11 * hAx + a21 * hAy) - hBx
        val faceBy = abs(dBy) - (a12 * hAx + a22 * hAy) - hBy
        if (faceBx > 0 || faceBy > 0) return 0

        // El eje de menor penetración, con preferencia por las caras de A
        // (evita que la elección salte entre ejes casi iguales de un paso a otro).
        var axis = FACE_A_X
        var separation = faceAx
        var nx = if (dAx > 0) cA else -cA
        var ny = if (dAx > 0) sA else -sA
        if (faceAy > RELATIVE_TOL * separation + ABSOLUTE_TOL * hAy) {
            axis = FACE_A_Y; separation = faceAy
            nx = if (dAy > 0) -sA else sA
            ny = if (dAy > 0) cA else -cA
        }
        if (faceBx > RELATIVE_TOL * separation + ABSOLUTE_TOL * hBx) {
            axis = FACE_B_X; separation = faceBx
            nx = if (dBx > 0) cB else -cB
            ny = if (dBx > 0) sB else -sB
        }
        if (faceBy > RELATIVE_TOL * separation + ABSOLUTE_TOL * hBy) {
            axis = FACE_B_Y; separation = faceBy
            nx = if (dBy > 0) -sB else sB
            ny = if (dBy > 0) cB else -cB
        }

        // Planos de recorte según la cara de referencia.
        val frontNx: Double
        val frontNy: Double
        val front: Double
        val sideNx: Double
        val sideNy: Double
        val negSide: Double
        val posSide: Double
        val negEdge: Int
        val posEdge: Int
        when (axis) {
            FACE_A_X -> {
                frontNx = nx; frontNy = ny
                front = a.x * frontNx + a.y * frontNy + hAx
                sideNx = -sA; sideNy = cA
                val side = a.x * sideNx + a.y * sideNy
                negSide = -side + hAy; posSide = side + hAy
                negEdge = EDGE3; posEdge = EDGE1
                incidentEdge(hBx, hBy, b.x, b.y, cB, sB, frontNx, frontNy)
            }
            FACE_A_Y -> {
                frontNx = nx; frontNy = ny
                front = a.x * frontNx + a.y * frontNy + hAy
                sideNx = cA; sideNy = sA
                val side = a.x * sideNx + a.y * sideNy
                negSide = -side + hAx; posSide = side + hAx
                negEdge = EDGE2; posEdge = EDGE4
                incidentEdge(hBx, hBy, b.x, b.y, cB, sB, frontNx, frontNy)
            }
            FACE_B_X -> {
                frontNx = -nx; frontNy = -ny
                front = b.x * frontNx + b.y * frontNy + hBx
                sideNx = -sB; sideNy = cB
                val side = b.x * sideNx + b.y * sideNy
                negSide = -side + hBy; posSide = side + hBy
                negEdge = EDGE3; posEdge = EDGE1
                incidentEdge(hAx, hAy, a.x, a.y, cA, sA, frontNx, frontNy)
            }
            else -> {
                frontNx = -nx; frontNy = -ny
                front = b.x * frontNx + b.y * frontNy + hBy
                sideNx = cB; sideNy = sB
                val side = b.x * sideNx + b.y * sideNy
                negSide = -side + hBx; posSide = side + hBx
                negEdge = EDGE2; posEdge = EDGE4
                incidentEdge(hAx, hAy, a.x, a.y, cA, sA, frontNx, frontNy)
            }
        }

        // La arista incidente, recortada por los dos lados de la cara de referencia.
        if (clipSegment(clip1, incident, -sideNx, -sideNy, negSide, negEdge) < 2) return 0
        if (clipSegment(clip2, clip1, sideNx, sideNy, posSide, posEdge) < 2) return 0

        val flip = axis == FACE_B_X || axis == FACE_B_Y
        var count = 0
        for (i in 0..1) {
            val v = clip2[i]
            val sep = frontNx * v.x + frontNy * v.y - front
            if (sep <= 0) {
                // El punto se lleva a la cara de referencia (fácil de descartar).
                out[count].setGeometry(v.x - sep * frontNx, v.y - sep * frontNy, nx, ny, sep, v.key(flip))
                count++
            }
        }
        return count
    }

    /** La arista de la caja incidente más enfrentada a la cara de referencia. */
    private fun incidentEdge(hx: Double, hy: Double, px: Double, py: Double, c: Double, s: Double, nx: Double, ny: Double) {
        // La normal en el sistema de la caja incidente, con el signo cambiado.
        val lx = -(c * nx + s * ny)
        val ly = -(-s * nx + c * ny)
        val v0 = incident[0]
        val v1 = incident[1]
        v0.inEdge1 = NO_EDGE; v0.outEdge1 = NO_EDGE
        v1.inEdge1 = NO_EDGE; v1.outEdge1 = NO_EDGE
        if (abs(lx) > abs(ly)) {
            if (lx >= 0) {
                v0.x = hx; v0.y = -hy; v0.inEdge2 = EDGE3; v0.outEdge2 = EDGE4
                v1.x = hx; v1.y = hy; v1.inEdge2 = EDGE4; v1.outEdge2 = EDGE1
            } else {
                v0.x = -hx; v0.y = hy; v0.inEdge2 = EDGE1; v0.outEdge2 = EDGE2
                v1.x = -hx; v1.y = -hy; v1.inEdge2 = EDGE2; v1.outEdge2 = EDGE3
            }
        } else {
            if (ly >= 0) {
                v0.x = hx; v0.y = hy; v0.inEdge2 = EDGE4; v0.outEdge2 = EDGE1
                v1.x = -hx; v1.y = hy; v1.inEdge2 = EDGE1; v1.outEdge2 = EDGE2
            } else {
                v0.x = -hx; v0.y = -hy; v0.inEdge2 = EDGE2; v0.outEdge2 = EDGE3
                v1.x = hx; v1.y = -hy; v1.inEdge2 = EDGE3; v1.outEdge2 = EDGE4
            }
        }
        for (v in incident) {
            val wx = px + c * v.x - s * v.y
            val wy = py + s * v.x + c * v.y
            v.x = wx; v.y = wy
        }
    }

    /** Recorta un segmento por una recta (se queda con lo que está detrás). */
    private fun clipSegment(
        vOut: Array<ClipVertex>,
        vIn: Array<ClipVertex>,
        nx: Double,
        ny: Double,
        offset: Double,
        clipEdge: Int,
    ): Int {
        var numOut = 0
        val d0 = nx * vIn[0].x + ny * vIn[0].y - offset
        val d1 = nx * vIn[1].x + ny * vIn[1].y - offset
        if (d0 <= 0) vOut[numOut++].copyFrom(vIn[0])
        if (d1 <= 0) vOut[numOut++].copyFrom(vIn[1])
        if (d0 * d1 < 0 && numOut < 2) {
            val t = d0 / (d0 - d1)
            val v = vOut[numOut]
            if (d0 > 0) {
                v.copyFrom(vIn[0])
                v.inEdge1 = clipEdge; v.inEdge2 = NO_EDGE
            } else {
                v.copyFrom(vIn[1])
                v.outEdge1 = clipEdge; v.outEdge2 = NO_EDGE
            }
            v.x = vIn[0].x + t * (vIn[1].x - vIn[0].x)
            v.y = vIn[0].y + t * (vIn[1].y - vIn[0].y)
            numOut++
        }
        return numOut
    }

    private companion object {
        const val FACE_A_X = 0
        const val FACE_A_Y = 1
        const val FACE_B_X = 2
        const val FACE_B_Y = 3
        const val NO_EDGE = 0
        const val EDGE1 = 1
        const val EDGE2 = 2
        const val EDGE3 = 3
        const val EDGE4 = 4
        const val RELATIVE_TOL = 0.95
        const val ABSOLUTE_TOL = 0.01
    }
}
