import Foundation

struct ImportResult: Equatable, Sendable {
    var added = 0
    var duplicates = 0
    var errors = 0
    var skippedActiveSleep = 0
    /** Незавершённый плач (ADR-0030) не импортируется: он стал бы «фантомным» таймером, как сон. */
    var skippedActiveCrying = 0
    /** Идущая болезнь из файла, когда у ребёнка уже идёт своя (ADR-0040): идущая у ребёнка одна. */
    var skippedActiveIllness = 0
    var childrenAdded = 0
    var birthDatesSet = 0
    var birthDateConflicts = 0
    var sexesSet = 0
    var sexConflicts = 0
    /** Фото записей из архива (ADR-0045). */
    var photosAdded = 0
    /** Из них — к записям, которые уже были в дневнике без фото. */
    var photosToExisting = 0
    var childPhotosSet = 0
    /** Фото, которые не загрузились: файла нет в архиве, он не JPEG или больше `EventPhoto.maxBytes`; у CSV — все. */
    var photosSkipped = 0
    /** Импортировали архив экспорта, а не CSV. */
    var archive = false
}

/**
 Импорт CSV — слияние по правилам ADR-0009, в одной транзакции; ничего не удаляет и не перезаписывает.
 Строки без колонки `child` (файлы старых версий) относятся к ребёнку `defaultChildId`. Фото из колонки
 `photos` без архива не загрузить — они считаются не загруженными.
 */
func importCsv(repository: any DiaryRepository, text: String, defaultChildId: Int64) async throws -> ImportResult {
    try await repository.inTransaction { transaction in
        let importer = CsvImporter(repository: transaction, defaultChildId: defaultChildId)
        try importer.run(text)
        return importer.finish(archive: false)
    }
}

/** Куда встанет фото из архива: к записи на место `position` или ребёнку. */
private enum PhotoTarget: Equatable {
    /** `existing` — запись уже была в дневнике, импорт её не добавлял. */
    case event(eventId: Int64, position: Int, existing: Bool)
    case childAvatar(childId: Int64)
}

private struct ChildRecord: Hashable {
    let childId: Int64
    let record: EventRecord
}

/**
 Импорт строк CSV и фото к ним (ADR-0045): `run` разбирает строки и запоминает, какие файлы фото нужны,
 `addPhoto` ставит прочитанное из архива фото, `finish` подводит итог.
 */
final class CsvImporter {
    private let repository: any DiaryRepositoryTransaction
    private let defaultChildId: Int64
    private var children: [Child] = []

    /** Записи дневника и добавленные импортом → id записи: по ним ищутся дубликаты. */
    private var known: [ChildRecord: Int64] = [:]

    /** Записи, у которых фото уже есть или ждут их из архива: второй раз фото к ним не добавляются. */
    private var withPhotos: Set<Int64> = []
    private var wanted: [String: [PhotoTarget]] = [:]
    private var result = ImportResult()

    init(repository: any DiaryRepositoryTransaction, defaultChildId: Int64) {
        self.repository = repository
        self.defaultChildId = defaultChildId
    }

    /** Файлы фото, которых ждут строки; пустеет по мере `addPhoto`. */
    var wantedPhotos: Set<String> { Set(wanted.keys) }

    func run(_ text: String) throws {
        children = try repository.allChildren()
        for event in try repository.allEvents() {
            let key = ChildRecord(childId: event.childId, record: event.toRecord())
            if known[key] == nil { known[key] = event.id }
        }
        withPhotos = Set(try repository.photoIds().keys)
        for row in try CsvCodec.parseRows(text) {
            let type = row[0].trimmingCharacters(in: .whitespacesAndNewlines)
            if row.contains(where: { $0.utf16.count > CsvCodec.maxFieldLength }) {
                result.errors += 1
            } else if type.caseInsensitiveCompare("type") == .orderedSame {
                continue
            } else if type.caseInsensitiveCompare(CsvCodec.profileRow) == .orderedSame {
                try importProfile(row)
            } else {
                try importEvent(row)
            }
        }
    }

