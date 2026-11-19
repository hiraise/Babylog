import Testing
@testable import BabyLog

/** Подсказки в листе лекарства: ввод, выбор подсказки, подстановка комментария и сохранение. */
@MainActor
struct MedicineSheetTest {
    @Test func tappingSuggestionPutsWholeNameAndHidesSuggestion() {
        let model = MedicineSheetModel(
            initial: nil,
            pastMedicines: [PastMedicine("Витамин D"), PastMedicine("Нурофен")]
        )
        model.name = "ви"
        #expect(model.suggestions.map(\.name) == ["Витамин D"])

        model.pick(model.suggestions[0])
        #expect(model.name == "Витамин D")
        #expect(model.suggestions.isEmpty)
        #expect(model.valid)
        #expect(model.trimmedName == "Витамин D")
    }

    @Test func suggestionsFollowTypedLetters() {
        let model = MedicineSheetModel(
            initial: nil,
            pastMedicines: [PastMedicine("Нурофен форте"), PastMedicine("Нурофен"), PastMedicine("Витамин D")]
        )
        model.name = "нурофен"
        #expect(model.suggestions.map(\.name) == ["Нурофен форте"])
    }

    @Test func suggestionFillsEmptyNoteAndAnotherSuggestionReplacesIt() {
        let model = MedicineSheetModel(
            initial: nil,
            pastMedicines: [PastMedicine("Витамин D", note: "1 капля"), PastMedicine("Виферон", note: "1 свеча")]
        )
        model.name = "ви"
        model.pick(model.suggestions[0])
        #expect(model.note == "1 капля")

        model.name = "ви"
        model.pick(model.suggestions[0])
        #expect(model.trimmedName == "Витамин D")
        #expect(model.trimmedNote == "1 капля")

        model.name = "виф"
        model.pick(model.suggestions[0])
        #expect(model.trimmedName == "Виферон")
        #expect(model.trimmedNote == "1 свеча")
    }

    @Test func suggestionKeepsNoteTypedByHand() {
        let model = MedicineSheetModel(
            initial: nil,
            pastMedicines: [PastMedicine("Витамин D", note: "1 капля")]
        )
        model.note = "от колик"
        model.name = "ви"
        model.pick(model.suggestions[0])
        #expect(model.trimmedName == "Витамин D")
        #expect(model.trimmedNote == "от колик")
    }
}
