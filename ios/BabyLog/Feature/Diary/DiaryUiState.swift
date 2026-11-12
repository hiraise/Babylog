import Foundation

/** Всё, что главный экран показывает из данных. Эфемерное состояние UI (открытые листы) — во View. */
struct DiaryUiState: Sendable {
    var children: [Child] = []
    /** Выбранный ребёнок; nil — данные ещё загружаются. */
    var child: Child?
    /** Записи выбранного ребёнка, новые сверху. */
    var events: [any BabyEvent] = []
    var activeSleep: Sleep?
    var activeFeeding: Feeding?
    /** Идущий плач (ADR-0030): широкая плитка под сном и кормлением идёт таймером. */
    var activeCrying: Crying?
    /** Последний законченный плач: «2 ч назад · 24 мин» на плитке плача. */
    var lastCrying: Crying?
    /** Конец последнего законченного сна: «не спит 1 ч 20 мин» на плитке сна. */
    var lastSleepEnd: Int64?
    /** Последняя бутылочка: с её объёмом и «что внутри» открывается следующая (ADR-0025). */
    var lastBottle: Bottle?
    /** Время последней смены подгузника: «2 ч 10 мин назад» на плитке «Подгузник». */
    var lastDiaper: Int64?
    /** Последние вес и рост: в шапке под именем и датой — на плитках «Вес» и «Рост» (ADR-0027). */
    var lastWeight: Weight?
    var lastHeight: Height?
    /** Идущая болезнь (ADR-0040): её карточка на «Сегодня», день на плитке «Здоровье». */
    var activeIllness: Illness?
    /** Последний замер температуры (ADR-0041): с его значения и места начинается следующий. */
    var lastTemperature: Temperature?
    /** Последнее лекарство — подпись в листе «Здоровье». */
    var lastMedicine: Medicine?
    /** Сколько фото у записей: id записи → число, для отметки в истории (ADR-0024). */
    var photoCounts: [Int64: Int] = [:]
    /** Сообщение для снекбара; UI показывает его и вызывает `consumeMessage()`. */
    var message: String?

    static func of(
        children: [Child],
        child: Child?,
        events: [any BabyEvent],
        photoCounts: [Int64: Int] = [:],
        message: String? = nil
    ) -> DiaryUiState {
        DiaryUiState(
            children: children,
            child: child,
            events: events,
            activeSleep: events.compactMap { $0 as? Sleep }.first(where: \.isActive),
            activeFeeding: events.compactMap { $0 as? Feeding }.first(where: \.isActive),
            activeCrying: events.compactMap { $0 as? Crying }.first(where: \.isActive),
            lastCrying: events.compactMap { $0 as? Crying }.filter { !$0.isActive }.max {
                ($0.end ?? $0.start) < ($1.end ?? $1.start)
            },
            lastSleepEnd: events.compactMap { ($0 as? Sleep)?.end }.max(),
            lastBottle: events.compactMap { $0 as? Bottle }.first,
            lastDiaper: events.compactMap { ($0 as? Diaper)?.start }.first,
            lastWeight: events.compactMap { $0 as? Weight }.first,
            lastHeight: events.compactMap { $0 as? Height }.first,
            activeIllness: events.compactMap { $0 as? Illness }.first(where: \.isActive),
            lastTemperature: events.compactMap { $0 as? Temperature }.first,
            lastMedicine: events.compactMap { $0 as? Medicine }.first,
            photoCounts: photoCounts,
            message: message
        )
    }
}
