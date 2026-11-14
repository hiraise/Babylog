import Foundation

/**
 Сводка болезни для врача (ADR-0040) — обычный текст для системного «Поделиться»: кто, с какого дня
 и что за болезнь, температура (самая высокая, последняя, по дням), лекарства, «по дневнику» против
 обычного и заметки. Только записи — без выводов: что они значат, решает врач.
 */
func doctorText(model: IllnessScreenModel, child: Child, lastWeight: Weight?, timeZone: TimeZone) -> String {
    let illness = model.illness
    let today = model.today
    let first = illness.startDate(timeZone)
    var blocks: [String] = []

    let who = [
        child.name.trimmingCharacters(in: .whitespacesAndNewlines),
        child.birthDate.map { formatAge(birth: toLocalDate($0, timeZone: timeZone), today: today, sex: child.sex, timeZone: timeZone) },
        lastWeight.map { formatWeight($0.grams) },
    ].compactMap { $0 }.joined(separator: ", ")
    let fromStart = illness.names.filter { $0.sinceDate(illness, timeZone: timeZone) <= first }
    let later = illness.names.filter { $0.sinceDate(illness, timeZone: timeZone) > first }
    let names = [
        fromStart.map(\.name).joined(separator: ", "),
        later.map { "с \(doctorDay($0.sinceDate(illness, timeZone: timeZone), timeZone: timeZone)) — \($0.name)" }.joined(separator: ", "),
    ].filter { !$0.isEmpty }.joined(separator: ", ")
    let period: String
    if illness.isActive {
        period = "Болеет с \(doctorDay(first, timeZone: timeZone)) (\(model.days)-й день)"
    } else {
        let endDay = illnessCalendar(timeZone).date(byAdding: .day, value: model.days - 1, to: first) ?? first
        period = "\(bySex(child.sex, masculine: "Болел", feminine: "Болела")) \(formatDayRange(first: first, last: endDay, timeZone: timeZone)) (\(model.days) \(plural(model.days, one: "день", few: "дня", many: "дней")))"
    }
    blocks.append("\(who)\n\(period)" + (names.isEmpty ? "." : ": \(names)."))

    if let temperatures = doctorTemperatureBlock(model.temperatures, timeZone: timeZone) {
        blocks.append(temperatures)
    }

    if !model.medicines.isEmpty {
        let lines: [String] = model.medicines.sorted { ($0.times.first ?? 0) < ($1.times.first ?? 0) }.map { medicine -> String in
            let trimmed = medicine.note.trimmingCharacters(in: .whitespacesAndNewlines)
            let dose = trimmed.isEmpty ? "" : " \(trimmed)"
            return "• \(medicine.name)\(dose) — \(medicine.count) \(plural(medicine.count, one: "раз", few: "раза", many: "раз")), последний \(doctorDay(toLocalDate(medicine.last, timeZone: timeZone), timeZone: timeZone)) в \(formatClock(medicine.last, timeZone: timeZone))"
        }
        blocks.append("Лекарства:\n\(lines.joined(separator: "\n"))")
    }

    if let diary = model.diary {
        let parts = diary.lines.map { line in
            let what: String
            switch line.kind {
            case .wetDiapers: what = "мокрых подгузников"
            case .feedings: what = "кормлений"
            case .sleep: what = "сон"
            case .crying: what = "плач"
            }
            return "\(what) \(line.format(line.value))" + (line.usual.map { " (обычно \(line.format($0)))" } ?? "")
        }.joined(separator: ", ")
        blocks.append((diary.yesterday ? "Вчера" : "В дни болезни в среднем") + ": \(parts).")
    }

    if !model.notes.isEmpty {
        let lines: [String] = model.notes.map { note -> String in
            let trimmed = note.note.trimmingCharacters(in: .whitespacesAndNewlines)
            let tags = note.tags.isEmpty ? nil : note.tags.joined(separator: ", ")
            let text = [trimmed.isEmpty ? nil : trimmed, tags.map { "[\($0)]" }].compactMap { $0 }.joined(separator: " ")
            return "\(doctorDay(toLocalDate(note.start, timeZone: timeZone), timeZone: timeZone)), \(formatClock(note.start, timeZone: timeZone)) — \(text.isEmpty ? "заметка" : text)"
        }
        blocks.append("Заметки:\n\(lines.joined(separator: "\n"))")
    }
    return blocks.joined(separator: "\n\n")
}

/**
 «Температура: макс. 39,2 (10 окт., 03:10), последняя 38,8 (10 окт., 10:40).» и ниже — самая высокая
 за каждые сутки: «По дням: 7 окт. — 38,4 · 8 — 38,6». Замеров нет — nil.
 */
private func doctorTemperatureBlock(_ temperatures: [Temperature], timeZone: TimeZone) -> String? {
    guard !temperatures.isEmpty,
          let maximum = temperatures.max(by: { $0.tenths == $1.tenths ? $0.start < $1.start : $0.tenths < $1.tenths }),
          let last = temperatures.max(by: { $0.start < $1.start }) else { return nil }
    func at(_ temperature: Temperature) -> String {
        "\(temperatureValue(temperature.tenths)) (\(doctorDay(toLocalDate(temperature.start, timeZone: timeZone), timeZone: timeZone)), \(formatClock(temperature.start, timeZone: timeZone)))"
    }
    let head = "Температура: макс. \(at(maximum))" + (last != maximum ? ", последняя \(at(last))." : ".")
    let byDay = Dictionary(grouping: temperatures) { toLocalDate($0.start, timeZone: timeZone) }.sorted { $0.key < $1.key }
    guard byDay.count >= 2 else { return head }
    var month = -1
    let lines: [String] = byDay.map { date, values -> String in
        let currentMonth = illnessCalendar(timeZone).component(.month, from: date)
        let label = currentMonth == month ? String(illnessCalendar(timeZone).component(.day, from: date)) : doctorDay(date, timeZone: timeZone)
        month = currentMonth
        return "\(label) — \(temperatureValue(values.map(\.tenths).max() ?? 0))"
    }
    return "\(head)\nПо дням: \(lines.joined(separator: " · "))"
}

/** «7 окт.» */
private func doctorDay(_ date: Date, timeZone: TimeZone) -> String {
    formatDayRange(first: date, last: date, timeZone: timeZone)
}
