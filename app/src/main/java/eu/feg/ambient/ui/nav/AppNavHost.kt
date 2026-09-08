package eu.feg.ambient.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import eu.feg.ambient.ui.home.HomeScreen
import eu.feg.ambient.ui.home.HomeViewModel
import eu.feg.ambient.ui.live.LiveScreen
import eu.feg.ambient.ui.live.LiveViewModel
import eu.feg.ambient.ui.match.MatchDetailScreen
import eu.feg.ambient.ui.match.MatchDetailViewModel
import eu.feg.ambient.ui.mybets.MyBetsScreen
import eu.feg.ambient.ui.mybets.MyBetsViewModel
import eu.feg.ambient.ui.mybets.ScanTicketScreen
import eu.feg.ambient.ui.promo.PromoScreen
import eu.feg.ambient.ui.rg.ResponsibleGamingScreen
import eu.feg.ambient.ui.rg.ResponsibleGamingViewModel
import eu.feg.ambient.ui.theme.LocalPskColors

private val MORE_ITEMS = listOf(
    "Lotto", "Promo", "Forum", "Results", "Statistics", "News",
    "Champions Club", "Branches", "Help", "Responsible gaming", "Settings",
)

/** PRD section 4 — one Activity, one NavHost, five tabs plus a More sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavHost(container: AppContainer, modifier: Modifier = Modifier) {
    val psk = LocalPskColors.current
    val navController: NavHostController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val slip by container.betRepository.slip.collectAsStateWithLifecycle()
    var showMore by remember { mutableStateOf(false) }

    // Content files are read once; they never change in Phase 1.
    val content = remember { StaticContent(container) }

    Scaffold(
        modifier = modifier,
        containerColor = psk.background,
        topBar = {
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
                        Icon(
                            imageVector = Icons.Filled.ReceiptLong,
                            contentDescription = "Bet slip",
                            tint = psk.textPrimary,
                            modifier = Modifier
                                .size(24.dp)
                                .clickable { navController.navigate(Routes.BET_SLIP) },
                        )
                    }
                    Icon(
                        imageVector = Icons.Filled.AccountCircle,
                        contentDescription = "More",
                        tint = psk.textPrimary,
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .size(24.dp)
                            .clickable { showMore = true },
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = psk.brandBlue,
                    titleContentColor = psk.textPrimary,
                    actionIconContentColor = psk.textPrimary,
                ),
            )
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
            startDestination = Routes.SPORT,
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
                MyBetsScreen(vm, onScanTicket = { navController.navigate(Routes.SCAN_TICKET) })
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
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(psk.background)
                        .verticalScroll(rememberScrollState()),
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

            composable(Routes.RESPONSIBLE_GAMING) {
                val vm: ResponsibleGamingViewModel = viewModel(
                    factory = PskViewModelFactory(container) { ResponsibleGamingViewModel(it) },
                )
                ResponsibleGamingScreen(vm)
            }

            composable(Routes.PROMO) { PromoScreen(content.promos) }
            composable(Routes.SCAN_TICKET) { ScanTicketScreen() }
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
                MORE_ITEMS.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showMore = false
                                when (item) {
                                    "Responsible gaming" ->
                                        navController.navigate(Routes.RESPONSIBLE_GAMING)
                                    "Promo" -> navController.navigate(Routes.PROMO)
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
