import Foundation

/** Период «Аналитики» (ADR-0028): последние 7 или 30 суток — по дням, всё время — по неделям. */
enum AnalyticsPeriod: CaseIterable, Hashable, Sendable {
    case week
    case month
    case all

    var title: String {
        switch self {
        case .week: "7 дней"
        case .month: "30 дней"
        case .all: "Всё время"
        }
    }

    var days: Int? {
        switch self {
        case .week: 7
        case .month: 30
        case .all: nil
        }
    }
}

/** Величина, которой посвящена карточка-график. */
enum Metric: CaseIterable, Hashable, Sendable {
    case feedings
    case bottles
    case sleep
    case diapers
    /** Плач (ADR-0030): минуты в сутки; карточка есть, только если плач записывали. */
    case crying

    var title: String {
        switch self {
        case .feedings: "Кормления"
        case .bottles: "Бутылочка"
        case .sleep: "Сон"
        case .diapers: "Подгузники"
        case .crying: "Плач"
        }
    }
}

/** Ряд графика; цвет ряда UI берёт из темы (`ChartColors`), а не из модели. Сон — ночной и дневной (ADR-0036). */
enum Series: Hashable, Sendable {
    case feeding
    case nightSleep
    case daySleep
    case diaperWet
    case diaperMixed
    case diaperDirty
    case crying
}

/**
 Столбик графика: части снизу вверх. `partial` — сутки или неделя ещё идут; `date` — день,
 который откроет «В историю» (у недельного столбика nil); `caption` — итоги столбика словами,
 их показывает выбранный столбик и читает VoiceOver. `ill` — в эти сутки (неделю) болел: столбик
 на полосе болезни (ADR-0040).
 */
struct ChartColumn: Sendable {
    var parts: [(series: Series, value: Float)]
    var label: String
    var partial: Bool
    var date: Date?
    var caption: String
    var ill: Bool = false
}

/**
 Пункт легенды: цвет ряда, вид подгузника для иконки (у сна иконки нет), название ряда для VoiceOver
 и среднее за сутки — у сна вместе с «ночью» или «днём».
 */
struct LegendItem: Hashable, Sendable {
    var series: Series
    var kind: DiaperKind?
    var label: String
    var text: String
}

/** Карточка-график: главное число, сравнение, пояснение, легенда и столбики. */
struct MetricModel: Sendable {
    var metric: Metric
    var value: String
    var unit: String
    var delta: String?
    var note: String?
    var legend: [LegendItem]
    var columns: [ChartColumn]
    var maxValue: Float
    var maxLabel: String
    var midLabel: String
}

private let hourValue: Float = 3_600_000
private let minuteValue: Float = 60_000

/** Сравнение с прошлым периодом — только если в нём не меньше стольких полных суток с записями. */
private let minDaysToCompare = 3
private let diaperSeries: [(DiaperKind, Series)] = [
    (.wet, .diaperWet),
    (.mixed, .diaperMixed),
    (.dirty, .diaperDirty),
]

/**
 Будущий столбик: сутки (одни `days` или пусто, если дневник не вели) или неделя (её полные сутки с записями).
 `ill` — болел в эти сутки или хотя бы в одни сутки недели.
 */
private struct Bucket {
    var days: [DayStats]
    var label: String
    var partial: Bool
    var date: Date?
    var name: String
    var ill: Bool
}

private func metricsCalendar(_ timeZone: TimeZone) -> Calendar {
    var calendar = Calendar(identifier: .gregorian)
    calendar.locale = RU
    calendar.firstWeekday = 2
    calendar.timeZone = timeZone
    return calendar
}

private func metricsFormatter(_ format: String, timeZone: TimeZone) -> DateFormatter {
    let formatter = DateFormatter()
    formatter.locale = RU
    formatter.calendar = metricsCalendar(timeZone)
    formatter.timeZone = timeZone
    formatter.shortWeekdaySymbols = ["вс", "пн", "вт", "ср", "чт", "пт", "сб"]
    formatter.shortStandaloneWeekdaySymbols = formatter.shortWeekdaySymbols
    formatter.shortMonthSymbols = ["янв.", "февр.", "мар.", "апр.", "мая", "июн.", "июл.", "авг.", "сент.", "окт.", "нояб.", "дек."]
    formatter.shortStandaloneMonthSymbols = ["янв.", "февр.", "март", "апр.", "май", "июнь", "июль", "авг.", "сент.", "окт.", "нояб.", "дек."]
    formatter.dateFormat = format
    return formatter
}

