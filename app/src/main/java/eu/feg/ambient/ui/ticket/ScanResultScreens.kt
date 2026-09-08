package eu.feg.ambient.ui.ticket

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import eu.feg.ambient.ambient.ticket.TicketLookupResult
import eu.feg.ambient.data.model.BetSource
import eu.feg.ambient.data.model.BetStatus
import eu.feg.ambient.data.model.Leg
import eu.feg.ambient.data.model.LegStatus
import eu.feg.ambient.data.model.PlacedBet
import eu.feg.ambient.ui.theme.LocalPskColors
import eu.feg.ambient.ui.theme.PskShapes
import eu.feg.ambient.ui.theme.PskTheme
import kotlinx.datetime.Instant

/**
 * What the scanner shows once the lookup has answered. One composable per answer, each with
 * one obvious action, so the customer standing in a branch never has to read a menu.
 */
@Composable
internal fun ScanResultContent(
    result: TicketLookupResult,
    onTrack: () -> Unit,
    onOpenMyBets: () -> Unit,
    onEnterManually: () -> Unit,
    onScanAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (result) {
            is TicketLookupResult.Found -> {
                SlipSummary(result.bet, headline = "We found your slip")
                PrimaryAction("Track on my lock screen", onTrack)
                SecondaryAction("Not this one? Scan again", onScanAgain)
            }
            is TicketLookupResult.AlreadyTracked -> {
                Headline("You're already tracking this", "It is on your lock screen and in My bets.")
                PrimaryAction("Open My bets", onOpenMyBets)
                SecondaryAction("Scan another slip", onScanAgain)
            }
            is TicketLookupResult.Settled -> {
                // Finished before it was scanned, so there is nothing to follow: show, do not offer.
                SlipSummary(result.bet, headline = settledHeadline(result.bet.status))
                PrimaryAction("Scan another slip", onScanAgain)
            }
            TicketLookupResult.NotFound -> {
                Headline("We couldn't find that slip", "Check the code printed under the barcode.")
                PrimaryAction("Enter code manually", onEnterManually)
                SecondaryAction("Scan again", onScanAgain)
            }
            is TicketLookupResult.Invalid -> {
                Headline("That slip can't be tracked", result.reason)
                PrimaryAction("Try again", onScanAgain)
            }
        }
    }
}

@Composable
internal fun TrackedContent(onOpenMyBets: () -> Unit, onScanAgain: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Headline("On your lock screen", "You'll see each leg settle as the matches play. Nothing else will buzz.")
        PrimaryAction("Open My bets", onOpenMyBets)
        SecondaryAction("Scan another slip", onScanAgain)
    }
}

/**
 * The legs and their status, nothing else. No stake, no odds, no payout — deliberately. This
 * screen is the way a paper slip reaches the lock screen, and the lock screen is a surface for
 * moments, not money. Amounts stay where the customer goes to look for them: My bets.
 */
@Composable
private fun SlipSummary(bet: PlacedBet, headline: String) {
    val psk = LocalPskColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PskShapes.card)
            .background(psk.surfaceRaised)
            .padding(12.dp)
            // One TalkBack node that reads as a sentence, rather than n rows of "pending".
            .semantics(mergeDescendants = true) { contentDescription = spokenSummary(bet, headline) },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(headline, style = MaterialTheme.typography.titleMedium, color = psk.textPrimary)
        bet.legs.forEach { leg ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = leg.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = psk.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = legLabel(leg.status),
                    style = MaterialTheme.typography.labelMedium,
                    color = legColor(leg.status),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun Headline(title: String, body: String) {
    val psk = LocalPskColors.current
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.semantics(mergeDescendants = true) {},
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = psk.textPrimary)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = psk.textSecondary)
    }
}

