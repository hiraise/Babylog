import SwiftUI
import Testing
@testable import BabyLog

/** Поле названия лекарства с подсказками из прежних записей, светлая и тёмная тема. */
@MainActor
@Suite(.serialized)
struct MedicineSuggestionsScreenshotTest {
    @Test(arguments: [false, true]) func medicineSuggestionsSnapshot(dark: Bool) {
        let medicines = ["Витамин D", "Виферон", "Нурофен", "Вибуркол свечи детские гомеопатические"]
            .map { PastMedicine($0) }
        assertComponentSnapshot(
            name: dark ? "dark" : "light",
            style: dark ? .dark : .light,
            width: 393,
            height: 220
        ) {
            MedicineNameField(
                value: .constant("Ви"),
                suggestions: medicineSuggestions(medicines, query: "Ви"),
                onPick: { _ in }
            )
        }
    }
}
