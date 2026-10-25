@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package app.tuneroam.radio.ui.theme

import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.tuneroam.radio.R

private val Night = Color(0xFF071018)
private val Ink = Color(0xFF101A24)
private val Panel = Color(0xFF172430)
val Amber = Color(0xFFF0B45A)
val Signal = Color(0xFF7DDFC3)
private val Foam = Color(0xFFE7F0F6)
private val Mist = Color(0xFF8BA0B3)
private val Danger = Color(0xFFF07178)

private fun outfit(weight: FontWeight) = Font(
    resId = R.font.outfit,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

private fun newsreader(weight: FontWeight) = Font(
    resId = R.font.newsreader,
    weight = weight,
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight.weight),
        FontVariation.Setting("opsz", 48f),
    ),
)

private val OutfitFamily = FontFamily(
    outfit(FontWeight.Normal),
    outfit(FontWeight.Medium),
    outfit(FontWeight.SemiBold),
)

private val NewsreaderFamily = FontFamily(
    newsreader(FontWeight.Normal),
    newsreader(FontWeight.Medium),
    newsreader(FontWeight.SemiBold),
)

private val TuneRoamTypography = androidx.compose.material3.Typography(
    displaySmall = TextStyle(
        fontFamily = NewsreaderFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 40.sp,
        lineHeight = 44.sp,
        color = Foam,
    ),
    headlineMedium = TextStyle(
        fontFamily = NewsreaderFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 28.sp,
        lineHeight = 32.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = NewsreaderFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = OutfitFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = OutfitFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = OutfitFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = OutfitFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = OutfitFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
)

private val TuneRoamColors = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF2A1C04),
    secondary = Signal,
    onSecondary = Color(0xFF04231C),
    background = Night,
    onBackground = Foam,
    surface = Ink,
    onSurface = Foam,
    surfaceVariant = Panel,
    onSurfaceVariant = Mist,
    error = Danger,
    onError = Color(0xFF2A0A0C),
    outline = Color(0xFF2C4254),
)

/**
 * Material's focus highlight is a faint 10% wash, lost on a projector across the room;
 * this one is easy to spot. Focus only lands on buttons from a remote or keyboard, so touch
 * looks the same as before.
 */
private val RemoteFriendlyRipple = RippleConfiguration(
    rippleAlpha = RippleAlpha(
        draggedAlpha = 0.16f,
        focusedAlpha = 0.28f,
        hoveredAlpha = 0.08f,
        pressedAlpha = 0.10f,
    ),
)

@Composable
fun TuneRoamTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TuneRoamColors,
        typography = TuneRoamTypography,
    ) {
        CompositionLocalProvider(LocalRippleConfiguration provides RemoteFriendlyRipple, content = content)
    }
}
