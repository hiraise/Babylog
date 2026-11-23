import Foundation

/**
 Навык из исследования ВОЗ Motor Development Study (ADR-0037). `window` — дни жизни от 1-го до 99-го
 перцентиля: в этом возрасте навык осваивают 98 детей из 100. `months` — те же границы в месяцах,
 как они напечатаны в таблице ВОЗ: показывай их, а не пересчёт дней — у ВОЗ дни до округления,
 и 409 дней там — 13,5 месяца, а не 13,4.
 */
struct MilestoneWindow: Hashable, Sendable {
    let id: String
    let title: String
    let window: ClosedRange<Int>
    let months: ClosedRange<Double>

    /** Где ребёнок относительно окна: до него, в нём или окно уже прошло. */
    func stateAt(_ ageDays: Int) -> MilestoneState {
        if ageDays < window.lowerBound { return .ahead }
        if ageDays > window.upperBound { return .past }
        return .inWindow
    }
}

/** Где ребёнок относительно окна: до него, в нём или окно уже прошло. */
enum MilestoneState: Hashable, Sendable {
    case ahead
    case inWindow
    case past
}

/** Окна навыков вместе с источником таблицы и датой, когда её сверяли. */
struct MilestoneTable: Hashable, Sendable {
    let source: TipSource
    let reviewedAt: Date
    let milestones: [MilestoneWindow]
}
