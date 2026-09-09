package eu.feg.ambient.ambient.surfaces.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.createBitmap
import eu.feg.ambient.ambient.identity.ClubTheme
import eu.feg.ambient.ambient.identity.CrestBitmap

/**
 * The ground the club card sits on: the club's own crest, blurred into a wash of its colours.
 *
 * WHY A BITMAP. Glance has no blur, no gradient and no layering worth the name -- a widget's
 * background is one drawable and nothing else. So the whole ground is painted here in three
 * passes and handed over as a single image: the club's colour as a diagonal gradient, its
 * crest blown up past the edges and blurred until it reads as texture rather than as a badge,
 * and a vignette that pulls the corners down so white text stays legible over the bright half.
 *
 * WHY BLURRED RATHER THAN PLACED. A crest at full strength behind a scoreline is a sticker on
 * a window: the eye keeps trying to read it and the score has to fight it. Blurred to a
 * suggestion, it stops competing and becomes what it should have been all along, which is
 * atmosphere. It is also the honest use of a GENERATED crest -- see CrestBitmap, which draws
 * a badge rather than shipping a trademark, and a drawn badge survives being enlarged eight
 * times precisely because it has no fine detail to lose.
 *
 * BLUR WITHOUT A BLUR API. RenderEffect is API 31 and RenderScript is deprecated, so this
 * does the old trick instead: scale the crest down to a thumbnail and back up with bilinear
 * filtering, twice. Two passes of that is a box blur in everything but name, it runs in a
 * millisecond, and it works on every device this app supports.
 *
 * Cached per club and size, like CrestBitmap and for the same reason: the widget redraws on
 * every tick of a live match, and a fresh 400x200 bitmap each time is a steady drip of
 * garbage behind the surface that has to stay smoothest.
 */
object ClubBackdrop {

    private val cache = HashMap<String, Bitmap>()

    @Synchronized
    fun of(theme: ClubTheme, widthPx: Int, heightPx: Int): Bitmap {
        val w = widthPx.coerceIn(MIN_PX, MAX_PX)
        val h = heightPx.coerceIn(MIN_PX, MAX_PX)
        val key = theme.clubId + "@" + w + "x" + h
        cache[key]?.let { return it }

        val bitmap = createBitmap(w, h)
        val canvas = Canvas(bitmap)

        paintGround(canvas, theme, w, h)
        paintCrestWash(canvas, theme, w, h)
        paintVignette(canvas, w, h)

        cache[key] = bitmap
        return bitmap
    }

    /**
     * The club's colour, dark enough to carry white text.
     *
     * [ClubTheme.primary] itself is often far too bright for a full-bleed ground -- Varazdin's
     * yellow and Hajduk's white would both leave the score invisible -- so the gradient runs
     * from a heavily darkened primary into near-black. The club is still unmistakable because
     * the hue survives the darkening; only the luminance goes.
     */
    private fun paintGround(canvas: Canvas, theme: ClubTheme, w: Int, h: Int) {
        canvas.drawColor(BASE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, w.toFloat(), h.toFloat(),
                darken(theme.primary.toArgb(), GROUND_TOP),
                darken(theme.secondary.toArgb(), GROUND_BOTTOM),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
    }

    /**
     * The crest, enlarged past the right edge and blurred.
     *
     * Off-centre and bleeding out of frame on purpose: a crest centred behind a centred score
     * fights it for the same axis, and one that fits inside the card reads as a watermark
     * somebody forgot to remove. Anchored right because the score sits centre-left once the
     * home team's name is in front of it.
     */
    private fun paintCrestWash(canvas: Canvas, theme: ClubTheme, w: Int, h: Int) {
        val crest = CrestBitmap.of(theme, CREST_SOURCE_PX)
        val blurred = blur(crest)
        val size = (h * CREST_SCALE).toInt()
        val left = (w * CREST_ANCHOR_X).toInt()
        val top = ((h - size) / 2f).toInt()
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = CREST_ALPHA }
        canvas.drawBitmap(
            blurred,
            Rect(0, 0, blurred.width, blurred.height),
            Rect(left, top, left + size, top + size),
            paint,
        )
    }

    /**
     * A radial darkening from the centre out.
     *
     * The blurred crest leaves bright patches wherever the club's lighter colour landed, and
     * text over those loses contrast in exactly the unpredictable way a generated background
     * always does. The vignette is the cheap insurance: it costs the design nothing and takes
     * the worst case off the table.
     */
    private fun paintVignette(canvas: Canvas, w: Int, h: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                w * 0.35f, h * 0.5f, maxOf(w, h) * 0.9f,
                intArrayOf(VIGNETTE_INNER, VIGNETTE_OUTER),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
    }

    /** Down to a thumbnail and back up, twice. See the class note on why this and not RenderEffect. */
    private fun blur(source: Bitmap): Bitmap {
        var out = source
        repeat(BLUR_PASSES) {
            val small = Bitmap.createScaledBitmap(out, THUMB_PX, THUMB_PX, true)
            out = Bitmap.createScaledBitmap(small, source.width, source.height, true)
        }
        return out
    }

    /** Toward black by [amount], keeping the hue. */
    private fun darken(argb: Int, amount: Float): Int {
        val keep = 1f - amount
        val r = ((argb shr 16 and 0xFF) * keep).toInt()
        val g = ((argb shr 8 and 0xFF) * keep).toInt()
        val b = ((argb and 0xFF) * keep).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private const val BASE = 0xFF0B0B0E.toInt()
    private const val GROUND_TOP = 0.62f
    private const val GROUND_BOTTOM = 0.86f

    private const val CREST_SOURCE_PX = 192
    private const val CREST_SCALE = 1.45f
    private const val CREST_ANCHOR_X = 0.58f
    private const val CREST_ALPHA = 56

    private const val THUMB_PX = 12
    private const val BLUR_PASSES = 2

    private const val VIGNETTE_INNER = 0x00000000
    private const val VIGNETTE_OUTER = 0x9E000000.toInt()

    private const val MIN_PX = 120
    private const val MAX_PX = 1400
}
