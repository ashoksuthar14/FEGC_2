package eu.feg.ambient.ui.dev

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.feg.ambient.ambient.narrator.NarratorLanguage
import eu.feg.ambient.ambient.narrator.Tone
import eu.feg.ambient.ambient.surfaces.ProtectionState
import eu.feg.ambient.ambient.surfaces.notifications.NotificationPermission
import eu.feg.ambient.ui.components.PskChip
import eu.feg.ambient.ambient.recap.RecapPeriod
import eu.feg.ambient.ui.components.MyClubRow
import eu.feg.ambient.ui.theme.LocalPskColors
import eu.feg.ambient.ui.theme.PskShapes
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Step 14D. The manual control room for every OS surface: it exists so the surfaces can be
 * demonstrated without waiting for a real match, and so there is a hand-driven fallback on
 * stage if the simulator misbehaves.
 *
 * Every button below calls SurfaceController and nothing else. No renderer is reachable from
 * here, which is what lets step 13B's Moment Engine take over by calling the same methods.
 */
@Composable
fun SurfaceLabScreen(viewModel: SurfaceLabViewModel, modifier: Modifier = Modifier) {
    val psk = LocalPskColors.current
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val diagnostics by viewModel.diagnostics.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.refreshDiagnostics() }

    // Also re-read on resume, so returning from the system settings screen tells the truth.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshDiagnostics()
        onPauseOrDispose { }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(psk.background),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "title") {
            Text("Surface Lab", style = MaterialTheme.typography.titleLarge, color = psk.textPrimary)
        }

        item(key = "diagnostics") {
            LabCard("Diagnostics") {
                DiagRow(
                    label = "Notification permission",
                    value = if (diagnostics.notificationPermissionGranted) "granted" else "denied",
                    good = diagnostics.notificationPermissionGranted,
                    action = if (diagnostics.notificationPermissionGranted) null else "Request",
                    onAction = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            viewModel.refreshDiagnostics()
                        }
                    },
                )
                DiagRow(
                    label = "canPostPromotedNotifications()",
                    value = diagnostics.canPostPromoted.toString(),
                    good = diagnostics.canPostPromoted,
                    action = if (diagnostics.canPostPromoted) null else "Open settings",
                    onAction = { openPromotedSettings(context) },
                )
                DiagRow(
                    label = "Last post was promoted",
                    value = when (diagnostics.lastPostWasPromoted) {
                        true -> "yes"
                        false -> "no"
                        null -> "unknown"
                    },
                    good = diagnostics.lastPostWasPromoted,
                )
                DiagRow(
                    label = "Alerts sent today",
                    value = diagnostics.alertsSentToday.toString() + " / " + diagnostics.alertBudget,
                    good = diagnostics.alertsSentToday < diagnostics.alertBudget,
                )
                DiagRow(
                    label = "Active Live Update",
                    value = diagnostics.activeLiveUpdateSlipId ?: "none",
                    good = null,
                )
                diagnostics.lastError?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = psk.negative)
                }
            }
        }

        item(key = "builder") {
            LabCard("Slip builder") {
                Picker("Slip") {
                    items(DemoSlip.entries.toList(), key = { it.name }) { demo ->
                        PskChip(demo.label, selected = state.demo == demo) { viewModel.selectSlip(demo) }
                    }
                }
                Picker("Protection") {
                    items(ProtectionState.entries.toList(), key = { it.name }) { protection ->
                        PskChip(protection.name, selected = state.protection == protection) {
                            viewModel.setProtection(protection)
                        }
                    }
                }
                Picker("Tone") {
                    items(Tone.entries.toList(), key = { it.name }) { tone ->
                        PskChip(tone.name, selected = state.tone == tone) { viewModel.setTone(tone) }
                    }
                }
                Picker("Language") {
                    items(NarratorLanguage.entries.toList(), key = { it.name }) { language ->
                        PskChip(language.name, selected = state.language == language) {
                            viewModel.setLanguage(language)
                        }
                    }
                }
                Text(
                    text = "Chip: " + state.slip.chipText + " · " + state.slip.legsWon + " won, " +
                        state.slip.legsLost + " lost of " + state.slip.legsTotal,
                    style = MaterialTheme.typography.labelSmall,
                    color = psk.textSecondary,
                )
            }
        }

        item(key = "live") {
            LabCard("Live Update") {
                ActionGrid(
                    listOf(
                        "Start" to viewModel::start,
                        "+1 minute" to viewModel::advanceMinute,
                        "Home goal" to viewModel::homeGoal,
                        "Away goal" to viewModel::awayGoal,
                        "Win next leg" to viewModel::winNextLeg,
                        "Lose next leg" to viewModel::loseNextLeg,
                        "Settle slip" to viewModel::settleSlip,
                        "End / dismiss" to viewModel::endLiveUpdate,
                    ),
                )
                NarrationPanel(state)
            }
        }

        item(key = "widget") {
            LabCard("Widget") {
                ActionGrid(
                    buildList<Pair<String, () -> Unit>> {
                        WidgetKind.entries.forEach { kind ->
                            add(kind.label to { viewModel.showWidget(kind) })
                        }
                        add("Force refresh" to { viewModel.forceRefreshWidget() })
                    },
                )
                Text(
                    text = "Last widget update: " +
                        (diagnostics.lastWidgetUpdate?.clockTime() ?: "never") +
                        (state.widgetKind?.let { " · " + it.label } ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = psk.textSecondary,
                )
            }
        }

        item(key = "alerts") {
            LabCard("Alerts") {
                ActionGrid(
                    listOf(
                        "Send settlement alert" to viewModel::sendSettlementAlert,
                        "Reset budget (demo only)" to viewModel::resetAlertBudget,
                    ),
                    perRow = 2,
                )
                state.alertNotice?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (it.startsWith("Budget spent")) psk.negative else psk.positive,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        item(key = "digest") {
            LabCard("While you were away (step 16)") {
                ActionGrid(
                    listOf(
                        "Simulate 90 min away" to { viewModel.simulateAway(90) },
                        "Build digest now" to viewModel::buildDigestNow,
                    ),
                    perRow = 2,
                )
                Text(
                    text = state.digestNotice
                        ?: "The widget is the trigger — it redraws with no process of ours alive.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = psk.textSecondary,
                )
            }
        }

        item(key = "loyalty") {
            LabCard("Missions and badges (N7)") {
                ActionGrid(
                    listOf(
                        "Complete next mission" to { viewModel.completeNextMission() },
                        "Follow two more clubs" to { viewModel.followTwoMoreClubs() },
                        "Grant 5 badges" to { viewModel.grantFiveBadges() },
                        "Reality check now" to { viewModel.realityCheckNow() },
                        "Ask consent again" to { viewModel.askMarketingAgain() },
                        "Reset loyalty" to { viewModel.resetLoyalty() },
                    ),
                    perRow = 2,
                )
                Text(
                    text = state.loyaltyNotice
                        ?: "Completion runs the real path: the tracker awards the badge and " +
                        "the engine decides whether it is worth a surface.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = psk.textSecondary,
                )
            }
        }

        item(key = "recap") {
            LabCard("Season recap (N5)") {
                ActionGrid(
                    listOf(
                        "Generate recap (month)" to { viewModel.generateRecap(RecapPeriod.MONTH) },
                        "Generate recap (season)" to { viewModel.generateRecap(RecapPeriod.SEASON) },
                    ),
                    perRow = 2,
                )
                Text(
                    text = state.recapNotice ?: "Counts only. No money on it, in any state.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = psk.textSecondary,
                )
            }
        }

        item(key = "club") {
            LabCard("My club (N6)") {
                val club by viewModel.myClubTheme.collectAsStateWithLifecycle()
                MyClubRow(selected = club, onSelect = viewModel::setMyClub)
                Text(
                    text = "Cosmetic only. UNVERIFIED and BLOCKED fall back to PSK blue.",
                    style = MaterialTheme.typography.labelSmall,
                    color = psk.textSecondary,
                )
            }
        }

        item(key = "speak") {
            LabCard("Spoken moment") {
                ActionGrid(
                    listOf("Speak current moment" to viewModel::speakCurrentMoment),
                    perRow = 2,
                )
                Text(
                    text = state.speakNotice ?: "On-device voice only. Never plays by itself.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state.speakNotice?.startsWith("Spoken") == true) {
                        psk.positive
                    } else {
                        psk.textSecondary
                    },
                )
            }
        }

        item(key = "shortcuts") {
            LabCard("Shortcuts") {
                ActionGrid(
                    listOf(
                        "Refresh shortcuts" to viewModel::refreshShortcuts,
                        "Rebuild shortcuts" to viewModel::rebuildShortcuts,
                    ),
                    perRow = 2,
                )
                state.shortcutNotice?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = psk.textSecondary)
                }
            }
        }

        item(key = "last") {
            Text(
                text = "Last action: " + state.lastAction + (if (state.running) " · working…" else ""),
                style = MaterialTheme.typography.labelSmall,
                color = psk.textSecondary,
            )
        }
    }
}

