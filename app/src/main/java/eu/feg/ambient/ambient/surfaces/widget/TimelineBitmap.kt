package eu.feg.ambient.ambient.surfaces.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.graphics.createBitmap
import kotlin.math.cos
import kotlin.math.sin

/**
 * The match timeline, drawn.
 *
 * Glance has no Canvas: a dotted track that changes colour at the current minute, a glowing
 * marker and goal badges positioned by minute cannot be built from Box and Row. So this is a
 * bitmap, redrawn whenever the minute or the goals change (the composition recomposes on every
 * store write, and the bitmap is keyed on the inputs).
 *
 * The red here is the "live" red, #E8342B, in every club's colours. It means the match is on,
 * not who the customer supports -- the club is in the accent and the crests.
 */
object TimelineBitmap {

    fun draw(
        widthPx: Int,
        density: Float,
        minute: Int,
        goalMinutes: List<Int>,
        matchMinutes: Int = 90,
    ): Bitmap {
        val h = (HEIGHT_DP * density).toInt().coerceAtLeast(1)
        val w = widthPx.coerceAtLeast((80 * density).toInt())
        val bitmap = createBitmap(w, h)
        val canvas = Canvas(bitmap)
        fun dp(v: Float) = v * density

        val cy = h / 2f
        val trackStart = dp(28f)
        val trackEnd = w - dp(28f)
        val trackLen = trackEnd - trackStart
        fun xAt(min: Int) = trackStart + trackLen * (min.coerceIn(0, matchMinutes) / matchMinutes.toFloat())
        val nowX = xAt(minute)

        // Labels at the ends: the same secondary grey the card's captions use.
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = LABEL
            textSize = dp(13f)
            typeface = Typeface.DEFAULT
        }
        val labelDy = -(label.descent() + label.ascent()) / 2f
        label.textAlign = Paint.Align.LEFT
        canvas.drawText("KO", 0f, cy + labelDy, label)
        label.textAlign = Paint.Align.RIGHT
        canvas.drawText("FT", w.toFloat(), cy + labelDy, label)

        // The dotted track: red up to now, grey beyond. 4dp dots on an 8dp pitch.
        val dot = Paint(Paint.ANTI_ALIAS_FLAG)
        val pitch = dp(8f)
        var x = trackStart
        while (x <= trackEnd) {
            dot.color = if (x <= nowX) LIVE_RED else TRACK_GREY
            canvas.drawCircle(x, cy, dp(2f), dot)
            x += pitch
        }

        // Goals: a dark disc with a red ring and a small ball on it, one per goal.
        val disc = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GOAL_DISC }
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = LIVE_RED; style = Paint.Style.STROKE; strokeWidth = dp(2f)
        }
        goalMinutes.forEach { g ->
            val gx = xAt(g)
            canvas.drawCircle(gx, cy, dp(12f), disc)
            canvas.drawCircle(gx, cy, dp(11f), ring)
            drawBall(canvas, gx, cy, dp(4.5f))
        }

        // Now: a soft red glow, then a white disc with a red ring on top of it.
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                nowX, cy, dp(14f),
                intArrayOf(0xB3E8342B.toInt(), 0x00E8342B), floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawCircle(nowX, cy, dp(14f), glow)
        canvas.drawCircle(nowX, cy, dp(8f), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = WHITE })
        canvas.drawCircle(
            nowX, cy, dp(6.5f),
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = LIVE_RED; style = Paint.Style.STROKE; strokeWidth = dp(3f)
            },
        )
        return bitmap
    }

    /** A white circle with a dark pentagon: enough to read as a football at 9dp. */
    private fun drawBall(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        canvas.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = WHITE })
        val path = Path()
        for (i in 0 until 5) {
            val a = Math.toRadians((-90 + i * 72).toDouble())
            val px = (cx + r * 0.45f * cos(a)).toFloat()
            val py = (cy + r * 0.45f * sin(a)).toFloat()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GOAL_DISC })
    }

    const val HEIGHT_DP = 34f

    private const val LIVE_RED = 0xFFE8342B.toInt()
    private const val TRACK_GREY = 0xFF4A505E.toInt()
    private const val GOAL_DISC = 0xFF7E1C1C.toInt()
    private const val LABEL = 0xFF939AA8.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()
}
