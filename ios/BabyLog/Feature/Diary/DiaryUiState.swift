import Foundation

/** Последнее кормление целиком: смена груди продолжает ту же сессию, бутылочка — одно время. */
struct LastFeeding: Hashable, Sendable {
    var start: Int64
    var end: Int64
    var breast: Breast?
}

/** Действие снекбара «Отменить»: вернуть таймер или удалить случайно начатый плач. */
enum MessageAction: Sendable {
    case resumeTimer(timer: any BabyEvent)
    case cancelCrying(childId: Int64, started: StartedCrying)
}

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
    /** Последнее кормление: начало всей сессии, конец и последняя грудь либо бутылочка. */
    var lastFeeding: LastFeeding?
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
    /** Названия для листов болезни: из прошлых болезней ребёнка, затем частые. */
    var illnessNames: [String] = []
    /** Сколько фото у записей: id записи → число, для отметки в истории (ADR-0024). */
    var photoCounts: [Int64: Int] = [:]
    /** Сообщение для снекбара; UI показывает его и вызывает `consumeMessage()`. */
    var message: String?
    var messageAction: MessageAction?

    static func of(
        children: [Child],
        child: Child?,
        events: [any BabyEvent],
        photoCounts: [Int64: Int] = [:],
        message: String? = nil,
        messageAction: MessageAction? = nil
    ) -> DiaryUiState {
        let session = feedingSessions(events: events)
            .filter { $0.end != nil }
            .max { ($0.end ?? 0) < ($1.end ?? 0) }
        let bottle = events.compactMap { $0 as? Bottle }.max { $0.start < $1.start }
        let byBottle = bottle != nil && (session?.end ?? Int64.min) < bottle!.start
        let lastFeeding: LastFeeding?
        if byBottle, let bottle {
            lastFeeding = LastFeeding(start: bottle.start, end: bottle.start, breast: nil)
        } else if let session, let end = session.end {
            lastFeeding = LastFeeding(start: session.start, end: end, breast: session.lastBreast)
        } else {
            lastFeeding = nil
        }

        return DiaryUiState(
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
            lastFeeding: lastFeeding,
            lastBottle: bottle,
            lastDiaper: events.compactMap { ($0 as? Diaper)?.start }.max(),
            lastWeight: events.compactMap { $0 as? Weight }.max { $0.start < $1.start },
            lastHeight: events.compactMap { $0 as? Height }.max { $0.start < $1.start },
            activeIllness: events.activeIllness(),
            lastTemperature: events.compactMap { $0 as? Temperature }.max { $0.start < $1.start },
            lastMedicine: events.compactMap { $0 as? Medicine }.max { $0.start < $1.start },
            illnessNames: events.illnessNameChoices(),
            photoCounts: photoCounts,
            message: message,
            messageAction: messageAction
        )
    }
}
