package eu.feg.ambient.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import eu.feg.ambient.ui.components.IconTouchTarget
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.feg.ambient.core.AppContainer
import eu.feg.ambient.ui.arena.ArenaScreen
import eu.feg.ambient.ui.betslip.BetSlipScreen
import eu.feg.ambient.ui.betslip.BetSlipViewModel
import eu.feg.ambient.ui.casino.CasinoScreen
import eu.feg.ambient.ui.casino.GameLoadingScreen
import eu.feg.ambient.ui.dev.BanditDebugScreen
import eu.feg.ambient.ui.recap.RecapViewModel
import eu.feg.ambient.ui.dev.EngineLabViewModel
import eu.feg.ambient.ui.dev.RegisterPanelScreen
import eu.feg.ambient.ui.dev.SurfaceLabScreen
import eu.feg.ambient.ui.dev.SurfaceLabViewModel
import eu.feg.ambient.ui.dev.WhyThisScreen
import eu.feg.ambient.ui.diagnostics.AiDiagnosticsScreen
import eu.feg.ambient.ui.diagnostics.AiDiagnosticsViewModel
import eu.feg.ambient.ui.home.HomeScreen
import eu.feg.ambient.ui.home.HomeViewModel
import eu.feg.ambient.ui.lab.NarratorLabScreen
import eu.feg.ambient.ui.lab.NarratorLabViewModel
import eu.feg.ambient.ui.live.LiveScreen
import eu.feg.ambient.ui.live.LiveViewModel
import eu.feg.ambient.ui.match.MatchDetailScreen
import eu.feg.ambient.ui.match.MatchDetailViewModel
import eu.feg.ambient.ui.mybets.MyBetsScreen
import eu.feg.ambient.ui.mybets.MyBetsViewModel
import eu.feg.ambient.ui.ticket.ScanTicketScreen
import eu.feg.ambient.ui.ticket.ScanTicketViewModel
import eu.feg.ambient.ambient.surfaces.ProtectionState
import androidx.compose.runtime.LaunchedEffect
import eu.feg.ambient.ui.promo.PromoScreen
import eu.feg.ambient.ui.loyalty.LoyaltyViewModel
import eu.feg.ambient.ui.loyalty.MissionsScreen
import eu.feg.ambient.ui.loyalty.RewardsScreen
import eu.feg.ambient.ui.rg.ResponsibleGamingScreen
import eu.feg.ambient.ui.rg.ResponsibleGamingViewModel
import eu.feg.ambient.ui.theme.LocalPskColors

private val MORE_ITEMS = listOf(
    "Lotto", "Promo", "Forum", "Results", "Statistics", "News",
    "Champions Club", "Branches", "Help", "Missions", "My rewards",
    "Responsible gaming", "Settings",
)

/** Phase 2 test benches, kept under their own heading so they read as developer tools. */
private val DEVELOPER_ITEMS = listOf(
    "AI diagnostics", "Narrator Lab", "Surface Lab",
    "Register Panel", "Why this?", "Bandit Debug",
)