/** The generated text plus the engine badge — which must be the engine that really ran. */
@Composable
private fun NarrationPanel(state: SurfaceLabUiState) {
    val psk = LocalPskColors.current
    val narrated = state.narrated
    Column(
        Modifier
            .fillMaxWidth()
            .clip(PskShapes.oddsButton)
            .background(psk.surfaceRaised)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (narrated == null) {
            Text(
                text = "No narration yet — press Start.",
                style = MaterialTheme.typography.bodyMedium,
                color = psk.textSecondary,
            )
            return@Column
        }
        Text(narrated.headline, style = MaterialTheme.typography.titleMedium, color = psk.textPrimary)
        Text(narrated.detail, style = MaterialTheme.typography.bodyMedium, color = psk.textSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier
                    .clip(PskShapes.chip)
                    .background(psk.brandBlue)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(
                    text = narrated.engine.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = psk.textPrimary,
                )
            }
            Text(
                text = narrated.latencyMs.toString() + " ms",
                style = MaterialTheme.typography.labelSmall,
                color = psk.textSecondary,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun LabCard(title: String, content: @Composable () -> Unit) {
    val psk = LocalPskColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(PskShapes.card)
            .background(psk.surface)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = psk.textPrimary)
        content()
    }
}

/**
 * One diagnostics line. [good] is nullable because "last post was promoted" is genuinely
 * unknown until something has been posted, and a green tick there would be a lie.
 */
@Composable
private fun DiagRow(
    label: String,
    value: String,
    good: Boolean?,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    val psk = LocalPskColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = psk.textSecondary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = when (good) {
                true -> psk.positive
                false -> psk.negative
                null -> psk.textPrimary
            },
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
        )
        action?.let { Action(it, onClick = onAction) }
    }
}

