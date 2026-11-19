import Foundation
import SwiftUI
import Testing
@testable import BabyLog

@MainActor
@Suite(.serialized)
struct ChildSelectorTest {
    private let utc = TimeZone(secondsFromGMT: 0)!

    @Test func infoCombinesAgeWeightAndHeight() {
        let child = Child(id: 1, name: "Маша", birthDate: 1_779_235_200_000, sex: .girl)
        let today = Date(timeIntervalSince1970: 1_792_056_000)
        let weight = Weight(childId: 1, start: 1, grams: 6_350)
        let height = Height(childId: 1, start: 1, millimeters: 625)

        #expect(childInfo(child: child, lastWeight: weight, lastHeight: height, today: today, timeZone: utc) == "4 мес 25 дн. · 6,35 кг · 62,5 см")
        #expect(childInfo(child: Child(id: 2, name: "Петя", birthDate: nil), lastWeight: nil, lastHeight: nil, today: today, timeZone: utc) == nil)
    }

    @Test(arguments: [false, true]) func selector(dark: Bool) {
        let child = Child(id: 1, name: "Маша", birthDate: 1_779_235_200_000, sex: .girl)
        assertComponentSnapshot(name: dark ? "dark" : "light", style: dark ? .dark : .light) {
            ChildSelector(
                child: child,
                lastWeight: Weight(childId: 1, start: 1, grams: 6_350),
                lastHeight: Height(childId: 1, start: 1, millimeters: 625),
                now: 1_792_056_000_000,
                onClick: {}
            )
        }
    }
}
