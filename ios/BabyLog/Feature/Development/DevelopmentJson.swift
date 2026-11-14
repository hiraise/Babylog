import Foundation

struct DevelopmentJsonError: LocalizedError, CustomStringConvertible {
    let message: String

    init(_ message: String) {
        self.message = message
    }

    var description: String { message }
    var errorDescription: String? { message }
}

/**
 Советы из `tips_ru.json` (ADR-0037): массив объектов с полями совета. Нарушение схемы, неизвестная
 тема, источник не из `allowedSourceHosts` — `DevelopmentJsonError` с `id` совета: такой файл
 не должен попасть в сборку, его ловит `TipsContentTest`.
 */
func parseTips(_ json: String) throws -> [Tip] {
    let root = try parse { try jsonRoot(json) }
    return try parse {
        guard let array = root as? [Any] else { throw DevelopmentJsonError("ожидался массив советов") }
        return try array.enumerated().map { index, value in
            guard let object = value as? [String: Any] else {
                throw DevelopmentJsonError("совет №\(index): ожидался объект")
            }
            let id = try requiredString(object, "id", owner: "совет №\(index)")
            return try parse(id) { try tip(object, id: id) }
        }
    }
}

/** Окна навыков из `who_motor_milestones.json`: источник, дата сверки и шесть навыков с окнами. */
func parseMilestones(_ json: String) throws -> MilestoneTable {
    let root = try parse { try jsonRoot(json) }
    return try parse("окна навыков") {
        guard let object = root as? [String: Any] else { throw DevelopmentJsonError("ожидался объект") }
        let sourceObject = try requiredObject(object, "source")
        let items = try requiredArray(object, "milestones")
        return MilestoneTable(
            source: try source(sourceObject),
            reviewedAt: try date(object, "reviewedAt"),
            milestones: try items.enumerated().map { index, value in
                guard let item = value as? [String: Any] else {
                    throw DevelopmentJsonError("навык №\(index): ожидался объект")
                }
                let fromMonths = try number(item, "fromMonths")
                let toMonths = try number(item, "toMonths")
                guard fromMonths < toMonths else { throw DevelopmentJsonError("fromMonths больше toMonths") }
                return MilestoneWindow(
                    id: try requiredString(item, "id", owner: "навык №\(index)"),
                    title: try requiredString(item, "title", owner: "навык №\(index)"),
                    window: try dayRange(item, from: "fromDays", to: "toDays"),
                    months: fromMonths...toMonths
                )
            }
        )
    }
}

private func tip(_ object: [String: Any], id: String) throws -> Tip {
    let ages = try dayRange(object, from: "ageFromDays", to: "ageToDays")
    guard ages.lowerBound >= 0 && ages.upperBound <= lastTipDay else {
        throw DevelopmentJsonError("возраст \(ages.lowerBound)–\(ages.upperBound) вне 0–\(lastTipDay)")
    }
    let sources = try requiredArray(object, "sources").map { value -> TipSource in
        guard let sourceObject = value as? [String: Any] else { throw DevelopmentJsonError("источник: ожидался объект") }
        return try source(sourceObject)
    }
    guard !sources.isEmpty else { throw DevelopmentJsonError("нет источников") }
    let points = try optionalArray(object, "points")?.map { value -> String in
        guard let string = value as? String else { throw DevelopmentJsonError("points: ожидалась строка") }
        return string.trimmingCharacters(in: .whitespacesAndNewlines)
    }.filter { !$0.isEmpty } ?? []
    let disagreement = try optionalArray(object, "disagreement")?.map { value -> SourcePosition in
        guard let item = value as? [String: Any] else { throw DevelopmentJsonError("disagreement: ожидался объект") }
        return SourcePosition(
            source: try requiredString(item, "source", owner: id),
            position: try requiredString(item, "position", owner: id)
        )
    } ?? []
    return Tip(
        id: id,
        ages: ages,
        category: try enumValue(object, "category", TipCategory.self),
        kind: try enumValue(object, "kind", TipKind.self),
        title: try requiredString(object, "title", owner: id),
        summary: try requiredString(object, "summary", owner: id),
        body: try requiredString(object, "body", owner: id),
        points: points,
        sources: sources,
        disagreement: disagreement,
        reviewedAt: try date(object, "reviewedAt")
    )
}

