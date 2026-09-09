package eu.feg.ambient.ui.consent

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import eu.feg.ambient.ui.theme.LocalPskColors
import eu.feg.ambient.ui.theme.PskShapes

/**
 * The marketing consent question, asked once.
 *
 * ON THE WORDING, because this is the one dialog in the app where the temptation to cheat is
 * strongest and the cost of cheating is highest.
 *
 * The brief was to word it so the customer trusts it and accepts. Those are two different
 * goals and only one of them is available honestly, so this asks for the first and lets the
 * second follow or not. Concretely, none of the usual levers are pulled here:
 *
 *  - the buttons are the SAME SIZE, the same shape and the same weight. A large filled Accept
 *    beside a grey text Decline is the single most common consent dark pattern there is.
 *  - nothing is pre-selected, and there is no third "not now" that quietly means ask again.
 *  - no loss framing. Not "don't miss out", not "you may miss offers" -- the app's own
 *    NarratorGuard rejects that vocabulary in every other string it prints, and a consent
 *    dialog that used it would be the product arguing against itself in its own voice.
 *  - it says plainly what declining costs, which is nothing that matters.
 *
 * What it does instead is be SPECIFIC, because specificity is what actually earns a yes from
 * somebody who has been trained to refuse: what will arrive, how often, from whom, and how to
 * undo it. Vagueness reads as something to be hidden, and a customer who says yes to a vague
 * ask is one who will say no to everything afterwards.
 *
 * There is a legal floor under the design choice as well as a principled one. Consent under
 * GDPR has to be freely given, specific, informed and unambiguous; consent extracted by
 * interface pressure is not, which means the manipulative version would also be the version
 * that does not hold up. The honest dialog is the one whose yes is worth having.
 */
@Composable
fun MarketingConsentDialog(
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    val psk = LocalPskColors.current

    // Not dismissible by tapping outside or by back. Not to trap anybody -- both answers are
    // one tap away and equally easy -- but because a dismissal is not an answer, and treating
    // it as "no" would file a decision the customer never made.
    Dialog(onDismissRequest = { }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(PskShapes.card)
                .background(psk.surface)
                .border(1.dp, psk.surfaceRaised, PskShapes.card)
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Can we send you offers?",
                style = MaterialTheme.typography.titleLarge,
                color = psk.textPrimary,
                fontWeight = FontWeight.Bold,
            )

            Text(
                text = "Promotions, prize games and bonus offers from PSK. " +
                    "At most one a week, and never between 10pm and 9am.",
                style = MaterialTheme.typography.bodyMedium,
                color = psk.textPrimary,
            )

            // The three things a customer actually wants to know before answering, and the
            // three an operator is usually vaguest about.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Point("This is separate from your bets. Scores and results for slips you have placed keep coming either way.")
                Point("You can change it any time under Responsible gaming, in two taps.")
                Point("We never share your details with anyone else to advertise to you.")
            }

            Text(
                text = "If you say no, nothing else about the app changes.",
                style = MaterialTheme.typography.bodySmall,
                color = psk.textSecondary,
            )

            // EQUAL WEIGHT. Same height, same corner, same border, same type. The only
            // difference is the word, which is the only difference there should be.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ConsentButton(
                    label = "No thanks",
                    onClick = onDecline,
                    modifier = Modifier.weight(1f),
                )
                ConsentButton(
                    label = "Yes, send them",
                    onClick = onAccept,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** One reassurance, with a dot. Bulleted because three sentences in a row is a paragraph. */
@Composable
private fun Point(text: String) {
    val psk = LocalPskColors.current
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .padding(top = 7.dp)
                .size(5.dp)
                .clip(PskShapes.chip)
                .background(psk.textSecondary),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = psk.textSecondary,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

/**
 * Both answers, drawn identically.
 *
 * There is no `primary` parameter on purpose. A future edit that wanted to emphasise one side
 * would have to add it, which is a visible decision rather than a colour somebody changed.
 */
@Composable
private fun ConsentButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val psk = LocalPskColors.current
    Box(
        modifier = modifier
            .clip(PskShapes.card)
            .background(psk.surfaceRaised)
            .border(1.dp, psk.brandBlue, PskShapes.card)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = psk.textPrimary,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
