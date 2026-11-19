import Foundation

/** Больше подсказок под полем не нужно: они растут вниз и сдвигают остальную форму. */
private let maxSuggestions = 3

/** Лекарство из прежних записей ребёнка: подсказка в листе лекарства (`pastMedicines`). */
struct PastMedicine: Hashable, Sendable {
    var name: String
    var note: String = ""

    init(_ name: String, note: String = "") {
        self.name = name
        self.note = note
    }
}

/**
 Лекарства из записей `events` без повторов, недавние первыми. Названия, которые различаются
 только регистром или «ё», — одно лекарство: остаётся последнее написание и последний непустой
 комментарий — запись без комментария не стирает дозу, которую писали раньше.
 */
func pastMedicines(events: [any BabyEvent]) -> [PastMedicine] {
    let records = events.compactMap { $0 as? Medicine }.sorted { $0.start > $1.start }
    var result: [PastMedicine] = []
    var indices: [String: Int] = [:]
    for record in records {
        let key = normalizedMedicine(record.name)
        let note = record.note.trimmingCharacters(in: .whitespacesAndNewlines)
        if let index = indices[key] {
            if result[index].note.isEmpty, !note.isEmpty { result[index].note = note }
        } else {
            indices[key] = result.count
            result.append(PastMedicine(record.name.trimmingCharacters(in: .whitespacesAndNewlines), note: note))
        }
    }
    return result
}

/**
 Подсказки к набранному `query` из `medicines` (недавние первыми): сначала те, чьё название
 начинается с `query`, затем те, где с него начинается другое слово («D» → «Витамин D»).
 Регистр и «ё» не различаются. Пустой ввод — без подсказок; название, набранное целиком,
 не подсказывается.
 */
func medicineSuggestions(_ medicines: [PastMedicine], query: String) -> [PastMedicine] {
    let typed = normalizedMedicine(query)
    guard !typed.isEmpty else { return [] }
    let candidates = medicines.map { ($0, normalizedMedicine($0.name)) }.filter { $0.1 != typed }
    let byStart = candidates.filter { $0.1.hasPrefix(typed) }
    let byWord = candidates.filter { _, name in
        !name.hasPrefix(typed) && name.split(whereSeparator: { !$0.isLetter && !$0.isNumber }).contains {
            $0.hasPrefix(typed)
        }
    }
    return Array((byStart + byWord).prefix(maxSuggestions).map(\.0))
}

/**
 Комментарий после касания подсказки `picked`. Пустой или подставленный прошлой подсказкой
 (`autoNote`) заменяется комментарием `picked`: передумал и выбрал другое лекарство — сменится
 и доза. Набранный или исправленный вручную остаётся.
 */
func noteAfterSuggestion(note: String, autoNote: String, picked: PastMedicine) -> String {
    note.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || note == autoNote ? picked.note : note
}

private func normalizedMedicine(_ text: String) -> String {
    text.trimmingCharacters(in: .whitespacesAndNewlines).lowercased().replacingOccurrences(of: "ё", with: "е")
}
