import Foundation
import Testing
@testable import BabyLog

struct NotesTest {
    private let zone = TimeZone(identifier: "Europe/Moscow")!
    private let today = notesTestDate(2026, 10, 18)
    private let minute: Int64 = 60_000

    private func at(_ date: Date, _ hour: Int, _ minute: Int = 0) -> Int64 {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = zone
        let value = calendar.date(bySettingHour: hour, minute: minute, second: 0, of: date)!
        return Int64(value.timeIntervalSince1970 * 1000)
    }

    private func day(_ offset: Int) -> Date {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = zone
        return calendar.date(byAdding: .day, value: offset, to: today)!
    }

    private var now: Int64 { at(today, 20) }

    private func cry(
        _ id: Int64,
        _ date: Date,
        _ hour: Int,
        _ minute: Int = 0,
        reasons: [String] = [],
        helped: [String] = []
    ) -> Crying {
        Crying(
            id: id,
            childId: 1,
            start: at(date, hour, minute),
            end: at(date, hour, minute) + 20 * self.minute,
            reasons: reasons,
            helped: helped
        )
    }

    @Test func feedHasOnlyCryingAndNotesByDaysNewestFirst() {
        let events: [any BabyEvent] = [
            cry(1, day(-1), 19, reasons: ["колики"]),
            Note(id: 2, childId: 1, start: at(today, 11, 20), tags: ["новое"], note: "Перевернулась!"),
            Diaper(id: 3, childId: 1, start: at(today, 12), kind: .wet),
            cry(4, today, 18, 32, reasons: ["колики", "усталость"]),
        ]

        let feed = notesFeed(events: events, filter: .all, timeZone: zone)
        #expect(feed.map(\.date) == [today, day(-1)])
        #expect(feed.first?.records.map(\.id) == [4, 2])
        #expect(notesFeed(events: events, filter: .cryingOnly, timeZone: zone).flatMap { $0.records.map(\.id) } == [4, 1])
        #expect(notesFeed(events: events, filter: .notesOnly, timeZone: zone).flatMap { $0.records.map(\.id) } == [2])
        #expect(notesFeed(events: events, filter: .tag("новое"), timeZone: zone).flatMap { $0.records.map(\.id) } == [2])
        #expect(filterTags(events: events) == ["колики", "новое", "усталость"])
    }

    @Test func tagSummaryCountsCryingAndNotesAndMarksLastFourWeeks() {
        let events: [any BabyEvent] = [
            cry(1, today, 18, reasons: ["колики", "усталость"], helped: ["укачивание"]),
            cry(2, day(-1), 19, reasons: ["колики"], helped: ["укачивание", "грудь"]),
            cry(3, day(-3), 20, 30, reasons: ["колики"], helped: ["тёплое на живот"]),
            Note(id: 4, childId: 1, start: at(day(-1), 21, 50), tags: ["питание мамы", "колики"], note: "Ела капусту"),
            cry(5, day(-35), 14, reasons: ["колики"]),
        ]

        let summary = tagSummary(events: events, tag: "колики", now: now, timeZone: zone)
        #expect(summary.cryings == 4)
        #expect(summary.notes == 1)
        #expect(summary.days.count == 28)
        #expect(summary.days.first?.date == notesTestDate(2026, 9, 21))
        let yesterday = summary.days.first { $0.date == day(-1) }
        #expect(yesterday?.cryings == 1)
        #expect(yesterday?.note == true)
        #expect(summary.days.last?.today == true)
        #expect(!summary.days.contains { $0.future })
        #expect(summary.peak == PeakHours(from: 18, to: 21, count: 3))
        #expect(summary.helped == [
            TagCount(name: "укачивание", count: 2),
            TagCount(name: "грудь", count: 1),
            TagCount(name: "тёплое на живот", count: 1),
        ])
        #expect(summary.together == [
            TagCount(name: "питание мамы", count: 1),
            TagCount(name: "усталость", count: 1),
        ])
    }

    @Test func beforeTaggedCryingShowsAwakeAndFeedingIntervals() {
        let date = day(-1)
        let events: [any BabyEvent] = [
            Sleep(id: 1, childId: 1, start: at(date, 6), end: at(date, 8)),
            Sleep(id: 2, childId: 1, start: at(date, 9), end: at(date, 10)),
            Sleep(id: 3, childId: 1, start: at(date, 11), end: at(date, 12)),
            Feeding(id: 4, childId: 1, start: at(date, 14), end: at(date, 14, 20), breast: .left),
            cry(5, date, 15, reasons: ["усталость"]),
        ]

        let summary = tagSummary(events: events, tag: "усталость", now: now, timeZone: zone)
        #expect(summary.awakeKnown == 1)
        #expect(summary.longerAwake == 1)
        #expect(summary.sinceFeeding == 40 * minute)
    }

    @Test func currentWeekDaysAfterTodayAreEmpty() {
        let wednesday = notesTestDate(2026, 10, 14)
        let summary = tagSummary(
            events: [Note(id: 1, childId: 1, start: at(wednesday, 9), tags: ["зубки"])],
            tag: "зубки",
            now: at(wednesday, 10),
            timeZone: zone
        )

        #expect(summary.days.last?.date == notesTestDate(2026, 10, 18))
        #expect(summary.days.filter(\.future).count == 4)
        #expect(summary.cryings == 0)
        #expect(summary.notes == 1)
    }

    @Test func illnessesAreNewestFirstAndActiveOneIsOnTop() {
        let past = Illness(
            id: 1,
            childId: 1,
            start: at(day(-11), 19, 30),
            end: at(day(-4), 20),
            names: [IllnessName("ОРВИ"), IllnessName("Отит", since: day(-8))]
        )
        let going = Illness(id: 2, childId: 1, start: at(day(-2), 8))
        let events: [any BabyEvent] = [
            past,
            going,
            Temperature(id: 3, childId: 1, start: at(day(-8), 3), tenths: 392),
            Temperature(id: 4, childId: 1, start: at(day(-9), 3), tenths: 386),
            Medicine(id: 5, childId: 1, start: at(day(-10), 9), name: "Ибупрофен"),
            Medicine(id: 6, childId: 1, start: at(day(-8), 12), name: "Амоксициллин"),
        ]

        let entries = illnessEntries(events: events, now: now, timeZone: zone)
        #expect(entries.map { $0.illness.id } == [2, 1])
        #expect(entries[0].days == 3)
        #expect(entries[0].range == "с 16 окт.")
        #expect(entries[1].title == "ОРВИ, Отит")
        #expect(entries[1].range == "7–14 окт.")
        #expect(entries[1].days == 8)
        #expect(entries[1].maxTenths == 392)
        #expect(entries[1].medicines == ["Ибупрофен", "Амоксициллин"])
    }

    @Test func illnessesFilterShowsNoFeedRecords() {
        #expect(!NotesFilter.illnesses.matches(Note(id: 1, childId: 1, start: now, note: "сыпь")))
    }
}

private func notesTestDate(_ year: Int, _ month: Int, _ day: Int) -> Date {
    var calendar = Calendar(identifier: .gregorian)
    calendar.timeZone = TimeZone(identifier: "Europe/Moscow")!
    return calendar.date(from: DateComponents(year: year, month: month, day: day))!
}
