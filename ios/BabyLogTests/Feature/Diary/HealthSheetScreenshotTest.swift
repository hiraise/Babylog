import Foundation
import Testing
@testable import BabyLog

@MainActor
@Suite(.serialized)
struct HealthSheetScreenshotTest {
    @Test(arguments: [false, true]) func healthChoicesWithPreviousValues(dark: Bool) {
        let child = Child(id: 1, name: "Маша", birthDate: nil, sex: .girl)
        let events: [any BabyEvent] = [
            Medicine(childId: 1, start: 1_760_499_600_000, name: "Нурофен", note: "2,5 мл"),
            Temperature(childId: 1, start: 1_760_503_200_000, tenths: 376, method: .ear)
        ]
        let state = DiaryUiState.of(children: [child], child: child, events: events)
        assertComponentSnapshot(
            name: dark ? "dark" : "light",
            style: dark ? .dark : .light,
            height: 360
        ) {
            HealthSheetContent(state: state, now: 1_760_541_600_000, onMedicine: {}, onTemperature: {})
        }
    }
}
