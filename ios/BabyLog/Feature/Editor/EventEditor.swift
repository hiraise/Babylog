import SwiftUI

/** Кнопки добавления: плитки «Сегодня» и «Заметка». Сон, кормление и плач начинаются отдельно. */
enum NewEventKind: Sendable {
    case diaper
    case medicine
    case weight
    case height
    case note
}

/** Лист новой записи; в этой сессии реализован подгузник, остальные формы подключают следующие сессии. */
struct NewEventSheet: View {
    @Binding var isPresented: Bool
    let kind: NewEventKind
    let child: Child
    let onDismiss: () -> Void
    let onSave: (any BabyEvent, PhotoEdit?) -> Void

    var body: some View {
        switch kind {
        case .diaper:
            DiaperSheet(isPresented: $isPresented, initial: nil, child: child, initialPhotos: [], onDismiss: onDismiss) { kind, time, note, photos in
                onSave(Diaper(childId: child.id, start: time, kind: kind, note: note), photos)
            }
        case .medicine, .weight, .height, .note:
            EmptyView()
        }
    }
}

/** Лист правки записи; id и ребёнок сохраняются. Формы следующих типов появятся в их сессиях переноса. */
struct EditEventSheet: View {
    @Binding var isPresented: Bool
    let event: any BabyEvent
    let child: Child
    let photos: [EventPhoto]
    let cryingChoices: CryingChoices
    let onDismiss: () -> Void
    let onSave: (any BabyEvent, PhotoEdit?) -> Void

    var body: some View {
        switch event {
        case let initial as Feeding:
            FeedingEditSheet(isPresented: $isPresented, initial: initial, onDismiss: onDismiss) { breast, start, end, note in
                var edited = initial
                edited.breast = breast
                edited.start = start
                edited.end = end
                edited.note = note
                onSave(edited, nil)
            }
        case let initial as Bottle:
            BottleSheet(isPresented: $isPresented, initial: initial, onDismiss: onDismiss) { volume, milk, time, note in
                var edited = initial
                edited.volumeMl = volume
                edited.milk = milk
                edited.start = time
                edited.note = note
                onSave(edited, nil)
            }
        case let initial as Diaper:
            DiaperSheet(isPresented: $isPresented, initial: initial, child: child, initialPhotos: photos, onDismiss: onDismiss) { kind, time, note, edit in
                var edited = initial
                edited.kind = kind
                edited.start = time
                edited.note = note
                onSave(edited, edit)
            }
        case let initial as Sleep:
            SleepEditSheet(isPresented: $isPresented, initial: initial, sex: child.sex, onDismiss: onDismiss) { start, end, note in
                var edited = initial
                edited.start = start
                edited.end = end
                edited.note = note
                onSave(edited, nil)
            }
        case let initial as Crying:
            CryingEditSheet(isPresented: $isPresented, initial: initial, choices: cryingChoices, onDismiss: onDismiss) { start, end, reasons, helped, note in
                var edited = initial
                edited.start = start
                edited.end = end
                edited.reasons = reasons
                edited.helped = helped
                edited.note = note
                onSave(edited, nil)
            }
        default:
            EmptyView()
        }
    }
}
