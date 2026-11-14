import Foundation
import Observation

/** Главный экран. Не зависит от SwiftUI: состояние и действия проверяются с фейками. */
@MainActor @Observable final class DiaryViewModel {
    private(set) var state = DiaryUiState()

    @ObservationIgnored private let repository: any DiaryRepository
    @ObservationIgnored private let selection: any SelectedChildStore
    @ObservationIgnored private let documents: any DocumentStore
    @ObservationIgnored private let clock: any Clock
    @ObservationIgnored private let timers: Timers
    @ObservationIgnored private var selectedId: Int64?
    @ObservationIgnored private var childrenTask: Task<Void, Never>?
    @ObservationIgnored private var selectionTask: Task<Void, Never>?
    @ObservationIgnored private var eventsTask: Task<Void, Never>?
    @ObservationIgnored private var photosTask: Task<Void, Never>?

    init(
        repository: any DiaryRepository,
        selection: any SelectedChildStore,
        documents: any DocumentStore,
        clock: any Clock
    ) {
        self.repository = repository
        self.selection = selection
        self.documents = documents
        self.clock = clock
        self.timers = Timers(repository: repository, clock: clock)
        observeChildren()
        observeSelection()
    }

    func selectChild(_ childId: Int64) {
        selection.select(childId)
        applySelection(childId)
    }

    /** Добавляет ребёнка и сразу выбирает его. Пустое или занятое имя игнорируется. */
    func addChild(name: String, birthDate: Int64?, sex: Sex?, photo: ChildPhoto? = nil) async {
        await write {
            let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
            guard isValidChildName(trimmed) else { return }
            let children = try await repository.allChildren()
            guard !isNameTaken(children, name: trimmed) else { return }
            let id = try await repository.addChild(name: trimmed, birthDate: birthDate, sex: sex, photo: photo)
            selectChild(id)
        }
    }

    func updateChild(_ child: Child) async {
        await write {
            let children = try await repository.allChildren()
            guard isValidChildName(child.name), !isNameTaken(children, name: child.name, exceptId: child.id) else { return }
            try await repository.updateChild(child)
        }
    }

    /** Удаляет ребёнка с его записями; последнего ребёнка удалить нельзя. */
    func deleteChild(_ child: Child) async {
        await write {
            if try await repository.deleteChild(child.id) { show("Удалено: \(child.name)") }
        }
    }

    /** Возвращает плач, который закончился с началом сна: экран сразу открывает его подробности. */
    @discardableResult
    func startSleep(start: Int64? = nil) async -> Crying? {
        guard let child = state.child else { return nil }
        let crying = state.activeCrying
        await write { _ = try await timers.startSleep(childId: child.id, start: start) }
        guard var crying else { return nil }
        crying.end = max(crying.start, start ?? clock.millis()).truncatedToSecond()
        return crying
    }

    func startFeeding(start: Int64? = nil, breast: Breast) async {
        guard let child = state.child else { return }
        await write { _ = try await timers.startFeeding(childId: child.id, breast: breast, start: start) }
    }

    func addBottle(volumeMl: Int, milk: Milk?, time: Int64) async {
        guard let child = state.child else { return }
        await write {
            try await repository.addBottle(Bottle(childId: child.id, start: time, volumeMl: volumeMl, milk: milk))
        }
    }

    /** Добавляет или обновляет запись и в той же операции сохраняет изменение её фото. */
    func saveEvent(_ event: any BabyEvent, photos: PhotoEdit? = nil) async {
        await write {
            if event.id == 0 {
                let id = try await repository.addEvent(event)
                if let photos { try await repository.setEventPhotos(eventId: id, edit: photos) }
            } else {
                try await repository.updateEvent(event)
                if let photos { try await repository.setEventPhotos(eventId: event.id, edit: photos) }
            }
        }
    }