private func mondayWeekday(_ date: Date, timeZone: TimeZone) -> Int {
    (metricsCalendar(timeZone).component(.weekday, from: date) + 5) % 7 + 1
}

/** По столбику на сутки периода `days`; сутки без записей — пустой столбик. */
private func dayBuckets(_ days: [DayStats], period: AnalyticsPeriod, timeZone: TimeZone) -> [Bucket] {
    var monthShown = false
    let calendar = metricsCalendar(timeZone)
    return days.map { day in
        let label: String
        if period == .week {
            label = metricsFormatter("EE", timeZone: timeZone).string(from: day.date)
        } else if mondayWeekday(day.date, timeZone: timeZone) != 1 {
            label = ""
        } else if !monthShown || calendar.component(.day, from: day.date) <= 7 {
            monthShown = true
            label = metricsFormatter("d MMM", timeZone: timeZone).string(from: day.date)
        } else {
            label = String(calendar.component(.day, from: day.date))
        }
        return Bucket(
            days: day.tracked ? [day] : [],
            label: label,
            partial: !day.complete,
            date: day.date,
            name: day.complete ? metricsFormatter("EE, d MMM", timeZone: timeZone).string(from: day.date) : "сегодня",
            ill: day.ill
        )
    }
}

/** Недели с понедельника: среднее за полные сутки с записями; текущая неделя — `Bucket.partial`. */
private func weekBuckets(_ days: [DayStats], timeZone: TimeZone) -> [Bucket] {
    var previousMonth = -1
    let calendar = metricsCalendar(timeZone)
    return days.chunked(into: 7).map { week in
        let start = week.first!.date
        let month = calendar.component(.month, from: start)
        let label = month != previousMonth ? metricsFormatter("LLL", timeZone: timeZone).string(from: start) : ""
        previousMonth = month
        return Bucket(
            days: week.filter(\.countable),
            label: label,
            partial: week.count < 7 || !week.last!.complete,
            date: nil,
            name: "неделя с \(metricsFormatter("d MMM", timeZone: timeZone).string(from: start))",
            ill: week.contains(where: \.ill)
        )
    }
}

private func averageFloat(_ values: [Float]) -> Float {
    Float(values.reduce(0.0) { $0 + Double($1) } / Double(values.count))
}

private func roundedInt(_ value: Float) -> Int {
    Int((value + 0.5).rounded(.down))
}

private func roundedMillis(_ value: Float) -> Int64 {
    Int64((value + 0.5).rounded(.down))
}

private func roundedAverageMillis(_ values: [Int64]) -> Int64 {
    Int64((values.reduce(0.0) { $0 + Double($1) } / Double(values.count) + 0.5).rounded(.down))
}

/** Средние за сутки по `days`; пусто — нечего показывать. */
private func parts(_ metric: Metric, days: [DayStats]) -> [(series: Series, value: Float)] {
    guard !days.isEmpty else { return [] }
    func avg(_ value: (DayStats) -> Float) -> Float {
        averageFloat(days.map(value))
    }
    switch metric {
    case .feedings:
        return [(.feeding, avg { Float($0.feedings) })]
    case .bottles:
        return [(.feeding, avg { Float($0.bottleMl) })]
    case .sleep:
        // Ночь внизу столбика, день сверху: ночной сон — основа суток.
        return [
            (.nightSleep, avg { Float($0.nightSleepMillis) } / hourValue),
            (.daySleep, avg { Float($0.daySleepMillis) } / hourValue),
        ]
    case .diapers:
        return diaperSeries.map { kind, series in
            (series, avg { Float($0.diapers[kind] ?? 0) })
        }
    case .crying:
        return [(.crying, avg { Float($0.cryingMillis) } / minuteValue)]
    }
}

private func total(_ metric: Metric, days: [DayStats]) -> Float? {
    let values = parts(metric, days: days)
    guard !values.isEmpty else { return nil }
    return Float(values.reduce(0.0) { $0 + Double($1.value) })
}

