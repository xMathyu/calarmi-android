package com.mathyusolutions.calarmi.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.text.TextPaint
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.core.graphics.ColorUtils
import kotlin.math.roundToInt

/**
 * Lo que los widgets no pueden dibujar solos. Glance solo pinta recursos o
 * bitmaps: los íconos de la app son `ImageVector` de Material (los mismos que
 * `SymbolIcons`), así que se rasterizan aquí, y los degradados igual.
 */
internal object WidgetGraphics {

    /** Un `ImageVector` de Material pintado de un color, en un cuadrado de `sizePx`. */
    fun vector(vector: ImageVector, sizePx: Int, color: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        drawVector(Canvas(bitmap), vector, RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat()), color)
        return bitmap
    }

    private fun drawVector(canvas: Canvas, vector: ImageVector, bounds: RectF, color: Int) {
        canvas.save()
        canvas.translate(bounds.left, bounds.top)
        canvas.scale(bounds.width() / vector.viewportWidth, bounds.height() / vector.viewportHeight)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        drawGroup(canvas, vector.root, paint, color)
        canvas.restore()
    }

    private fun drawGroup(canvas: Canvas, group: VectorGroup, paint: Paint, color: Int) {
        canvas.save()
        canvas.translate(group.translationX + group.pivotX, group.translationY + group.pivotY)
        canvas.rotate(group.rotation)
        canvas.scale(group.scaleX, group.scaleY)
        canvas.translate(-group.pivotX, -group.pivotY)
        for (node in group) {
            when (node) {
                is VectorGroup -> drawGroup(canvas, node, paint, color)
                is VectorPath -> {
                    if (node.fill == null) continue
                    val path: Path = PathParser().addPathNodes(node.pathData).toPath().asAndroidPath()
                    path.fillType = if (node.pathFillType == PathFillType.EvenOdd) Path.FillType.EVEN_ODD else Path.FillType.WINDING
                    paint.color = ColorUtils.setAlphaComponent(color, (android.graphics.Color.alpha(color) * node.fillAlpha).roundToInt())
                    canvas.drawPath(path, paint)
                }
            }
        }
        canvas.restore()
    }

    /**
     * Un cuadro (o círculo con `cornerFraction` = 0.5) de fondo translúcido con
     * un emoji o un símbolo encima: el ícono de las tarjetas y de las alarmas.
     */
    fun tile(
        sizePx: Int,
        background: Int,
        cornerFraction: Float,
        emoji: String?,
        symbol: ImageVector?,
        symbolColor: Int,
        contentFraction: Float = 0.5f,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val size = sizePx.toFloat()
        val radius = size * cornerFraction
        canvas.drawRoundRect(RectF(0f, 0f, size, size), radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = background })
        if (emoji != null) {
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = size * contentFraction
                textAlign = Paint.Align.CENTER
            }
            val metrics = paint.fontMetrics
            val baseline = size / 2f - (metrics.ascent + metrics.descent) / 2f
            canvas.drawText(emoji, size / 2f, baseline, paint)
        } else if (symbol != null) {
            val inset = size * (1f - contentFraction) / 2f
            drawVector(canvas, symbol, RectF(inset, inset, size - inset, size - inset), symbolColor)
        }
        return bitmap
    }

    /**
     * Un degradado diagonal (de arriba a la izquierda a abajo a la derecha). Se
     * estira al tamaño del widget: con pocos colores no se notan los píxeles.
     */
    fun gradient(from: Int, to: Int, sizePx: Int = 160): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val paint = Paint().apply {
            shader = LinearGradient(0f, 0f, sizePx.toFloat(), sizePx.toFloat(), from, to, Shader.TileMode.CLAMP)
        }
        Canvas(bitmap).drawRect(0f, 0f, sizePx.toFloat(), sizePx.toFloat(), paint)
        return bitmap
    }

    /** `#RRGGBB` → color de Android; `fallback` si no se entiende. */
    fun parse(hex: String?, fallback: Int): Int = runCatching {
        android.graphics.Color.parseColor(if (hex!!.startsWith("#")) hex else "#$hex")
    }.getOrDefault(fallback)

    /** Mezcla `color` con `other` (`amount` = cuánto de `other`). */
    fun mix(color: Int, other: Int, amount: Float): Int = ColorUtils.blendARGB(color, other, amount)

    fun withAlpha(color: Int, alpha: Float): Int = ColorUtils.setAlphaComponent(color, (alpha * 255).roundToInt())
}
