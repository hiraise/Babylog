import SwiftUI
import Testing
@testable import BabyLog

@MainActor
@Suite(.serialized)
struct TimeAgoPickerScreenshotTest {
    @Test(arguments: [false, true]) func customMinutesInChipRow(dark: Bool) {
        let clock = TestClock(date: Date(timeIntervalSince1970: 1_792_057_200), timeZone: .utc)
        let model = TimeAgoPickerModel(now: clock.millis())
        model.beginCustom()
        model.updateCustom("90")

        assertComponentSnapshot(
            name: dark ? "dark" : "light",
            style: dark ? .dark : .light,
            width: 360,
            height: 240,
            clock: clock
        ) {
            TimeAgoPicker(model: model)
        }
    }
}

private extension TimeZone {
    static var utc: TimeZone { TimeZone(secondsFromGMT: 0)! }
}
