import Foundation

import Foundation

let RU = Locale(identifier: "ru")

private func formatter(_ format: String, timeZone: TimeZone) -> DateFormatter {
    let formatter = DateFormatter()
    formatter.locale = RU
    formatter.calendar = Calendar(identifier: .gregorian)
    formatter.timeZone = timeZone
    formatter.dateFormat = format
    return formatter
}

private func calendar(_ timeZone: TimeZone) -> Calendar {
    var calendar = Calendar(identifier: .gregorian)
    calendar.locale = RU
    calendar.firstWeekday = 2
    calendar.timeZone = timeZone
    return calendar
}

func toLocalDate(_ millis: Int64, timeZone: TimeZone = .current) -> Date {
    calendar(timeZone).startOfDay(for: Date(timeIntervalSince1970: Double(millis) / 1000))
}

func formatClock(_ millis: Int64, timeZone: TimeZone = .current) -> String {
    formatter("HH:mm", timeZone: timeZone).string(from: Date(timeIntervalSince1970: Double(millis) / 1000))
}

func formatDayHeader(_ date: Date, today: Date, timeZone: TimeZone = .current) -> String {
    let calendar = calendar(timeZone)
    if calendar.isDate(date, inSameDayAs: today) { return "Сегодня" }
    if let yesterday = calendar.date(byAdding: .day, value: -1, to: today), calendar.isDate(date, inSameDayAs: yesterday) {
        return "Вчера"
    }
    return formatter("d MMMM, EEEE", timeZone: timeZone).string(from: date)
}

/** «20 мая 2026». */
func formatDate(_ millis: Int64, timeZone: TimeZone = .current) -> String {
    formatter("d MMMM yyyy", timeZone: timeZone).string(from: Date(timeIntervalSince1970: Double(millis) / 1000))
}

/** «сегодня», «вчера», «15 окт.», в другом году — «15 окт. 2025». */
func formatShortDay(_ date: Date, today: Date, timeZone: TimeZone = .current) -> String {
    let calendar = calendar(timeZone)
    if calendar.isDate(date, inSameDayAs: today) { return "сегодня" }
    if let yesterday = calendar.date(byAdding: .day, value: -1, to: today), calendar.isDate(date, inSameDayAs: yesterday) {
        return "вчера"
    }
    let sameYear = calendar.component(.year, from: date) == calendar.component(.year, from: today)
    return formatter(sameYear ? "d MMM" : "d MMM yyyy", timeZone: timeZone).string(from: date)
}

/** «2 ч 15 мин», «45 мин», «меньше минуты». */
func formatDuration(_ millis: Int64) -> String {
    let totalMinutes = millis / 60_000
    let hours = totalMinutes / 60
    let minutes = totalMinutes % 60
    if totalMinutes < 1 { return "меньше минуты" }
    if hours == 0 { return "\(minutes) мин" }
    if minutes == 0 { return "\(hours) ч" }
    return "\(hours) ч \(minutes) мин"
}

/** «только что», «25 мин назад», «3 ч 10 мин назад». */
func formatAgo(_ millis: Int64) -> String {
    millis < 60_000 ? "только что" : "\(formatDuration(millis)) назад"
}

/** Таймер сна и кормления: «1:23:45» или «23:45». */
func formatTimer(_ millis: Int64) -> String {
    let totalSeconds = millis / 1000
    let hours = totalSeconds / 3600
    let minutes = (totalSeconds % 3600) / 60
    let seconds = totalSeconds % 60
    let mm = String(format: "%02d", Int(minutes))
    let ss = String(format: "%02d", Int(seconds))
    return hours > 0 ? "\(hours):\(mm):\(ss)" : "\(mm):\(ss)"
}

func breastLabel(_ breast: Breast) -> String {
    breast == .left ? "левая" : "правая"
}

/** Женский род для девочки, мужской — для мальчика и когда пол не указан (ADR-0016). */
func bySex(_ sex: Sex?, masculine: String, feminine: String) -> String {
    sex == .girl ? feminine : masculine
}

/** Русское число: 1 кормление, 2 кормления, 5 кормлений. */
func plural(_ n: Int, one: String, few: String, many: String) -> String {
    let lastTwo = n % 100
    let last = n % 10
    if (11...14).contains(lastTwo) { return many }
    if last == 1 { return one }
    if (2...4).contains(last) { return few }
    return many
}

func diaperLabel(_ kind: DiaperKind, sex: Sex?) -> String {
    switch kind {
    case .wet: bySex(sex, masculine: "пописал", feminine: "пописала")
    case .dirty: bySex(sex, masculine: "покакал", feminine: "покакала")
    case .mixed: "и то, и то"
    }
}

