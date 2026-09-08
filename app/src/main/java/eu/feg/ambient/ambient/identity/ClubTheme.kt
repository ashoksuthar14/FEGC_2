package eu.feg.ambient.ambient.identity

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import eu.feg.ambient.ambient.surfaces.ProtectionState
import eu.feg.ambient.ui.theme.PskColors

/**
 * A club's identity, reduced to the four things a surface actually needs.
 *
 * COSMETIC ONLY. There is no money in a colour and no personal data in a crest, so this
 * carries no compliance surface of its own: no new field, no new consent, nothing that
 * changes what a customer is shown, only how it looks. That is what lets it survive Calm
 * Mode untouched, which is a claim the rest of this file has to keep true.
 *
 * NO SCRAPED LOGOS. Club crests are trademarks. Two initials in the club's colour is an
 * identity cue we are entitled to draw, and it renders at any size on any surface without
 * an asset, a download or a licence.
 */
@Immutable
data class ClubTheme(
    val clubId: String,
    val name: String,
    /** Three characters for the status chip and the small widget, where a name will not fit. */
    val short: String,
    /** The accent. Everything the club touches is tinted with this and nothing else. */
    val primary: Color,
    /** Text and glyphs that sit on [primary]. Computed by [contrastOn], never chosen by eye. */
    val onPrimary: Color,
    val crestInitials: String,
    /** The club's second colour, used for the crest pattern. */
    val secondary: Color,
    /** How the crest is striped. Clubs are told apart by pattern as much as by hue. */
    val pattern: ClubPattern,
    /**
     * [primary] lightened until it is legible on the app's near-black surface.
     *
     * The raw club colour cannot be used for text on a dark card: Sparta's burgundy scores
     * 2.1 against our background and would be unreadable, Dinamo's blue 3.2 and marginal.
     * This is the colour the club actually appears in on a widget.
     */
    val accentOnDark: Color,
    /** A wash of the club colour, for the card ground. Subtle on purpose — see [tintedSurface]. */
    val surfaceTint: Color,
)

/**
 * The crest pattern.
 *
 * Football clubs are recognised by pattern before colour — Slavia's halves, a checkerboard —
 * and a set of eight plain discs all look like the same badge in a different hue. This is what
 * makes a generated crest read as a crest.
 */
enum class ClubPattern { SOLID, HALVES, STRIPES, CHECKS, HOOP }

/**
 * The eight clubs, plus the operator's own colours for a customer who follows nobody.
 *
 * ON THE COLOURS: these are approximations of each club's recognisable primary, picked to be
 * distinguishable from one another and legible under [contrastOn]. They are not licensed
 * brand values, and a production build would take them from a rights-cleared source.
 *
 * ON THE PALETTE RULE: Color.kt says nothing outside it may declare a Color, and everywhere
 * else in this app that holds. A feature whose entire subject is the customer's club colour
 * is the one thing that rule cannot cover — so the exception is here, in one file, named,
 * rather than spread across the surfaces that use it. [Default] still comes from PskColors.
 */
object ClubThemes {

    private val psk = PskColors()

    val Default = club(
        clubId = "",
        name = "PSK",
        short = "PSK",
        primary = psk.brandBlue,
        crestInitials = "PSK",
        secondary = psk.brandBlueDeep,
        pattern = ClubPattern.SOLID,
    )

    val DinamoZagreb = club(
        "dinamo_zagreb", "Dinamo Zagreb", "DIN", Color(0xFF1560BD), "DZ",
        Color(0xFFFFFFFF), ClubPattern.HOOP,
    )
    val HajdukSplit = club(
        "hajduk_split", "Hajduk Split", "HAJ", Color(0xFFFFFFFF), "HS",
        Color(0xFF1B4F9C), ClubPattern.CHECKS,
    )
    val Rijeka = club(
        "rijeka", "Rijeka", "RIJ", Color(0xFF0A4C9E), "RI",
        Color(0xFFFFFFFF), ClubPattern.HALVES,
    )
    val Varazdin = club(
        "varazdin", "Varaždin", "VAR", Color(0xFFF5C518), "VŽ",
        Color(0xFF14224F), ClubPattern.STRIPES,
    )
    val Sparta = club(
        "sparta", "Sparta", "SPA", Color(0xFF8B1A1A), "SP",
        Color(0xFFF2C300), ClubPattern.HOOP,
    )
    val Slavia = club(
        "slavia", "Slavia", "SLA", Color(0xFFD31F2B), "SL",
        Color(0xFFFFFFFF), ClubPattern.HALVES,
    )
    val Liverpool = club(
        "liverpool", "Liverpool", "LIV", Color(0xFFC8102E), "LFC",
        Color(0xFF00B2A9), ClubPattern.SOLID,
    )
    val RealMadrid = club(
        "real_madrid", "Real Madrid", "RMA", Color(0xFFFEBE10), "RM",
        Color(0xFF00529F), ClubPattern.STRIPES,
    )

    val all: List<ClubTheme> = listOf(
        DinamoZagreb, HajdukSplit, Rijeka, Varazdin, Sparta, Slavia, Liverpool, RealMadrid,
    )

