package eu.feg.ambient

import android.app.Application

/**
 * Holds the single [eu.feg.ambient.core.AppContainer] once step 3 introduces it.
 * No DI framework — plain constructors, per PRD section 2.3.
 */
class AmbientApp : Application()
