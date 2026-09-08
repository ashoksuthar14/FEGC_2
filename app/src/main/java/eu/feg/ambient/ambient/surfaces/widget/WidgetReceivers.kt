package eu.feg.ambient.ambient.surfaces.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * One receiver per widget, because the launcher's picker lists providers, not states: five
 * entries is what makes "one widget per capability" visible before anything is tapped.
 *
 * Each is named exactly as AndroidManifest.xml declares it -- renaming one silently breaks
 * that widget rather than failing to compile, so they are kept together where the set can be
 * read against the manifest in one glance.
 */
class ClubWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ClubWidget()
}

class DigestWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DigestWidget()
}

class SeasonWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SeasonWidget()
}

class ProtectionWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ProtectionWidget()
}
