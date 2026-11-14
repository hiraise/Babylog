import Foundation
import Testing
@testable import BabyLog

struct DevelopmentJsonTest {
    @Test func parsesAFullTip() throws {
        let tip = try parseTips("[\(tipJson())]").only

        #expect(tip.id == "m3-rolling")
        #expect(tip.ages == 91...210)
        #expect(tip.category == .safety)
        #expect(tip.kind == .tip)
        #expect(tip.points == ["пункт"])
        #expect(tip.sources.only.url == "https://www.nhs.uk/baby/")
        #expect(tip.disagreement == [SourcePosition(source: "ВОЗ", position: "с 6 месяцев")])
        #expect(tip.reviewedAt == Self.date("2026-10-20"))
    }

    @Test func pointsAndDisagreementAreOptional() throws {
        let tip = try parseTips("[\(tipJson(points: nil, disagreement: nil))]").only
        #expect(tip.points.isEmpty)
        #expect(tip.disagreement.isEmpty)
    }

    @Test func rejectsATipWithoutSources() {
        assertRejected("m3-rolling", tipJson(sources: "[]"))
    }

    @Test func rejectsASourceOutsideTheAllowedSites() {
        assertRejected(
            "не из списка",
            tipJson(sources: #"[{"name": "Блог", "url": "https://example.com/a", "section": "Раздел"}]"#)
        )
    }

    @Test func rejectsUnknownCategoryEmptyTextAndAgesBeyondTheFirstYear() {
        assertRejected("category", tipJson(category: "GROWTH"))
        assertRejected("title", tipJson(title: "  "))
        assertRejected("вне 0–364", tipJson(to: 400))
        assertRejected("ageFromDays больше ageToDays", tipJson(from: 100, to: 50))
    }

    @Test func allowedSourcesIncludeSubdomainsAndDoiButOnlyOverHttps() {
        #expect(isAllowedSourceUrl("https://cdn.who.int/media/a.pdf"))
        #expect(isAllowedSourceUrl("https://www.healthychildren.org/English/a.aspx"))
        #expect(isAllowedSourceUrl("https://doi.org/10.1542/peds.113.6.e570"))
        #expect(isAllowedSourceUrl("http://publication.pravo.gov.ru/Document/View/0001202112200070"))
        #expect(!isAllowedSourceUrl("http://www.nhs.uk/"))
        #expect(!isAllowedSourceUrl("ftp://publication.pravo.gov.ru/"))
        #expect(!isAllowedSourceUrl("https://notwho.int/"))
        #expect(!isAllowedSourceUrl("не ссылка"))
    }

    @Test func parsesMilestoneWindows() throws {
        let table = try parseMilestones(#"""
        {"source": {"name": "ВОЗ", "url": "https://cdn.who.int/t.pdf", "section": "1st и 99th"},
         "reviewedAt": "2026-10-20",
         "milestones": [{"id": "sitting", "title": "Сидит без поддержки", "fromDays": 115, "toDays": 279, "fromMonths": 3.8, "toMonths": 9.2}]}
        """#)

        #expect(table.milestones == [MilestoneWindow(
            id: "sitting",
            title: "Сидит без поддержки",
            window: 115...279,
            months: 3.8...9.2
        )])
        #expect(table.source.name == "ВОЗ")
    }

    private func assertRejected(_ messagePart: String, _ tip: String) {
        do {
            _ = try parseTips("[\(tip)]")
            Issue.record("Ожидалась ошибка разбора")
        } catch {
            #expect(String(describing: error).contains(messagePart), "«\(error)» без «\(messagePart)»")
        }
    }

    private func tipJson(
        from: Int = 91,
        to: Int = 210,
        category: String = "SAFETY",
        title: String = "Скоро начнёт переворачиваться",
        points: String? = #"["пункт"]"#,
        sources: String = #"[{"name": "NHS — Baby", "url": "https://www.nhs.uk/baby/", "section": "Раздел"}]"#,
        disagreement: String? = #"[{"source": "ВОЗ", "position": "с 6 месяцев"}]"#
    ) -> String {
        var json = #"{"id": "m3-rolling", "ageFromDays": \#(from), "ageToDays": \#(to), "category": "\#(category)", "kind": "TIP","#
        json += #" "title": "\#(title)", "summary": "Кратко", "body": "Текст", "sources": \#(sources), "reviewedAt": "2026-10-20""#
        if let points { json += #", "points": \#(points)"# }
        if let disagreement { json += #", "disagreement": \#(disagreement)"# }
        return json + "}"
    }

    private static func date(_ value: String) -> Date {
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.date(from: value)!
    }
}

private extension Array {
    var only: Element {
        precondition(count == 1)
        return self[0]
    }
}