/** «8,3», «245 мл», «13 ч 5 мин». */
func formatValue(_ metric: Metric, value: Float) -> String {
    switch metric {
    case .feedings, .diapers:
        return String(format: "%.1f", locale: RU, Double(value))
    case .bottles:
        return "\(roundedInt(value / 5) * 5) мл"
    case .sleep:
        return formatDuration(roundedMillis(value * hourValue))
    case .crying:
        // Ноль — «0 мин», а не «меньше минуты»: плача в эти сутки не было.
        return value <= 0 ? "0 мин" : formatDuration(roundedMillis(value * minuteValue))
    }
}

/** «+0,4», «−15 мин», «+20 мл»; меньше шага — «без изменений». */
private func formatChange(_ metric: Metric, change: Float) -> String {
    let sign = change > 0 ? "+" : "−"
    switch metric {
    case .feedings, .diapers:
        return abs(change) < 0.05 ? "без изменений" : sign + String(format: "%.1f", locale: RU, Double(abs(change)))
    case .bottles:
        return abs(change) < 5 ? "без изменений" : "\(sign)\(roundedInt(abs(change) / 5) * 5) мл"
    case .sleep:
        return abs(change) * hourValue < 60_000
            ? "без изменений"
            : sign + formatDuration(roundedMillis(abs(change) * hourValue))
    case .crying:
        return abs(change) < 1 ? "без изменений" : sign + formatDuration(roundedMillis(abs(change) * minuteValue))
    }
}

/** Верх шкалы — круглое число не меньше максимума: середина шкалы тоже круглая. */
private func scaleTop(_ metric: Metric, max: Float) -> Int {
    switch metric {
    case .feedings, .diapers:
        return Swift.max(2, Int(ceil(max / 2)) * 2)
    case .bottles:
        return Swift.max(100, Int(ceil(max / 100)) * 100)
    case .sleep:
        return Swift.max(4, Int(ceil(max / 4)) * 4)
    case .crying:
        // Плач — в минутах: верх шкалы кратен получасу.
        return Swift.max(30, Int(ceil(max / 30)) * 30)
    }
}

private func axisLabel(_ metric: Metric, value: Int) -> String {
    switch metric {
    case .sleep: "\(value) ч"
    case .crying: formatDuration(Int64(value) * 60_000)
    case .feedings, .bottles, .diapers: String(value)
    }
}

/**
 Карточка `metric` за период: `days` — сутки периода, `previous` — столько же суток перед ним
 (для «всё время» пусто). Средние — только по полным суткам с записями (`DayStats.countable`).
 */
func metricModel(metric: Metric, period: AnalyticsPeriod, days: [DayStats], previous: [DayStats], sex: Sex?, timeZone: TimeZone) -> MetricModel {
    let byWeeks = period == .all
    let buckets = byWeeks ? weekBuckets(days, timeZone: timeZone) : dayBuckets(days, period: period, timeZone: timeZone)
    let columns = buckets.map { bucket in
        ChartColumn(
            parts: parts(metric, days: bucket.days),
            label: bucket.label,
            partial: bucket.partial,
            date: bucket.date,
            caption: caption(metric: metric, bucket: bucket, byWeeks: byWeeks, sex: sex, timeZone: timeZone),
            ill: bucket.ill
        )
    }
    let largest = columns.map { column in
        Float(column.parts.reduce(0.0) { $0 + Double($1.value) })
    }.max() ?? 0
    let top = scaleTop(metric, max: largest)
    let countable = days.filter(\.countable)

    let value: Float?
    let delta: String?
    var note: String?
    let legendDays: [DayStats]
    if byWeeks {
        // Главное число — последняя полная неделя, для сравнения — первая неделя дневника.
        let weeks = buckets.filter { !$0.days.isEmpty }
        let latestIndex = weeks.indices.last(where: { !weeks[$0].partial }) ?? weeks.indices.last
        let latest = latestIndex.map { weeks[$0] }
        legendDays = latest?.days ?? []
        value = total(metric, days: legendDays)
        delta = "по неделям"
        if weeks.count > 1, latestIndex != 0, let first = weeks.first,
           let firstValue = total(metric, days: first.days) {
            note = "в первую неделю дневника — \(formatValue(metric, value: firstValue))"
        } else {
            note = nil
        }
    } else {
        legendDays = countable
        value = total(metric, days: countable)
        let before = previous.filter(\.countable)
        let previousValue = total(metric, days: before)
        if let value, let previousValue, before.count >= minDaysToCompare {
            let suffix = period == .week ? " к прошлой неделе" : " к прошлым 30 дням"
            delta = formatChange(metric, change: value - previousValue) + suffix
        } else {
            delta = nil
        }
        note = nil
    }
    let notes = [periodNote(metric: metric, countable: byWeeks ? legendDays : countable, all: days), note]
        .compactMap { $0 }
    note = notes.joined(separator: " · ")

    let legend: [LegendItem]
    if legendDays.isEmpty {
        legend = []
    } else if metric == .diapers {
        legend = diaperSeries.map { kind, series in
            LegendItem(
                series: series,
                kind: kind,
                label: diaperLabel(kind, sex: sex),
                text: String(format: "%.1f", locale: RU, Double(averageFloat(legendDays.map { Float($0.diapers[kind] ?? 0) })))
            )
        }
    } else if metric == .sleep {
        // Сколько спит ночью и сколько днём — в среднем за те же сутки, что и главное число.
        legend = [
            LegendItem(series: .nightSleep, kind: nil, label: "сон", text: "ночью \(formatDuration(roundedAverageMillis(legendDays.map(\.nightSleepMillis))))"),
            LegendItem(series: .daySleep, kind: nil, label: "сон", text: "днём \(formatDuration(roundedAverageMillis(legendDays.map(\.daySleepMillis))))"),
        ]
    } else {
        legend = []
    }

    return MetricModel(
        metric: metric,
        value: value.map { formatValue(metric, value: $0) } ?? "—",
        unit: value == nil ? "нет полных суток с записями" : "в сутки",
        delta: delta,
        note: note?.isEmpty == true ? nil : note,
        legend: legend,
        columns: columns,
        maxValue: Float(top),
        maxLabel: axisLabel(metric, value: top),
        midLabel: axisLabel(metric, value: top / 2)
    )
}

