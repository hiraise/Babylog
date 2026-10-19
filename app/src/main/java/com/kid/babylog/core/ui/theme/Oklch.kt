package com.kid.babylog.core.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** Насколько канал может выйти за 0–1 до того, как цвет считается вне охвата sRGB. */
private const val GAMUT_TOLERANCE = 1e-4

/** Шаг, которым цвет вне охвата sRGB теряет хрому. */
private const val CHROMA_STEP = 0.002

/**
 * Цвет из OKLCH: светлота [l] (0–1), хрома [c], оттенок [h] в градусах. Пространство равномерно
 * для глаза: при одной [l] цвета любого оттенка одинаково светлые. Поэтому спокойные тёмные
 * палитры задаются в нём (ADR-0032). Цвет вне охвата sRGB теряет хрому, пока не войдёт в него.
 */
internal fun oklch(l: Double, c: Double, h: Double): Color {
    val radians = Math.toRadians(h)
    var chroma = c
    while (true) {
        val rgb = oklabToLinearSrgb(l, chroma * cos(radians), chroma * sin(radians))
        if (chroma <= 0 || rgb.all { it in -GAMUT_TOLERANCE..1 + GAMUT_TOLERANCE }) {
            val (red, green, blue) = rgb.map { (encodeSrgb(it.coerceIn(0.0, 1.0)) * 255).roundToInt() }
            return Color(red, green, blue)
        }
        chroma -= CHROMA_STEP
    }
}

/** OKLab → линейный sRGB (Björn Ottosson, 2020). */
private fun oklabToLinearSrgb(l: Double, a: Double, b: Double): List<Double> {
    val long = (l + 0.3963377774 * a + 0.2158037573 * b).pow(3)
    val medium = (l - 0.1055613458 * a - 0.0638541728 * b).pow(3)
    val short = (l - 0.0894841775 * a - 1.2914855480 * b).pow(3)
    return listOf(
        4.0767416621 * long - 3.3077115913 * medium + 0.2309699292 * short,
        -1.2684380046 * long + 2.6097574011 * medium - 0.3413193965 * short,
        -0.0041960863 * long - 0.7034186147 * medium + 1.7076147010 * short,
    )
}

/** Линейный канал sRGB → гамма-кодированный, оба 0–1. */
private fun encodeSrgb(linear: Double): Double =
    if (linear <= 0.0031308) 12.92 * linear else 1.055 * linear.pow(1 / 2.4) - 0.055
