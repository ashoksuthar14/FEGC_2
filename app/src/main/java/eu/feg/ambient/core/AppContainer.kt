package eu.feg.ambient.core

import android.content.Context
import eu.feg.ambient.data.clock.MatchClock
import eu.feg.ambient.data.clock.SystemMatchClock
import eu.feg.ambient.data.mock.MockDataSource
import eu.feg.ambient.data.repo.BetRepository
import eu.feg.ambient.data.repo.MatchRepository
import eu.feg.ambient.data.repo.UserStateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Plain constructors instead of a DI framework (PRD section 2.3). Held by the Application,
 * so the tick and the repositories outlive any single screen.
 */
class AppContainer(context: Context) {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Public so the UI can read the static content files directly. */
    val source = MockDataSource.fromContext(context)

    /** Declared as the interface, never the impl — Phase 2 swaps this line alone. */
    val clock: MatchClock = SystemMatchClock()

    val matchRepository = MatchRepository(source, clock, appScope)

    val betRepository = BetRepository(clock)

    val userStateRepository = UserStateRepository(
        context.getSharedPreferences("ambient", Context.MODE_PRIVATE),
    )
}
