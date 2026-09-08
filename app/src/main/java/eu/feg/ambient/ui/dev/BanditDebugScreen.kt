package eu.feg.ambient.ui.dev

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.feg.ambient.ambient.engine.router.Arms
import eu.feg.ambient.ambient.engine.router.RewardTable
import eu.feg.ambient.ui.components.PskChip
import eu.feg.ambient.ui.theme.LocalPskColors
import eu.feg.ambient.ui.theme.PskShapes
import java.util.Locale

/**
 * Step 13 Prompt 4. The learning, made watchable.
 *
 * Everything here is sized for a room, not for a desk: six bars rather than all forty-nine,
 * each one tall enough and labelled with words rather than an enum triple. The pre-seed marker
 * is drawn on every bar because "it moved" only means something if you can see where it
 * started — that is the demo beat the priors in [RewardTable] were measured to produce.
 */
@Composable
fun BanditDebugScreen(viewModel: EngineLabViewModel, modifier: Modifier = Modifier) {
    val psk = LocalPskColors.current
    val state by viewModel.bandit.collectAsStateWithLifecycle()
    val feedback by viewModel.feedback.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(psk.background),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "title") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "How it learns",
                    style = MaterialTheme.typography.titleLarge,
                    color = psk.textPrimary,
                )
                Text(
                    text = "Every notification is a guess about what is worth your attention. " +
                        "Opening one says yes, swiping it away says no, and coming back on " +
                        "your own after we stayed quiet says the silence was right.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = psk.textSecondary,
                )
            }
        }

        // The gestures themselves, above the bars. The bars say where the router has got to;
        // this says how it got there, and it is the half a judge can check against what they
        // just did with their thumb.
        item(key = "feedback") {
            DevCard("What you have taught it") {
                if (feedback.isEmpty()) {
                    Text(
                        text = "Nothing yet. Open or swipe away a notification and it appears here.",
                        style = MaterialTheme.typography.labelSmall,
                        color = psk.textSecondary,
                    )
                } else {
                    feedback.forEach { row ->
                        DevRow(
                            label = row.gesture + " · " + humanArm(row.tone),
                            value = (if (row.reward > 0) "+" else "") + format(row.reward),
                        )
                    }
                }
            }
        }

        item(key = "context") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Context bucket",
                    style = MaterialTheme.typography.labelMedium,
                    color = psk.textSecondary,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(EngineLabViewModel.CONTEXTS.size, key = { it }) { index ->
                        val context = EngineLabViewModel.CONTEXTS[index]
                        PskChip(
                            label = context,
                            selected = context == state.context,
                        ) { viewModel.selectContext(context) }
                    }
                }
                Text(
                    text = "Counters are kept per bucket. Nothing learned here leaks into another.",
                    style = MaterialTheme.typography.labelSmall,
                    color = psk.textSecondary,
                )
            }
        }

        item(key = "preseed") {
            DevCard("Cold start") {
                DevRow(
                    label = "Pre-seed Beta(wins, losses)",
                    value = format(RewardTable.PRIOR_WINS) + ", " + format(RewardTable.PRIOR_LOSSES),
                )
                DevRow(
                    label = "Every arm starts at",
                    value = format(EngineLabViewModel.PRE_SEED_MEAN),
                )
                DevRow(
                    label = "One gesture moves it by",
                    value = format(RewardTable.THUMBS_UP),
                )
                Text(
                    text = "Measured, not guessed: this prior is the one where an incumbent arm " +
                        "visibly flips after exactly two gestures.",
                    style = MaterialTheme.typography.labelSmall,
                    color = psk.textSecondary,
                )
            }
        }

        item(key = "gestures") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DevAction(
                    label = "👍 reward top arm",
                    modifier = Modifier.weight(1f),
                ) { viewModel.rewardTopArm(up = true) }
                DevAction(
                    label = "👎 punish top arm",
                    modifier = Modifier.weight(1f),
                ) { viewModel.rewardTopArm(up = false) }
            }
        }

        item(key = "bars-header") {
            Text(
                text = "Top " + EngineLabViewModel.TOP_ARMS + " of " + Arms.allArms().size + " arms",
                style = MaterialTheme.typography.titleMedium,
                color = psk.textPrimary,
            )
        }

        items(state.bars.size, key = { state.bars[it].armId }) { index ->
            ArmBarRow(bar = state.bars[index], leading = index == 0)
        }

        state.lastGesture?.let { gesture ->
            item(key = "gesture-notice") {
                Text(
                    text = gesture,
                    style = MaterialTheme.typography.labelMedium,
                    color = psk.textSecondary,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

/**
 * One arm. The bar is drawn against a full-width 0..1 track rather than normalised to the
 * leader, so a bar that grows really did grow — a relative scale would show movement even
 * when every arm moved together.
 */
@Composable
private fun ArmBarRow(bar: ArmBar, leading: Boolean) {
    val psk = LocalPskColors.current
    val fraction by animateFloatAsState(
        targetValue = bar.mean.toFloat().coerceIn(0.02f, 1f),
        label = "armMean",
    )

    Column(
        Modifier
            .fillMaxWidth()
            .clip(PskShapes.card)
            .background(psk.surface)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = humanArm(bar.armId),
                style = MaterialTheme.typography.titleMedium,
                color = if (leading) psk.jackpotYellow else psk.textPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                maxLines = 2,
            )
            Text(
                text = format(bar.mean),
                style = MaterialTheme.typography.headlineSmall,
                color = if (leading) psk.jackpotYellow else psk.textPrimary,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(BAR_HEIGHT)
                .clip(PskShapes.oddsButton)
                .background(psk.surfaceRaised),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .clip(PskShapes.oddsButton)
                    .background(if (leading) psk.jackpotYellow else psk.brandBlue),
            )
            // The pre-seed marker: a zero-width column ending exactly where every arm began.
            Box(
                Modifier
                    .fillMaxWidth(EngineLabViewModel.PRE_SEED_MEAN.toFloat())
                    .fillMaxHeight(),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(
                    Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(psk.textPrimary),
                )
            }
        }

        Text(
            text = "wins " + format(bar.wins) + " · losses " + format(bar.losses) +
                " · start " + format(EngineLabViewModel.PRE_SEED_MEAN),
            style = MaterialTheme.typography.labelMedium,
            color = psk.textSecondary,
            fontFamily = FontFamily.Monospace,
        )
    }
}

/**
 * "LIVE_UPDATE|WITTY|IMMEDIATE" reads as noise across a room. The words are the same three
 * facts, in the order a person would say them.
 */
private fun humanArm(armId: String): String {
    if (armId == Arms.NOTHING_ID) return "Stay silent"
    val parts = armId.split("|")
    if (parts.size < 3) return armId
    val where = when (parts[0]) {
        "LIVE_UPDATE" -> "Lock screen"
        "WIDGET" -> "Widget"
        "IN_APP" -> "In-app"
        "ALERT" -> "Alert"
        else -> parts[0]
    }
    val voice = parts[1].lowercase(Locale.US).replace('_', ' ')
    val timing = when (parts[2]) {
        "IMMEDIATE" -> "now"
        "NEXT_BREAK" -> "at the break"
        "END_OF_MATCH" -> "full time"
        else -> parts[2].lowercase(Locale.US)
    }
    return where + " · " + voice + " · " + timing
}

private fun format(value: Double): String = String.format(Locale.US, "%.2f", value)

private val BAR_HEIGHT = 28.dp
