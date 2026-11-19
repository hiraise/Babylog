import Foundation

/** Синхронные операции внутри одной GRDB-транзакции. Не выносить за пределы замыкания `inTransaction`. */
protocol DiaryRepositoryTransaction {
    func allChildren() throws -> [Child]
    func allEvents() throws -> [any BabyEvent]
    @discardableResult func addChild(name: String, birthDate: Int64?, sex: Sex?, photo: ChildPhoto?) throws -> Int64
    func updateChild(_ child: Child) throws
    @discardableResult func deleteChild(_ childId: Int64) throws -> Bool
    @discardableResult func addEvent(_ event: any BabyEvent) throws -> Int64
    func updateEvent(_ event: any BabyEvent) throws
    func deleteEvent(_ event: any BabyEvent) throws
    @discardableResult func startSleep(childId: Int64, start: Int64) throws -> Bool
    func startCrying(childId: Int64, start: Int64) throws -> Int64?
    func startIllness(_ illness: Illness) throws -> Int64?
    @discardableResult func startFeeding(childId: Int64, start: Int64, breast: Breast) throws -> Bool
    func setEventPhotos(eventId: Int64, edit: PhotoEdit) throws
    func photoIds() throws -> [Int64: [Int64]]
    func photoJpeg(_ photoId: Int64) throws -> Data?
    func addEventPhoto(eventId: Int64, position: Int, jpeg: Data) throws
    func addBottle(_ bottle: Bottle) throws
}

/** Единственный доступ к данным дневника. Время записей усекается до секунды здесь (ADR-0008). */
protocol DiaryRepository: Sendable {
    /** Дети в порядке добавления. Всегда есть хотя бы один ребёнок. */
    func observeChildren() -> AsyncThrowingStream<[Child], Error>
    /** Записи ребёнка, новые сверху (по времени, затем по id). */
    func observeEvents(childId: Int64) -> AsyncThrowingStream<[any BabyEvent], Error>
    func allChildren() async throws -> [Child]
    /** Все записи всех детей по возрастанию времени — для экспорта и импорта. */
    func allEvents() async throws -> [any BabyEvent]
    @discardableResult func addChild(name: String, birthDate: Int64?, sex: Sex?, photo: ChildPhoto?) async throws -> Int64
    func updateChild(_ child: Child) async throws
    @discardableResult func deleteChild(_ childId: Int64) async throws -> Bool
    @discardableResult func addEvent(_ event: any BabyEvent) async throws -> Int64
    func updateEvent(_ event: any BabyEvent) async throws
    func deleteEvent(_ event: any BabyEvent) async throws
    @discardableResult func startSleep(childId: Int64, start: Int64) async throws -> Bool
    func startCrying(childId: Int64, start: Int64) async throws -> Int64?
    func startIllness(_ illness: Illness) async throws -> Int64?
    @discardableResult func startFeeding(childId: Int64, start: Int64, breast: Breast) async throws -> Bool
    func observeEventPhotos(eventId: Int64) -> AsyncThrowingStream<[EventPhoto], Error>
    func observePhotoCounts(childId: Int64) -> AsyncThrowingStream<[Int64: Int], Error>
    func setEventPhotos(eventId: Int64, edit: PhotoEdit) async throws
    func photoIds() async throws -> [Int64: [Int64]]
    func photoJpeg(_ photoId: Int64) async throws -> Data?
    func addEventPhoto(eventId: Int64, position: Int, jpeg: Data) async throws
    func addBottle(_ bottle: Bottle) async throws
    /** Выполняет `block` в одной транзакции: либо применяются все изменения, либо ни одного. */
    func inTransaction<T: Sendable>(_ block: @Sendable (any DiaryRepositoryTransaction) throws -> T) async throws -> T
}

/** Усечение до секунды с округлением вниз, как `Math.floorDiv` на Android. */
extension Int64 {
    func truncatedToSecond() -> Int64 {
        let quotient = self / 1000
        return (self % 1000 < 0 ? quotient - 1 : quotient) * 1000
    }
}
