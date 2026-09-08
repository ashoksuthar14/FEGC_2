package eu.feg.ambient.ambient.surfaces.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * The manifest entry point. Named exactly as AndroidManifest.xml declares it, so renaming
 * this class silently breaks the widget rather than failing to compile.
 *
 * There is deliberately nothing else here: the receiver's whole job is to hand the launcher
 * a [GlanceAppWidget], and every decision about what to draw belongs in AmbientWidget where
 * it can be read in one place.
 */
class AmbientWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AmbientWidget()
}
