import Foundation

/**
 Раздел «Аналитики» (ADR-0029): плитка на обзоре и её подробный экран. Новый показатель — новый
 раздел здесь, его плитка в `AnalyticsOverview` и экран в `AnalyticsDetail`, а не карточка в ленте.
 */
enum AnalyticsSection: String, CaseIterable, Hashable, Sendable {
    case rhythm
    case feedings
    case sleep
    case diapers
    case crying
    case weight
    case height
    case medicines
    case tags

    var title: String {
        switch self {
        case .rhythm: "Ритм дня"
        case .feedings: "Кормления"
        case .sleep: "Сон"
        case .diapers: "Подгузники"
        case .crying: "Плач"
        case .weight: "Вес"
        case .height: "Рост"
        case .medicines: "Лекарства"
        case .tags: "Метки"
        }
    }

    /** У ритма всегда последняя неделя, у веса и роста — всё время; остальные зависят от периода. */
    var usesPeriod: Bool {
        switch self {
        case .feedings, .sleep, .diapers, .crying, .medicines, .tags: true
        case .rhythm, .weight, .height: false
        }
    }
}

/** Бутылочка — раздел кормлений, своей плитки у неё нет (ADR-0028). */
extension Metric {
    var section: AnalyticsSection {
        switch self {
        case .feedings, .bottles: .feedings
        case .sleep: .sleep
        case .diapers: .diapers
        case .crying: .crying
        }
    }
}
