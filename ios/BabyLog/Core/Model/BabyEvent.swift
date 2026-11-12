import Foundation

/**
 Запись дневника. Время — epoch millis с точностью до секунды (ADR-0008).

 Новый тип записи — новый тип здесь. Все `switch` по `BabyEvent` пишутся без `default`,
 поэтому компилятор сам покажет места, которые нужно дополнить.
 */
protocol BabyEvent: Hashable, Sendable {
    var id: Int64 { get }
    var childId: Int64 { get }
    var start: Int64 { get }
    var end: Int64? { get }
    var note: String { get }
}

extension BabyEvent {
    var id: Int64 { 0 }
    var childId: Int64 { 0 }
    var isActive: Bool { end == nil }
}

protocol FeedingEvent: BabyEvent { var breast: Breast { get } }
protocol BottleEvent: BabyEvent { var volumeMl: Int { get }; var milk: Milk? { get } }
protocol DiaperEvent: BabyEvent { var kind: DiaperKind { get } }
protocol SleepEvent: BabyEvent {}
protocol MedicineEvent: BabyEvent { var name: String { get } }
protocol WeightEvent: BabyEvent { var grams: Int { get } }
protocol HeightEvent: BabyEvent { var millimeters: Int { get } }
protocol CryingEvent: BabyEvent { var reasons: [String] { get }; var helped: [String] { get } }
protocol NoteEvent: BabyEvent { var tags: [String] { get } }
protocol IllnessEvent: BabyEvent { var illnessTitle: String { get } }
protocol TemperatureEvent: BabyEvent { var tenths: Int { get }; var method: TemperatureMethod? { get } }

/** Кормление одной грудью; `end` = nil — кормление идёт сейчас (у ребёнка не больше одного). */
struct Feeding: FeedingEvent {
    var id: Int64 = 0
    var childId: Int64
    var start: Int64
    var end: Int64? = nil
    var breast: Breast
    var note: String = ""
}

/** Кормление из бутылочки (ADR-0025) — разовая запись без таймера. */
struct Bottle: BottleEvent {
    var id: Int64 = 0
    var childId: Int64
    var start: Int64
    var volumeMl: Int
    var milk: Milk? = nil
    var note: String = ""
    var end: Int64? { nil }

    /** Допустимый объём в миллилитрах (защита от опечаток). */
    static let range = 5...500
}

struct Diaper: DiaperEvent {
    var id: Int64 = 0
    var childId: Int64
    var start: Int64
    var kind: DiaperKind
    var note: String = ""
    var end: Int64? { nil }
}

/** Сон; `end` = nil — ребёнок спит сейчас (активный сон, у ребёнка не больше одного). */
struct Sleep: SleepEvent {
    var id: Int64 = 0
    var childId: Int64
    var start: Int64
    var end: Int64? = nil
    var note: String = ""
}

/** Плач (ADR-0030): причины — общие с заметками метки, «что помогло» — свой список. */
struct Crying: CryingEvent {
    var id: Int64 = 0
    var childId: Int64
    var start: Int64
    var end: Int64? = nil
    var reasons: [String] = []
    var helped: [String] = []
    var note: String = ""
}

/** Заметка (ADR-0031): текст в `note`, метки в `tags`; разовая, без конца. */
struct Note: NoteEvent {
    var id: Int64 = 0
    var childId: Int64
    var start: Int64
    var tags: [String] = []
    var note: String = ""
    var end: Int64? { nil }
}

struct Medicine: MedicineEvent {
    var id: Int64 = 0
    var childId: Int64
    var start: Int64
    var name: String
    var note: String = ""
    var end: Int64? { nil }
}

struct Weight: WeightEvent {
    var id: Int64 = 0
    var childId: Int64
    var start: Int64
    var grams: Int
    var note: String = ""
    var end: Int64? { nil }

    static let range = 300...30_000
}

/** Рост (ADR-0027) в миллиметрах. */
struct Height: HeightEvent {
    var id: Int64 = 0
    var childId: Int64
    var start: Int64
    var millimeters: Int
    var note: String = ""
    var end: Int64? { nil }

    static let range = 200...1500
}

/** Болезнь (ADR-0040) — период; `end` = nil — болеет сейчас. */
struct Illness: IllnessEvent {
    var id: Int64 = 0
    var childId: Int64
    var start: Int64
    var end: Int64? = nil
    var names: [IllnessName] = []
    var note: String = ""

    var illnessTitle: String { title() ?? "" }
}

/** Название болезни и день, с которого оно стоит; nil — с начала болезни. Дата — локальная полночь. */
struct IllnessName: Hashable, Sendable {
    var name: String
    var since: Date? = nil

    init(_ name: String, since: Date? = nil) {
        self.name = name
        self.since = since
    }
}

/** Температура (ADR-0041) в десятых градуса: `384` — 38,4 °C. */
struct Temperature: TemperatureEvent {
    var id: Int64 = 0
    var childId: Int64
    var start: Int64
    var tenths: Int
    var method: TemperatureMethod? = nil
    var note: String = ""
    var end: Int64? { nil }

    static let range = 340...430
}

/** Где мерили температуру. Код в базе и CSV — `rawValue`. */
enum TemperatureMethod: String, CaseIterable, Hashable, Sendable {
    case armpit = "ARMPIT"
    case ear = "EAR"
    case rectal = "RECTAL"
    case forehead = "FOREHEAD"
}

enum Breast: String, CaseIterable, Hashable, Sendable {
    case left = "LEFT"
    case right = "RIGHT"

    /** Другая грудь — для «Сменить грудь». */
    var opposite: Breast { self == .left ? .right : .left }
}

enum DiaperKind: String, CaseIterable, Hashable, Sendable {
    case wet = "WET"
    case dirty = "DIRTY"
    case mixed = "MIXED"
}

/** Что в бутылочке: смесь или сцеженное грудное молоко. Код в базе и CSV — `rawValue`. */
enum Milk: String, CaseIterable, Hashable, Sendable {
    case formula = "FORMULA"
    case breastMilk = "BREAST_MILK"
}
