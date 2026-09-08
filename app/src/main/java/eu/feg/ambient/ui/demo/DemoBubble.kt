package eu.feg.ambient.ui.demo

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.feg.ambient.ambient.surfaces.DemoNotifier
import eu.feg.ambient.core.AppContainer
import eu.feg.ambient.ui.theme.LocalPskColors
import eu.feg.ambient.ui.theme.PskShapes
import kotlinx.coroutines.launch

/**
 * The stage button: a bubble that sends one of the app's two notifications on request.
 *
 * WHY IT LOOKS LIKE THIS. It is deliberately not a polished part of the product -- it is a
 * dark bubble with a bell on it, in the corner, present only in debug builds. A demo control
 * that blends into the app is one a presenter taps by accident and an audience mistakes for a
 * feature. This one should read as a remote control.
 *
 * The two doors are the two things the app has to say: something about a match the customer
 * follows, and something about a mission they have not finished. Both are written by the real
 * narrator and checked by NarratorGuard -- see DemoNotifier, which explains at length why
 * neither of them says "bet now".
 *
 * Pressing the same door twice gives a different sentence: the notifier rotates subject and
 * tone, so a judge who presses it three times sees three notifications rather than one
 * notification three times.
 */
@Composable
fun DemoBubble(container: AppContainer, modifier: Modifier = Modifier) {
    val psk = LocalPskColors.current
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier.padding(end = 4.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // The last headline sent, so the presenter can see it worked without leaving the app
        // and pulling the shade down mid-sentence.
        note?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = psk.textSecondary,
                modifier = Modifier
                    .width(220.dp)
                    .clip(PskShapes.card)
                    .background(psk.surface)
                    .border(1.dp, psk.surfaceRaised, PskShapes.card)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }

        AnimatedVisibility(
            visible = open,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
        ) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DemoOption(
                    label = "Match update",
                    description = "Send a notification about a match you follow",
                ) {
                    scope.launch {
                        note = DemoNotifier.general(container)
                            ?: "Nothing live to report right now."
                    }
                }
                DemoOption(
                    label = "Mission to earn",
                    description = "Send a notification about a mission you have not finished",
                ) {
                    scope.launch {
                        note = DemoNotifier.reward(container)
                            ?: "Every mission is already done."
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(psk.brandBlue)
                .border(1.dp, psk.textPrimary.copy(alpha = 0.25f), CircleShape)
                .semantics {
                    contentDescription =
                        if (open) "Close the demo notification menu" else "Demo notifications"
                }
                .clickable(role = Role.Button) {
                    open = !open
                    if (!open) note = null
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (open) Icons.Filled.Close else Icons.Filled.NotificationsActive,
                contentDescription = null,
                tint = psk.textPrimary,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/** One door. A wide target, because it is pressed on a stage with a phone in one hand. */
@Composable
private fun DemoOption(label: String, description: String, onClick: () -> Unit) {
    val psk = LocalPskColors.current
    Row(
        modifier = Modifier
            .clip(PskShapes.chip)
            .background(psk.surfaceRaised)
            .border(1.dp, psk.brandBlue.copy(alpha = 0.5f), PskShapes.chip)
            .semantics { contentDescription = description }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = psk.textPrimary,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}
