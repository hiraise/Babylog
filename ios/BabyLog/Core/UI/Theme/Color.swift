import SwiftUI
import UIKit

extension Color {
    static func argb(_ value: UInt32) -> Color {
        rgb(
            Int((value >> 16) & 0xFF),
            Int((value >> 8) & 0xFF),
            Int(value & 0xFF)
        )
    }

    static func rgb(_ red: Int, _ green: Int, _ blue: Int) -> Color {
        Color(
            .sRGB,
            red: Double(red) / 255,
            green: Double(green) / 255,
            blue: Double(blue) / 255,
            opacity: 1
        )
    }

    var rgba: (red: Double, green: Double, blue: Double, alpha: Double) {
        var red: CGFloat = 0
        var green: CGFloat = 0
        var blue: CGFloat = 0
        var alpha: CGFloat = 0
        UIColor(self).getRed(&red, green: &green, blue: &blue, alpha: &alpha)
        return (Double(red), Double(green), Double(blue), Double(alpha))
    }

    var luminance: Double {
        let channels = [rgba.red, rgba.green, rgba.blue].map { channel in
            channel <= 0.04045 ? channel / 12.92 : pow((channel + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2]
    }

    func compositeOver(_ background: Color) -> Color {
        let foreground = rgba
        let back = background.rgba
        let alpha = foreground.alpha + back.alpha * (1 - foreground.alpha)
        guard alpha > 0 else { return .clear }
        func channel(_ first: Double, _ second: Double) -> Double {
            (first * foreground.alpha + second * back.alpha * (1 - foreground.alpha)) / alpha
        }
        return Color(
            .sRGB,
            red: channel(foreground.red, back.red),
            green: channel(foreground.green, back.green),
            blue: channel(foreground.blue, back.blue),
            opacity: alpha
        )
    }
}

struct BabyLogColorScheme {
    let primary: Color
    let onPrimary: Color
    let primaryContainer: Color
    let onPrimaryContainer: Color
    let inversePrimary: Color
    let secondary: Color
    let onSecondary: Color
    let secondaryContainer: Color
    let onSecondaryContainer: Color
    let tertiary: Color
    let onTertiary: Color
    let tertiaryContainer: Color
    let onTertiaryContainer: Color
    let background: Color
    let onBackground: Color
    let surface: Color
    let onSurface: Color
    let surfaceVariant: Color
    let onSurfaceVariant: Color
    let surfaceTint: Color
    let inverseSurface: Color
    let inverseOnSurface: Color
    let error: Color
    let onError: Color
    let errorContainer: Color
    let onErrorContainer: Color
    let outline: Color
    let outlineVariant: Color
    let scrim: Color
    let surfaceBright: Color
    let surfaceDim: Color
    let surfaceContainerLowest: Color
    let surfaceContainerLow: Color
    let surfaceContainer: Color
    let surfaceContainerHigh: Color
    let surfaceContainerHighest: Color
    let primaryFixed: Color
    let primaryFixedDim: Color
    let onPrimaryFixed: Color
    let onPrimaryFixedVariant: Color
    let secondaryFixed: Color
    let secondaryFixedDim: Color
    let onSecondaryFixed: Color
    let onSecondaryFixedVariant: Color
    let tertiaryFixed: Color
    let tertiaryFixedDim: Color
    let onTertiaryFixed: Color
    let onTertiaryFixedVariant: Color
}

// Fixed-роли по смыслу Material 3 не зависят от темы: одни и те же цвета во всех схемах, светлой и тёмных.
let PrimaryFixed = Color.argb(0xFFC9EDC5)
let PrimaryFixedDim = Color.argb(0xFF9BD896)
let OnPrimaryFixed = Color.argb(0xFF002204)
let OnPrimaryFixedVariant = Color.argb(0xFF1D5A1C)
let SecondaryFixed = Color.argb(0xFFD8E8D3)
let SecondaryFixedDim = Color.argb(0xFFBCCCB7)
let OnSecondaryFixed = Color.argb(0xFF121F10)
let OnSecondaryFixedVariant = Color.argb(0xFF3B4B38)
let TertiaryFixed = Color.argb(0xFFFFDEA6)
let TertiaryFixedDim = Color.argb(0xFFF0C06A)
let OnTertiaryFixed = Color.argb(0xFF261900)
let OnTertiaryFixedVariant = Color.argb(0xFF5D4200)

/**
 Светлая палитра по макету HiMom: светлый фон, белые карточки, зелёный акцент.
 `surfaceContainerLow` — карточки, `surfaceContainer` — меню, `surfaceContainerHigh` — листы и пикеры.
 На `surfaceContainerHighest` не бывает акцентного текста: контраст `primary` и `error` там ниже 4,5:1.
 Акцент и второстепенный текст затемнены относительно макета ради контраста (ADR-0010).
 */
let LightColors = BabyLogColorScheme(
    primary: Color.argb(0xFF348333),
    onPrimary: Color.argb(0xFFFFFFFF),
    primaryContainer: Color.argb(0xFFD5EFD1),
    onPrimaryContainer: Color.argb(0xFF0B3A0B),
    inversePrimary: Color.argb(0xFF6CC96B),
    secondary: Color.argb(0xFF4F6B4C),
    onSecondary: Color.argb(0xFFFFFFFF),
    secondaryContainer: Color.argb(0xFFDCEBD8),
    onSecondaryContainer: Color.argb(0xFF142513),
    tertiary: Color.argb(0xFF7A5900),
    onTertiary: Color.argb(0xFFFFFFFF),
    tertiaryContainer: Color.argb(0xFFFEC874),
    onTertiaryContainer: Color.argb(0xFF2A1C00),
    background: Color.argb(0xFFF9FAFB),
    onBackground: Color.argb(0xFF040415),
    surface: Color.argb(0xFFF9FAFB),
    onSurface: Color.argb(0xFF040415),
    surfaceVariant: Color.argb(0xFFF2F2F2),
    onSurfaceVariant: Color.argb(0xFF6E6E6E),
    surfaceTint: Color.argb(0xFFF9FAFB),
    inverseSurface: Color.argb(0xFF29323C),
    inverseOnSurface: Color.argb(0xFFF2F4F5),
    error: Color.argb(0xFFC93C3C),
    onError: Color.argb(0xFFFFFFFF),
    errorContainer: Color.argb(0xFFFFE0DE),
    onErrorContainer: Color.argb(0xFF5C1210),
    outline: Color.argb(0xFF8C8C8C),
    outlineVariant: Color.argb(0xFFE2E2E2),
    scrim: Color.argb(0xFF000000),
    surfaceBright: Color.argb(0xFFFFFFFF),
    surfaceDim: Color.argb(0xFFF2F2F2),
    surfaceContainerLowest: Color.argb(0xFFFFFFFF),
    surfaceContainerLow: Color.argb(0xFFFFFFFF),
    surfaceContainer: Color.argb(0xFFFFFFFF),
    surfaceContainerHigh: Color.argb(0xFFFFFFFF),
    surfaceContainerHighest: Color.argb(0xFFF2F2F2),
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

/** Тёмная палитра «Классика»: тёмно-серый фон, карточки `#29323C`, зелёный акцент макета. */
let ClassicDarkColors = BabyLogColorScheme(
    primary: Color.argb(0xFF49B848),
    onPrimary: Color.argb(0xFF0C1F0C),
    primaryContainer: Color.argb(0xFF1F4A1E),
    onPrimaryContainer: Color.argb(0xFFC6EFC2),
    inversePrimary: Color.argb(0xFF2C6F2B),
    secondary: Color.argb(0xFFB5CDB0),
    onSecondary: Color.argb(0xFF1F331C),
    secondaryContainer: Color.argb(0xFF34472F),
    onSecondaryContainer: Color.argb(0xFFD2E9CC),
    tertiary: Color.argb(0xFFFEC874),
    onTertiary: Color.argb(0xFF3A2800),
    tertiaryContainer: Color.argb(0xFF5A4100),
    onTertiaryContainer: Color.argb(0xFFFFDEA6),
    background: Color.argb(0xFF22272C),
    onBackground: Color.argb(0xFFFFFFFF),
    surface: Color.argb(0xFF22272C),
    onSurface: Color.argb(0xFFFFFFFF),
    surfaceVariant: Color.argb(0xFF36414D),
    onSurfaceVariant: Color.argb(0xFFB0B0B0),
    surfaceTint: Color.argb(0xFF22272C),
    inverseSurface: Color.argb(0xFFEEF1F3),
    inverseOnSurface: Color.argb(0xFF22272C),
    error: Color.argb(0xFFFF8A80),
    onError: Color.argb(0xFF4A0E0B),
    errorContainer: Color.argb(0xFF6B1E1B),
    onErrorContainer: Color.argb(0xFFFFDAD6),
    outline: Color.argb(0xFF8B949D),
    outlineVariant: Color.argb(0xFF3B4652),
    scrim: Color.argb(0xFF000000),
    surfaceBright: Color.argb(0xFF2F3944),
    surfaceDim: Color.argb(0xFF1C2025),
    surfaceContainerLowest: Color.argb(0xFF1C2025),
    surfaceContainerLow: Color.argb(0xFF29323C),
    surfaceContainer: Color.argb(0xFF29323C),
    surfaceContainerHigh: Color.argb(0xFF2F3944),
    surfaceContainerHighest: Color.argb(0xFF36414D),
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
