import Foundation

/** Деление шкалы графика: значение и подпись. */
struct Tick: Hashable, Sendable {
    var value: Float
    var label: String
}

/**
 Карточка замера — веса или роста — всегда за всё время: меряют редко, за неделю точек нет.
 `points` — дни от первого замера и значение в единицах оси (кг, см); график — от двух замеров.
 `rate` — прибавка без даты для плитки обзора («+85 г в неделю»), `note` — она же с датой базы;
 `history` — замеры для экрана раздела (ADR-0029), свежие сверху.
 */
struct GrowthModel: Sendable {
    var value: String
    var date: String
    var rate: String?
    var note: String?
    var points: [(x: Float, y: Float)]
    var xMax: Float
    var yMin: Float
    var yMax: Float
    var yTicks: [Tick]
    var xTicks: [Tick]
    var description: String
    var history: [GrowthEntry]
}

/** Замер в списке: дата («сегодня», «14 окт.»), значение и разница с предыдущим («+270 г»; у первого — nil). */
struct GrowthEntry: Hashable, Sendable {
    var date: String
    var value: String
    var change: String?
}

/** Замер: время и значение в единицах записи (граммы, миллиметры). */
private struct Measurement {
    var id: Int64
    var start: Int64
    var value: Int
}

/**
 Чем вес отличается от роста на карточке: единицы оси и подписей, и как считать прибавку —
 к замеру не ближе `baseDays` дней (ближе разница — в пределах ошибки замера), за `rateDays` дней.
 */
private struct GrowthKind {
    var title: String
    var unit: String
    var toAxis: (Int) -> Float
    var format: (Int) -> String
    var baseDays: Int64
    var rateDays: Float
    var rateLabel: String
    /** Прибавка за `rateDays` в единицах записи → «95 г», «2,5 см». */
    var formatRate: (Float) -> String
    var measurements: (Int) -> String
}

private let weightKind = GrowthKind(
    title: "Вес",
    unit: "кг",
    toAxis: { Float($0) / 1000 },
    format: formatWeight,
    baseDays: 14,
    rateDays: 7,
    rateLabel: "в неделю",
    formatRate: { "\(Int(($0 / 5 + 0.5).rounded(.down)) * 5) г" },
    measurements: { "\($0) \(plural($0, one: "взвешивание", few: "взвешивания", many: "взвешиваний"))" }
)

/** Рост меряют раз в месяц с точностью до 0,5 см (ADR-0027): прибавка — в месяц, база — не ближе 4 недель. */
private let heightKind = GrowthKind(
    title: "Рост",
    unit: "см",
    toAxis: { Float($0) / 10 },
    format: formatHeight,
    baseDays: 28,
    rateDays: 30.44,
    rateLabel: "в месяц",
    formatRate: { String(format: "%.1f см", locale: RU, Double($0 / 10)) },
    measurements: { "\($0) \(plural($0, one: "замер", few: "замера", many: "замеров"))" }
)

private let growthDayMillis: Float = 86_400_000
private let growthSteps: [Float] = [1, 2, 5, 10, 20, 50]
private let maxGrowthTicks = 5

private func growthCalendar(_ timeZone: TimeZone) -> Calendar {
    var calendar = Calendar(identifier: .gregorian)
    calendar.locale = RU
    calendar.firstWeekday = 2
    calendar.timeZone = timeZone
    return calendar
}

private func growthFormatter(_ format: String, timeZone: TimeZone) -> DateFormatter {
    let formatter = DateFormatter()
    formatter.locale = RU
    formatter.calendar = growthCalendar(timeZone)
    formatter.timeZone = timeZone
    formatter.shortWeekdaySymbols = ["вс", "пн", "вт", "ср", "чт", "пт", "сб"]
    formatter.shortStandaloneWeekdaySymbols = formatter.shortWeekdaySymbols
    formatter.shortMonthSymbols = ["янв.", "февр.", "мар.", "апр.", "мая", "июн.", "июл.", "авг.", "сент.", "окт.", "нояб.", "дек."]
    formatter.shortStandaloneMonthSymbols = ["янв.", "февр.", "март", "апр.", "май", "июнь", "июль", "авг.", "сент.", "окт.", "нояб.", "дек."]
    formatter.dateFormat = format
    return formatter
}

func weightModel(events: [any BabyEvent], now: Int64, timeZone: TimeZone) -> GrowthModel? {
    growthModel(
        kind: weightKind,
        all: events.compactMap { ($0 as? Weight).map { Measurement(id: $0.id, start: $0.start, value: $0.grams) } },
        now: now,
        timeZone: timeZone
    )
}

func heightModel(events: [any BabyEvent], now: Int64, timeZone: TimeZone) -> GrowthModel? {
    growthModel(
        kind: heightKind,
        all: events.compactMap { ($0 as? Height).map { Measurement(id: $0.id, start: $0.start, value: $0.millimeters) } },
        now: now,
        timeZone: timeZone
    )
}

