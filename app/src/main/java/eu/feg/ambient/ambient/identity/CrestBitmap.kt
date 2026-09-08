package eu.feg.ambient.ambient.identity

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.createBitmap

/**
 * The club's badge, drawn.
 *
 * NOT A SCRAPED LOGO, and it cannot become one. Club crests are trademarks, and an app that
 * ships eight of them has a licensing problem rather than a design feature. What this draws
 * instead is an honest generated badge: a shield in the club's two colours, carrying the
 * pattern the club is actually recognised by — Slavia's halves, a checkerboard, hoops — with
 * the initials over it. At 24 dp on a widget it reads as a crest, which is the whole job.
 *
 * A shield rather than a disc because a disc full of stripes reads as a pie chart. The shape
 * is what makes the pattern legible as a badge.
 *
 * One bitmap per club per size, cached: a Live Update reposts on every tick, and allocating
 * a bitmap each time would be a steady drip of garbage behind the surface that must stay
 * smoothest.
 */
object CrestBitmap {

    private val cache = HashMap<String, Bitmap>()

    @Synchronized
    fun of(theme: ClubTheme, sizePx: Int = DEFAULT_SIZE_PX): Bitmap {
        val key = theme.clubId + "@" + sizePx
        cache[key]?.let { return it }

        val bitmap = createBitmap(sizePx, sizePx)
        val canvas = Canvas(bitmap)
        val shield = shieldPath(sizePx.toFloat())

        // Everything is clipped to the shield, so the pattern below can be drawn as plain
        // rectangles and still come out shield-shaped.
        canvas.save()
        canvas.clipPath(shield)
        drawPattern(canvas, theme, sizePx.toFloat())
        canvas.restore()

        drawInitials(canvas, theme, sizePx.toFloat())
        drawEdge(canvas, shield, sizePx.toFloat())

        cache[key] = bitmap
        return bitmap
    }

    /** A rounded shield: square shoulders, a curve into a point at the bottom. */
    private fun shieldPath(size: Float): Path {
        val inset = size * 0.06f
        val left = inset
        val right = size - inset
        val top = inset
        val shoulder = size * 0.62f
        val bottom = size - inset
        val midX = size / 2f

        return Path().apply {
            moveTo(left, top + size * 0.10f)
            quadTo(left, top, left + size * 0.10f, top)
            lineTo(right - size * 0.10f, top)
            quadTo(right, top, right, top + size * 0.10f)
            lineTo(right, shoulder)
            // The two curves that bring the sides down to the point.
            cubicTo(right, bottom - size * 0.10f, midX + size * 0.22f, bottom, midX, bottom)
            cubicTo(midX - size * 0.22f, bottom, left, bottom - size * 0.10f, left, shoulder)
            close()
        }
    }

    private fun drawPattern(canvas: Canvas, theme: ClubTheme, size: Float) {
        val primary = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = theme.primary.toArgb() }
        val secondary = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = theme.secondary.toArgb() }

        canvas.drawRect(0f, 0f, size, size, primary)
        when (theme.pattern) {
            ClubPattern.SOLID -> Unit

            ClubPattern.HALVES ->
                canvas.drawRect(size / 2f, 0f, size, size, secondary)

            ClubPattern.STRIPES -> {
                val stripe = size / 7f
                var x = stripe
                while (x < size) {
                    canvas.drawRect(x, 0f, x + stripe, size, secondary)
                    x += stripe * 2
                }
            }

            ClubPattern.HOOP ->
                canvas.drawRect(0f, size * 0.40f, size, size * 0.60f, secondary)

            ClubPattern.CHECKS -> {
                val cell = size / 5f
                var row = 0
                while (row * cell < size) {
                    var col = 0
                    while (col * cell < size) {
                        if ((row + col) % 2 == 1) {
                            canvas.drawRect(
                                col * cell, row * cell,
                                (col + 1) * cell, (row + 1) * cell,
                                secondary,
                            )
                        }
                        col++
                    }
                    row++
                }
            }
        }
    }

    /**
     * The initials, in a band across the middle.
     *
     * The band exists because a patterned badge has no single background colour to contrast
     * against — letters straddling a checkerboard are illegible whatever colour they are. The
     * band is the club's primary and the letters are [ClubTheme.onPrimary], which is the pair
     * we already computed to clear 4.5:1.
     */
    private fun drawInitials(canvas: Canvas, theme: ClubTheme, size: Float) {
        val bandHeight = size * 0.30f
        val top = size * 0.34f
        val band = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = theme.primary.toArgb() }
        canvas.drawRoundRect(
            RectF(size * 0.06f, top, size * 0.94f, top + bandHeight),
            size * 0.04f,
            size * 0.04f,
            band,
        )

        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.onPrimary.toArgb()
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            textSize = bandHeight * 0.74f
        }
        // Centred on the glyphs' own bounds: capitals have no descender, so centring on the
        // baseline would sit them visibly low in the band.
        val bounds = Rect()
        text.getTextBounds(theme.crestInitials, 0, theme.crestInitials.length, bounds)
        canvas.drawText(
            theme.crestInitials,
            size / 2f,
            top + bandHeight / 2f + bounds.height() / 2f,
            text,
        )
    }

    /** A hairline edge, so a white club still has a shape against a light background. */
    private fun drawEdge(canvas: Canvas, shield: Path, size: Float) {
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = size * 0.035f
            color = EDGE_COLOR
        }
        canvas.drawPath(shield, edge)
    }

    /**
     * The card's crest: a rounded square in the club colour with the initials on it, with an
     * optional light border. The shield stays for the notification and the shortcuts; the
     * match card's header and score row want the tile shape the mockup shows.
     */
    @Synchronized
    fun square(theme: ClubTheme, sizePx: Int, cornerPx: Float, bordered: Boolean): Bitmap {
        val key = "sq:" + theme.clubId + ":" + theme.crestInitials + "@" + sizePx + ":" + bordered
        cache[key]?.let { return it }
        val bitmap = createBitmap(sizePx, sizePx)
        val canvas = Canvas(bitmap)
        val rect = RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat())
        canvas.drawRoundRect(rect, cornerPx, cornerPx, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = theme.primary.toArgb() })
        if (bordered) {
            val inset = sizePx * 0.03f
            canvas.drawRoundRect(
                RectF(inset, inset, sizePx - inset, sizePx - inset), cornerPx, cornerPx,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE; strokeWidth = inset * 1.5f; color = 0x3DFFFFFF
                },
            )
        }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.onPrimary.toArgb()
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            textSize = sizePx * 0.34f
        }
        val bounds = Rect()
        text.getTextBounds(theme.crestInitials, 0, theme.crestInitials.length, bounds)
        canvas.drawText(theme.crestInitials, sizePx / 2f, sizePx / 2f + bounds.height() / 2f, text)
        cache[key] = bitmap
        return bitmap
    }

    private const val DEFAULT_SIZE_PX = 192
    private const val EDGE_COLOR = 0x33000000
}
