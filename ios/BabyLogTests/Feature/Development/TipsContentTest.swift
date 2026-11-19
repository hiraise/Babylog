import Foundation
import Testing
@testable import BabyLog

private let developmentDoseRegex = try! NSRegularExpression(
    pattern: #"\d+(?:[.,]\d+)?\s*(?:мкг|мг|МЕ|ME|IU|mg)(?!\p{L})|мл\s*/\s*кг"#
)

/** Проверка контента раздела «Развитие» по правилам ADR-0037 на файлах, которые попадут в сборку. */
struct TipsContentTest {
    private let source = AssetTipsSource()
    private let today = Self.today()

    @Test func idsAreUnique() throws {
        let tips = try source.tips()
        let duplicates = Dictionary(grouping: tips, by: \.id).filter { $0.value.count > 1 }.keys
        #expect(duplicates.isEmpty, "повторяются id: \(duplicates)")
    }

    @Test func everyWeekOfTheFirstYearHasATip() throws {
        let tips = try source.tips()
        let empty = (1...52).filter { week in
            let days = ((week - 1) * 7)...(week * 7 - 1)
            return !tips.contains { $0.ages.lowerBound <= days.upperBound && $0.ages.upperBound >= days.lowerBound }
        }
        #expect(empty.isEmpty, "недели без советов: \(empty)")
    }

    @Test func textsFitTheScreen() throws {
        let tooLong = try source.tips().flatMap { tip -> [String] in
            var errors: [String] = []
            if tip.title.count > 60 { errors.append("\(tip.id): title") }
            if tip.summary.count > 100 { errors.append("\(tip.id): summary") }
            if tip.body.count > 600 { errors.append("\(tip.id): body") }
            if tip.points.count > 5 || tip.points.contains(where: { $0.count > 90 }) {
                errors.append("\(tip.id): points")
            }
            return errors
        }
        #expect(tooLong.isEmpty, "длиннее лимита: \(tooLong)")
    }

    @Test func tipsNameNoDoses() throws {
        let withDoses = try source.tips().filter { tip in
            texts(tip).contains { text in
                developmentDoseRegex.firstMatch(in: text, range: NSRange(text.startIndex..., in: text)) != nil
            }
        }.map(\.id)
        #expect(withDoses.isEmpty, "дозы в советах: \(withDoses)")
    }

    @Test func sourcePositionsContinueTheLineAfterTheSourceName() throws {
        // Экран склеивает «NHS — позиция»: позиция — со строчной, кроме аббревиатуры в начале («ВОЗ советует…»).
        let capitalized = try source.tips().flatMap { tip in
            tip.disagreement.map { (tip.id, $0.position) }
        }.filter { _, position in
            guard let firstCharacter = position.first else { return false }
            let first = String(position.prefix { $0 != " " })
            let upper = String(firstCharacter).uppercased() == String(firstCharacter) &&
                String(firstCharacter).lowercased() != String(firstCharacter)
            let abbreviation = first.count > 1 && first.allSatisfy { character in
                let value = String(character)
                return value.rangeOfCharacter(from: .letters) == nil || value.uppercased() == value
            }
            return upper && !abbreviation
        }
        #expect(capitalized.isEmpty, "позиции с заглавной: \(capitalized)")
    }

    @Test func everyTipAndTheMilestoneTableWereReviewedWithinTwoYears() throws {
        let stale = try source.tips().filter { !isFresh($0.reviewedAt) }.map(\.id)
        #expect(stale.isEmpty, "перепроверить: \(stale)")
        #expect(isFresh(try source.milestones().reviewedAt), "перепроверить окна навыков")
    }

    @Test func milestoneTableHasSixWindowsFromWho() throws {
        let table = try source.milestones()
        #expect(table.milestones.count == 6)
        #expect(table.source.url.hasPrefix("https://cdn.who.int/"))
        #expect(Set(table.milestones.map(\.id)).count == table.milestones.count)

        // Месяцы — как напечатаны у ВОЗ, но не дальше 0,1 от пересчёта дней.
        let daysPerMonth = 30.4375
        let mismatched = table.milestones.filter { milestone in
            abs(milestone.months.lowerBound - Double(milestone.window.lowerBound) / daysPerMonth) > 0.1 ||
                abs(milestone.months.upperBound - Double(milestone.window.upperBound) / daysPerMonth) > 0.1
        }
        #expect(mismatched.isEmpty, "дни и месяцы не сходятся: \(mismatched.map(\.id))")
    }

    private func isFresh(_ reviewedAt: Date) -> Bool {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        let oldest = calendar.date(byAdding: .year, value: -2, to: today)!
        return reviewedAt <= today && reviewedAt >= oldest
    }

    private func texts(_ tip: Tip) -> [String] {
        [tip.title, tip.summary, tip.body] + tip.points + tip.disagreement.map(\.position)
    }

    private static func today() -> Date {
        var local = Calendar(identifier: .gregorian)
        local.timeZone = .current
        let components = local.dateComponents([.year, .month, .day], from: Date())
        var utc = Calendar(identifier: .gregorian)
        utc.timeZone = TimeZone(secondsFromGMT: 0)!
        return utc.date(from: components)!
    }
}
