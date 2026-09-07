package eu.feg.ambient.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * PRD section 3.2. Inter is not bundled in Phase 1, so the substitute named in the PRD —
 * Roboto, which is [FontFamily.Default] on Android — carries every style.
 */
private val PskFontFamily = FontFamily.Default

val PskTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = PskFontFamily,
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = PskFontFamily,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = PskFontFamily,
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = PskFontFamily,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = PskFontFamily,
        fontSize = 11.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 14.sp,
    ),
)

/**
 * Odds styles live outside [Typography] because Material has no slot for them.
 * [oddsValue] is tabular so a 2.10 -> 2.05 change does not reflow the row.
 */
object PskTextStyles {
    val oddsValue = TextStyle(
        fontFamily = PskFontFamily,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 18.sp,
        fontFeatureSettings = "tnum",
    )
    val oddsLabel = TextStyle(
        fontFamily = PskFontFamily,
        fontSize = 10.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 12.sp,
    )
}