private func source(_ object: [String: Any]) throws -> TipSource {
    let url = try requiredString(object, "url", owner: "источник")
    guard isAllowedSourceUrl(url) else {
        throw DevelopmentJsonError("источник не из списка ADR-0037: \(url)")
    }
    return TipSource(
        name: try requiredString(object, "name", owner: url),
        url: url,
        section: try requiredString(object, "section", owner: url)
    )
}

private func dayRange(_ object: [String: Any], from: String, to: String) throws -> ClosedRange<Int> {
    let lower = try integer(object, from)
    let upper = try integer(object, to)
    guard lower <= upper else { throw DevelopmentJsonError("\(from) больше \(to)") }
    return lower...upper
}

private func requiredString(_ object: [String: Any], _ name: String, owner: String) throws -> String {
    let value = (object[name] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
    guard !value.isEmpty else { throw DevelopmentJsonError("\(owner): нет поля \(name)") }
    return value
}

private func requiredObject(_ object: [String: Any], _ name: String) throws -> [String: Any] {
    guard let value = object[name] as? [String: Any] else { throw DevelopmentJsonError("\(name): ожидался объект") }
    return value
}

private func requiredArray(_ object: [String: Any], _ name: String) throws -> [Any] {
    guard let value = object[name] as? [Any] else { throw DevelopmentJsonError("\(name): ожидался массив") }
    return value
}

private func optionalArray(_ object: [String: Any], _ name: String) throws -> [Any]? {
    guard let raw = object[name], !(raw is NSNull) else { return nil }
    guard let value = raw as? [Any] else { throw DevelopmentJsonError("\(name): ожидался массив") }
    return value
}

private func integer(_ object: [String: Any], _ name: String) throws -> Int {
    guard let raw = object[name], CFGetTypeID(raw as CFTypeRef) != CFBooleanGetTypeID(), let value = raw as? NSNumber else {
        throw DevelopmentJsonError("\(name): ожидалось число")
    }
    return value.intValue
}

private func number(_ object: [String: Any], _ name: String) throws -> Double {
    guard let raw = object[name], CFGetTypeID(raw as CFTypeRef) != CFBooleanGetTypeID(), let value = raw as? NSNumber else {
        throw DevelopmentJsonError("\(name): ожидалось число")
    }
    return value.doubleValue
}

private func enumValue<E: RawRepresentable>(_ object: [String: Any], _ name: String, _ type: E.Type) throws -> E where E.RawValue == String {
    guard let code = object[name] as? String, let value = E(rawValue: code) else {
        let code = object[name] as? String ?? ""
        throw DevelopmentJsonError("\(name): неизвестное значение \(code)")
    }
    return value
}

private func date(_ object: [String: Any], _ name: String) throws -> Date {
    guard let value = object[name] as? String else { throw DevelopmentJsonError("\(name): не дата ГГГГ-ММ-ДД") }
    let formatter = DateFormatter()
    formatter.calendar = Calendar(identifier: .gregorian)
    formatter.locale = Locale(identifier: "en_US_POSIX")
    formatter.timeZone = TimeZone(secondsFromGMT: 0)
    formatter.dateFormat = "yyyy-MM-dd"
    formatter.isLenient = false
    guard let date = formatter.date(from: value), formatter.string(from: date) == value else {
        throw DevelopmentJsonError("\(name): не дата ГГГГ-ММ-ДД")
    }
    return date
}

private func jsonRoot(_ json: String) throws -> Any {
    guard let data = json.data(using: .utf8) else { throw DevelopmentJsonError("не UTF-8") }
    return try JSONSerialization.jsonObject(with: data)
}

/** Все ошибки разбора приходят наружу одним типом с местом ошибки. */
private func parse<T>(_ where: String? = nil, block: () throws -> T) throws -> T {
    do {
        return try block()
    } catch let error as DevelopmentJsonError {
        guard let owner = `where` else { throw error }
        throw DevelopmentJsonError("\(owner): \(error.message)")
    } catch {
        let message = String(describing: error)
        if let owner = `where` { throw DevelopmentJsonError("\(owner): \(message)") }
        throw DevelopmentJsonError(message)
    }
}