    /**
     Фото `name` из архива; nil — файл не JPEG или больше `EventPhoto.maxBytes`. Фото записи встаёт на своё
     место по колонке `photos`, фото ребёнка — только если у него своего нет.
     */
    func addPhoto(name: String, jpeg: Data?) throws {
        guard let targets = wanted.removeValue(forKey: name) else { return }
        guard let jpeg else {
            result.photosSkipped += targets.count
            return
        }
        for target in targets {
            switch target {
            case let .event(eventId, position, existing):
                try repository.addEventPhoto(eventId: eventId, position: position, jpeg: jpeg)
                result.photosAdded += 1
                if existing { result.photosToExisting += 1 }
            case let .childAvatar(childId):
                guard let index = children.firstIndex(where: { $0.id == childId }) else { continue }
                children[index].photo = ChildPhoto(jpeg: jpeg)
                try repository.updateChild(children[index])
                result.childPhotosSet += 1
            }
        }
    }

    /** Итог; фото, которых так и не нашлось в архиве (или файл — CSV без архива), — не загруженные. */
    func finish(archive: Bool) -> ImportResult {
        result.photosSkipped += wanted.values.reduce(0) { $0 + $1.count }
        result.archive = archive
        return result
    }

    /**
     Строка PROFILE: создаёт ребёнка, если его нет; дату рождения и пол ставит, только если они
     не заданы. Пустой пол — файл до ADR-0016 или пол не указан: его не трогаем.
     */
    private func importProfile(_ row: [String]) throws {
        let name = CsvCodec.childName(row)
        let rawBirth = row.count > 1 ? row[1] : ""
        let birth = CsvCodec.parseTime(rawBirth)
        let badBirth = !rawBirth.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && birth == nil
        let rawSex = row.count > 3 ? row[3] : ""
        let sex = Sex.fromCode(rawSex)
        let badSex = !rawSex.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && sex == nil
        // В файлах старых версий PROFILE без имени и всегда с датой рождения.
        if badBirth || badSex || (name.isEmpty && birth == nil) || !isAcceptableName(name) {
            result.errors += 1
            return
        }
        guard let child = findChild(name) else {
            let created = try createChild(name: name, birthDate: birth, sex: sex)
            wantChildPhoto(created, row: row)
            return
        }
        wantChildPhoto(child, row: row)
        // Отличающиеся дату и пол не применяем: старый бэкап не должен молча откатить исправление.
        var updated = child
        if child.birthDate == nil {
            if let birth {
                updated.birthDate = birth
                result.birthDatesSet += 1
            }
        } else if let birth, child.birthDate != birth {
            result.birthDateConflicts += 1
        }
        if child.sex == nil {
            if let sex {
                updated.sex = sex
                result.sexesSet += 1
            }
        } else if let sex, child.sex != sex {
            result.sexConflicts += 1
        }
        if updated != child {
            try repository.updateChild(updated)
            if let index = children.firstIndex(of: child) { children[index] = updated }
        }
    }

    private func importEvent(_ row: [String]) throws {
        let name = CsvCodec.childName(row)
        let record = CsvCodec.rowToRecord(row)
        // Проверяем запись до того, как создавать ради неё ребёнка.
        let probe = record?.toEvent(childId: 0)
        guard let record, let probe, isAcceptableName(name) else {
            result.errors += 1
            return
        }
        // Незавершённые сон и плач не импортируем: они стали бы «фантомными» активными таймерами.
        if let sleep = probe as? Sleep, sleep.isActive {
            result.skippedActiveSleep += 1
            return
        }
        if let crying = probe as? Crying, crying.isActive {
            result.skippedActiveCrying += 1
            return
        }
        let child = try findChild(name) ?? createChild(name: name, birthDate: nil, sex: nil)
        guard let parsed = record.toEvent(childId: child.id) else { return }
        // Идущая болезнь переносится, но вторая идущая — нет (ADR-0040).
        let ownActiveIllness = known.keys.contains {
            $0.childId == child.id && $0.record.type == EventRecord.illness && $0.record.end == nil
        }
        let parsedKey = ChildRecord(childId: child.id, record: parsed.toRecord())
        if let illness = parsed as? Illness, illness.isActive, ownActiveIllness, known[parsedKey] == nil {
            result.skippedActiveIllness += 1
            return
        }
        let event = withLegacyFeedingEnd(parsed)
        // Идущее кормление из собственного экспорта уже есть в базе — без конца, как в файле.
        let existing = known[parsedKey] ?? known[ChildRecord(childId: child.id, record: event.toRecord())]
        if let existing {
            result.duplicates += 1
            wantEventPhotos(existing, row: row, existing: true)
            return
        }
        let id = try repository.addEvent(event)
        known[ChildRecord(childId: child.id, record: event.toRecord())] = id
        result.added += 1
        wantEventPhotos(id, row: row, existing: false)
    }

