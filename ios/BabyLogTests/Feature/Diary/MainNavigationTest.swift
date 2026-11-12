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
