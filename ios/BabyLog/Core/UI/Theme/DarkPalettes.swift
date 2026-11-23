import SwiftUI

private struct Lch {
    let l: Double
    let c: Double
    let h: Double
}

private let lowest = 0.165
private let background = 0.195
private let card = 0.252
private let sheet = 0.287
private let highest = 0.327
private let divider = 0.36
private let outline = 0.63
private let mutedText = 0.765
private let text = 0.925
private let tone = 0.47
private let errorHue = 24.0

/**
 Спокойная тёмная палитра (ADR-0032). Светлота поверхностей, текста и рамок общая:
 палитры различаются оттенком и хромой. Акцент насыщеннее фона, а тон таймера светлее карточки.
 */
private func calmDarkScheme(
    neutralHue: Double,
    neutralChroma: Double,
    textHue: Double,
    textChroma: Double,
    accent: Lch,
    secondaryChroma: Double,
    tertiary: Lch,
    toneChroma: Double? = nil
) -> BabyLogColorScheme {
    func neutral(_ l: Double, _ c: Double? = nil) -> Color {
        oklch(l, c ?? neutralChroma, neutralHue)
    }
    func ofAccent(_ l: Double, _ c: Double) -> Color { oklch(l, c, accent.h) }
    func ofTertiary(_ l: Double, _ c: Double) -> Color { oklch(l, c, tertiary.h) }
    func ofError(_ l: Double, _ c: Double) -> Color { oklch(l, c, errorHue) }
    let textColor = oklch(text, textChroma, textHue)
    let backgroundColor = neutral(background)
    let cardColor = neutral(card)
    let sheetColor = neutral(sheet)
    let highestColor = neutral(highest)
    let lowestColor = neutral(lowest)
    return BabyLogColorScheme(
        primary: ofAccent(accent.l, accent.c),
        onPrimary: ofAccent(0.22, min(0.04, accent.c * 0.4)),
        primaryContainer: ofAccent(tone, toneChroma ?? accent.c),
        onPrimaryContainer: ofAccent(0.97, 0.02),
        inversePrimary: ofAccent(0.47, accent.c),
        secondary: ofAccent(0.80, secondaryChroma),
        onSecondary: ofAccent(0.22, secondaryChroma * 0.8),
        secondaryContainer: ofAccent(0.34, secondaryChroma * 0.9),
        onSecondaryContainer: ofAccent(0.92, secondaryChroma * 0.7),
        tertiary: ofTertiary(tertiary.l, tertiary.c),
        onTertiary: ofTertiary(0.22, 0.04),
        tertiaryContainer: ofTertiary(0.36, tertiary.c * 0.55),
        onTertiaryContainer: ofTertiary(0.92, tertiary.c * 0.4),
        background: backgroundColor,
        onBackground: textColor,
        surface: backgroundColor,
        onSurface: textColor,
        surfaceVariant: highestColor,
        onSurfaceVariant: oklch(mutedText, textChroma * 1.2, textHue),
        surfaceTint: backgroundColor,
        inverseSurface: oklch(0.92, textChroma, textHue),
        inverseOnSurface: neutral(0.25),
        error: ofError(0.74, 0.12),
        onError: ofError(0.22, 0.05),
        errorContainer: ofError(0.36, 0.09),
        onErrorContainer: ofError(0.92, 0.04),
        outline: neutral(outline, neutralChroma * 1.2),
        outlineVariant: neutral(divider),
        scrim: .black,
        surfaceBright: sheetColor,
        surfaceDim: lowestColor,
        surfaceContainerLowest: lowestColor,
        surfaceContainerLow: cardColor,
        surfaceContainer: cardColor,
        surfaceContainerHigh: sheetColor,
        surfaceContainerHighest: highestColor,
        primaryFixed: PrimaryFixed,
        primaryFixedDim: PrimaryFixedDim,
        onPrimaryFixed: OnPrimaryFixed,
        onPrimaryFixedVariant: OnPrimaryFixedVariant,
        secondaryFixed: SecondaryFixed,
        secondaryFixedDim: SecondaryFixedDim,
        onSecondaryFixed: OnSecondaryFixed,
        onSecondaryFixedVariant: OnSecondaryFixedVariant,
        tertiaryFixed: TertiaryFixed,
        tertiaryFixedDim: TertiaryFixedDim,
        onTertiaryFixed: OnTertiaryFixed,
        onTertiaryFixedVariant: OnTertiaryFixedVariant
    )
}

private let DuskColors = calmDarkScheme(
    neutralHue: 285, neutralChroma: 0.020, textHue: 290, textChroma: 0.012,
    accent: Lch(l: 0.74, c: 0.119, h: 292), secondaryChroma: 0.035,
    tertiary: Lch(l: 0.82, c: 0.078, h: 60)
)
private let NightlightColors = calmDarkScheme(
    neutralHue: 55, neutralChroma: 0.011, textHue: 80, textChroma: 0.018,
    accent: Lch(l: 0.76, c: 0.119, h: 62), secondaryChroma: 0.035,
    tertiary: Lch(l: 0.80, c: 0.065, h: 145), toneChroma: 0.095
)
private let SageColors = calmDarkScheme(
    neutralHue: 165, neutralChroma: 0.012, textHue: 115, textChroma: 0.012,
    accent: Lch(l: 0.75, c: 0.098, h: 150), secondaryChroma: 0.03,
    tertiary: Lch(l: 0.82, c: 0.078, h: 60)
)
private let PowderColors = calmDarkScheme(
    neutralHue: 350, neutralChroma: 0.016, textHue: 25, textChroma: 0.012,
    accent: Lch(l: 0.74, c: 0.105, h: 8), secondaryChroma: 0.03,
    tertiary: Lch(l: 0.82, c: 0.065, h: 290)
)

func darkColors(_ palette: DarkPalette) -> BabyLogColorScheme {
    switch palette {
    case .dusk: DuskColors
    case .nightlight: NightlightColors
    case .sage: SageColors
    case .powder: PowderColors
    case .classic: ClassicDarkColors
    }
}

func darkChartColors(_ palette: DarkPalette) -> ChartColors {
    palette == .classic ? ClassicDarkChartColors : CalmDarkChartColors
}
