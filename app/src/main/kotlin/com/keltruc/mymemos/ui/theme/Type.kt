package com.keltruc.mymemos.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.keltruc.mymemos.R

/**
 * Google Sans Flex (OFL 1.1, googlefonts/googlesans-flex). One variable file carries
 * weight, width, optical size and slant, so italics come from the slnt axis.
 */
private fun googleSans(weight: FontWeight, style: FontStyle = FontStyle.Normal, opticalSize: Float) = Font(
    resId = R.font.googlesansflex,
    weight = weight,
    style = style,
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight.weight),
        FontVariation.Setting("opsz", opticalSize),
        FontVariation.slant(if (style == FontStyle.Italic) -10f else 0f),
    ),
)

private fun family(opticalSize: Float) = FontFamily(
    googleSans(FontWeight.Normal, opticalSize = opticalSize),
    googleSans(FontWeight.Medium, opticalSize = opticalSize),
    googleSans(FontWeight.SemiBold, opticalSize = opticalSize),
    googleSans(FontWeight.Bold, opticalSize = opticalSize),
    googleSans(FontWeight.Normal, FontStyle.Italic, opticalSize),
    googleSans(FontWeight.SemiBold, FontStyle.Italic, opticalSize),
)

/** Text sizes use the small optical size; headlines the large one, for tighter display forms. */
val GoogleSansText = family(opticalSize = 17f)
val GoogleSansDisplay = family(opticalSize = 36f)

private val base = Typography()

private fun TextStyle.with(family: FontFamily, weight: FontWeight, size: Float? = null, height: Float? = null) = copy(
    fontFamily = family,
    fontWeight = weight,
    fontSize = size?.sp ?: fontSize,
    lineHeight = height?.sp ?: lineHeight,
    letterSpacing = 0.sp,
)

val AppTypography = Typography(
    displayLarge = base.displayLarge.with(GoogleSansDisplay, FontWeight.Bold),
    displayMedium = base.displayMedium.with(GoogleSansDisplay, FontWeight.Bold),
    displaySmall = base.displaySmall.with(GoogleSansDisplay, FontWeight.Bold),
    headlineLarge = base.headlineLarge.with(GoogleSansDisplay, FontWeight.Bold),
    headlineMedium = base.headlineMedium.with(GoogleSansDisplay, FontWeight.SemiBold),
    headlineSmall = base.headlineSmall.with(GoogleSansDisplay, FontWeight.SemiBold),
    titleLarge = base.titleLarge.with(GoogleSansText, FontWeight.SemiBold),
    titleMedium = base.titleMedium.with(GoogleSansText, FontWeight.SemiBold),
    titleSmall = base.titleSmall.with(GoogleSansText, FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.with(GoogleSansText, FontWeight.Normal, 17f, 26f),
    bodyMedium = base.bodyMedium.with(GoogleSansText, FontWeight.Normal, 14.5f, 21f),
    bodySmall = base.bodySmall.with(GoogleSansText, FontWeight.Normal),
    labelLarge = base.labelLarge.with(GoogleSansText, FontWeight.SemiBold),
    labelMedium = base.labelMedium.with(GoogleSansText, FontWeight.Medium),
    labelSmall = base.labelSmall.with(GoogleSansText, FontWeight.Medium),
)
