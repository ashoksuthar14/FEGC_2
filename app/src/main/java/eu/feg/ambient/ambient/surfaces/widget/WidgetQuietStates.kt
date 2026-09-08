package eu.feg.ambient.ambient.surfaces.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.height
import androidx.glance.layout.width
import androidx.glance.text.Text
import eu.feg.ambient.MainActivity
import eu.feg.ambient.ambient.surfaces.ProtectionState
import eu.feg.ambient.ambient.surfaces.WidgetState
import kotlinx.datetime.Instant

/**
 * The four states where nothing is happening, which is most of the time.
 *
 * They are in their own file because they are the ones that decide whether the widget earns
 * its place on a home screen. A card that is blank between slips gets removed, and a removed
 * widget cannot be the fallback path to the customer when notifications are denied.
 */

/** "While you were away" — the catch-up, not a nudge to bet. */
@Composable
internal fun DigestCard(state: WidgetState.Digest, feedback: FeedbackMark? = null) {
    WidgetCard(
        description = "While you were away. " + state.headline + ". " + state.detail,
        onClick = openRoute(ROUTE_MY_BETS),
    ) {
        Text(text = "WHILE YOU WERE AWAY", style = WidgetText.label, maxLines = 1)
        Spacer(GlanceModifier.height(6.dp))
        Text(text = state.headline, style = WidgetText.title, maxLines = 2)
        Spacer(GlanceModifier.height(4.dp))
        // Two lines, not three: a digest that needs a paragraph has stopped being a digest.
        Text(text = state.detail, style = WidgetText.meta, maxLines = 2)
        Spacer(GlanceModifier.defaultWeight())
        ActionRow(muteMatch = null, given = feedback)
    }
}

/**
 * Nothing in flight.
 *
 * With a followed team it shows the next fixture; without one it says how to make the widget
 * useful. It never invents a reason to open the app — an idle widget that advertises is the
 * fastest way to have the widget removed.
 */
@Composable
internal fun IdleCard(state: WidgetState.Idle) {
    val fixture = state.nextFixture
    WidgetCard(
        description = if (fixture != null) "Next up, " + fixture else FOLLOW_PROMPT,
        onClick = openRoute(ROUTE_LIVE),
    ) {
        if (fixture != null) {
            // The quiet day is where N6 earns its place: a big crest and a fixture, which is
            // a card a fan keeps on a home screen. The old version said "Next up" in 11sp
            // grey and was indistinguishable from an empty widget at a glance.
            Text(text = "NEXT UP", style = WidgetText.label, maxLines = 1)
            Spacer(GlanceModifier.height(8.dp))
            Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                WidgetCrest(size = 44.dp)
                Spacer(GlanceModifier.width(10.dp))
                Column {
                    Text(text = fixture, style = WidgetText.title, maxLines = 2)
                    state.kickoff?.let {
                        Text(text = untilLabel(it), style = WidgetText.meta, maxLines = 1)
                    }
                }
            }
        } else {
            Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                WidgetCrest(size = 32.dp)
                Spacer(GlanceModifier.width(8.dp))
                Text(text = "NO CLUB YET", style = WidgetText.label, maxLines = 1)
            }
            Spacer(GlanceModifier.height(8.dp))
            Text(text = FOLLOW_PROMPT, style = WidgetText.body, maxLines = 3)
        }
        Spacer(GlanceModifier.defaultWeight())
    }
}

/**
 * Protection is showing, so the widget shows protection and nothing else.
 *
 * The three cases differ in kind, not in degree, which is why they are not one card with a
 * changing subtitle:
 *  - CALM       the customer is still betting, so a way out has to be one tap away.
 *  - UNVERIFIED we do not yet know who this is. No match, no odds, no money, no invitation —
 *               a neutral card that leads to verification and nowhere else.
 *  - BLOCKED    the account is closed to play. The only affordance is help.
 *
 * Note that a CALM slip with a score is drawn by CalmSlipCard instead: WidgetState.Protected
 * carries no slip, so when it is the state we have, there is no score to show and we do not
 * pretend otherwise.
 */
@Composable
internal fun ProtectedCard(protection: ProtectionState, lastRegisterCheck: Instant?) {
    when (protection) {
        ProtectionState.UNVERIFIED -> WidgetCard(
            description = "Verify your account to continue.",
            onClick = actionStartActivity<MainActivity>(),
        ) {
            Text(text = "Verify to continue", style = WidgetText.title, maxLines = 2)
            Spacer(GlanceModifier.height(6.dp))
            Text(
                text = "We need to confirm your details before this can show anything.",
                style = WidgetText.body,
                maxLines = 4,
            )
        }

        ProtectionState.BLOCKED -> WidgetCard(description = "Your account is protected.") {
            Text(text = "Account protected", style = WidgetText.title, maxLines = 2)
            Spacer(GlanceModifier.height(6.dp))
            Text(text = "Play is paused on this account.", style = WidgetText.body, maxLines = 3)
            Spacer(GlanceModifier.defaultWeight())
            WidgetActionButton(
                label = "Get help",
                description = "Open support and responsible gambling help",
                action = actionRunCallback<PanicAction>(),
                fill = WidgetTokens.surfaceRaised,
            )
        }

        ProtectionState.CALM -> WidgetCard(
            description = "Protection active, register checked " + agoLabel(lastRegisterCheck),
        ) {
            Text(text = "Protection active", style = WidgetText.title, maxLines = 2)
            Spacer(GlanceModifier.height(6.dp))
            Text(
                text = "Register checked " + agoLabel(lastRegisterCheck),
                style = WidgetText.meta,
                maxLines = 2,
            )
            Spacer(GlanceModifier.defaultWeight())
            WidgetActionButton(
                label = "Take a break",
                description = "Open protection tools and take a break",
                action = actionRunCallback<PanicAction>(),
                fill = WidgetTokens.surfaceRaised,
            )
        }

        // NORMAL should never reach here — the controller only sends Protected when it is not
        // normal. Rendering the neutral card rather than throwing keeps a wiring mistake from
        // becoming a crash on someone's home screen.
        ProtectionState.NORMAL -> WidgetCard(description = "Nothing to show right now.") {
            Text(text = FOLLOW_PROMPT, style = WidgetText.body, maxLines = 3)
        }
    }
}

/**
 * Deliberately not "Follow a team to see it here": following is step 16's work and there is no
 * button for it yet. Copy that sends someone hunting for a control the app does not have reads
 * as a broken app, not a forthcoming feature. Placing a bet is the thing that actually makes
 * this widget come alive today, so that is what it says.
 */
internal const val FOLLOW_PROMPT = "Place a bet and it will show up here"