private func growthModel(kind: GrowthKind, all: [Measurement], now: Int64, timeZone: TimeZone) -> GrowthModel? {
    let measurements = all.sorted { $0.start == $1.start ? $0.id < $1.id : $0.start < $1.start }
    guard let last = measurements.last else { return nil }
    let first = measurements.first!
    let calendar = growthCalendar(timeZone)

    func dateOf(_ time: Int64) -> Date {
        toLocalDate(time, timeZone: timeZone)
    }

    let today = dateOf(now)
    let base = measurements.last(where: { $0.start <= last.start - kind.baseDays * Int64(growthDayMillis) })
    let rate = base.map { base -> String in
        let elapsed = Float(last.start - base.start) / growthDayMillis
        let perPeriod = Float(last.value - base.value) * kind.rateDays / elapsed
        let sign = perPeriod >= 0 ? "+" : "−"
        return "\(sign)\(kind.formatRate(abs(perPeriod))) \(kind.rateLabel)"
    }
    let note = base.map { "\(rate!) с \(growthFormatter("d MMM", timeZone: timeZone).string(from: dateOf($0.start)))" }
    let history = measurements.enumerated().map { index, measurement -> GrowthEntry in
        let change: String?
        if index > 0 {
            let diff = measurement.value - measurements[index - 1].value
            if diff > 0 {
                change = "+" + kind.formatRate(Float(diff))
            } else if diff < 0 {
                change = "−" + kind.formatRate(Float(-diff))
            } else {
                change = "±0"
            }
        } else {
            change = nil
        }
        return GrowthEntry(
            date: growthDayName(dateOf(measurement.start), today: today, timeZone: timeZone),
            value: kind.format(measurement.value),
            change: change
        )
    }.reversed()

    let points = measurements.map {
        (x: Float($0.start - first.start) / growthDayMillis, y: kind.toAxis($0.value))
    }
    let low = measurements.map { kind.toAxis($0.value) }.min()!
    let high = measurements.map { kind.toAxis($0.value) }.max()!
    let step = growthSteps.first { ceil(high / $0) - floor(low / $0) < Float(maxGrowthTicks) } ?? growthSteps.last!
    let yMin = floor(low / step) * step
    let roundedHigh = ceil(high / step) * step
    let yMax = roundedHigh <= yMin ? yMin + step : roundedHigh
    var yValues: [Float] = []
    var value = yMin
    while value <= yMax + step / 2 {
        yValues.append(value)
        value += step
    }
    let yTicks = yValues.enumerated().map { index, value in
        Tick(value: value, label: "\(Int((value + 0.5).rounded(.down)))" + (index == yValues.count - 1 ? " \(kind.unit)" : ""))
    }

    // Деления по оси времени — начала месяцев; на коротком отрезке — первый и последний замер.
    let firstDate = dateOf(first.start)
    let lastDate = dateOf(last.start)
    let firstComponents = calendar.dateComponents([.year, .month], from: firstDate)
    var monthStart = calendar.date(byAdding: .month, value: 1, to: calendar.date(from: DateComponents(
        year: firstComponents.year,
        month: firstComponents.month,
        day: 1
    ))!)!
    var monthStarts: [Tick] = []
    while monthStart <= lastDate {
        let days = calendar.dateComponents([.day], from: firstDate, to: monthStart).day ?? 0
        monthStarts.append(Tick(value: Float(days), label: growthFormatter("LLL", timeZone: timeZone).string(from: monthStart)))
        monthStart = calendar.date(byAdding: .month, value: 1, to: monthStart)!
    }
    let xTicks = monthStarts.count >= 2 ? monthStarts : [
        Tick(value: 0, label: growthFormatter("d MMM", timeZone: timeZone).string(from: firstDate)),
        Tick(value: points.last!.x, label: growthFormatter("d MMM", timeZone: timeZone).string(from: lastDate)),
    ]

    return GrowthModel(
        value: kind.format(last.value),
        date: growthDayName(lastDate, today: today, timeZone: timeZone),
        rate: rate,
        note: note,
        points: points,
        xMax: max(points.last!.x, 1),
        yMin: yMin,
        yMax: yMax,
        yTicks: yTicks,
        xTicks: xTicks,
        description: "\(kind.title) с \(growthFormatter("d MMM", timeZone: timeZone).string(from: firstDate)): \(kind.measurements(measurements.count)), "
            + "от \(kind.format(measurements.map(\.value).min()!)) до \(kind.format(measurements.map(\.value).max()!))",
        history: Array(history)
    )
}

/** Лекарство за период: название из последней записи, сколько приёмов (`count`) и когда последний (`last`). */
struct MedicineLine: Hashable, Sendable {
    var name: String
    var info: String
    var count: Int
    var last: String
}

/** Лекарства с `since` (nil — за всё время); названия сравниваются без регистра, как в сводке дня. */
func medicineLines(events: [any BabyEvent], since: Int64?, now: Int64, timeZone: TimeZone) -> [MedicineLine] {
    let today = toLocalDate(now, timeZone: timeZone)
    let medicines = events.compactMap { $0 as? Medicine }.filter { since == nil || $0.start >= since! }
    let groups = Dictionary(grouping: medicines) {
        $0.name.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
    }
    return groups.values.map { doses in
        (last: doses.max { $0.start < $1.start }!, count: doses.count)
    }.sorted { $0.last.start > $1.last.start }.map { last, count in
        let lastDose = "последний \(growthDayName(toLocalDate(last.start, timeZone: timeZone), today: today, timeZone: timeZone)) в \(formatClock(last.start, timeZone: timeZone))"
        return MedicineLine(
            name: last.name.trimmingCharacters(in: .whitespacesAndNewlines),
            info: "\(count) \(plural(count, one: "приём", few: "приёма", many: "приёмов")) · \(lastDose)",
            count: count,
            last: lastDose
        )
    }
}

/** «сегодня», «вчера», «14 окт.». */
private func growthDayName(_ date: Date, today: Date, timeZone: TimeZone) -> String {
    let calendar = growthCalendar(timeZone)
    if calendar.isDate(date, inSameDayAs: today) { return "сегодня" }
    if let yesterday = calendar.date(byAdding: .day, value: -1, to: today), calendar.isDate(date, inSameDayAs: yesterday) {
        return "вчера"
    }
    return growthFormatter("d MMM", timeZone: timeZone).string(from: date)
}