@Composable
private fun Picker(label: String, content: LazyListScope.() -> Unit) {
    val psk = LocalPskColors.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = psk.textSecondary)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

/** Fixed rows rather than a flow layout, so the buttons keep the same places on stage. */
@Composable
private fun ActionGrid(actions: List<Pair<String, () -> Unit>>, perRow: Int = 3) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        actions.chunked(perRow).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { (label, onClick) ->
                    Action(label, Modifier.weight(1f), onClick)
                }
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun Action(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val psk = LocalPskColors.current
    Box(
        modifier
            .clip(PskShapes.oddsButton)
            .background(psk.brandBlue)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = psk.textPrimary,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

/**
 * The system screen that governs promoted ongoing notifications. It does not exist on every
 * build, so a missing activity falls back to the app's ordinary notification settings rather
 * than crashing a demo.
 */
private fun openPromotedSettings(context: Context) {
    val promoted = Intent(NotificationPermission.PROMOTED_SETTINGS_ACTION)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (runCatching { context.startActivity(promoted) }.isSuccess) return

    val fallback = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(fallback) }
}

private fun Instant.clockTime(): String {
    val time = toLocalDateTime(TimeZone.currentSystemDefault()).time
    return pad(time.hour) + ":" + pad(time.minute) + ":" + pad(time.second)
}

private fun pad(value: Int): String = if (value < 10) "0" + value else value.toString()
