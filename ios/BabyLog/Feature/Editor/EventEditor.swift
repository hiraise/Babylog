import SwiftUI

/** Кнопки добавления: плитки «Сегодня» и «Заметка». Сон, кормление и плач начинаются отдельно. */
enum NewEventKind: Sendable {
    case diaper
    case medicine
    case weight
    case height
    case note
}

/** Лист новой записи; подсказки и метки строятся по записям выбранного ребёнка. */
struct NewEventSheet: View {
    @Environment(\.clock) private var clock
    @Binding var isPresented: Bool
    let kind: NewEventKind
    let child: Child
    let events: [any BabyEvent]
    let onDismiss: () -> Void
    let onSave: (any BabyEvent, PhotoEdit?) -> Void

    var body: some View {
        switch kind {
        case .diaper:
            DiaperSheet(isPresented: $isPresented, initial: nil, child: child, initialPhotos: [], onDismiss: onDismiss) { kind, time, note, photos in
                onSave(Diaper(childId: child.id, start: time, kind: kind, note: note), photos)
            }
        case .medicine:
            let illness = events.compactMap { $0 as? Illness }.first(where: \.isActive)
            MedicineSheet(
                isPresented: $isPresented,
                initial: nil,
                pastMedicines: pastMedicines(events: events),
                illnessMedicines: illness.map { events.illnessMedicines($0, now: clock.millis()) } ?? [],
                onDismiss: onDismiss
            ) { name, time, note in
                onSave(Medicine(childId: child.id, start: time, name: name, note: note), nil)
            }
        case .weight:
            WeightSheet(isPresented: $isPresented, initial: nil, onDismiss: onDismiss) { grams, time, note in
                onSave(Weight(childId: child.id, start: time, grams: grams, note: note), nil)
            }
        case .height:
            HeightSheet(isPresented: $isPresented, initial: nil, onDismiss: onDismiss) { millimeters, time, note in
                onSave(Height(childId: child.id, start: time, millimeters: millimeters, note: note), nil)
            }
        case .note:
            NoteSheet(
                isPresented: $isPresented,
                initial: nil,
                tagOptions: noteTagChoices(events: events),
                initialPhotos: [],
                onDismiss: onDismiss
            ) { text, tags, time, photos in
                onSave(Note(childId: child.id, start: time, tags: tags, note: text), photos)
            }
        }
    }
}

/** Лист правки записи; id и ребёнок сохраняются. */
struct EditEventSheet: View {
    @Binding var isPresented: Bool
    let event: any BabyEvent
    let child: Child
    let events: [any BabyEvent]
    let photos: [EventPhoto]
    let cryingChoices: CryingChoices
    let onDismiss: () -> Void
    let onSave: (any BabyEvent, PhotoEdit?) -> Void

    init(
        isPresented: Binding<Bool>,
        event: any BabyEvent,
        child: Child,
        events: [any BabyEvent],
        photos: [EventPhoto],
        cryingChoices: CryingChoices,
        onDismiss: @escaping () -> Void,
        onSave: @escaping (any BabyEvent, PhotoEdit?) -> Void
    ) {
        _isPresented = isPresented
        self.event = event
        self.child = child
        self.events = events
        self.photos = photos
        self.cryingChoices = cryingChoices
        self.onDismiss = onDismiss
        self.onSave = onSave
    }

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
        case let initial as Medicine:
            MedicineSheet(isPresented: $isPresented, initial: initial, pastMedicines: pastMedicines(events: events), onDismiss: onDismiss) { name, time, note in
                var edited = initial
                edited.name = name
                edited.start = time
                edited.note = note
                onSave(edited, nil)
            }
        case let initial as Weight:
            WeightSheet(isPresented: $isPresented, initial: initial, onDismiss: onDismiss) { grams, time, note in
                var edited = initial
                edited.grams = grams
                edited.start = time
                edited.note = note
                onSave(edited, nil)
            }
        case let initial as Height:
            HeightSheet(isPresented: $isPresented, initial: initial, onDismiss: onDismiss) { millimeters, time, note in
                var edited = initial
                edited.millimeters = millimeters
                edited.start = time
                edited.note = note
                onSave(edited, nil)
            }
        case let initial as Note:
            NoteSheet(
                isPresented: $isPresented,
                initial: initial,
                tagOptions: noteTagChoices(events: events),
                initialPhotos: photos,
                onDismiss: onDismiss
            ) { text, tags, time, edit in
                var edited = initial
                edited.note = text
                edited.tags = tags
                edited.start = time
                onSave(edited, edit)
            }
        case let initial as Temperature:
            TemperatureSheet(
                isPresented: $isPresented,
                initial: initial,
                last: events.compactMap { $0 as? Temperature }.max { $0.start < $1.start },
                sex: child.sex,
                offerIllness: false,
                onDismiss: onDismiss
            ) { tenths, method, time, note, _ in
                var edited = initial
                edited.tenths = tenths
                edited.method = method
                edited.start = time
                edited.note = note
                onSave(edited, nil)
            }
        case let initial as Illness:
            IllnessEditSheet(isPresented: $isPresented, initial: initial, onDismiss: onDismiss) { start, end, note in
                var edited = initial
                edited.start = start
                edited.end = end
                edited.note = note
                onSave(edited, nil)
            }
        default:
            EmptyView()
        }
    }
}
