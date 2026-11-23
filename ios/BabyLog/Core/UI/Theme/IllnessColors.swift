import SwiftUI

extension BabyLogColorScheme {
    /** Второй акцент наполовину поверх карточки: заметно, но без тревожного красного (ADR-0040). */
    var illnessContainer: Color {
        tertiaryContainer.opacity(0.5).compositeOver(surfaceContainerLow)
    }

    /** Полоса дней болезни за столбиками; в светлой теме бледнее, потому что это фон, а не ряд. */
    var illnessBand: Color {
        tertiaryContainer
            .opacity(surfaceContainerLow.luminance > 0.5 ? 0.35 : 0.5)
            .compositeOver(surfaceContainerLow)
    }
}
