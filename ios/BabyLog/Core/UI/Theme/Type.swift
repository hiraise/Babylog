import SwiftUI

struct AppTextStyle {
    let font: Font
    let size: CGFloat
    let lineHeight: CGFloat
}

private func nunito(_ size: CGFloat, _ lineHeight: CGFloat) -> AppTextStyle {
    AppTextStyle(
        font: .custom("Nunito-ExtraBold", size: size, relativeTo: .body),
        size: size,
        lineHeight: lineHeight
    )
}

private func inter(_ size: CGFloat, _ lineHeight: CGFloat, weight: String = "Regular") -> AppTextStyle {
    AppTextStyle(
        font: .custom("Inter-\(weight)", size: size, relativeTo: .body),
        size: size,
        lineHeight: lineHeight
    )
}

/**
 Шкала шрифтов BabyLog (ADR-0010): `display*`, `headline*` и `labelLarge` — Nunito ExtraBold,
 остальное — Inter. У Nunito цифры одной ширины, поэтому таймер не прыгает.
 */
struct BabyLogTypography {
    let displayLarge = nunito(57, 64)
    let displayMedium = nunito(45, 52)
    let displaySmall = nunito(36, 44)
    let headlineLarge = nunito(32, 40)
    let headlineMedium = nunito(28, 36)
    let headlineSmall = nunito(24, 32)
    let titleLarge = inter(20, 26, weight: "Medium")
    let titleMedium = inter(16, 22, weight: "Medium")
    let titleSmall = inter(14, 20, weight: "Medium")
    let bodyLarge = inter(16, 22)
    let bodyMedium = inter(14, 20)
    let bodySmall = inter(12, 16)
    let labelLarge = nunito(16, 20)
    let labelMedium = inter(12, 16, weight: "Medium")
    let labelSmall = inter(11, 16, weight: "Medium")
}

let BabyLogTypeScale = BabyLogTypography()

private struct AppTextStyleModifier: ViewModifier {
    let style: AppTextStyle

    func body(content: Content) -> some View {
        content
            .font(style.font)
            .lineSpacing(style.lineHeight - style.size)
            .tracking(0)
    }
}

extension View {
    func appTextStyle(_ style: AppTextStyle) -> some View {
        modifier(AppTextStyleModifier(style: style))
    }
}
