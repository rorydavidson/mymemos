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

private fun openSans(weight: FontWeight, style: FontStyle = FontStyle.Normal) = Font(
    resId = if (style == FontStyle.Italic) R.font.opensans_italic_variable else R.font.opensans_variable,
    weight = weight,
    style = style,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight), FontVariation.width(100f)),
)

val OpenSans = FontFamily(
    openSans(FontWeight.Normal),
    openSans(FontWeight.Medium),
    openSans(FontWeight.SemiBold),
    openSans(FontWeight.Bold),
    openSans(FontWeight.Normal, FontStyle.Italic),
    openSans(FontWeight.SemiBold, FontStyle.Italic),
)

private val base = Typography()

private fun TextStyle.openSans(weight: FontWeight? = null, size: Float? = null, height: Float? = null) = copy(
    fontFamily = OpenSans,
    fontWeight = weight ?: fontWeight,
    fontSize = size?.sp ?: fontSize,
    lineHeight = height?.sp ?: lineHeight,
    letterSpacing = 0.sp,
)

val AppTypography = Typography(
    displayLarge = base.displayLarge.openSans(FontWeight.Bold),
    displayMedium = base.displayMedium.openSans(FontWeight.Bold),
    displaySmall = base.displaySmall.openSans(FontWeight.Bold),
    headlineLarge = base.headlineLarge.openSans(FontWeight.Bold),
    headlineMedium = base.headlineMedium.openSans(FontWeight.SemiBold),
    headlineSmall = base.headlineSmall.openSans(FontWeight.SemiBold),
    titleLarge = base.titleLarge.openSans(FontWeight.SemiBold),
    titleMedium = base.titleMedium.openSans(FontWeight.SemiBold),
    titleSmall = base.titleSmall.openSans(FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.openSans(FontWeight.Normal, 16f, 25f),
    bodyMedium = base.bodyMedium.openSans(FontWeight.Normal, 14f, 21f),
    bodySmall = base.bodySmall.openSans(FontWeight.Normal),
    labelLarge = base.labelLarge.openSans(FontWeight.SemiBold),
    labelMedium = base.labelMedium.openSans(FontWeight.Medium),
    labelSmall = base.labelSmall.openSans(FontWeight.Medium),
)