/** Пояснение под главным числом: по `countable` — полным суткам с записями, `all` — всем суткам периода. */
private func periodNote(metric: Metric, countable: [DayStats], all: [DayStats]) -> String? {
    let result: String
    switch metric {
    case .feedings:
        result = [typicalInterval(countable.flatMap(\.feedingStarts)), breastTime(countable)]
            .compactMap { $0 }.joined(separator: " · ")
    case .bottles:
        result = countable.isEmpty
            ? ""
            : String(format: "%.1f", locale: RU, Double(averageFloat(countable.map { Float($0.bottles) }))) + " бутылочки в сутки"
    case .sleep:
        var notes = [
            countable.isEmpty ? nil : String(format: "%.1f", locale: RU, Double(averageFloat(countable.map { Float($0.sleeps) }))) + " раза в сутки",
            typicalInterval(countable.flatMap(\.sleepStarts)),
        ].compactMap { $0 }
        if let longest = all.map(\.longestSleepMillis).max(), longest > 0 {
            notes.append("самый долгий сон — \(formatDuration(longest))")
        }
        result = notes.joined(separator: " · ")
    case .diapers:
        result = ""
    case .crying:
        result = [
            countable.isEmpty ? nil : String(format: "%.1f", locale: RU, Double(averageFloat(countable.map { Float($0.cryings) }))) + " раза в сутки",
            cryingPeak(countable.flatMap(\.cryingStartMinutes)),
        ].compactMap { $0 }.joined(separator: " · ")
    }
    return result.isEmpty ? nil : result
}

/**
 «обычно раз в 2 ч 50 мин» — медиана промежутков между началами кормлений или снов (`starts`):
 пропущенная запись её не сдвинет.
 */
private func typicalInterval(_ starts: [Int64]) -> String? {
    let sorted = starts.sorted()
    let gaps = zip(sorted, sorted.dropFirst()).map { $1 - $0 }.sorted()
    guard gaps.count >= 2, let median = gaps.median() else { return nil }
    return "обычно раз в \(formatDuration(median))"
}

/**
 «у груди 2 ч 16 мин в сутки» — по суткам, где у всех кормлений грудью есть длительность. Кормления
 до ADR-0015 записаны без неё: такие сутки в среднее не входят, а не считаются нулём, и это видно —
 «по 5 дням из 7». Нет ни одних таких суток — строки нет.
 */
