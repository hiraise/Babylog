import Foundation

/** Длина метки: «перевозбуждение» с запасом, но не фраза — фразу пишут в комментарий. */
let maxTagLength = 30

/** Метка в общем виде: строчные буквы, одиночные пробелы, без «;» и «|», не длиннее `maxTagLength`. */
func normalizeTag(_ raw: String) -> String? {
    let separators = raw.replacingOccurrences(of: "[;|]", with: " ", options: .regularExpression)
    let spaces = separators.replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression)
    let normalized = String(spaces.trimmingCharacters(in: .whitespacesAndNewlines).lowercased().prefix(maxTagLength))
        .trimmingCharacters(in: .whitespacesAndNewlines)
    return normalized.isEmpty ? nil : normalized
}

let defaultCryingReasons = ["колики", "усталость", "голод", "живот", "зубки", "перевозбуждение"]
let defaultCryingHelped = ["укачивание", "на руках", "грудь", "тёплое на живот", "белый шум", "соска"]
let defaultNoteTags = ["новое", "зубки", "сыпь", "питание мамы", "прививка", "колики"]

/** Метки записи — один словарь (ADR-0031): причины плача и метки заметок. */
extension BabyEvent {
    var eventTags: [String] {
        switch self {
        case let event as Crying: event.reasons
        case let event as Note: event.tags
        default: []
        }
    }
}
