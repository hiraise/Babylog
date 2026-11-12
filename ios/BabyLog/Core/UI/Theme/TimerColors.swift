import SwiftUI

/** Фон и содержимое идущего таймера; в тёмной теме — тон акцента, а не сам акцент (ADR-0032). */
struct TimerColors {
    let container: Color
    let content: Color
}

func makeTimerColors(_ colors: BabyLogColorScheme, dark: Bool) -> TimerColors {
    dark
        ? TimerColors(container: colors.primaryContainer, content: colors.onPrimaryContainer)
        : TimerColors(container: colors.primary, content: colors.onPrimary)
}
