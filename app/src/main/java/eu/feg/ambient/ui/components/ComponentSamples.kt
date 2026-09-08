package eu.feg.ambient.ui.components

/**
 * Sample rows shared by the Compose previews and the on-device gallery.
 * Fixtures and prices are lifted from the screenshots so the samples read as real.
 */
internal val samplePrematchRow = MatchRowUi(
    id = "m1",
    homeName = "Ipswich",
    awayName = "Liverpool",
    timeText = "tomorrow 00:30",
    badges = listOf("BB", "90+"),
    marketLabel = "Basic offer",
    odds = listOf(
        RowOdd("1", "6.50"),
        RowOdd("X", "5.50"),
        RowOdd("2", "1.50"),
    ),
)

internal val sampleLiveRow = MatchRowUi(
    id = "m2",
    homeName = "NK Lucko",
    awayName = "Solin",
    timeText = "1. poluvrijeme - 44m",
    isLive = true,
    homeScore = 0,
    awayScore = 1,
    marketLabel = "Match",
    odds = listOf(
        RowOdd("NK Lucko", "6.00", flash = OddsFlash.UP),
        RowOdd("Draw", "3.40"),
        RowOdd("Solin", "1.50", flash = OddsFlash.DOWN),
    ),
)

internal val sampleTopBadgeRow = MatchRowUi(
    id = "m3",
    homeName = "Betis",
    awayName = "Real Madrid",
    timeText = "tomorrow 00:30",
    badges = listOf("BB", "90+"),
    marketLabel = "Basic offer",
    isFavourite = true,
    odds = listOf(
        RowOdd("1", "7.50"),
        RowOdd("X", "5.60"),
        RowOdd("2", "1.45", isTop = true),
    ),
)

internal val sampleLockedRow = MatchRowUi(
    id = "m4",
    homeName = "FC Gabala",
    awayName = "Imisli FK",
    timeText = "2. poluvrijeme - 86m",
    isLive = true,
    homeScore = 2,
    awayScore = 1,
    badges = listOf("BB"),
    marketLabel = "Match",
    odds = listOf(
        RowOdd("FK Gabala", "1.10", state = OddsState.LOCKED),
        RowOdd("Draw", "5.90", state = OddsState.LOCKED),
        RowOdd("Imisli FK", "90.00", state = OddsState.LOCKED),
    ),
)

internal val sampleDoubleChanceRow = MatchRowUi(
    id = "m5",
    homeName = "JiPPO Joensuu",
    awayName = "Kotka KTP",
    timeText = "1. poluvrijeme - 16m",
    isLive = true,
    homeScore = 0,
    awayScore = 1,
    marketLabel = "Match - double chance",
    odds = listOf(
        RowOdd("1X", "2.15"),
        RowOdd("12", "1.18"),
        RowOdd("X2", "1.06", state = OddsState.SELECTED),
    ),
)
