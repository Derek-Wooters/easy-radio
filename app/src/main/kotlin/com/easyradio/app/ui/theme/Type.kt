package com.easyradio.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.easyradio.app.R

@OptIn(ExperimentalTextApi::class)
private fun nunitoWeight(axisWeight: Int, fontWeight: FontWeight) = Font(
    resId = R.font.nunito_variable,
    weight = fontWeight,
    variationSettings = FontVariation.Settings(FontVariation.weight(axisWeight)),
)

// docs/designs/1a-foundations.png: "Type -- Nunito"
val NunitoFamily = FontFamily(
    nunitoWeight(400, FontWeight.Normal),
    nunitoWeight(600, FontWeight.SemiBold),
    nunitoWeight(700, FontWeight.Bold),
    nunitoWeight(800, FontWeight.ExtraBold),
)

val EasyRadioTypography = Typography(
    headlineMedium = TextStyle(fontFamily = NunitoFamily, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp),
    titleLarge = TextStyle(fontFamily = NunitoFamily, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp),
    titleMedium = TextStyle(fontFamily = NunitoFamily, fontWeight = FontWeight.Bold, fontSize = 17.sp),
    bodyLarge = TextStyle(fontFamily = NunitoFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = NunitoFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    labelLarge = TextStyle(fontFamily = NunitoFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    labelSmall = TextStyle(fontFamily = NunitoFamily, fontWeight = FontWeight.Bold, fontSize = 11.sp),
)
