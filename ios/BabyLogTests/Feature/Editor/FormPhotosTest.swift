import Foundation
import Testing
@testable import BabyLog

/** Фото формы подгузника и подпись к ним для «Поделиться». */
struct FormPhotosTest {
    private let zone = TimeZone(secondsFromGMT: 0)!
    private let time = formPhotoAt("2026-10-17T14:30:00Z")
    private var today: Date { toLocalDate(time, timeZone: zone) }

    @Test func shareTextSaysWhoWhatAndWhen() {
        let masha = Child(id: 1, name: " Маша ", birthDate: formPhotoAt("2026-06-05T00:00:00Z"), sex: .girl)
        #expect(diaperShareText(
            child: masha,
            kind: .dirty,
            time: time,
            note: " зелёный, жидкий ",
            today: today,
            timeZone: zone
        ) == "Маша, 4 мес 12 дн.\nПодгузник: покакала\n17 октября 2026, 14:30\nКомментарий: зелёный, жидкий")
    }

    @Test func shareTextWithoutBirthDateAndComment() {
        #expect(diaperShareText(
            child: Child(id: 2, name: "Петя", birthDate: nil, sex: .boy),
            kind: .mixed,
            time: time,
            note: "  ",
            today: today,
            timeZone: zone
        ) == "Петя\nПодгузник: и то, и то\n17 октября 2026, 14:30")
    }

    @Test func savedPhotosKeepTheirOrderAndNewOnesGoAfterThem() {
        let photos: [FormPhoto] = [.saved(id: 7), .pending(path: "/a"), .saved(id: 3), .pending(path: "/gone")]
        let edit = photos.toEdit { path in path == "/a" ? Data([1]) : nil }
        #expect(edit.keep == [7, 3])
        #expect(edit.add == [Data([1])])
    }

    @Test func savedStateStoresOnlyIdsAndPaths() {
        let photos: [FormPhoto] = [.saved(id: 7), .pending(path: "/tmp/photo.jpg")]
        #expect(FormPhotosSaver.restore(FormPhotosSaver.save(photos)) == photos)
    }
}

private func formPhotoAt(_ iso: String) -> Int64 {
    Int64(ISO8601DateFormatter().date(from: iso)!.timeIntervalSince1970 * 1000)
}
