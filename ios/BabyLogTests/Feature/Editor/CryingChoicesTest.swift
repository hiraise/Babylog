import Foundation
import Testing
@testable import BabyLog

struct CryingChoicesTest {
    @Test func usedTagsComeFirstByFrequencyThenDefaultsWithoutRepeats() {
        let events: [any BabyEvent] = [
            Crying(id: 1, childId: 1, start: 0, end: 1, reasons: ["зубки", "срыгивание"], helped: ["соска"]),
            Crying(id: 2, childId: 1, start: 10, end: 11, reasons: ["срыгивание"]),
            Crying(id: 3, childId: 1, start: 20, end: 21, reasons: ["колики"], helped: ["прогулка"]),
        ]

        let choices = cryingChoices(events: events)
        #expect(choices.reasons.shown == ["срыгивание", "зубки", "колики", "усталость", "голод", "живот", "перевозбуждение"])
        #expect(choices.helped == ["прогулка", "соска", "укачивание", "на руках", "грудь", "тёплое на живот", "белый шум"])
    }

    @Test func chipsAreOwnKindAndOtherKindsTagsAreHints() {
        let events: [any BabyEvent] = [
            Note(id: 1, childId: 1, start: 0, tags: ["питание мамы"]),
            Note(id: 2, childId: 1, start: 10, tags: ["питание мамы", "зубки"]),
            Crying(id: 3, childId: 1, start: 20, end: 21, reasons: ["колики", "перевозбуждение"]),
        ]

        let crying = cryingChoices(events: events).reasons
        #expect(crying.shown == ["колики", "перевозбуждение", "усталость", "голод", "живот", "зубки"])
        #expect(crying.other == ["питание мамы"])
        let note = noteTagChoices(events: events)
        #expect(note.shown == ["питание мамы", "зубки", "новое", "сыпь", "прививка", "колики"])
        #expect(note.other == ["перевозбуждение"])
    }

    @Test func noMoreThanTenReasonsAndEightWaysToCalm() {
        var events: [any BabyEvent] = []
        for index in 1...12 {
            let start = Int64(index * 10)
            events.append(Crying(id: Int64(index), childId: 1, start: start, end: start + 1, reasons: ["метка \(index)"], helped: ["способ \(index)"]))
        }
        let choices = cryingChoices(events: events)
        #expect(choices.reasons.shown.count == 10)
        #expect(choices.helped.count == 8)
    }
}
