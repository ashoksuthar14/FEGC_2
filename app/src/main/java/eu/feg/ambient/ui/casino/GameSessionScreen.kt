package eu.feg.ambient.ui.casino

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.feg.ambient.core.AppContainer
import eu.feg.ambient.ui.components.PskChip
import eu.feg.ambient.ui.theme.LocalPskColors
import eu.feg.ambient.ui.theme.PskShapes

/**
 * What a tapped game tile opens.
 *
 * NO GAME IS PLAYED HERE, and the PRD is explicit that none ever will be: this is a replica,
 * and a working slot machine is neither buildable in a hackathon nor something anybody should
 * want in one. What the screen does instead is run the SESSION, which is the part this product
 * has something to say about.
 *
 * The clock on it is not decoration. It is the same count the engine is watching, and at the
 * customer's own reality-check interval the engine raises a SESSION_LENGTH moment that goes
 * through protection, scoring, the router, the guard and the ledger exactly as a goal does.
 * That is the whole demonstration: same machinery, other vertical, and what comes out is a
 * note about time rather than an invitation to keep going.
 */
@Composable
fun GameSessionScreen(
    container: AppContainer,
    gameId: String,
    modifier: Modifier = Modifier,
    onLeave: () -> Unit = {},
) {
    val psk = LocalPskColors.current
    val session by container.gameSessionTracker.session.collectAsStateWithLifecycle()
    val game = container.source.casinoGames().firstOrNull { it.id == gameId }

    // The session is the screen's lifetime. Leaving ends it, because leaving IS the end of it
    // -- and a tracker that kept counting after the customer walked away would report a
    // session length that never happened.
    DisposableEffect(gameId) {
        container.gameSessionTracker.start(container.appScope, gameId, game?.name)
        onDispose { container.gameSessionTracker.stop() }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(psk.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = game?.name ?: "Game",
            style = MaterialTheme.typography.titleLarge,
            color = psk.textPrimary,
        )
        game?.provider?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = psk.textSecondary,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(PskShapes.card)
                .background(psk.surface)
                .border(1.dp, psk.surfaceRaised, PskShapes.card)
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "This is a replica. No game runs here.",
                style = MaterialTheme.typography.bodyMedium,
                color = psk.textSecondary,
            )
        }

        SessionCard(minutes = session.minutes, every = container.userStateRepository
            .state.value.realityCheckMinutes)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PskChip("Leave the game", onClick = onLeave)
        }
    }
}

/**
 * The clock, and what it is for.
 *
 * The number is set large for the same reason the score is large on the live card: it is the
 * one fact the surface exists to carry. The line under it says when the app will speak, which
 * is the opposite of how a session timer usually behaves -- most are hidden behind a settings
 * screen, and the ones that are not rarely tell you what they are going to do.
 */
@Composable
private fun SessionCard(minutes: Int, every: Int) {
    val psk = LocalPskColors.current
    val next = (every - (minutes % every.coerceAtLeast(1))).coerceAtLeast(0)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PskShapes.card)
            .background(psk.surface)
            .border(1.dp, psk.positive.copy(alpha = 0.5f), PskShapes.card)
            .padding(16.dp)
            .semantics {
                contentDescription = "You have been playing for " + minutes + " minutes. " +
                    "The next reality check is in " + next + " minutes."
            },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "Playing for",
            style = MaterialTheme.typography.labelMedium,
            color = psk.textSecondary,
        )
        Text(
            text = minutes.toString() + (if (minutes == 1) " minute" else " minutes"),
            style = MaterialTheme.typography.headlineMedium,
            color = psk.textPrimary,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "We will tell you again in " + next +
                (if (next == 1) " minute" else " minutes") +
                ". You can change that under Responsible gaming.",
            style = MaterialTheme.typography.labelSmall,
            color = psk.textSecondary,
        )
    }
}
