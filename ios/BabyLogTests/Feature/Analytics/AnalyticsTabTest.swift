import Foundation
import Testing
@testable import BabyLog

/** Навигация «Аналитики» и выбор столбика — через модели, как принято для iOS (ADR-0051). */
@MainActor
@Suite(.serialized)
struct AnalyticsTabTest {
    @Test func tileOpensSectionAndBackReturnsToOverview() {
        let model = AnalyticsContentModel()
        model.open(.weight)
        #expect(model.section == .weight)
        model.back()
        #expect(model.section == nil)
    }

    @Test func bottlesLiveInsideFeedings() {
        #expect(Metric.bottles.section == .feedings)
        #expect(AnalyticsSection.weight.usesPeriod == false)
        #expect(AnalyticsSection.height.usesPeriod == false)
        #expect(AnalyticsSection.rhythm.usesPeriod == false)
        #expect(AnalyticsSection.feedings.usesPeriod)
    }

    @Test func barSelectionAndPeriodOrChildChanges() {
        let model = AnalyticsContentModel()
        model.updateChild(1)
        model.open(.feedings)
        model.selectBar(metric: .feedings, index: 3)
        #expect(model.selection == "feedings:3")
        #expect(model.selectedIndex(metric: .feedings) == 3)
        #expect(model.selectedIndex(metric: .bottles) == nil)

        model.selectPeriod(.month)
        #expect(model.selection == nil)
        model.selectBar(metric: .feedings, index: 2)
        model.updateChild(2)
        #expect(model.selection == nil)
        #expect(model.section == .feedings)
    }

    @Test func rhythmAndMetricOpenDayInDiary() {
        var navigation = DiaryNavigationState(section: .analytics)
        navigation.openDay()
        #expect(navigation.section == .diary)
        #expect(navigation.diaryView == .days)
        #expect(navigation.analyticsSection == nil)
    }

    @Test func cryingCardAndTagOpenTheirDestinations() {
        var navigation = DiaryNavigationState(section: .diary, diaryView: .notes)
        navigation.openCryingAnalytics()
        #expect(navigation.section == .analytics)
        #expect(navigation.analyticsSection == .crying)

        navigation.openTag("колики")
        #expect(navigation.section == .diary)
        #expect(navigation.diaryView == .notes)
        #expect(navigation.notesFilter == .tag("колики"))
        #expect(navigation.analyticsSection == nil)
    }

    @Test func timelineUsesNightFeedingCryingAndNotes() {
        let events = analyticsSampleEvents()
        let model = analyticsModel(events: events, period: .week, now: analyticsNow, timeZone: analyticsZone, sex: .girl)
        let rows = timelineRows(model.rhythm!.days, chart: LightChartColors)
        #expect(rows.count == rhythmDays)
        #expect(rows.contains { !$0.bands.isEmpty })
        #expect(rows.contains { !$0.ticks.isEmpty })
        #expect(rows.contains { !$0.dots.isEmpty })
        #expect(rows.contains { !$0.pins.isEmpty })
    }
}
