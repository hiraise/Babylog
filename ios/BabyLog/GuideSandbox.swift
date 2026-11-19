import Foundation
import Observation

/**
 Пример дневника для гайда справки (ADR-0043): те же модели, что в приложении, но над базой в памяти,
 со своими часами `GuideClock`, выбором ребёнка и «Развитием». В настоящую базу и настройки дневника
 ничего не пишет. `close` останавливает наблюдение примера и закрывает его базу.
 */
@MainActor @Observable final class GuideSandbox {
    let topic: HelpTopic
    let clock: GuideClock
    let controller: GuideController
    let diary: DiaryViewModel
    let development: DevelopmentViewModel

    /** Записи примера уже в базе: до этого экран дневника не показывают — он мелькнул бы пустым. */
    private(set) var ready = false

    @ObservationIgnored private let repository: any DiaryRepository
    @ObservationIgnored private let database: AppDatabase?
    @ObservationIgnored private let onClose: () -> Void
    @ObservationIgnored private var fillTask: Task<Void, Never>?
    @ObservationIgnored private var childrenTask: Task<Void, Never>?
    @ObservationIgnored private var eventsTask: Task<Void, Never>?

    init(
        topic: HelpTopic,
        repository: (any DiaryRepository)? = nil,
        tips: any TipsSource,
        documents: any DocumentStore,
        baseClock: any Clock,
        onClose: @escaping () -> Void = {}
    ) {
        self.topic = topic
        if let repository {
            self.repository = repository
            database = nil
        } else {
            let database = try! AppDatabase.inMemory()
            self.database = database
            self.repository = RoomDiaryRepository(database: database)
        }
        self.onClose = onClose
        clock = GuideClock(base: baseClock)
        controller = GuideController(topic: topic, clock: clock)
        let selection = MemorySelectedChildStore()
        diary = DiaryViewModel(
            repository: self.repository,
            selection: selection,
            documents: documents,
            clock: clock
        )
        development = DevelopmentViewModel(source: tips, seen: MemoryDevelopmentSeenStore())
        fill(selection: selection)
    }

    private func fill(selection: MemorySelectedChildStore) {
        fillTask = Task { [weak self, repository, controller, clock] in
            guard let self else { return }
            do {
                let childId = try await fillSampleDiary(repository: repository, now: clock.millis(), timeZone: clock.timeZone)
                selection.select(childId)
                ready = true
                childrenTask = Task { [controller] in
                    do {
                        for try await children in repository.observeChildren() {
                            controller.onChildren(children)
                        }
                    } catch { }
                }
                eventsTask = Task { [controller] in
                    do {
                        for try await events in repository.observeEvents(childId: childId) {
                            controller.onEvents(events)
                        }
                    } catch { }
                }
            } catch is CancellationError {
                // Пользователь закрыл пример до конца наполнения: это штатная отмена, не ошибка базы.
            } catch {
                // База в памяти создаётся известной миграцией; ошибка здесь — программная, как в AppContainer.
                assertionFailure("Не удалось заполнить пример дневника: \(error)")
            }
        }
    }

    func close() {
        fillTask?.cancel()
        childrenTask?.cancel()
        eventsTask?.cancel()
        try? database?.close()
        onClose()
    }
}

/** Пример идущего гайда: сохраняется при смене экрана и закрывается вместе с гайдом. */
@MainActor final class GuideSessions {
    private let create: (HelpTopic) -> GuideSandbox
    private var current: GuideSandbox?

    init(create: @escaping (HelpTopic) -> GuideSandbox) {
        self.create = create
    }

    /** Пример для `topic`: тот же, если гайд по ней уже идёт, иначе — новый. */
    func open(_ topic: HelpTopic) -> GuideSandbox {
        if let current, current.topic.id == topic.id { return current }
        return restart(topic)
    }

    /** Пример заново: «Ещё раз» в конце гайда. */
    func restart(_ topic: HelpTopic) -> GuideSandbox {
        close()
        let sandbox = create(topic)
        current = sandbox
        return sandbox
    }

    func close() {
        current?.close()
        current = nil
    }
}
