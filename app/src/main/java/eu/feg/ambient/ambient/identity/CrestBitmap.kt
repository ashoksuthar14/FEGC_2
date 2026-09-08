package eu.feg.ambient.ambient.identity

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.createBitmap

/**
 * The same crest as [eu.feg.ambient.ui.components.CrestBadge], drawn for surfaces that take
 * a Bitmap rather than a composable — the notification's large icon, and shortcut icons.
 *
 * Two drawings of one mark rather than one shared drawing, because the alternative is
 * rendering a Compose tree to a bitmap on every notification post, which is far more moving
 * parts on the path that must never fail. The rule they share is the one that matters: the
 * letters use [ClubTheme.onPrimary], so they are legible on white and on navy alike.
 *
 * Cached per club, because a Live Update reposts on every tick and allocating a bitmap each
 * time would be a steady drip of garbage behind the one surface that must stay smooth.
 */
object CrestBitmap {

    private val cache = HashMap<String, Bitmap>()

    @Synchronized
    fun of(theme: ClubTheme, sizePx: Int = DEFAULT_SIZE_PX): Bitmap {
        val key = theme.clubId + "@" + sizePx
        cache[key]?.let { return it }

        val bitmap = createBitmap(sizePx, sizePx)
        val canvas = Canvas(bitmap)
        val radius = sizePx / 2f

        val circle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = theme.primary.toArgb() }
        canvas.drawCircle(radius, radius, radius, circle)

        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.onPrimary.toArgb()
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            textSize = sizePx * INITIALS_RATIO
        }
        // Centre on the glyphs' own bounds rather than the font metrics: two capitals have no
        // descender, and centring on the baseline would sit them visibly low in the circle.
        val bounds = Rect()
        text.getTextBounds(theme.crestInitials, 0, theme.crestInitials.length, bounds)
        canvas.drawText(theme.crestInitials, radius, radius + bounds.height() / 2f, text)

        cache[key] = bitmap
        return bitmap
    }

    private const val DEFAULT_SIZE_PX = 192
    private const val INITIALS_RATIO = 0.38f
}
