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
 * The ground the club card sits on: the club's own crest, over a wash of its colours.
 *
 * WHY A BITMAP. Glance has no blur, no gradient and no layering worth the name -- a widget's
 * background is one drawable and nothing else. So the whole ground is painted here in three
 * passes and handed over as a single image: the club's colour as a diagonal gradient, its
 * crest blown up past the right edge, and a vignette that pulls the corners down so white
 * text stays legible over the bright half.
 *
 * THE CREST IS SHARP, and that is a deliberate reversal. It was blurred first, on the theory
 * that a badge at full strength behind a scoreline is a sticker on a window the eye keeps
 * trying to read. That is true of a photograph; it is not true of THIS badge. CrestBitmap
 * draws a flat shield -- two colours, one pattern, two initials, no gradients and no fine
 * detail -- so enlarged it stays a clean shape rather than becoming noise, and blurring it
 * only threw away the one thing that identifies the club: the pattern. Slavia's halves and a
 * checkerboard are indistinguishable once smeared.
 *
 * So the crest is drawn at the size it appears, from CrestBitmap's own cache, and held back
 * with alpha rather than with blur. Alpha keeps the edges honest; blur destroyed the content.
 * The score stays dominant because the crest is anchored off-centre and bleeds out of frame,
 * not because it has been made illegible.
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
     * The crest, enlarged past the right edge.
     *
     * Off-centre and bleeding out of frame on purpose: a crest centred behind a centred score
     * fights it for the same axis, and one that fits inside the card reads as a watermark
     * somebody forgot to remove. Anchored right because the score sits centre-left once the
     * home team's name is in front of it.
     */
    private fun paintCrestWash(canvas: Canvas, theme: ClubTheme, w: Int, h: Int) {
        val size = (h * CREST_SCALE).toInt().coerceIn(MIN_PX, MAX_PX)
        // Asked for at the size it will be drawn, so no scaling happens at all. CrestBitmap
        // caches per club AND size, so this costs one badge per widget geometry.
        val crest = CrestBitmap.of(theme, size)
        val left = (w * CREST_ANCHOR_X).toInt()
        val top = ((h - size) / 2f).toInt()
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = CREST_ALPHA }
        canvas.drawBitmap(
            crest,
            Rect(0, 0, crest.width, crest.height),
            Rect(left, top, left + size, top + size),
            paint,
        )
    }

    /**
     * A radial darkening from the centre out.
     *
     * The crest's lighter colour still lands wherever the pattern puts it, and white text over
     * Varazdin's yellow or Hajduk's white would lose contrast. Lighter than it was when the
     * crest was a blur, because a sharp badge at a known alpha is a predictable background
     * rather than an unpredictable one -- but not gone, because the clubs are not all dark.
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

    private const val CREST_SCALE = 1.35f
    private const val CREST_ANCHOR_X = 0.60f

    /**
     * Higher than it was when the crest was blurred.
     *
     * A blur spreads a badge into a haze that reads at almost any strength; a sharp one at the
     * same alpha looks like a printing error. This is the point where the pattern is plainly
     * legible and the score still wins the card.
     */
    private const val CREST_ALPHA = 104

    private const val VIGNETTE_INNER = 0x00000000

    /**
     * Lighter than it was, for the same reason.
     *
     * The vignette existed to rescue contrast from a blur's unpredictable bright patches. A
     * sharp crest at a known alpha has no such patches, so the darkening can come off and let
     * the badge show.
     */
    private const val VIGNETTE_OUTER = 0x7A000000.toInt()

    private const val MIN_PX = 120
    private const val MAX_PX = 1400
}
