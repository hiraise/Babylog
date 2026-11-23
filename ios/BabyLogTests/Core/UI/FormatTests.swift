import Foundation
import Testing
@testable import BabyLog

private let testZone = TimeZone(identifier: "Europe/Moscow")!

private func day(_ value: String) -> Date {
    let formatter = DateFormatter()
    formatter.locale = RU
    formatter.calendar = Calendar(identifier: .gregorian)
    formatter.timeZone = testZone
    formatter.dateFormat = "yyyy-MM-dd HH:mm"
    return formatter.date(from: value)!
}

private struct Feeding: FeedingEvent {
    let start: Int64
    let end: Int64?
    let breast: Breast
    let note: String = ""
}
private struct Diaper: DiaperEvent {
    let start: Int64
    let end: Int64? = nil
    let kind: DiaperKind
    let note: String = ""
}
private struct Bottle: BottleEvent {
    let start: Int64
    let end: Int64? = nil
    let volumeMl: Int
    let milk: Milk?
    let note: String = ""
}
private struct Crying: CryingEvent {
    let start: Int64
    let end: Int64?
    let reasons: [String]
    let helped: [String]
    let note: String = ""
}

struct FormatTests {
    @Test func durationTimerAndAgoMatchAndroid() {
        #expect(formatDuration(45_999) == "меньше минуты")
        #expect(formatDuration(45 * 60_000) == "45 мин")
        #expect(formatDuration(2 * 60 * 60_000) == "2 ч")
        #expect(formatDuration(8_100_000) == "2 ч 15 мин")
        #expect(formatAgo(30_000) == "только что")
        #expect(formatAgo(8_100_000) == "2 ч 15 мин назад")
        #expect(formatTimer(754_000) == "12:34")
        #expect(formatTimer(5_025_000) == "1:23:45")
    }

    @Test func russianPluralsAndSexMatchAndroid() {
        let cases = [1: "кормление", 2: "кормления", 4: "кормления", 5: "кормлений", 11: "кормлений", 12: "кормлений", 21: "кормление", 22: "кормления", 25: "кормлений"]
        for (number, expected) in cases {
            #expect(plural(number, one: "кормление", few: "кормления", many: "кормлений") == expected)
        }
        #expect(bySex(.girl, masculine: "родился", feminine: "родилась") == "родилась")
        #expect(bySex(nil, masculine: "родился", feminine: "родилась") == "родился")
        #expect(diaperLabel(.wet, sex: .girl) == "пописала")
    }

    @Test func genitiveNamesCoverEndingsExceptionsAndCase() {
        let names = [
            "Маша": "Маши", "Петя": "Пети", "Мария": "Марии", "Алиса": "Алисы",
            "Андрей": "Андрея", "Иван": "Ивана", "Любовь": "Любови", "Игорь": "Игоря",
            "Павел": "Павла", "Лев": "Льва", "Пётр": "Петра", "МАША": "МАШИ",
            "Анна-Мария": "Анны-Марии", "Кармен": "Кармен",
        ]
        for (name, expected) in names {
            #expect(genitiveName(name, sex: name == "Любовь" || name == "Кармен" ? .girl : nil) == expected)
        }
        #expect(genitiveName("Петя, младший", sex: nil) == nil)
        #expect(diaryTitle(" Маша ", sex: .girl) == "Дневник Маши")
        #expect(diaryTitle("Peter", sex: nil) == "Дневник: Peter")
    }

    @Test func measuresUseRussianDecimalComma() {
        #expect(formatWeight(850) == "850 г")
        #expect(formatWeight(4350) == "4,35 кг")
        #expect(formatWeight(4000) == "4 кг")
        #expect(formatHeight(625) == "62,5 см")
        #expect(heightCentimeters(620) == "62")
        #expect(formatTemperature(384) == "38,4\u{00A0}°C")
        #expect(temperatureValue(384) == "38,4")
        #expect(bottleLabel(120, milk: .formula) == "бутылочка 120\u{00A0}мл, смесь")
    }

    @Test func datesAndAgeUseFixedRussianCalendar() {
        let today = day("2026-05-20 15:00")
        #expect(formatDayHeader(today, today: today, timeZone: testZone) == "Сегодня")
        #expect(formatDayHeader(day("2026-05-19 00:00"), today: today, timeZone: testZone) == "Вчера")
        #expect(formatShortDay(day("2026-05-20 00:00"), today: today, timeZone: testZone) == "сегодня")
        #expect(formatDayRange(first: day("2026-10-07 12:00"), last: day("2026-10-14 12:00"), timeZone: testZone) == "7–14 окт.")
        #expect(formatAge(birth: day("2026-03-10 10:00"), today: today, sex: .girl, timeZone: testZone) == "2 мес 10 дн.")
        #expect(formatAge(birth: day("2027-01-01 00:00"), today: today, sex: .boy, timeZone: testZone) == "ещё не родился")
    }

    @Test func eventTitlesTimeLinesAndIconsMatchAndroid() {
        let start = Int64(day("2026-05-20 14:30").timeIntervalSince1970 * 1000)
        let feeding = Feeding(start: start, end: nil, breast: .left)
        #expect(eventTitle(feeding, sex: .girl) == "Кормление · левая · идёт")
        #expect(eventTimeLine(feeding, timeZone: testZone) == "с 14:30")
        #expect(eventIcon(feeding) == "ic_feeding")
        let diaper = Diaper(start: start, kind: .mixed)
        #expect(eventTitle(diaper, sex: nil) == "Подгузник · и то, и то")
        #expect(eventIcon(diaper) == "ic_diaper_mixed")
        #expect(eventTitle(Bottle(start: start, volumeMl: 120, milk: nil), sex: nil) == "Кормление · бутылочка 120\u{00A0}мл")
        let crying = Crying(start: start, end: start + 60_000, reasons: ["голод"], helped: ["обняли"])
        #expect(eventTitle(crying, sex: nil) == "Плач · голод")
        #expect(eventTimeLine(crying, timeZone: testZone) == "14:30–14:31 · 1 мин · помогло: обняли")
    }
}
