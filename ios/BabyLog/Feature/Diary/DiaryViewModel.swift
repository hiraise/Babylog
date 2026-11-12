import Foundation
import Observation

/** Главный экран. Не зависит от SwiftUI: состояние и действия проверяются с фейками. */
@MainActor @Observable final class DiaryViewModel {
    private(set) var state = DiaryUiState()

    @ObservationIgnored private let repository: any DiaryRepository
    @ObservationIgnored private let selection: any SelectedChildStore
    @ObservationIgnored private let documents: any DocumentStore
    @ObservationIgnored private let clock: any Clock
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

    func consumeMessage() {
        state.message = nil
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
            message: state.message
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
            message: state.message
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

    private func show(_ text: String) {
        state.message = text
    }

    private static let writeError = "Не удалось сохранить. Проверьте, есть ли свободное место в памяти телефона"
    private static let readError = "Не удалось прочитать дневник. Пробую ещё раз…"

    private static func retryDelay(_ attempt: Int) -> Int {
        1000 * min(attempt, 10)
    }
}