private func breastTime(_ days: [DayStats]) -> String? {
    let withBreast = days.filter { $0.breastFeedings > 0 }
    let known = withBreast.compactMap(\.breastMillis)
    guard !known.isEmpty else { return nil }
    let text = "у груди \(formatDuration(roundedAverageMillis(known))) в сутки"
    if known.count == withBreast.count { return text }
    return "\(text) по \(known.count) \(plural(known.count, one: "дню", few: "дням", many: "дням")) из \(withBreast.count)"
}

/** Итоги столбика словами: для выбранного столбика и для VoiceOver. */
private func caption(metric: Metric, bucket: Bucket, byWeeks: Bool, sex: Sex?, timeZone: TimeZone) -> String {
    var suffix = ""
    if bucket.partial { suffix += byWeeks ? " (неделя идёт)" : " (день идёт)" }
    if bucket.ill { suffix += byWeeks ? " · были дни болезни" : " · " + bySex(sex, masculine: "болел", feminine: "болела") }
    guard !bucket.days.isEmpty else { return "\(bucket.name): записей нет\(suffix)" }
    if byWeeks {
        let average = total(metric, days: bucket.days) ?? 0
        let split = metric == .sleep
            ? sleepSplit(
                nightMillis: roundedAverageMillis(bucket.days.map(\.nightSleepMillis)),
                dayMillis: roundedAverageMillis(bucket.days.map(\.daySleepMillis))
            )
            : ""
        return "\(bucket.name): в среднем \(formatValue(metric, value: average)) в сутки\(split)\(suffix)"
    }
    let day = bucket.days[0]
    let text: String
    switch metric {
    case .feedings:
        var value = "\(day.feedings) \(plural(day.feedings, one: "кормление", few: "кормления", many: "кормлений"))"
        if day.bottles > 0 { value += ", из них бутылочкой \(day.bottles)" }
        if let breastMillis = day.breastMillis, breastMillis > 0 {
            value += ", у груди \(formatDuration(breastMillis))"
        }
        text = value
    case .bottles:
        text = "\(day.bottles) \(plural(day.bottles, one: "бутылочка", few: "бутылочки", many: "бутылочек")), \(day.bottleMl) мл"
    case .sleep:
        text = "сон \(formatDuration(day.sleepMillis))\(day.sleepSplit())"
            + (day.sleeps > 0 ? ", \(day.sleeps) \(plural(day.sleeps, one: "раз", few: "раза", many: "раз"))" : "")
            + (day.longestSleepMillis > 0 ? ", самый долгий \(formatDuration(day.longestSleepMillis))" : "")
    case .crying:
        // Плач через полночь: минуты есть в обоих сутках, а раз — только в тех, где он начался.
        if day.cryings == 0 && day.cryingMillis == 0 {
            text = "плача нет"
        } else if day.cryings == 0 {
            text = "плач \(formatDuration(day.cryingMillis))"
        } else {
            text = "плач \(formatDuration(day.cryingMillis)), \(day.cryings) \(plural(day.cryings, one: "раз", few: "раза", many: "раз"))"
        }
    case .diapers:
        let details = diaperSeries.compactMap { kind, _ -> String? in
            day.diapers[kind].map { "\(diaperLabel(kind, sex: sex)) \($0)" }
        }.joined(separator: ", ")
        text = "\(day.diaperCount) \(plural(day.diaperCount, one: "подгузник", few: "подгузника", many: "подгузников"))"
            + (day.diaperCount > 0 ? " (\(details))" : "")
    }
    return "\(bucket.name): \(text)\(suffix)"
}

/** « (ночью 9 ч 40 мин, днём 2 ч 52 мин)» — из чего сложился сон (ADR-0036); сна нет — пусто. */
func sleepSplit(nightMillis: Int64, dayMillis: Int64) -> String {
    let parts = [
        nightMillis > 0 ? "ночью \(formatDuration(nightMillis))" : nil,
        dayMillis > 0 ? "днём \(formatDuration(dayMillis))" : nil,
    ].compactMap { $0 }
    return parts.isEmpty ? "" : " (\(parts.joined(separator: ", ")))"
}

extension DayStats {
    func sleepSplit() -> String {
        BabyLog.sleepSplit(nightMillis: nightSleepMillis, dayMillis: daySleepMillis)
    }
}

private extension Array {
    func chunked(into size: Int) -> [[Element]] {
        stride(from: 0, to: count, by: size).map { Array(self[$0..<Swift.min($0 + size, count)]) }
    }
}
