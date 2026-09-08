package eu.feg.ambient.ambient.surfaces.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.feg.ambient.ambient.narrator.NarratedText
import eu.feg.ambient.ambient.narrator.NarratorEngine
import eu.feg.ambient.ambient.surfaces.DemoSurfaceData
import eu.feg.ambient.ambient.surfaces.LegStatus
import eu.feg.ambient.ui.theme.PskColors

/**
 * The Live card, previewable in Android Studio.
 *
 * WHY THIS IS A MIRROR AND NOT THE REAL COMPOSABLE: rendering a GlanceAppWidget in a preview
 * needs androidx.glance:glance-appwidget-preview, which is not a dependency of this module
 * and adding one is not this step's to make. Glance composables cannot be hosted by a plain
 * @Preview either — they emit a RemoteViews tree, not a Compose one.
 *
 * So this is a Compose restatement of the same layout, reading the same tokens and the same
 * demo slip. It is a proportions-and-colour check, not proof the widget renders; that proof
 * is adding the widget to the home screen and driving it from the Surface Lab.
 */
@Preview(name = "Widget · Live", widthDp = 160, heightDp = 160, showBackground = false)
@Preview(name = "Widget · Live · 200% font", widthDp = 160, heightDp = 160, fontScale = 2.0f)
@Composable
private fun AmbientWidgetLivePreview() {
    val psk = PskColors()
    val slip = DemoSurfaceData.threeLegLive().copy(
        narrated = NarratedText(
            headline = "Two down, Liverpool to go.",
            detail = "Betis and Sparta are home. Liverpool lead at 61'.",
            engine = NarratorEngine.TEMPLATE,
            latencyMs = 0,
        ),
    )
    Column(
        modifier = Modifier
            .size(160.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(psk.background)
            .padding(12.dp),
    ) {
        Text(
            text = slip.chipText,
            color = psk.textPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Text(
            text = "Liverpool 1 – 0 Ipswich",
            color = psk.textSecondary,
            fontSize = 11.sp,
            maxLines = 1,
        )
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            slip.legs.forEachIndexed { index, leg ->
                if (index > 0) Spacer(Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            when (leg.status) {
                                LegStatus.WON -> psk.positive
                                LegStatus.LOST -> psk.negative
                                else -> psk.surfaceRaised
                            },
                        ),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        slip.narrated?.headline?.let {
            Text(text = it, color = psk.textPrimary, fontSize = 12.sp, maxLines = 3)
        }
        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf("👍", "👎", "Mute").forEach { label ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(psk.surfaceRaised)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        color = psk.textPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
