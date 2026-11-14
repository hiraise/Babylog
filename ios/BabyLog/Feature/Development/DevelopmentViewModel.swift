import Foundation
import Observation

/** Советы и окна навыков из файлов приложения (ADR-0037). */
struct DevelopmentData: Sendable {
    let tips: [Tip]
    let milestones: MilestoneTable
}

/**
 Раздел «Развитие» (ADR-0037): читает советы и окна навыков с диска в фоне и помнит, какую неделю
 жизни раздел уже открывали у каждого ребёнка. Что показать на неделе — чистая `developmentWeek`.
 */
@MainActor @Observable final class DevelopmentViewModel {
    /** nil — файлы ещё читаются. */
    private(set) var data: DevelopmentData?

    /** Последняя открытая неделя жизни по id ребёнка. */
    private(set) var seenWeeks: [Int64: Int]

    @ObservationIgnored private let seen: any DevelopmentSeenStore
    @ObservationIgnored private var dataTask: Task<Void, Never>?
    @ObservationIgnored private var seenTask: Task<Void, Never>?

    init(source: any TipsSource, seen: any DevelopmentSeenStore) {
        self.seen = seen
        seenWeeks = seen.seenWeeks.value
        load(source)
        observeSeen()
    }

    func markSeen(childId: Int64, week: Int) {
        seen.markSeen(childId: childId, week: week)
    }

    private func load(_ source: any TipsSource) {
        dataTask = Task { [weak self, source] in
            let loaded = try? await Task.detached(priority: .userInitiated) {
                try DevelopmentData(tips: source.tips(), milestones: source.milestones())
            }.value
            guard let loaded, !Task.isCancelled else { return }
            self?.data = loaded
        }
    }

    private func observeSeen() {
        seenTask = Task { [weak self, seen] in
            for await weeks in seen.seenWeeks.stream() {
                self?.seenWeeks = weeks
            }
        }
    }
}
