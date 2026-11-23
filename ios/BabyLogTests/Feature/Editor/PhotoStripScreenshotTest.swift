import SwiftUI
import Testing
@testable import BabyLog

/**
 Фото в форме подгузника: без фото — только «добавить»; два фото и «добавить»; шесть
 фото — «добавить» больше нет. Светлая и тёмная тема, на фоне листа.
 */
@MainActor
@Suite(.serialized)
struct PhotoStripScreenshotTest {
    @Test(arguments: [false, true]) func photoStripStates(dark: Bool) {
        let clock = TestClock(date: Date(timeIntervalSince1970: 1_792_057_200), timeZone: .utc)
        let landscape = testBitmap(width: 400, height: 300).toJpeg(quality: 85)
        let portrait = testBitmap(width: 300, height: 400).toJpeg(quality: 85)
        let jpegOf = { (photo: FormPhoto) -> Data? in
            guard case let .saved(id) = photo else { return nil }
            return id % 2 == 0 ? portrait : landscape
        }
        assertComponentSnapshot(
            name: dark ? "dark" : "light",
            style: dark ? .dark : .light,
            width: 360,
            height: 340,
            clock: clock
        ) {
            PhotoStripSnapshotContent(jpegOf: jpegOf)
        }
    }
}

private struct PhotoStripSnapshotContent: View {
    @Environment(\.babyLogTheme) private var theme
    let jpegOf: (FormPhoto) -> Data?

    var body: some View {
        let two: [FormPhoto] = [.saved(id: 1), .saved(id: 2)]
        let six = (Int64(1)...6).map { FormPhoto.saved(id: $0) }
        VStack(spacing: theme.spacing.m) {
            PhotoStrip(photos: [], jpegOf: jpegOf, canAdd: true, processing: false, onAdd: {}, onOpen: { _ in })
            PhotoStrip(photos: two, jpegOf: jpegOf, canAdd: true, processing: false, onAdd: {}, onOpen: { _ in })
            PhotoStrip(photos: six, jpegOf: jpegOf, canAdd: false, processing: false, onAdd: {}, onOpen: { _ in })
        }
        .padding(theme.spacing.l)
        .background(theme.colors.surfaceContainerHigh)
    }
}

private extension TimeZone {
    static var utc: TimeZone { TimeZone(secondsFromGMT: 0)! }
}
