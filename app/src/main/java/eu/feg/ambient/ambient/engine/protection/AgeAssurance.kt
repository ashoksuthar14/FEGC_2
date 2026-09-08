package eu.feg.ambient.ambient.engine.protection

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether we have assurance that this person is old enough to be shown any of this.
 *
 * PHASE 3. Real age assurance is an identity integration — document capture, a bank or eID
 * hand-off, and a re-verification schedule — none of which belongs in a 24-hour build. What
 * matters now is that the *seam* is real: everything downstream already treats an unverified
 * user as a user who gets no surfaces, so dropping a real provider in later changes this file
 * and nothing else.
 *
 * The flow exists alongside [isVerified] because the Lab flips this live, and step 14E needs
 * a protection change to reach the surfaces immediately rather than at the next tick.
 */
interface AgeAssurance {

    suspend fun isVerified(): Boolean

    val verified: StateFlow<Boolean>
}

/**
 * Demo implementation. Defaults to verified: a hackathon audience has not done KYC, and a
 * demo that opens on UNVERIFIED shows nothing at all.
 *
 * That default is safe *here* and nowhere else. A real provider must default to false — it is
 * the one place in this package that fails open, and it does so only because there is no
 * provider behind it to ask.
 */
class PrefsAgeAssurance(
    private val prefs: SharedPreferences?,
) : AgeAssurance {

    constructor(context: Context) : this(
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE),
    )

    private val _verified = MutableStateFlow(prefs?.getBoolean(KEY, true) ?: true)
    override val verified: StateFlow<Boolean> = _verified.asStateFlow()

    override suspend fun isVerified(): Boolean = _verified.value

    fun setVerified(verified: Boolean) {
        _verified.value = verified
        prefs?.edit()?.putBoolean(KEY, verified)?.apply()
    }

    private companion object {
        const val PREFS = "ambient_protection"
        const val KEY = "age_verified"
    }
}
