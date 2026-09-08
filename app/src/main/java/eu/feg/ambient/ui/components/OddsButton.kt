package eu.feg.ambient.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.Animatable
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import eu.feg.ambient.ui.theme.LocalPskColors
import eu.feg.ambient.ui.theme.PskShapes
import eu.feg.ambient.ui.theme.PskTextStyles
import eu.feg.ambient.ui.theme.PskTheme
import kotlinx.coroutines.delay

/** The three visual states an odds cell can be in (PRD section 3.4). */
enum class OddsState { DEFAULT, SELECTED, LOCKED }

/**
 * A one-shot tint of the button border when the price moves.
 * Phase 2's simulator drives this from the same flow that moves the odds.
 */
enum class OddsFlash { NONE, UP, DOWN }

private const val FLASH_MILLIS = 900

/**
 * The single most reused component in the app.
 *
 * [label] is free text on purpose: prematch rows show 1 / X / 2, live rows show team names
 * ("NK Lucko", "Draw", "Solin") and double chance shows 1X / 12 / X2 — all seen in the
 * screenshots. [value] arrives pre-formatted so the component stays free of number policy.
 *
 * [selectionLabel] is what a screen reader says instead of [label]: "Liverpool win" where
 * the cell draws "1". It defaults to [label], which is already spoken-friendly on live rows
 * and home cards; [MatchRow] expands 1 / X / 2 from the team names.
 */
@Composable
fun OddsButton(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    state: OddsState = OddsState.DEFAULT,
    isTop: Boolean = false,
    flash: OddsFlash = OddsFlash.NONE,
    selectionLabel: String? = null,
    onClick: () -> Unit = {},
) {
    val psk = LocalPskColors.current
    val locked = state == OddsState.LOCKED
    val selected = state == OddsState.SELECTED
    val reduced = reducedMotion()
    val interaction = remember { MutableInteractionSource() }

    val fill by animateColorAsState(
        targetValue = if (selected) psk.brandBlue else psk.oddsCell,
        // "Remove animations" means the fill changes on the frame it is asked to, not over one.
        animationSpec = if (reduced) snap<Color>() else spring<Color>(),
        label = "oddsFill",
    )

    // Border returns to transparent on its own, so a flash never leaves residue behind.
    val flashBorder = remember { Animatable(Color.Transparent) }
    LaunchedEffect(flash, value) {
        if (flash == OddsFlash.NONE) return@LaunchedEffect
        flashBorder.snapTo(if (flash == OddsFlash.UP) psk.positive else psk.negative)
        if (reduced) {
            // Held, then gone: the same signal without a fade for eyes that cannot follow one.
            delay(FLASH_MILLIS.toLong())
            flashBorder.snapTo(Color.Transparent)
        } else {
            flashBorder.animateTo(Color.Transparent, tween(FLASH_MILLIS))
        }
    }

    // "Liverpool win, 1.85" plus whatever the colours alone would have said.
    val description = buildString {
        append(selectionLabel ?: label)
        if (isNotEmpty()) append(", ")
        append(value)
        if (isTop) append(", top pick")
        when (flash) {
            OddsFlash.UP -> append(", odds up")
            OddsFlash.DOWN -> append(", odds down")
            OddsFlash.NONE -> Unit
        }
        if (locked) append(", locked")
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            // 44 dp in the screenshots; 48 dp is the accessible minimum and nothing in the
            // row is tight enough to notice the difference.
            .defaultMinSize(minHeight = 48.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                this.selected = selected
            }
            .clip(PskShapes.oddsButton)
            .background(fill)
            .border(1.dp, flashBorder.value, PskShapes.oddsButton)
            // A locked cell takes no ripple, because it takes no tap.
            .then(
                if (locked) {
                    Modifier
                } else {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = LocalIndication.current,
                        role = Role.Button,
                        onClick = onClick,
                    )
                },
            )
            .focusRing(interaction, PskShapes.oddsButton)
            .alpha(if (locked) 0.4f else 1f)
            .padding(horizontal = 6.dp, vertical = 5.dp),
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = label,
                style = PskTextStyles.oddsLabel,
                color = if (selected) psk.textPrimary else psk.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                // Keep clear of the TOP badge, which sits in the top-right corner.
                modifier = Modifier.padding(end = if (isTop) 22.dp else 0.dp),
            )
            Text(
                text = value,
                style = PskTextStyles.oddsValue,
                color = psk.textPrimary,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
        }

        // A green or red border is invisible to roughly one man in twelve, so the direction
        // is also drawn as an arrow. It fades with the border, so it leaves no residue either.
        if (flash != OddsFlash.NONE) {
            Text(
                text = if (flash == OddsFlash.UP) "▲" else "▼",
                style = PskTextStyles.oddsLabel,
                color = if (flash == OddsFlash.UP) psk.positive else psk.negative,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .alpha(flashBorder.value.alpha),
            )
        }

        if (isTop) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(PskShapes.oddsButton)
                    .background(psk.jackpotYellow)
                    .padding(horizontal = 3.dp),
            ) {
                Text(
                    text = "TOP",
                    style = PskTextStyles.oddsLabel,
                    color = psk.background,
                )
            }
        }
    }
}

@Preview(name = "Odds states", showBackground = true, backgroundColor = 0xFF0E0E10)
@Composable
private fun OddsButtonPreview() {
    PskTheme {
        Row(
            modifier = Modifier
                .width(340.dp)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            OddsButton("1", "6.50", Modifier.weight(1f), selectionLabel = "Ipswich win")
            OddsButton("X", "5.50", Modifier.weight(1f), state = OddsState.SELECTED)
            OddsButton("2", "1.50", Modifier.weight(1f), isTop = true)
            OddsButton("2", "1.45", Modifier.weight(1f), state = OddsState.LOCKED)
        }
    }
}

@Preview(name = "Odds flash", showBackground = true, backgroundColor = 0xFF0E0E10)
@Composable
private fun OddsButtonFlashPreview() {
    PskTheme {
        Row(
            modifier = Modifier
                .width(340.dp)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            OddsButton("NK Lucko", "6.00", Modifier.weight(1f), flash = OddsFlash.UP)
            OddsButton("Draw", "3.40", Modifier.weight(1f), flash = OddsFlash.DOWN)
            OddsButton("Solin", "1.50", Modifier.weight(1f))
        }
    }
}
