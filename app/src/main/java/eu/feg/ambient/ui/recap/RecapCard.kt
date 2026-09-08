package eu.feg.ambient.ui.recap

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import eu.feg.ambient.ambient.recap.Recap
import eu.feg.ambient.ambient.recap.RecapPeriod
import eu.feg.ambient.ui.theme.LocalPskColors
import eu.feg.ambient.ui.theme.PskShapes
import eu.feg.ambient.ui.theme.PskTheme
import kotlinx.datetime.Instant

/**
 * N5 in the app: the recap as a card in the Moments inbox, with the two things the widget
 * cannot offer — a share sheet and a proper touch target for the speaker.
 *
 * The counts are laid out as three big numbers because that is the shape people share: a
 * Wrapped card is a poster, not a paragraph. The narrator's detail sits under them as the
 * sentence version of the same facts, and it is the sentence that goes into the share sheet,
 * because a screenshot of numbers with no words is a screenshot nobody understands.
 *
 * NO MONEY. [Recap] cannot carry a stake, a return or a balance, so this card cannot show
 * one, which is what makes it safe to hand to a share sheet. Every colour is a PskColors
 * token; the Material Button is coloured explicitly rather than trusted to the scheme.
 */
@Composable
fun RecapCard(
    recap: Recap,
    onSpeak: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val psk = LocalPskColors.current
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(PskShapes.card)
            .background(psk.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = periodLabel(recap.period),
                    style = MaterialTheme.typography.labelSmall,
                    color = psk.textSecondary,
                )
                Text(
                    text = recap.headline,
                    style = MaterialTheme.typography.titleLarge,
                    color = psk.textPrimary,
                )
            }
            // 48 dp, because a control a screen-reader user reaches for must be reachable by
            // someone whose aim is not perfect either. Same idiom as the "Why this?" rows.
            Text(
                text = "🔊",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .size(48.dp)
                    .clip(PskShapes.chip)
                    .clickable(onClickLabel = SPEAK_LABEL, onClick = onSpeak)
                    .semantics { contentDescription = SPEAK_LABEL }
                    .wrapContentSize(),
            )
        }

        // One node for TalkBack. Three numbers with three captions read as six fragments;
        // the narrator's spoken line reads as the sentence they add up to.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) { contentDescription = recap.spokenText },
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Stat(value = recap.matchesFollowed, label = "matches followed")
            // A prediction is a leg outcome, never money. Shown as "of N" so a strong month
            // and a quiet one look different, which a bare count would hide.
            Stat(
                value = recap.predictionsRight,
                label = if (recap.predictionsTotal > 0) {
                    "of " + recap.predictionsTotal + " predictions right"
                } else {
                    "predictions right"
                },
            )
            Stat(value = recap.longestStreak, label = "day streak")
        }

        Text(
            text = recap.detail,
            style = MaterialTheme.typography.bodyMedium,
            color = psk.textPrimary,
        )

        Button(
            onClick = { share(context, recap) },
            colors = ButtonDefaults.buttonColors(
                containerColor = psk.brandBlue,
                contentColor = psk.textPrimary,
            ),
            shape = PskShapes.chip,
        ) {
            Text(text = "Share", style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** A big number and its caption. The number is tabular so a row of three lines up. */
@Composable
private fun RowScope.Stat(value: Int, label: String) {
    val psk = LocalPskColors.current
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.Bold,
            color = psk.textPrimary,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = psk.textSecondary,
        )
    }
}

private fun periodLabel(period: RecapPeriod): String = when (period) {
    RecapPeriod.MONTH -> "THE LAST 30 DAYS"
    RecapPeriod.SEASON -> "THE SEASON SO FAR"
}

/**
 * Plain text into the system share sheet: the headline and the narrator's sentence, nothing
 * else. Text rather than an image because text is what every target accepts, and because the
 * text has already passed the narrator's guard — an image rendered later would not have.
 */
private fun share(context: android.content.Context, recap: Recap) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, recap.headline)
        putExtra(Intent.EXTRA_TEXT, recap.headline + "\n" + recap.detail)
    }
    // The chooser never throws for a missing target, but a Context that is not an Activity
    // can; a share button that crashes the inbox is worse than one that does nothing.
    runCatching { context.startActivity(Intent.createChooser(send, "Share your recap")) }
}

private const val SPEAK_LABEL = "Read this recap out loud"

/** A recap shaped like the one the spec quotes, for previews and the Surface Lab. */
internal fun sampleRecap(period: RecapPeriod = RecapPeriod.MONTH): Recap = Recap(
    period = period,
    from = Instant.fromEpochMilliseconds(1_756_000_000_000L),
    to = Instant.fromEpochMilliseconds(1_758_600_000_000L),
    matchesFollowed = 38,
    teamsFollowed = listOf("Sparta", "Liverpool", "Betis"),
    topTeam = "Sparta",
    topTeamCount = 6,
    predictionsRight = 21,
    predictionsTotal = 30,
    checkIns = 44,
    longestStreak = 5,
    headline = "Your month with PSK",
    detail = "38 matches followed, 21 predictions right, Sparta, every single week, " +
        "checked in 5 days running.",
    spokenText = "Your month with PSK. 38 matches followed, 21 predictions right, Sparta, " +
        "every single week, checked in 5 days running.",
)

@Preview(name = "Recap card")
@Preview(name = "Recap card · 200% font", fontScale = 2.0f)
@Composable
private fun RecapCardPreview() {
    PskTheme {
        // The app ground comes from the token, not from a preview annotation literal:
        // nothing outside Color.kt declares a colour, previews included.
        Column(Modifier.background(LocalPskColors.current.background).padding(12.dp)) {
            RecapCard(recap = sampleRecap(), onSpeak = {})
        }
    }
}
