package com.potheride.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Pothe Ride's palette.
 *
 * The structure is the one every transport app converges on for good reasons — a
 * near-black primary action against white surfaces, because a full-width high-contrast
 * button stays legible on a phone held at arm's length in daylight, and colour is
 * reserved for meaning rather than decoration. The identity here is Pothe Ride's own:
 * a deep paddy green as the route colour and a CNG-yellow signal accent, neither of
 * which belongs to anyone else's brand.
 */

// Core neutrals — the structural palette.
val Ink = Color(0xFF0E0F10)
val InkSoft = Color(0xFF2A2D2F)
val Slate = Color(0xFF6B7075)
val Mist = Color(0xFFA8AEB3)
val Line = Color(0xFFE3E5E7)
val Cloud = Color(0xFFF2F3F4)
val Paper = Color(0xFFF8F8F6)
val Snow = Color(0xFFFFFFFF)

/**
 * The wireframes' primary call-to-action is a saturated blue pill, not the near-black
 * button the palette above describes. Blue wins: it is what every board shows, and it
 * separates "the one action that moves you forward" from the black selection chips,
 * which would otherwise be the same colour as the button.
 */
val ActionBlue = Color(0xFF1668E3)
val ActionBluePressed = Color(0xFF0F52B8)
val ActionBlueOnDark = Color(0xFF4E93F5)

/** Map canvas, transcribed from the wireframe boards. */
val MapCanvas = Color(0xFFE4E1D8)
val MapCanvasDark = Color(0xFF23262A)
val MapStreet = Color(0xFFCDC9BE)
val MapStreetDark = Color(0xFF33373C)

/** The blue overlay drawn on the stretch of route the passenger shares with the driver. */
val SharedStretchBlue = Color(0xFF7FB1F0)

// Brand accents — used for meaning, never as background wallpaper.
val RouteGreen = Color(0xFF0B6E4F)
val RouteGreenSoft = Color(0xFFE3F1EB)
val SignalAmber = Color(0xFFF2B705)
val SignalAmberSoft = Color(0xFFFDF3D6)
val AlertRed = Color(0xFFD7263D)
val AlertRedSoft = Color(0xFFFBE4E7)
val InfoBlue = Color(0xFF1B6CA8)
val InfoBlueSoft = Color(0xFFE2EEF7)

// Dark-theme neutrals.
val DarkBackground = Color(0xFF0E0F10)
val DarkSurface = Color(0xFF17191B)
val DarkSurfaceHigh = Color(0xFF212426)
val DarkLine = Color(0xFF2E3234)
val DarkText = Color(0xFFF2F3F4)

/**
 * Semantic colours for the ride progress tracker. Keeping them here rather than
 * inline in the composable means the driver's and passenger's views of the same
 * ride cannot drift apart visually.
 */
object StatusColors {
    val pending = SignalAmber
    val active = RouteGreen
    val complete = RouteGreen
    val failed = AlertRed
    val idle = Mist
}

// Brightened accents for dark surfaces, where the light-theme greens and reds fail
// contrast against near-black.
val Color_RouteGreenOnDark = Color(0xFF4FC79B)
val Color_AlertRedOnDark = Color(0xFFFF6B7D)