/** The fallback when the camera is refused, and the path the demo takes when a venue is too dark. */
@Composable
internal fun ManualEntry(permissionDenied: Boolean, onLookUp: (String) -> Unit, onUseCamera: (() -> Unit)?) {
    val psk = LocalPskColors.current
    var text by remember { mutableStateOf("") }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = if (permissionDenied) "No camera, no problem. Type the code printed under the barcode." else "Enter ticket code",
            style = MaterialTheme.typography.titleMedium,
            color = psk.textPrimary,
        )
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            placeholder = { Text("e.g. PSK-2026-000123", color = psk.textSecondary) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onLookUp(text) }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = psk.textPrimary,
                unfocusedTextColor = psk.textPrimary,
                focusedContainerColor = psk.surfaceRaised,
                unfocusedContainerColor = psk.surfaceRaised,
                focusedBorderColor = psk.brandBlue,
                unfocusedBorderColor = psk.surfaceVariant,
                cursorColor = psk.brandBlue,
            ),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Ticket code" },
        )
        PrimaryAction("Look up", onClick = { onLookUp(text) })
        if (onUseCamera != null) SecondaryAction("Use the camera instead", onUseCamera)
    }
}

@Composable
internal fun LookingUp(code: String) {
    val psk = LocalPskColors.current
    Column(
        Modifier.fillMaxSize().semantics(mergeDescendants = true) {},
        Arrangement.Center,
        Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = psk.brandBlue, trackColor = psk.surfaceVariant, modifier = Modifier.size(36.dp))
        Text("Looking up " + code, color = psk.textSecondary, modifier = Modifier.padding(top = 12.dp))
    }
}

/** The blue full-width button the bet slip uses for PLACE BET, so it reads as the same app. */
@Composable
internal fun PrimaryAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val psk = LocalPskColors.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(PskShapes.card)
            .background(psk.brandBlue)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = psk.textPrimary)
    }
}

@Composable
internal fun SecondaryAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val psk = LocalPskColors.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clip(PskShapes.card)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = psk.textSecondary)
    }
}

private fun settledHeadline(status: BetStatus): String = when (status) {
    BetStatus.WON -> "This slip already won"
    BetStatus.LOST -> "This slip has already settled"
    BetStatus.VOID -> "This slip was voided"
    BetStatus.OPEN -> "This slip has settled"
}

private fun legLabel(status: LegStatus): String = when (status) {
    LegStatus.PENDING -> "Pending"
    LegStatus.WON -> "Won"
    LegStatus.LOST -> "Lost"
    LegStatus.VOID -> "Void"
}

@Composable
private fun legColor(status: LegStatus) = LocalPskColors.current.let { psk ->
    when (status) {
        LegStatus.PENDING -> psk.textSecondary
        LegStatus.WON -> psk.positive
        LegStatus.LOST -> psk.negative
        LegStatus.VOID -> psk.textSecondary
    }
}

private fun spokenSummary(bet: PlacedBet, headline: String): String {
    val legs = bet.legs.joinToString(", ") { it.description + " " + legLabel(it.status).lowercase() }
    return headline + ". " + bet.legs.size + " legs: " + legs + "."
}

internal val sampleScannedBet = PlacedBet(
    id = "retail-demo",
    legs = listOf(
        Leg("m1", "Dinamo Zagreb — 1", 1.45),
        Leg("m2", "Hajduk Split — Over 2.5", 1.90, LegStatus.WON),
        Leg("m3", "Rijeka — X", 3.20),
    ),
    stake = 5.0,
    totalOdds = 8.81,
    placedAt = Instant.fromEpochSeconds(1_757_000_000),
    source = BetSource.RETAIL,
)

@Preview(showBackground = true, backgroundColor = 0xFF0E0E10)
@Composable
private fun FoundPreview() {
    PskTheme { ScanResultContent(TicketLookupResult.Found(sampleScannedBet), {}, {}, {}, {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFF0E0E10)
@Composable
private fun NotFoundPreview() {
    PskTheme { ScanResultContent(TicketLookupResult.NotFound, {}, {}, {}, {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFF0E0E10)
@Composable
private fun TrackedPreview() {
    PskTheme { TrackedContent({}, {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFF0E0E10)
@Composable
private fun ManualEntryPreview() {
    PskTheme { ManualEntry(permissionDenied = true, onLookUp = {}, onUseCamera = null) }
}