    /**
     * The theme for a stored club id, honouring protection.
     *
     *  - NORMAL and CALM keep the club. Calm Mode removes what rewards checking the phone —
     *    progress, celebration, a result approaching — and a colour is none of those. Taking
     *    someone's club away at the moment they ask to cool down would read as a punishment
     *    for asking.
     *  - UNVERIFIED and BLOCKED fall back to [Default]. A protected surface has to look like
     *    the operator speaking, not like a fan page: the customer is being told something
     *    about their account, and the frame around that must not be their club's.
     */
    fun forState(clubId: String?, protection: ProtectionState): ClubTheme = when (protection) {
        ProtectionState.UNVERIFIED, ProtectionState.BLOCKED -> Default
        ProtectionState.NORMAL, ProtectionState.CALM -> byId(clubId)
    }

    fun byId(clubId: String?): ClubTheme =
        all.firstOrNull { it.clubId == clubId } ?: Default

    /**
     * A crest for any team on the card. One of the eight clubs gets its own theme; anyone
     * else gets a neutral tile with their initials -- the score row needs two crests whether
     * or not either side is a club we know.
     */
    fun forTeam(name: String): ClubTheme = byName(name) ?: neutral(name)

    private fun neutral(name: String): ClubTheme {
        val words = name.split(' ', '-', '\u2013').filter { it.isNotBlank() }
        // "Ipswich" -> IPS, "Real Madrid" -> RM. A one-word club gets a three-letter code, the
        // way a scoreboard writes it; a lone "I" on a crest reads as nothing at all.
        val code = if (words.size == 1) words[0].take(3).uppercase()
        else words.take(3).joinToString("") { it.first().uppercaseChar().toString() }
        val safe = code.ifBlank { "?" }
        return club("", name, safe.take(3), psk.surfaceRaised, safe, psk.surfaceVariant, ClubPattern.SOLID)
    }

    /** Team names arrive from the fixtures as text; the narrator's facts carry no ids. */
    fun byName(name: String?): ClubTheme? =
        name?.let { n -> all.firstOrNull { it.name.equals(n, ignoreCase = true) } }

    private fun club(
        clubId: String,
        name: String,
        short: String,
        primary: Color,
        crestInitials: String,
        secondary: Color,
        pattern: ClubPattern,
    ) = ClubTheme(
        clubId = clubId,
        name = name,
        short = short,
        primary = primary,
        onPrimary = contrastOn(primary),
        crestInitials = crestInitials,
        secondary = secondary,
        pattern = pattern,
        // Legible against the ground the widget actually paints -- the club-tinted surface --
        // not the darker app background. Five clubs sat at 4.2-4.5:1 on the tint when the
        // target was the background; ContrastTest checks this exact pair.
        surfaceTint = tintedSurface(primary),
        accentOnDark = legibleOnDark(primary, background = tintedSurface(primary)),
    )
}

/**
 * The legible text colour for anything drawn on [bg].
 *
 * NEVER HARDCODE WHITE. Hajduk are white and Dinamo are blue, and the same literal cannot
 * serve both — white on white is not a styling flaw, it is a control the customer cannot
 * see. This picks whichever of the app's two text colours has the higher WCAG contrast
 * ratio, which for every colour in [ClubThemes] clears 4.5:1 with room to spare.
 */
fun contrastOn(bg: Color): Color {
    val onLight = Color(0xFF0E0E10)
    val onDark = Color(0xFFFFFFFF)
    return if (contrastRatio(bg, onDark) >= contrastRatio(bg, onLight)) onDark else onLight
}

/** WCAG 2.1 relative-luminance contrast, (lighter + 0.05) / (darker + 0.05). */
fun contrastRatio(a: Color, b: Color): Double {
    val la = relativeLuminance(a)
    val lb = relativeLuminance(b)
    val lighter = maxOf(la, lb)
    val darker = minOf(la, lb)
    return (lighter + 0.05) / (darker + 0.05)
}

private fun relativeLuminance(color: Color): Double =
    0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)

private fun channel(value: Float): Double {
    val c = value.toDouble()
    return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
}

/**
 * The club colour, lightened until text in it is readable on our near-black ground.
 *
 * Using the raw club colour for anything but a filled shape is the trap here: Sparta's
 * burgundy on the app background scores 2.1:1 and Dinamo's blue 3.2:1, both under the 4.5:1
 * a caption needs. Lightening preserves the hue — Sparta still reads as red, Dinamo as blue —
 * while making the colour usable as ink. It is the same move a tonal palette makes, done for
 * one colour and one background.
 */
fun legibleOnDark(
    color: Color,
    background: Color = Color(0xFF0E0E10),
    target: Double = 4.5,
): Color {
    var current = color
    var steps = 0
    while (contrastRatio(current, background) < target && steps < MAX_LIGHTEN_STEPS) {
        current = Color(
            red = current.red + (1f - current.red) * LIGHTEN_STEP,
            green = current.green + (1f - current.green) * LIGHTEN_STEP,
            blue = current.blue + (1f - current.blue) * LIGHTEN_STEP,
            alpha = current.alpha,
        )
        steps++
    }
    return current
}

/**
 * A wash of the club colour for the card ground.
 *
 * Fourteen per cent, not more. A card painted in the club's full colour is a fan app; a card
 * with a trace of it in the dark is the customer's corner of the operator's app, which is the
 * distinction N6 lives on. It also keeps every foreground contrast ratio we computed valid,
 * because the ground barely moves.
 */
fun tintedSurface(
    color: Color,
    background: Color = Color(0xFF17171C),
    amount: Float = 0.14f,
): Color = Color(
    red = background.red + (color.red - background.red) * amount,
    green = background.green + (color.green - background.green) * amount,
    blue = background.blue + (color.blue - background.blue) * amount,
)

private const val LIGHTEN_STEP = 0.10f
private const val MAX_LIGHTEN_STEPS = 24