    /** Удаляет запись вместе со связанными фото; ошибка становится сообщением экрана. */
    func deleteEvent(_ event: any BabyEvent) async {
        await write { try await repository.deleteEvent(event) }
    }

    /** Фото записи загружаются перед открытием её редактора. */
    func eventPhotos(_ eventId: Int64) async -> [EventPhoto] {
        do {
            for try await photos in repository.observeEventPhotos(eventId: eventId) { return photos }
        } catch {
            show(Self.readError)
        }
        return []
    }

    /**
     Болезнь началась (ADR-0040): `names` — с начала болезни, `temperatureTenths` — замер «сейчас» с местом
     прошлого замера. Идущая болезнь уже есть — ничего: она у ребёнка одна.
     */
    func startIllness(start: Int64, names: [String], note: String, temperatureTenths: Int?) async {
        guard let child = state.child else { return }
        let method = state.lastTemperature?.method
        await write {
            try await repository.inTransaction { transaction in
                let illness = Illness(childId: child.id, start: start, names: names.map { IllnessName($0) }, note: note)
                guard try transaction.startIllness(illness) != nil else { return }
                if let temperatureTenths {
                    _ = try transaction.addEvent(Temperature(
                        childId: child.id,
                        start: max(clock.millis(), start),
                        tenths: temperatureTenths,
                        method: method
                    ))
                }
            }
        }
    }

    /** «Прошлая вернулась» или «Ещё болеет»: у болезни снова нет конца — если другой идущей нет. */
    func resumeIllness(_ illness: Illness) async {
        guard state.activeIllness == nil else { return }
        await write {
            var resumed = current(illness)
            resumed.end = nil
            try await repository.updateEvent(resumed)
        }
    }

    /** «Выздоровел(а)»: болезнь заканчивается в `end`, но не раньше начала. */
    func recoverIllness(_ illness: Illness, end: Int64) async {
        await write {
            var recovered = current(illness)
            recovered.end = max(end, recovered.start)
            try await repository.updateEvent(recovered)
        }
    }

    /** Новое название болезни (`old` = nil) или правка: названия без повторов без учёта регистра. */
    func saveIllnessName(_ illness: Illness, old: IllnessName?, name: IllnessName) async {
        await write {
            var fresh = current(illness)
            let changed = old.map { oldName in fresh.names.map { $0 == oldName ? name : $0 } } ?? (fresh.names + [name])
            var seen: Set<String> = []
            fresh.names = changed.filter { seen.insert($0.name.lowercased()).inserted }
            try await repository.updateEvent(fresh)
        }
    }

    func removeIllnessName(_ illness: Illness, name: IllnessName) async {
        await write {
            var fresh = current(illness)
            fresh.names = fresh.names.filter { $0 != name }
            try await repository.updateEvent(fresh)
        }
    }

    /**
     Замер температуры (ADR-0041), новый или правка; `startIllness` — «Заболел(а)» в листе: болезнь начинается
     с этого замера, если идущей ещё нет.
     */
    func saveTemperature(_ temperature: Temperature, startIllness: Bool) async {
        guard let child = state.child else { return }
        await write {
            try await repository.inTransaction { transaction in
                if temperature.id == 0 {
                    var created = temperature
                    created.childId = child.id
                    _ = try transaction.addEvent(created)
                } else {
                    try transaction.updateEvent(temperature)
                }
                if startIllness {
                    _ = try transaction.startIllness(Illness(childId: child.id, start: temperature.start))
                }
            }
        }
    }

    /** Свежая запись болезни: лист мог открыться до того, как в ней что-то поменялось. */
    private func current(_ illness: Illness) -> Illness {
        state.events.compactMap { $0 as? Illness }.first { $0.id == illness.id } ?? illness
    }