/** PRD section 4 — one Activity, one NavHost, five tabs plus a More sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavHost(
    container: AppContainer,
    modifier: Modifier = Modifier,
    /**
     * Lets a launch intent open a screen directly, e.g.
     * `am start -n eu.feg.ambient/.MainActivity --es route narrator_lab`.
     * The app's own navigation never sets this; it exists so screens deep in the More sheet
     * can be reached deterministically for screenshots and demos.
     */
    startRoute: String? = null,
) {
    val psk = LocalPskColors.current
    val navController: NavHostController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val slip by container.betRepository.slip.collectAsStateWithLifecycle()
    var showMore by remember { mutableStateOf(false) }

    // Content files are read once; they never change in Phase 1.
    val content = remember { StaticContent(container) }
    val club by container.myClubTheme.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        containerColor = psk.background,
        topBar = {
          Column {
            TopAppBar(
                title = {
                    Text(
                        text = "PSK",
                        color = psk.textPrimary,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                actions = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "Search",
                        tint = psk.textPrimary,
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .size(24.dp),
                    )
                    BadgedBox(
                        badge = {
                            if (slip.selections.isNotEmpty()) {
                                Badge(containerColor = psk.jackpotYellow, contentColor = psk.background) {
                                    Text(slip.selections.size.toString())
                                }
                            }
                        },
                        modifier = Modifier.padding(horizontal = 8.dp),
                    ) {
                        IconTouchTarget(
                            contentDescription = "Bet slip",
                            onClick = { navController.navigate(Routes.BET_SLIP) },
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ReceiptLong,
                                contentDescription = null,
                                tint = psk.textPrimary,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                    IconTouchTarget(
                        contentDescription = "Account and more",
                        onClick = { showMore = true },
                        modifier = Modifier.padding(horizontal = 8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AccountCircle,
                            contentDescription = null,
                            tint = psk.textPrimary,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = psk.brandBlue,
                    titleContentColor = psk.textPrimary,
                    actionIconContentColor = psk.textPrimary,
                ),
            )
            // N6, applied with a light hand: the bar stays the operator's blue and the club
            // gets a stripe under it. Repainting the whole bar would make the app look like a
            // fan app rather than the customer's corner of PSK's, which is the distinction
            // this feature lives or dies on. It disappears under protection with the theme.
            if (club.clubId.isNotEmpty()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(club.primary),
                )
            }
          }
        },
        bottomBar = {
            BottomBar(currentRoute = currentRoute) { tab ->
                navController.navigate(tab.route) {
                    popUpTo(Routes.SPORT) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        },
    ) { inner ->
        NavHost(
            navController = navController,
            startDestination = startRoute ?: Routes.SPORT,
            modifier = Modifier.padding(inner),
        ) {
            composable(Routes.SPORT) {
                val vm: HomeViewModel = viewModel(
                    factory = PskViewModelFactory(container) { HomeViewModel(it) },
                )
                HomeScreen(
                    viewModel = vm,
                    promos = content.promos,
                    arenaTips = content.arenaTips,
                    onMatchClick = { navController.navigate(Routes.match(it)) },
                    onCopyTip = { content.copyTip(it) },
                    onOpenResponsibleGaming = { navController.navigate(Routes.RESPONSIBLE_GAMING) },
                )
            }

            composable(Routes.LIVE) {
                val vm: LiveViewModel = viewModel(
                    factory = PskViewModelFactory(container) { LiveViewModel(it) },
                )
                LiveScreen(vm, onMatchClick = { navController.navigate(Routes.match(it)) })
            }

            composable(Routes.CASINO) {
                CasinoScreen(
                    games = content.casinoGames,
                    wins = content.recentWins,
                    promos = content.promos,
                    onGameClick = { navController.navigate(Routes.GAME_LOADING) },
                )
            }

            composable(Routes.ARENA) {
                ArenaScreen(
                    tips = content.arenaTips,
                    onCopySlip = {
                        content.copyTip(it.id)
                        navController.navigate(Routes.BET_SLIP)
                    },
                )
            }

            composable(Routes.MY_BETS) {
                val vm: MyBetsViewModel = viewModel(
                    factory = PskViewModelFactory(container) { MyBetsViewModel(it) },
                )
                val protection by container.protectionEvaluator.state.collectAsStateWithLifecycle()
                MyBetsScreen(
                    vm,
                    onScanTicket = { navController.navigate(Routes.SCAN_TICKET) },
                    canScan = protection == ProtectionState.NORMAL,
                )
            }

            composable(Routes.MATCH) { entry ->
                val matchId = entry.arguments?.getString("matchId").orEmpty()
                val vm: MatchDetailViewModel = viewModel(
                    factory = PskViewModelFactory(container) { MatchDetailViewModel(it, matchId) },
                )
                MatchDetailScreen(vm, onOpenSlip = { navController.navigate(Routes.BET_SLIP) })
            }

            composable(Routes.BET_SLIP) {
                val vm: BetSlipViewModel = viewModel(
                    factory = PskViewModelFactory(container) { BetSlipViewModel(it) },
                )
                // No verticalScroll here: BetSlipScreen scrolls its own Column, and nesting
                // two vertical scrollers hands the inner one an infinite height constraint,
                // which Compose throws on rather than silently mis-measuring.
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(psk.background),
                ) {
                    BetSlipScreen(
                        viewModel = vm,
                        onPlaced = {
                            navController.navigate(Routes.MY_BETS) {
                                popUpTo(Routes.SPORT)
                            }
                        },
                    )
                }
            }

            // N7. Two routes rather than one screen with tabs: the loyalty shortcut deep-links
            // straight to rewards, and a tab index inside a deep link is the kind of thing that
            // silently stops matching. Each screen offers the other, so the pair still reads as
            // one place.
            composable(Routes.MISSIONS) {
                val vm: LoyaltyViewModel = viewModel(
                    factory = PskViewModelFactory(container) { LoyaltyViewModel(it) },
                )
                MissionsScreen(
                    viewModel = vm,
                    // The deposit-limit mission is completed by actually setting a limit, so
                    // its card opens the tool rather than explaining where to find it.
                    onSetLimit = { navController.navigate(Routes.RESPONSIBLE_GAMING) },
                    onOpenRewards = { navController.navigate(Routes.REWARDS) },
                )
            }

            composable(Routes.REWARDS) {
                val vm: LoyaltyViewModel = viewModel(
                    factory = PskViewModelFactory(container) { LoyaltyViewModel(it) },
                )
                RewardsScreen(
                    viewModel = vm,
                    onOpenMissions = { navController.navigate(Routes.MISSIONS) },
                )
            }

            composable(Routes.RESPONSIBLE_GAMING) {
                val vm: ResponsibleGamingViewModel = viewModel(
                    factory = PskViewModelFactory(container) { ResponsibleGamingViewModel(it) },
                )
                ResponsibleGamingScreen(vm)
            }

            composable(Routes.PROMO) { PromoScreen(content.promos) }

            composable(Routes.AI_DIAGNOSTICS) {
                val vm: AiDiagnosticsViewModel = viewModel(
                    factory = PskViewModelFactory(container) { AiDiagnosticsViewModel(it) },
                )
                AiDiagnosticsScreen(vm)
            }

            composable(Routes.NARRATOR_LAB) {
                val vm: NarratorLabViewModel = viewModel(
                    factory = PskViewModelFactory(container) { NarratorLabViewModel(it) },
                )
                NarratorLabScreen(vm)
            }

            composable(Routes.SURFACE_LAB) {
                val vm: SurfaceLabViewModel = viewModel(
                    factory = PskViewModelFactory(container) { SurfaceLabViewModel(it) },
                )
                SurfaceLabScreen(vm)
            }

            composable(Routes.REGISTER_PANEL) {
                val vm: EngineLabViewModel = viewModel(
                    factory = PskViewModelFactory(container) { EngineLabViewModel(it) },
                )
                RegisterPanelScreen(vm)
            }

            composable(Routes.WHY_THIS) {
                val vm: EngineLabViewModel = viewModel(
                    factory = PskViewModelFactory(container) { EngineLabViewModel(it) },
                )
                val recapVm: RecapViewModel = viewModel(
                    factory = PskViewModelFactory(container) { RecapViewModel(it) },
                )
                val month by recapVm.month.collectAsStateWithLifecycle()
                val season by recapVm.season.collectAsStateWithLifecycle()
                WhyThisScreen(vm, recap = month ?: season, onSpeakRecap = recapVm::speak)
            }

            composable(Routes.BANDIT_DEBUG) {
                val vm: EngineLabViewModel = viewModel(
                    factory = PskViewModelFactory(container) { EngineLabViewModel(it) },
                )
                BanditDebugScreen(vm)
            }

            composable(Routes.SCAN_TICKET) {
                // Belt and braces: the chip is hidden under protection, and the shortcut
                // still exists, so the route refuses on its own as well.
                val protection by container.protectionEvaluator.state.collectAsStateWithLifecycle()
                if (protection != ProtectionState.NORMAL) {
                    LaunchedEffect(protection) { navController.popBackStack() }
                } else {
                    val vm: ScanTicketViewModel = viewModel(
                        factory = PskViewModelFactory(container) { ScanTicketViewModel(it) },
                    )
                    ScanTicketScreen(
                        viewModel = vm,
                        onOpenMyBets = {
                            navController.navigate(Routes.MY_BETS) { launchSingleTop = true }
                        },
                        onBack = { navController.popBackStack() },
                    )
                }
            }
            composable(Routes.GAME_LOADING) { GameLoadingScreen() }
        }
    }

    if (showMore) {
        ModalBottomSheet(
            onDismissRequest = { showMore = false },
            sheetState = rememberModalBottomSheetState(),
            containerColor = psk.surface,
        ) {
            Column(Modifier.padding(bottom = 24.dp)) {
                (MORE_ITEMS + "Developer" + DEVELOPER_ITEMS).forEach { item ->
                    if (item == "Developer") {
                        Text(
                            text = "Developer",
                            style = MaterialTheme.typography.labelSmall,
                            color = psk.textSecondary,
                            modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 4.dp),
                        )
                        return@forEach
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showMore = false
                                when (item) {
                                    "Missions" -> navController.navigate(Routes.MISSIONS)
                                    "My rewards" -> navController.navigate(Routes.REWARDS)
                                    "Responsible gaming" ->
                                        navController.navigate(Routes.RESPONSIBLE_GAMING)
                                    "Promo" -> navController.navigate(Routes.PROMO)
                                    "AI diagnostics" -> navController.navigate(Routes.AI_DIAGNOSTICS)
                                    "Narrator Lab" -> navController.navigate(Routes.NARRATOR_LAB)
                                    "Surface Lab" -> navController.navigate(Routes.SURFACE_LAB)
                                    "Register Panel" ->
                                        navController.navigate(Routes.REGISTER_PANEL)
                                    "Why this?" -> navController.navigate(Routes.WHY_THIS)
                                    "Bandit Debug" -> navController.navigate(Routes.BANDIT_DEBUG)
                                }
                            }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = item,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (item == "Responsible gaming") psk.positive else psk.textPrimary,
                        )
                    }
                }
            }
        }
    }
}
