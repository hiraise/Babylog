import Foundation
import Testing
@testable import BabyLog

/** Источники справки о сне — постоянные DOI-ссылки, открываемые только по нажатию. */
struct SleepHelpTest {
    @Test func sourcesArePersistentDoiLinks() {
        #expect(sleepSources.map(\.url) == [
            "https://doi.org/10.1542/peds.113.6.e570",
            "https://doi.org/10.1016/j.sleep.2009.04.012",
        ])
        #expect(sleepSources.allSatisfy { URL(string: $0.url) != nil })
    }

    @Test func helpUsesBisqNightHours() {
        #expect(nightStartHour == 19)
        #expect(nightEndHour == 7)
    }
}