    /** Более раннее начало плача сдвигает и конец сна, который этот плач прервал. */
    func saveCryingDetails(_ crying: Crying, start: Int64, reasons: [String], helped: [String], note: String) async {
        await write {
            try await repository.inTransaction { transaction in
                var edited = crying
                edited.start = start
                edited.reasons = reasons
                edited.helped = helped
                edited.note = note
                try transaction.updateEvent(edited)
                guard start < crying.start else { return }
                for case var sleep as Sleep in try transaction.allEvents()
                where sleep.childId == crying.childId && sleep.end == crying.start {
                    sleep.end = start
                    try transaction.updateEvent(sleep)
                }
            }
        }
    }

    /** Остановку касанием можно отменить, пока не начался другой сон или кормление (ADR-0021). */
    func endSleep() async {
        guard let child = state.child else { return }
        await write {
            guard let stopped = try await timers.endSleep(childId: child.id), let end = stopped.end else { return }
            var resumed = stopped
            resumed.end = nil
            show("Сон: \(formatDuration(end - stopped.start))", action: .resumeTimer(timer: resumed))
        }
    }

    func endFeeding() async {
        guard let child = state.child else { return }
        await write {
            guard let stopped = try await timers.endFeeding(childId: child.id), let end = stopped.end else { return }
            var resumed = stopped
            resumed.end = nil
            show("Кормление: \(formatDuration(end - stopped.start))", action: .resumeTimer(timer: resumed))
        }
    }

    func switchBreast() async {
        guard let child = state.child else { return }
        await write { _ = try await timers.switchBreast(childId: child.id) }
    }

    /** Плач начинается сразу; «Отменить» удаляет его и возвращает прерванный сон. */
    func startCrying() async {
        guard let child = state.child else { return }
        await write {
            guard let started = try await timers.startCrying(childId: child.id) else { return }
            show(
                started.sleep == nil ? "Плач начат" : "Плач начат, сон закончен",
                action: .cancelCrying(childId: child.id, started: started)
            )
        }
    }

    /** Возвращает остановленный плач для листа подробностей; снекбар отмены при этом убирается. */
    @discardableResult
    func endCrying() async -> Crying? {
        state.message = nil
        state.messageAction = nil
        guard let child = state.child else { return nil }
        var stopped: Crying?
        await write { stopped = try await timers.endCrying(childId: child.id) }
        return stopped
    }

    /** «Ещё плачет» продолжает ту же запись; во сне плач возобновить нельзя. */
    func resumeCrying(_ crying: Crying) async {
        await write {
            let active = try await timers.active(childId: crying.childId)
            guard active.sleep == nil, active.crying == nil else { return }
            var resumed = crying
            resumed.end = nil
            try await repository.updateEvent(resumed)
        }
    }

    /** Весь дневник с фото — архивом .zip (ADR-0045). */
    func exportTo(_ url: URL) async {
        show("Сохраняю дневник в файл…")
        do {
            let result = try await documents.write(url) {
                try await exportArchive(repository: repository, output: $0, zone: clock.timeZone)
            }
            show(result.describe())
        } catch {
            show("Ошибка экспорта: \(error.localizedDescription)")
        }
    }

    /** Архив экспорта или CSV; строки без ребёнка попадают выбранному ребёнку (ADR-0045). */
    func importFrom(_ url: URL) async {
        guard let child = state.child else { return }
        show("Загружаю дневник из файла…")
        do {
            let result = try await importFile(
                repository: repository,
                documents: documents,
                url: url,
                defaultChildId: child.id
            )
            show(result.describe())
        } catch {
            show("Ошибка импорта: \(error.localizedDescription)")
        }
    }

    func consumeMessage(performed: Bool = false) async {
        let action = state.messageAction
        state.message = nil
        state.messageAction = nil
        guard performed, let action else { return }
        await write {
            switch action {
            case let .resumeTimer(timer):
                _ = try await timers.resume(timer: timer)
            case let .cancelCrying(childId, started):
                try await timers.cancelCrying(
                    childId: childId,
                    cryingId: started.cryingId,
                    sleep: started.sleep
                )
            }
        }
    }

