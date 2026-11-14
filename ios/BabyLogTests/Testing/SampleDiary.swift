import Foundation
@testable import BabyLog

private let sampleMinute: Int64 = 60_000
let sampleAgeDays = 148

private struct SampleRandom {
    var state: UInt64

    init(seed: Int) { state = UInt64(UInt(bitPattern: seed)) }

    mutating func nextInt(_ bound: Int) -> Int {
        state = state &* 6_364_136_223_846_793_005 &+ 1_442_695_040_888_963_407
        return Int((state >> 32) % UInt64(bound))
    }

    mutating func nextInt(_ lower: Int, _ upper: Int) -> Int {
        lower + nextInt(upper - lower)
    }

    mutating func nextFloat() -> Double {
        Double(nextInt(10_000)) / 10_000
    }

    mutating func nextBool() -> Bool {
        nextInt(2) == 0
    }
}

/**
 Дневник девочки за `days` суток по `now`: ночной и дневной сон, кормления, бутылочки, подгузники,
 лекарства, болезнь, плач, заметки и замеры. Записи детерминированы `seed`: эталоны не плывут.
 */
func sampleMonths(childId: Int64, today: Date, now: Int64, timeZone: TimeZone, days: Int = 60, seed: Int = 20_261_018) -> [any BabyEvent] {
    var calendar = Calendar(identifier: .gregorian)
    calendar.locale = RU
    calendar.timeZone = timeZone
    var random = SampleRandom(seed: seed)
    var events: [any BabyEvent] = []
    var id: Int64 = 1
    func nextId() -> Int64 {
        defer { id += 1 }
        return id
    }
    func feed(_ start: Int64, minutes: Int) {
        let currentId = nextId()
        events.append(Feeding(id: currentId, childId: childId, start: start, end: start + Int64(minutes) * sampleMinute, breast: currentId % 2 == 0 ? .left : .right))
    }
    func midnight(daysAgo: Int) -> Int64 {
        let date = calendar.date(byAdding: .day, value: -daysAgo, to: today)!
        return Int64(date.timeIntervalSince1970 * 1000)
    }
    func hoursAfter(_ daysAgo: Int, _ hours: Double) -> Int64 {
        midnight(daysAgo: daysAgo) + Int64(hours * 3_600_000)
    }

    var previousBed: Int64?
    for dayIndex in stride(from: days - 1, through: 0, by: -1) {
        let midnight = midnight(daysAgo: dayIndex)
        func at(_ minutes: Int) -> Int64 { midnight + Int64(minutes) * sampleMinute }
        func jitter(_ minutes: Int) -> Int { random.nextInt(-minutes, minutes + 1) }
        let wake = at(405 + jitter(20))
        if let bed = previousBed {
            let firstWake = at(90 + jitter(30))
            let firstFeed = 14 + jitter(5)
            events.append(Sleep(id: nextId(), childId: childId, start: bed, end: firstWake))
            feed(firstWake + 3 * sampleMinute, minutes: firstFeed)
            if random.nextBool() {
                events.append(Diaper(id: nextId(), childId: childId, start: firstWake + Int64(5 + firstFeed) * sampleMinute, kind: .wet))
            }
            let resumed = firstWake + Int64(8 + firstFeed) * sampleMinute
            if random.nextFloat() < 0.7 {
                let secondWake = at(280 + jitter(30))
                let secondFeed = 13 + jitter(4)
                events.append(Sleep(id: nextId(), childId: childId, start: resumed, end: secondWake))
                feed(secondWake + 3 * sampleMinute, minutes: secondFeed)
                events.append(Sleep(id: nextId(), childId: childId, start: secondWake + Int64(8 + secondFeed) * sampleMinute, end: wake))
            } else {
                events.append(Sleep(id: nextId(), childId: childId, start: resumed, end: wake))
            }
        }
        feed(wake + 10 * sampleMinute, minutes: 18 + jitter(5))
        events.append(Diaper(id: nextId(), childId: childId, start: wake + 32 * sampleMinute, kind: .wet))
        events.append(Medicine(id: nextId(), childId: childId, start: wake + 35 * sampleMinute, name: "Витамин D", note: "1 капля"))
        for (index, nap) in [(515, 75), (725, 85), (930, 60)].enumerated() {
            let napStart = at(nap.0 + jitter(15))
            let napEnd = napStart + Int64(nap.1 + jitter(15)) * sampleMinute
            events.append(Sleep(id: nextId(), childId: childId, start: napStart, end: napEnd))
            feed(napEnd + 10 * sampleMinute, minutes: 17 + jitter(5))
            let kind: DiaperKind = index == 1 && random.nextFloat() < 0.4 ? .mixed : .wet
            events.append(Diaper(id: nextId(), childId: childId, start: napEnd + 30 * sampleMinute, kind: kind))
        }
        if random.nextFloat() < 0.7 {
            let napStart = at(1095 + jitter(15))
            let napEnd = napStart + Int64(35 + jitter(10)) * sampleMinute
            events.append(Sleep(id: nextId(), childId: childId, start: napStart, end: napEnd))
            feed(napEnd + 12 * sampleMinute, minutes: 16 + jitter(4))
        } else {
            feed(at(1120 + jitter(10)), minutes: 18)
        }
        events.append(Diaper(id: nextId(), childId: childId, start: at(1170 + jitter(10)), kind: random.nextFloat() < 0.6 ? .dirty : .wet))
        let bed = at(1290 + jitter(20))
        if random.nextFloat() < 0.4 {
            events.append(Bottle(id: nextId(), childId: childId, start: bed - 20 * sampleMinute, volumeMl: 120 + 10 * random.nextInt(4), milk: .formula))
        } else {
            feed(bed - 25 * sampleMinute, minutes: 17)
        }
        events.append(Diaper(id: nextId(), childId: childId, start: bed - 32 * sampleMinute, kind: .wet))
        previousBed = bed
    }

    for (age, grams) in [(0, 3250), (4, 3120), (10, 3300), (17, 3560), (31, 4080), (45, 4620), (59, 5020), (73, 5380), (87, 5690), (108, 5850), (122, 6080), (144, 6350)] {
        events.append(Weight(id: nextId(), childId: childId, start: hoursAfter(sampleAgeDays - age, 10), grams: grams))
    }
    for (age, millimeters) in [(0, 500), (31, 540), (59, 575), (87, 600), (122, 625), (144, 645)] {
        events.append(Height(id: nextId(), childId: childId, start: hoursAfter(sampleAgeDays - age, 11), millimeters: millimeters))
    }
    let nurofen = hoursAfter(4, 22)
    events.append(Medicine(id: nextId(), childId: childId, start: nurofen, name: "Нурофен", note: "2,5 мл"))
    events.append(Medicine(id: nextId(), childId: childId, start: nurofen + 86_400_000, name: "Нурофен", note: "2,5 мл"))
    events.append(Illness(id: nextId(), childId: childId, start: hoursAfter(5, 18), end: hoursAfter(2, 10), names: [IllnessName("ОРВИ")]))
    for (daysAgo, hour, tenths) in [(5, 18.5, 382), (4, 21.8, 386), (3, 21.7, 381), (2, 8.0, 372)] {
        events.append(Temperature(id: nextId(), childId: childId, start: hoursAfter(daysAgo, hour), tenths: tenths, method: .armpit))
    }

    var crying = SampleRandom(seed: seed + 7)
    let reasons = [["колики"], ["усталость"], ["колики", "живот"], ["перевозбуждение"], []]
    let helped = [["укачивание"], ["грудь"], ["тёплое на живот"], ["на руках", "укачивание"]]
    for dayIndex in stride(from: days - 1, through: 0, by: -1) {
        if crying.nextFloat() < 0.5 { continue }
        let start = midnight(daysAgo: dayIndex) + Int64(1175 + crying.nextInt(-10, 11)) * sampleMinute
        let length = 12 + crying.nextInt(30)
        events.append(Crying(
            id: nextId(),
            childId: childId,
            start: start,
            end: start + Int64(length) * sampleMinute,
            reasons: reasons[crying.nextInt(reasons.count)],
            helped: helped[crying.nextInt(helped.count)]
        ))
    }

    func noteAt(_ daysAgo: Int, _ hour: Int, _ text: String, _ tags: [String] = []) {
        events.append(Note(id: nextId(), childId: childId, start: hoursAfter(daysAgo, Double(hour)), tags: tags, note: text))
    }
    for daysAgo in stride(from: 2, to: days, by: 4) {
        noteAt(daysAgo, 13, "Ела капусту на обед", ["питание мамы"])
    }
    for daysAgo in 0...3 {
        noteAt(daysAgo, 16, "Тянет всё в рот, тёрла дёсны", ["зубки"])
    }
    for daysAgo in [3, 17, 31] {
        noteAt(daysAgo, 11, "Новое: держит голову лёжа на животе", ["новое"])
    }
    noteAt(1, 9, "Гуляли в парке два часа")

    return events.filter { $0.start < now }.map { event in
        if var sleep = event as? Sleep {
            if (sleep.end ?? now) > now { sleep.end = nil }
            return sleep
        }
        if var feeding = event as? Feeding {
            if (feeding.end ?? now) > now { feeding.end = nil }
            return feeding
        }
        return event
    }
}

