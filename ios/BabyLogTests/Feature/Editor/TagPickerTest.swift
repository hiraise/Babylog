import Testing
@testable import BabyLog

/** «+ своя метка» (ADR-0031): подсказки другого типа следуют за вводом, касание выбирает метку. */
@MainActor
struct TagPickerTest {
    @Test func hintsOfOtherKindFollowTypedLettersAndPickTag() {
        let model = TagPickerModel(
            title: "Метки",
            options: ["новое"],
            selected: [],
            addLabel: "своя метка",
            other: ["питание мамы", "перевозбуждение"],
            otherTitle: "Метки заметок:"
        )
        model.beginAdding()
        #expect(model.hints == ["питание мамы", "перевозбуждение"])

        model.updateDraft("мам")
        #expect(model.hints == ["питание мамы"])
        model.pick(model.hints[0])

        #expect(model.selected == ["питание мамы"])
        #expect(!model.adding)
        #expect(model.draft.isEmpty)
        #expect(model.shownTags == ["новое", "питание мамы"])
    }
}