/** «Дневник Маши»; если имя нельзя уверенно склонять — «Дневник: Петя, младший». */
func diaryTitle(_ name: String, sex: Sex?) -> String {
    let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
    if let genitive = genitiveName(trimmed, sex: sex) { return "Дневник \(genitive)" }
    return "Дневник: \(trimmed)"
}

private let genitiveExceptions = ["павел": "павла", "лев": "льва", "пётр": "петра", "петр": "петра"]

func genitiveName(_ name: String, sex: Sex?) -> String? {
    let cyrillic = !name.isEmpty && name.allSatisfy { character in
        character == "-" || character == "ё" || character == "Ё" ||
            ("а"..."я").contains(character) || ("А"..."Я").contains(character)
    }
    guard cyrillic, !name.hasPrefix("-"), !name.hasSuffix("-") else { return nil }
    return name.split(separator: "-", omittingEmptySubsequences: false)
        .map { genitiveWord(String($0), sex: sex) }
        .joined(separator: "-")
}

private func genitiveWord(_ word: String, sex: Sex?) -> String {
    let lower = word.lowercased()
    if let exception = genitiveExceptions[lower] { return matchCase(exception, original: word) }
    let stem = String(word.dropLast())
    let ending: String
    switch lower.last {
    case "я": ending = "и"
    case "а":
        let previous = stem.last.map { String($0).lowercased() }
        ending = previous.map { "гкхжчшщ".contains($0) } == true ? "и" : "ы"
    case "й": ending = "я"
    case "ь": ending = sex == .girl ? "и" : "я"
    case let last? where "бвгджзклмнпрстфхцчшщ".contains(last):
        return sex == .girl ? word : matchCase(lower + "а", original: word)
    default: return word
    }
    let suffix = word.count > 1 && word == word.uppercased() ? ending.uppercased() : ending
    return stem + suffix
}

private func matchCase(_ declined: String, original: String) -> String {
    if original.count > 1 && original == original.uppercased() { return declined.uppercased() }
    guard let first = original.first, String(first).uppercased() == String(first), String(first).lowercased() != String(first) else {
        return declined
    }
    return declined.prefix(1).uppercased() + declined.dropFirst()
}

/** Заголовок записи; `sex` — пол ребёнка, чья это запись. */
func eventTitle(_ event: any BabyEvent, sex: Sex?) -> String {
    switch event {
    case let event as any FeedingEvent:
        return "Кормление · \(breastLabel(event.breast))" + (event.isActive ? " · идёт" : "")
    case let event as any BottleEvent:
        return "Кормление · \(bottleLabel(event.volumeMl, milk: event.milk))"
    case let event as any DiaperEvent:
        return "Подгузник · \(diaperLabel(event.kind, sex: sex))"
    case let event as any SleepEvent:
        return event.isActive ? "Сон · идёт" : "Сон"
    case let event as any MedicineEvent:
        return "Лекарство · \(event.name)"
    case let event as any WeightEvent:
        return "Вес · \(formatWeight(event.grams))"
    case let event as any HeightEvent:
        return "Рост · \(formatHeight(event.millimeters))"
    case let event as any CryingEvent:
        return (["Плач", event.reasons.joined(separator: ", "), event.isActive ? "идёт" : ""])
            .filter { !$0.isEmpty }.joined(separator: " · ")
    case let event as any NoteEvent:
        return (["Заметка", event.tags.joined(separator: ", ")]).filter { !$0.isEmpty }.joined(separator: " · ")
    case let event as any IllnessEvent:
        return (["Болезнь", event.illnessTitle, event.isActive ? "идёт" : ""])
            .filter { !$0.isEmpty }.joined(separator: " · ")
    case let event as any TemperatureEvent:
        return "Температура · \(formatTemperature(event.tenths))"
    default:
        return ""
    }
}

func eventTimeLine(_ event: any BabyEvent, timeZone: TimeZone = .current) -> String {
    switch event {
    case let event as any SleepEvent:
        return timeRange(event.start, end: event.end, timeZone: timeZone)
    case let event as any FeedingEvent:
        return event.end == event.start
            ? formatClock(event.start, timeZone: timeZone)
            : timeRange(event.start, end: event.end, timeZone: timeZone)
    case let event as any CryingEvent:
        let helped = event.helped.isEmpty ? "" : " · помогло: \(event.helped.joined(separator: ", "))"
        return timeRange(event.start, end: event.end, timeZone: timeZone) + helped
    case let event as any IllnessEvent:
        let start = "с \(formatClock(event.start, timeZone: timeZone))"
        guard let end = event.end else { return start }
        return "\(start) по \(formatter("d MMM", timeZone: timeZone).string(from: Date(timeIntervalSince1970: Double(end) / 1000)))"
    case let event as any TemperatureEvent:
        let method = event.method.map { " · \(temperatureMethodLabel($0))" } ?? ""
        return formatClock(event.start, timeZone: timeZone) + method
    default:
        return formatClock(event.start, timeZone: timeZone)
    }
}

