package eu.feg.ambient.ambient.surfaces.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.unit.ColorProvider

/**
 * The "glass" card shell every widget state sits in.
 *
 * NOT A BLUR. No widget API can blur the wallpaper behind it, and chasing one is an hour lost.
 * What reads as glass at arm's length is three cheaper things done together: a translucent
 * dark fill so the wallpaper shows through, a one-pixel light hairline so the card has an
 * edge on a dark wallpaper, and a faint diagonal gradient so the surface is not flat. The fill
 * and the gradient are one bitmap, because Glance allows a single background per node and a
 * RemoteViews colour cannot carry a gradient.
 *
 * The border is the outer of two nested boxes: Glance has no border modifier, so the outer box
 * is the border colour with the full radius and the inner box sits 1dp inside it.
 */
@Composable
internal fun GlassCard(
    description: String,
    onClick: Action? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalContext.current.resources.displayMetrics.density
    var outer = GlanceModifier
        .fillMaxSize()
        .background(ColorProvider(BORDER))
        .cornerRadius(RADIUS)
        .semantics { contentDescription = description }
    if (onClick != null) outer = outer.clickable(onClick)

    Box(modifier = outer) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(BORDER_WIDTH),
        ) {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ImageProvider(GlassFill.of(density)), ContentScale.FillBounds)
                    .cornerRadius(RADIUS - BORDER_WIDTH)
                    .padding(horizontal = PAD_H, vertical = PAD_V),
                verticalAlignment = Alignment.Vertical.Top,
                horizontalAlignment = Alignment.Horizontal.Start,
                content = content,
            )
        }
    }
}

internal val RADIUS = 28.dp
internal val BORDER_WIDTH = 1.dp
internal val PAD_H = 22.dp
internal val PAD_V = 14.dp

/** White at 14 per cent: enough edge to separate the card from a dark wallpaper, no more. */
private val BORDER = Color(0x24FFFFFF)

/**
 * The fill: #1A1E28 at 62 per cent, under a top-left to bottom-right run of white from 7 to
 * 2 per cent. Drawn small and stretched -- a gradient survives scaling and the bitmap costs
 * nothing to keep.
 */
internal object GlassFill {
    private var cached: Bitmap? = null

    @Synchronized
    fun of(density: Float): Bitmap {
        cached?.let { return it }
        val size = (64 * density).toInt().coerceAtLeast(64)
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        canvas.drawColor(0x9E1A1E28.toInt())
        val sheen = Paint().apply {
            shader = LinearGradient(
                0f, 0f, size.toFloat(), size.toFloat(),
                0x12FFFFFF, 0x05FFFFFF, Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), sheen)
        cached = bitmap
        return bitmap
    }
}