func analyticsAt(_ iso: String) -> Int64 {
    Int64(ISO8601DateFormatter().date(from: iso)!.timeIntervalSince1970 * 1000)
}

let analyticsNow = analyticsAt("2026-10-18T08:20:00Z")
let analyticsZone = TimeZone(secondsFromGMT: 0)!
let analyticsChild = Child(id: 1, name: "Маша", birthDate: analyticsAt("2026-05-23T00:00:00Z"), sex: .girl)

func analyticsSampleEvents() -> [any BabyEvent] {
    sampleMonths(childId: analyticsChild.id, today: toLocalDate(analyticsNow, timeZone: analyticsZone), now: analyticsNow, timeZone: analyticsZone)
}

@MainActor
func makeAnalyticsViewModel() async throws -> DiaryViewModel {
    let repository = FakeDiaryRepository(children: [analyticsChild])
    let events = analyticsSampleEvents()
    for event in events { _ = try await repository.addEvent(event) }
    let clock = TestClock(date: Date(timeIntervalSince1970: Double(analyticsNow) / 1000), timeZone: analyticsZone)
    let model = DiaryViewModel(repository: repository, selection: MemorySelectedChildStore(), documents: FakeDocumentStore(), clock: clock)
    _ = await waitUntil { model.state.events.count == events.count }
    return model
}
