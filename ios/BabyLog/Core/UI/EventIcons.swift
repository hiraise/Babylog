import Foundation

/**
 Иконка типа записи (ADR-0010) для истории, сводки и листов вместо эмодзи.
 Сторону кормления иконка не различает: она указана в тексте; у бутылочки — отдельный значок.
 */
func eventIcon(_ event: any BabyEvent) -> String {
    switch event {
    case is any FeedingEvent: "ic_feeding"
    case is any BottleEvent: "ic_bottle"
    case let event as any DiaperEvent: diaperIcon(event.kind)
    case is any SleepEvent: "ic_sleep"
    case is any MedicineEvent: "ic_medicine"
    case is any WeightEvent: "ic_weight"
    case is any HeightEvent: "ic_height"
    case is any CryingEvent: "ic_crying"
    case is any NoteEvent: "ic_note"
    case is any IllnessEvent: "ic_illness"
    case is any TemperatureEvent: "ic_thermometer"
    default: ""
    }
}

/** Капля — пописал, волны — покакал, капля и волны — и то, и то (свои иконки ADR-0014). */
func diaperIcon(_ kind: DiaperKind) -> String {
    switch kind {
    case .wet: "ic_diaper_wet"
    case .dirty: "ic_diaper_dirty"
    case .mixed: "ic_diaper_mixed"
    }
}
