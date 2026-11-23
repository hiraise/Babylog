import Foundation

/** Тема совета (ADR-0037); код в `tips_ru.json` — `rawValue`, `title` — подпись на экране. */
enum TipCategory: String, CaseIterable, Hashable, Sendable {
    case sleep = "SLEEP"
    case feeding = "FEEDING"
    case development = "DEVELOPMENT"
    case safety = "SAFETY"
    case crying = "CRYING"
    case health = "HEALTH"

    var title: String {
        switch self {
        case .sleep: "Сон"
        case .feeding: "Кормление"
        case .development: "Развитие"
        case .safety: "Безопасность"
        case .crying: "Плач и поведение"
        case .health: "Здоровье"
        }
    }
}

/** `doctor` — «когда показать педиатру»: тревожные признаки, на экране — отдельным блоком. */
enum TipKind: String, Hashable, Sendable {
    case tip = "TIP"
    case doctor = "DOCTOR"
}

/** Источник совета: `section` — раздел или страница, где стоит факт; по нему совет сверяют. */
struct TipSource: Hashable, Sendable {
    let name: String
    let url: String
    let section: String
}

/** Позиция одного источника, когда источники расходятся: «ВОЗ» — «прикорм с 6 месяцев». */
struct SourcePosition: Hashable, Sendable {
    let source: String
    let position: String
}

/**
 Совет раздела «Развитие» (ADR-0037). `ages` — дни жизни, в которые совет показывается, включительно;
 `points` — необязательный список; `disagreement` — пуст, если источники не расходятся.
 */
struct Tip: Hashable, Sendable {
    let id: String
    let ages: ClosedRange<Int>
    let category: TipCategory
    let kind: TipKind
    let title: String
    let summary: String
    let body: String
    let points: [String]
    let sources: [TipSource]
    let disagreement: [SourcePosition]
    let reviewedAt: Date
}

/** Последний день, для которого пишутся советы: первый год жизни — дни 0–364 (ADR-0037). */
let lastTipDay = 364

/**
 Сайты, на которые может ссылаться совет (ADR-0037): ВОЗ, CDC, AAP и его сайт для родителей, NHS,
 Минздрав России и портал публикации его приказов, Союз педиатров России, статьи по DOI.
 Поддомены подходят: `cdn.who.int`, `cr.minzdrav.gov.ru`, `publication.pravo.gov.ru`.
 */
let allowedSourceHosts = [
    "who.int",
    "cdc.gov",
    "aap.org",
    "healthychildren.org",
    "nhs.uk",
    "minzdrav.gov.ru",
    "pravo.gov.ru",
    "pediatr-russia.ru",
    "doi.org",
]

/**
 Сайты из `allowedSourceHosts`, которые открываются только по `http`: портал официального опубликования
 правовых актов `publication.pravo.gov.ru` по `https` не отвечает, а приказы Минздрава — на нём.
 */
let httpOnlySourceHosts = ["pravo.gov.ru"]

/** Ссылка на сайт из списка — по `https`, а на сайт из списка исключений — и по `http`. */
func isAllowedSourceUrl(_ url: String) -> Bool {
    guard let components = URLComponents(string: url),
          let host = components.host?.lowercased(),
          let scheme = components.scheme else { return false }
    func matches(_ hosts: [String]) -> Bool {
        hosts.contains { host == $0 || host.hasSuffix(".\($0)") }
    }
    switch scheme {
    case "https": return matches(allowedSourceHosts)
    case "http": return matches(httpOnlySourceHosts)
    default: return false
    }
}