    private func observeChildren() {
        childrenTask = Task { [weak self, repository] in
            var attempt = 0
            while !Task.isCancelled {
                do {
                    for try await children in repository.observeChildren() {
                        self?.apply(children)
                    }
                    return
                } catch is CancellationError {
                    return
                } catch {
                    guard let self else { return }
                    if attempt == 0 { self.show(Self.readError) }
                    attempt += 1
                    try? await Task.sleep(for: .milliseconds(Self.retryDelay(attempt)))
                }
            }
        }
    }

    private func observeSelection() {
        selectionTask = Task { [weak self, selection] in
            for await id in selection.selectedChildId.stream() {
                self?.applySelection(id)
            }
        }
    }

    private func apply(_ children: [Child]) {
        let child = children.first { $0.id == selectedId } ?? children.first
        replace(children: children, child: child)
    }

    private func applySelection(_ id: Int64?) {
        selectedId = id
        let children = state.children
        let child = children.first { $0.id == id } ?? children.first
        replace(children: children, child: child)
    }

    private func replace(children: [Child], child: Child?) {
        let changed = state.child?.id != child?.id
        state = DiaryUiState.of(
            children: children,
            child: child,
            events: changed ? [] : state.events,
            photoCounts: changed ? [:] : state.photoCounts,
            message: state.message,
            messageAction: state.messageAction
        )
        if changed { observeChild(child) }
    }

    private func observeChild(_ child: Child?) {
        eventsTask?.cancel()
        photosTask?.cancel()
        guard let child else { return }
        let childId = child.id
        eventsTask = Task { [weak self, repository] in
            var attempt = 0
            while !Task.isCancelled {
                do {
                    for try await events in repository.observeEvents(childId: childId) {
                        self?.replaceEvents(events, childId: childId)
                    }
                    return
                } catch is CancellationError {
                    return
                } catch {
                    guard let self else { return }
                    if attempt == 0 { self.show(Self.readError) }
                    attempt += 1
                    try? await Task.sleep(for: .milliseconds(Self.retryDelay(attempt)))
                }
            }
        }
        photosTask = Task { [weak self, repository] in
            var attempt = 0
            while !Task.isCancelled {
                do {
                    for try await counts in repository.observePhotoCounts(childId: childId) {
                        self?.replacePhotoCounts(counts, childId: childId)
                    }
                    return
                } catch is CancellationError {
                    return
                } catch {
                    guard let self else { return }
                    if attempt == 0 { self.show(Self.readError) }
                    attempt += 1
                    try? await Task.sleep(for: .milliseconds(Self.retryDelay(attempt)))
                }
            }
        }
    }

    private func replaceEvents(_ events: [any BabyEvent], childId: Int64) {
        guard state.child?.id == childId else { return }
        state = DiaryUiState.of(
            children: state.children,
            child: state.child,
            events: events,
            photoCounts: state.photoCounts,
            message: state.message,
            messageAction: state.messageAction
        )
    }

    private func replacePhotoCounts(_ counts: [Int64: Int], childId: Int64) {
        guard state.child?.id == childId else { return }
        state.photoCounts = counts
    }

    /** Ошибка записи становится сообщением, а не закрывает приложение: форма не должна потерять введённое. */
    private func write(_ block: () async throws -> Void) async {
        do {
            try await block()
        } catch is CancellationError {
            return
        } catch {
            show(Self.writeError)
        }
    }

    private func show(_ text: String, action: MessageAction? = nil) {
        state.message = text
        state.messageAction = action
    }

    private static let writeError = "Не удалось сохранить. Проверьте, есть ли свободное место в памяти телефона"
    private static let readError = "Не удалось прочитать дневник. Пробую ещё раз…"

    private static func retryDelay(_ attempt: Int) -> Int {
        1000 * min(attempt, 10)
    }
}
