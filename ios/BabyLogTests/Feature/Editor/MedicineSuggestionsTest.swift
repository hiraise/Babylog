import Testing
@testable import BabyLog

struct MedicineSuggestionsTest {
    @Test func pastMedicinesAreDistinctRecentFirstInLatestSpelling() {
        let events: [any BabyEvent] = [
            Medicine(childId: 1, start: 1_000, name: "нурофен"),
            Medicine(childId: 1, start: 4_000, name: " Нурофен "),
            Diaper(childId: 1, start: 5_000, kind: .wet),
            Medicine(childId: 1, start: 3_000, name: "Витамин D"),
            Medicine(childId: 1, start: 2_000, name: "Эспумизан")
        ]
        #expect(pastMedicines(events: events).map(\.name) == ["Нурофен", "Витамин D", "Эспумизан"])
    }

    @Test func pastMedicineKeepsLatestNonBlankNote() {
        let events: [any BabyEvent] = [
            Medicine(childId: 1, start: 1_000, name: "Витамин D", note: "2 капли"),
            Medicine(childId: 1, start: 2_000, name: "витамин d", note: " 1 капля "),
            Medicine(childId: 1, start: 3_000, name: "Витамин D", note: " "),
            Medicine(childId: 1, start: 4_000, name: "Нурофен")
        ]
        #expect(pastMedicines(events: events) == [PastMedicine("Нурофен"), PastMedicine("Витамин D", note: "1 капля")])
    }

    @Test func namesStartingWithTypedLettersComeBeforeWordMatches() {
        let medicines = self.medicines("Витамин D", "Дюфалак", "Нурофен", "Детский Панадол")
        #expect(suggested(medicines, "д") == ["Дюфалак", "Детский Панадол"])
        #expect(suggested(medicines, "D") == ["Витамин D"])
        #expect(suggested(medicines, "пан") == ["Детский Панадол"])
    }

    @Test func caseAndYoAreIgnored() {
        let medicines = self.medicines("Ёлочка", "НУРОФЕН")
        #expect(suggested(medicines, "ел") == ["Ёлочка"])
        #expect(suggested(medicines, "нур") == ["НУРОФЕН"])
    }

    @Test func nothingIsSuggestedForEmptyInputOrFullyTypedName() {
        let medicines = self.medicines("Нурофен", "Нурофен форте")
        #expect(suggested(medicines, " ").isEmpty)
        #expect(suggested(medicines, "нурофен ") == ["Нурофен форте"])
        #expect(suggested(medicines, "Нурофен форте").isEmpty)
    }

    @Test func atMostThreeSuggestionsRecentFirst() {
        let medicines = self.medicines("Аква Марис", "Аквадетрим", "Амоксиклав", "Арбидол")
        #expect(suggested(medicines, "а") == ["Аква Марис", "Аквадетрим", "Амоксиклав"])
    }

    @Test func suggestionFillsEmptyNoteOrReplacesOneItFilledItself() {
        let vitamin = PastMedicine("Витамин D", note: "1 капля")
        let nurofen = PastMedicine("Нурофен", note: "2,5 мл")
        #expect(noteAfterSuggestion(note: "", autoNote: "", picked: vitamin) == "1 капля")
        #expect(noteAfterSuggestion(note: " ", autoNote: "", picked: vitamin) == "1 капля")
        #expect(noteAfterSuggestion(note: "1 капля", autoNote: "1 капля", picked: nurofen) == "2,5 мл")
        #expect(noteAfterSuggestion(note: "1 капля", autoNote: "1 капля", picked: PastMedicine("Эспумизан")).isEmpty)
    }

    @Test func suggestionKeepsNoteTypedOrEditedByHand() {
        let vitamin = PastMedicine("Витамин D", note: "1 капля")
        #expect(noteAfterSuggestion(note: "от колик", autoNote: "", picked: vitamin) == "от колик")
        #expect(noteAfterSuggestion(
            note: "1 капля утром",
            autoNote: "1 капля",
            picked: PastMedicine("Нурофен", note: "2,5 мл")
        ) == "1 капля утром")
    }

    private func medicines(_ names: String...) -> [PastMedicine] { names.map { PastMedicine($0) } }

    private func suggested(_ medicines: [PastMedicine], _ query: String) -> [String] {
        medicineSuggestions(medicines, query: query).map(\.name)
    }
}