/** «38,4 °C»: между числом и градусами — неразрывный пробел (ADR-0041). */
func formatTemperature(_ tenths: Int) -> String {
    "\(tenths / 10),\(tenths % 10)\u{00A0}°C"
}

func temperatureValue(_ tenths: Int) -> String {
    "\(tenths / 10),\(tenths % 10)"
}

func temperatureMethodLabel(_ method: TemperatureMethod) -> String {
    switch method {
    case .armpit: "подмышкой"
    case .ear: "в ухе"
    case .rectal: "ректально"
    case .forehead: "на лбу"
    }
}

/** «бутылочка 120 мл», «бутылочка 120 мл, смесь»; пробел перед «мл» неразрывный. */
func bottleLabel(_ volumeMl: Int, milk: Milk?) -> String {
    "бутылочка \(volumeMl)\u{00A0}мл" + (milk.map { ", \(milkLabel($0))" } ?? "")
}

func milkLabel(_ milk: Milk) -> String {
    milk == .formula ? "смесь" : "сцеженное молоко"
}

/** «7–14 окт.», через месяц — «28 сен.–3 окт.», один день — «7 окт.». */
func formatDayRange(first: Date, last: Date, timeZone: TimeZone = .current) -> String {
    let calendar = calendar(timeZone)
    let short = formatter("d MMM", timeZone: timeZone)
    if calendar.isDate(first, inSameDayAs: last) { return short.string(from: first) }
    let firstComponents = calendar.dateComponents([.year, .month], from: first)
    let lastComponents = calendar.dateComponents([.year, .month], from: last)
    if firstComponents.year == lastComponents.year, firstComponents.month == lastComponents.month {
        return "\(calendar.component(.day, from: first))–\(short.string(from: last))"
    }
    return "\(short.string(from: first))–\(short.string(from: last))"
}

func formatChipDay(_ date: Date, today: Date, timeZone: TimeZone = .current) -> String {
    let calendar = calendar(timeZone)
    let short = formatter("d MMM", timeZone: timeZone).string(from: date)
    if calendar.isDate(date, inSameDayAs: today) { return "сегодня, \(short)" }
    if let yesterday = calendar.date(byAdding: .day, value: -1, to: today), calendar.isDate(date, inSameDayAs: yesterday) {
        return "вчера, \(short)"
    }
    return short
}

func formatWeekdayDay(_ date: Date, timeZone: TimeZone = .current) -> String {
    formatter("EE, d MMM", timeZone: timeZone).string(from: date)
}

private func timeRange(_ start: Int64, end: Int64?, timeZone: TimeZone) -> String {
    guard let end else { return "с \(formatClock(start, timeZone: timeZone))" }
    return "\(formatClock(start, timeZone: timeZone))–\(formatClock(end, timeZone: timeZone)) · \(formatDuration(end - start))"
}

/** «850 г» до килограмма, дальше «4,35 кг» без хвостовых нулей. */
func formatWeight(_ grams: Int) -> String {
    if grams < 1000 { return "\(grams) г" }
    let number = String(format: "%.2f", locale: RU, Double(grams) / 1000)
        .withoutSuffix("0").withoutSuffix("0").withoutSuffix(",")
    return "\(number) кг"
}

func formatHeight(_ millimeters: Int) -> String {
    "\(heightCentimeters(millimeters)) см"
}

func heightCentimeters(_ millimeters: Int) -> String {
    let tenths = millimeters % 10
    return tenths == 0 ? "\(millimeters / 10)" : "\(millimeters / 10),\(tenths)"
}

/** Возраст по-русски: «12 дн.», «2 мес 10 дн.», «1 г 3 мес». */
func formatAge(birth: Date, today: Date, sex: Sex?, timeZone: TimeZone = .current) -> String {
    if birth > today { return bySex(sex, masculine: "ещё не родился", feminine: "ещё не родилась") }
    let period = calendar(timeZone).dateComponents([.year, .month, .day], from: birth, to: today)
    let years = period.year ?? 0
    let months = period.month ?? 0
    let days = period.day ?? 0
    if years == 0 && months == 0 { return "\(days) дн." }
    if years == 0 { return days > 0 ? "\(months) мес \(days) дн." : "\(months) мес" }
    return months > 0 ? "\(years) г \(months) мес" : "\(years) г"
}

private extension String {
    func withoutSuffix(_ suffix: String) -> String {
        hasSuffix(suffix) ? String(dropLast(suffix.count)) : self
    }
}
