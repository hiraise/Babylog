import SwiftUI
import Testing
@testable import BabyLog

@MainActor
@Suite(.serialized)
struct MainNavigationTest {
    @Test func sectionsKeepAndroidOrderAndTitles() {
        #expect(MainSection.allCases.map(\.title) == ["Сегодня", "Дневник", "Развитие", "Аналитика"])
        #expect(DiaryView.allCases.map(\.title) == ["По дням", "Заметки"])
    }

    @Test func tagOpensNotesAndBackClearsFilterBeforeToday() {
        var navigation = DiaryNavigationState(section: .analytics)
        navigation.openTag("колики")
        #expect(navigation.section == .diary)
        #expect(navigation.diaryView == .notes)
        #expect(navigation.notesFilter == .tag("колики"))

        let cleared = navigation.back()
        #expect(cleared)
        #expect(navigation.section == .diary)
        #expect(navigation.notesFilter == .all)
        let returned = navigation.back()
        #expect(returned)
        #expect(navigation.section == .today)
        let handled = navigation.back()
        #expect(!handled)
    }

    @Test(arguments: [false, true]) func navigationBar(dark: Bool) {
        assertComponentSnapshot(name: dark ? "dark" : "light", style: dark ? .dark : .light) {
            MainNavigationBar(selected: .diary, onSelect: { _ in }, developmentBadge: true)
        }
    }

    @Test func diarySectionUsesSegmentedChoice() {
        assertComponentSnapshot(name: "diary_section", style: .light) {
            DiarySection(view: .days, onView: { _ in }) { view in
                Text(view.title).frame(maxWidth: .infinity, maxHeight: .infinity)
            }
            .frame(height: 300)
        }
    }
}
