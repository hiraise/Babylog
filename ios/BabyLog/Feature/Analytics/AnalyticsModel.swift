import Foundation

/**
 Всё, что показывает вкладка «Аналитика» (ADR-0028). Считается из записей ребёнка на лету,
 ничего не хранит: исправленная запись сразу меняет графики.
 */
struct AnalyticsModel: Sendable {
    /** У ребёнка есть хоть одна запись; иначе вкладка — подсказка вместо графиков. */
    var hasRecords: Bool
    /** Ритм последних 7 суток — независимо от периода; nil — за неделю записей нет. */
    var rhythm: RhythmModel?
    var metrics: [MetricModel]
    /** Вес и рост — за всё время; nil — замеров нет. */
    var weight: GrowthModel?
    var height: GrowthModel?
    var medicines: [MedicineLine]
    /** Что было перед плачем и почему (ADR-0030); nil — законченного плача за период нет. */
    var crying: CryingModel? = nil
    /** Метки плача и заметок (ADR-0031); nil — меток и заметок у ребёнка нет: плитки нет. */
    var tags: TagsModel? = nil
    /** «Болела: 7–14 окт.» под выбором периода (ADR-0040); nil — в периоде не болел(а). */
    var illnessNote: String? = nil
    /** Сон в дни болезни и без — в разделе «Сон»; nil — сравнивать не с чем. */
    var illnessSleep: IllnessSleep? = nil
}

/**
 `events` — записи одного ребёнка в любом порядке; `sex` — для «пописал / пописала».
 Карточка «Бутылочка» есть, только если за период были бутылочки; «Плач» — если плач записывали
 хоть раз: тем, кто его не отмечает, плитка с нулями не нужна.
 */
func analyticsModel(events: [any BabyEvent], period: AnalyticsPeriod, now: Int64, timeZone: TimeZone, sex: Sex?) -> AnalyticsModel {
    guard !events.isEmpty else {
        return AnalyticsModel(
            hasRecords: false,
            rhythm: nil,
            metrics: [],
            weight: nil,
            height: nil,
            medicines: []
        )
    }
    var calendar = Calendar(identifier: .gregorian)
    calendar.locale = RU
    calendar.firstWeekday = 2
    calendar.timeZone = timeZone
    let today = toLocalDate(now, timeZone: timeZone)
    let length = period.days
    let days: [DayStats]
    let previous: [DayStats]
    if length == nil {
        // «Всё время» — с первой записи режима: вес и рост с рождения не растягивают графики на пустые недели.
        let firstRoutine = events.filter(\.isRoutineForAnalytics).map(\.start).min() ?? now
        // Записи «из будущего» период не сдвигают: он кончается сегодня.
        let firstDay = min(toLocalDate(firstRoutine, timeZone: timeZone), today)
        // Недели — с понедельника: столбики «всего времени» совпадают с календарными неделями.
        let weekday = (calendar.component(.weekday, from: firstDay) + 5) % 7 + 1
        let from = calendar.date(byAdding: .day, value: -(weekday - 1), to: firstDay)!
        days = dailyStats(events: events, from: from, to: today, now: now, timeZone: timeZone)
        previous = []
    } else {
        let from = calendar.date(byAdding: .day, value: -(2 * length! - 1), to: today)!
        let both = dailyStats(events: events, from: from, to: today, now: now, timeZone: timeZone)
        previous = Array(both.prefix(length!))
        days = Array(both.dropFirst(length!))
    }
    let week = length == rhythmDays
        ? days
        : dailyStats(
            events: events,
            from: calendar.date(byAdding: .day, value: -(rhythmDays - 1), to: today)!,
            to: today,
            now: now,
            timeZone: timeZone
        )
    let periodStart = Int64(days.first!.date.timeIntervalSince1970 * 1000)
    let since = length == nil ? nil : periodStart
    let cries = events.contains { $0 is Crying }
    return AnalyticsModel(
        hasRecords: true,
        rhythm: week.contains(where: \.tracked) ? rhythmModel(events: events, stats: week, now: now, timeZone: timeZone) : nil,
        metrics: Metric.allCases
            .filter { $0 != .bottles || days.contains { $0.bottles > 0 } }
            .filter { $0 != .crying || cries }
            .map { metricModel(metric: $0, period: period, days: days, previous: previous, sex: sex, timeZone: timeZone) },
        weight: weightModel(events: events, now: now, timeZone: timeZone),
        height: heightModel(events: events, now: now, timeZone: timeZone),
        medicines: medicineLines(events: events, since: since, now: now, timeZone: timeZone),
        crying: cries ? cryingModel(events: events, from: periodStart, to: now + 1) : nil,
        tags: tagsModel(events: events, days: days, byWeeks: length == nil, timeZone: timeZone),
        illnessNote: illnessNote(events: events, first: days.first!.date, last: days.last!.date, today: today, sex: sex, timeZone: timeZone),
        illnessSleep: illnessSleep(days, sex: sex)
    )
}

/** Запись режима дня: по ним видно, что дневник в эти сутки вели (`DayStats.tracked`). */
private extension BabyEvent {
    var isRoutineForAnalytics: Bool {
        switch self {
        case is Feeding, is Bottle, is Sleep, is Diaper:
            true
        case is Medicine, is Weight, is Height, is Crying, is Note, is Illness, is Temperature:
            // Плач и заметки без сна и кормлений режим дня не описывают (ADR-0030, ADR-0031).
            false
        default:
            false
        }
    }
}
