import Foundation
import Testing
@testable import BabyLog

@MainActor
struct ChildSheetTest {
    private let children = [
        Child(id: 1, name: "Маша", birthDate: nil, sex: .girl),
        Child(id: 2, name: "Петя", birthDate: nil, sex: .boy),
    ]

    @Test func nameIsRequiredTrimmedAndUnique() {
        let model = ChildSheetModel(initial: nil, children: children)
        #expect(!model.valid)
        model.name = " маша "
        #expect(model.taken)
        #expect(!model.valid)
        model.name = " Соня "
        #expect(model.valid)
        #expect(model.trimmedName == "Соня")
    }

    @Test func editingAllowsSameChildsOwnName() {
        let model = ChildSheetModel(initial: children[0], children: children)
        #expect(model.valid)
        model.name = "Петя"
        #expect(!model.valid)
    }

    @Test func sexCanChangeButFormDoesNotClearIt() {
        let model = ChildSheetModel(initial: children[0], children: children)
        model.sex = .boy
        #expect(model.sex == .boy)
        model.sex = .girl
        #expect(model.sex == .girl)
    }

    @Test func nameIsLimitedToFortyCharacters() {
        let model = ChildSheetModel(initial: nil, children: children)
        model.name = String(repeating: "а", count: 50)
        #expect(model.name.count == Child.maxNameLength)
    }
}
