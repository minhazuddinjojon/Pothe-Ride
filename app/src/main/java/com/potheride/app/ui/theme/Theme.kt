package com.potheride.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.potheride.app.core.format.AppLanguage
import com.potheride.app.core.i18n.EnglishStrings
import com.potheride.app.core.i18n.Strings
import com.potheride.app.core.i18n.stringsFor

val PotheShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

private val LightColors = lightColorScheme(
    primary = Ink,
    onPrimary = Snow,
    primaryContainer = Cloud,
    onPrimaryContainer = Ink,
    secondary = RouteGreen,
    onSecondary = Snow,
    secondaryContainer = RouteGreenSoft,
    onSecondaryContainer = RouteGreen,
    tertiary = SignalAmber,
    onTertiary = Ink,
    tertiaryContainer = SignalAmberSoft,
    onTertiaryContainer = InkSoft,
    error = AlertRed,
    onError = Snow,
    errorContainer = AlertRedSoft,
    onErrorContainer = AlertRed,
    background = Paper,
    onBackground = Ink,
    surface = Snow,
    onSurface = Ink,
    surfaceVariant = Cloud,
    onSurfaceVariant = Slate,
    outline = Line,
    outlineVariant = Line,
    scrim = Ink
)

private val DarkColors = darkColorScheme(
    primary = Snow,
    onPrimary = Ink,
    primaryContainer = DarkSurfaceHigh,
    onPrimaryContainer = DarkText,
    secondary = Color_RouteGreenOnDark,
    onSecondary = Ink,
    secondaryContainer = DarkSurfaceHigh,
    onSecondaryContainer = Color_RouteGreenOnDark,
    tertiary = SignalAmber,
    onTertiary = Ink,
    tertiaryContainer = DarkSurfaceHigh,
    onTertiaryContainer = SignalAmber,
    error = Color_AlertRedOnDark,
    onError = Ink,
    errorContainer = DarkSurfaceHigh,
    onErrorContainer = Color_AlertRedOnDark,
    background = DarkBackground,
    onBackground = DarkText,
    surface = DarkSurface,
    onSurface = DarkText,
    surfaceVariant = DarkSurfaceHigh,
    onSurfaceVariant = Mist,
    outline = DarkLine,
    outlineVariant = DarkLine,
    scrim = Ink
)

/**
 * Makes the active language's string table available to every composable without
 * threading it through each signature. Reading it via a CompositionLocal means a
 * language switch recomposes the whole tree instantly, with no activity restart.
 */
val LocalStrings = staticCompositionLocalOf<Strings> { EnglishStrings }

val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.ENGLISH }

/**
 * Colours the map draws with. Held in a CompositionLocal rather than read from the
 * colour scheme because the map is the one surface whose palette is dictated by
 * cartography — canvas, streets, route, shared stretch — and none of those map onto a
 * Material role without distorting it.
 */
data class MapColors(
    val canvas: Color,
    val street: Color,
    val route: Color,
    val sharedStretch: Color,
    val livePosition: Color,
    val origin: Color,
    val destination: Color
)

val LocalMapColors = staticCompositionLocalOf {
    MapColors(
        canvas = MapCanvas, street = MapStreet, route = RouteGreen,
        sharedStretch = SharedStretchBlue, livePosition = SignalAmber,
        origin = RouteGreen, destination = AlertRed
    )
}

/**
 * The colour of the single forward action on a screen.
 *
 * Deliberately *not* `colorScheme.primary`. The wireframes use near-black for
 * selection chips and blue for the call to action; collapsing both onto `primary`
 * would make a selected `Cash` chip look like a button you are meant to press.
 */
val LocalCtaColor = staticCompositionLocalOf { ActionBlue }

/**
 * The metadata text style for the active language.
 *
 * Monospace for Latin/numeric content, sans for Bangla — see [MetaMonoStyle].
 */
@Composable
fun metaTextStyle(): TextStyle =
    if (LocalAppLanguage.current == AppLanguage.BANGLA) MetaSansStyle else MetaMonoStyle

@Composable
fun PotheRideTheme(
    language: AppLanguage = AppLanguage.ENGLISH,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val mapColors = if (darkTheme) {
        MapColors(
            canvas = MapCanvasDark, street = MapStreetDark, route = Color_RouteGreenOnDark,
            sharedStretch = SharedStretchBlue, livePosition = SignalAmber,
            origin = Color_RouteGreenOnDark, destination = Color_AlertRedOnDark
        )
    } else {
        MapColors(
            canvas = MapCanvas, street = MapStreet, route = RouteGreen,
            sharedStretch = SharedStretchBlue, livePosition = SignalAmber,
            origin = RouteGreen, destination = AlertRed
        )
    }

    CompositionLocalProvider(
        LocalStrings provides stringsFor(language),
        LocalAppLanguage provides language,
        LocalMapColors provides mapColors,
        LocalCtaColor provides if (darkTheme) ActionBlueOnDark else ActionBlue
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = PotheTypography,
            shapes = PotheShapes,
            content = content
        )
    }
}
