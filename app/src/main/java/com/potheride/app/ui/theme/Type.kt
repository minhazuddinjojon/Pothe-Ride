package com.potheride.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

/**
 * Type scale.
 *
 * Uses the platform's default font family deliberately: on Android that resolves to
 * Roboto, which ships complete Bengali coverage. A bundled Latin-only display face
 * would fall back mid-sentence and render Bangla in a visibly different typeface,
 * which is exactly the kind of detail that makes a bilingual app feel unfinished.
 */
private val Sans = FontFamily.Default

val PotheTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Bold,
        fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Bold,
        fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.3).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Bold,
        fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.2).sp
    ),
    titleLarge = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp, lineHeight = 25.sp
    ),
    titleMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp, lineHeight = 22.sp
    ),
    titleSmall = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp, lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 23.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 17.sp
    ),
    labelLarge = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp, lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Medium,
        fontSize = 13.sp, lineHeight = 17.sp
    ),
    labelSmall = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 0.6.sp
    )
)

/**
 * The metadata voice.
 *
 * Every wireframe sets secondary detail — `CNG · 2 seats left · leaves 9:50am`,
 * `yesterday · ৳45 · cash`, `boards near Kazipara · 1 seat` — in a monospace face.
 * That is doing real work, not styling: these lines are dense strings of numbers,
 * times and codes, and a fixed advance width stops a column of them from ragging.
 *
 * Bengali has no monospace coverage in the platform fonts, so this deliberately
 * falls back to the sans family when the active language is Bangla — see
 * [metaTextStyle]. Forcing Monospace there would render Bangla in a substituted face.
 */
val MetaMonoStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    lineHeight = 18.sp,
    letterSpacing = (-0.2).sp
)

val MetaSansStyle = MetaMonoStyle.copy(fontFamily = Sans, letterSpacing = 0.sp)

/** Small caps section label — `MOBILE NUMBER`, `FARE BREAKDOWN`, `PAY WITH`. */
val EyebrowStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Medium,
    fontSize = 11.sp,
    lineHeight = 15.sp,
    letterSpacing = 1.0.sp
)

/** Numeric emphasis used for fares and earnings, where the figure is the message. */
val MoneyTextStyle = TextStyle(
    fontFamily = Sans,
    fontWeight = FontWeight.Bold,
    fontSize = 28.sp,
    lineHeight = 34.sp,
    letterSpacing = (-0.5).sp,
    textAlign = TextAlign.Start
)
