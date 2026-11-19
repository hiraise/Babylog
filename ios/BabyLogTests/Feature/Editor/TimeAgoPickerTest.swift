import Foundation
import Testing
@testable import BabyLog

@MainActor
struct TimeAgoPickerTest {
    private let now: Int64 = 1_792_057_200_000

    @Test func otherChipTurnsIntoMinutesAndUpdatesTime() {
        let model = TimeAgoPickerModel(now: now)
        model.select(15)
        #expect(model.minutesAgo == 15)

        model.beginCustom()
        #expect(model.minutesAgo == 0)
        #expect(model.custom == "")
        model.updateCustom("25")

        #expect(model.minutesAgo == 25)
        #expect(model.time() == now - 25 * 60_000)
    }

    @Test func hoursAreTypedAsMinutesAndCappedAtADay() {
        let model = TimeAgoPickerModel(now: now)
        model.beginCustom()
        model.updateCustom("90")
        #expect(model.minutesAgo == 90)
        #expect(model.hint == " · 1 ч 30 мин назад")

        model.updateCustom("2000")
        #expect(model.minutesAgo == maxMinutesAgo)
        #expect(model.hint == " · не больше суток")
    }

    @Test func presetChipClosesCustomField() {
        let model = TimeAgoPickerModel(now: now)
        model.beginCustom()
        model.updateCustom("7")
        model.select(30)

        #expect(model.minutesAgo == 30)
        #expect(model.custom == nil)
    }

    @Test func inputKeepsUpToFourDigits() {
        #expect(customMinutesInput("1 2,5") == "125")
        #expect(customMinutesInput("12345") == "1234")
        #expect(customMinutes("") == 0)
        #expect(customMinutes("9999") == maxMinutesAgo)
    }
}