    /**
     Фото строки ждут из архива, если у записи их ещё нет: так архив возвращает фото записям, перенесённым
     раньше через CSV, а фото записи, у которой они есть, не смешивает с чужими (ADR-0045).
     */
    private func wantEventPhotos(_ eventId: Int64, row: [String], existing: Bool) {
        let names = Array(CsvCodec.photoNames(row).prefix(EventPhoto.maxPerEvent))
        if names.isEmpty || !withPhotos.insert(eventId).inserted { return }
        for (position, file) in names.enumerated() {
            want(file, target: .event(eventId: eventId, position: position, existing: existing))
        }
    }

    /** Фото ребёнка из файла ставится, только если своего у него нет: как дата рождения и пол. */
    private func wantChildPhoto(_ child: Child, row: [String]) {
        guard let file = CsvCodec.photoNames(row).first else { return }
        let target = PhotoTarget.childAvatar(childId: child.id)
        if child.photo != nil || wanted.values.contains(where: { $0.contains(target) }) { return }
        want(file, target: target)
    }

    private func want(_ file: String, target: PhotoTarget) {
        wanted[file, default: []].append(target)
    }

    /**
     `FEEDING` без `end` — кормление из файла до ADR-0015 (или шедшее во время экспорта):
     импортируется без длительности, а не «фантомным» таймером.
     */
    private func withLegacyFeedingEnd(_ event: any BabyEvent) -> any BabyEvent {
        guard var feeding = event as? Feeding, feeding.isActive else { return event }
        feeding.end = feeding.start
        return feeding
    }

    private func isAcceptableName(_ name: String) -> Bool {
        name.isEmpty || isValidChildName(name)
    }

    /** Пустое имя — ребёнок по умолчанию (выбранный на экране). */
    private func findChild(_ name: String) -> Child? {
        if name.isEmpty {
            return children.first { $0.id == defaultChildId } ?? children.first
        }
        return children.first { sameChildName($0.name, name) }
    }

    private func createChild(name: String, birthDate: Int64?, sex: Sex?) throws -> Child {
        let id = try repository.addChild(name: name, birthDate: birthDate, sex: sex, photo: nil)
        let child = Child(
            id: id,
            name: name.trimmingCharacters(in: .whitespacesAndNewlines),
            birthDate: birthDate,
            sex: sex
        )
        children.append(child)
        result.childrenAdded += 1
        return child
    }
}

/** Итог импорта для пользователя. */
extension ImportResult {
    func describe() -> String {
        var text = "Импорт: добавлено \(added)"
        if duplicates > 0 { text += ", дубликатов \(duplicates)" }
        if errors > 0 { text += ", ошибок \(errors)" }
        if skippedActiveSleep > 0 { text += ", пропущено незавершённых снов \(skippedActiveSleep)" }
        if skippedActiveCrying > 0 { text += ", пропущено незавершённого плача \(skippedActiveCrying)" }
        if skippedActiveIllness > 0 {
            text += ", пропущено идущих болезней \(skippedActiveIllness) — у ребёнка уже идёт болезнь"
        }
        if childrenAdded > 0 { text += ", новых детей \(childrenAdded)" }
        if birthDatesSet > 0 { text += ", дат рождения импортировано \(birthDatesSet)" }
        if birthDateConflicts > 0 { text += ", дата рождения в файле отличается — оставлена текущая" }
        if sexesSet > 0 { text += ", пол указан у детей: \(sexesSet)" }
        if sexConflicts > 0 { text += ", пол в файле отличается — оставлен текущий" }
        if photosAdded > 0 {
            text += ", фото \(photosAdded)"
            if photosToExisting > 0 { text += " (к прежним записям — \(photosToExisting))" }
        }
        if childPhotosSet > 0 { text += ", фото детей \(childPhotosSet)" }
        if photosSkipped > 0 {
            if archive {
                text += ", не загружено фото \(photosSkipped)"
            } else {
                text += ", фото не загружены (\(photosSkipped)) — они в архиве .zip, импортируйте его"
            }
        }
        return text
    }
}
