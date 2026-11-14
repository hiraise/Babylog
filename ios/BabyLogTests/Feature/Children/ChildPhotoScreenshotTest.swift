import SwiftUI
import Testing
import UIKit
@testable import BabyLog

/** Аватар с фото, кнопка фото и кадрирование в светлой и тёмной теме. */
@MainActor
@Suite(.serialized)
struct ChildPhotoScreenshotTest {
    @Test(arguments: [false, true]) func photoAvatarPhotoButtonAndCropArea(dark: Bool) {
        let clock = TestClock(date: Date(timeIntervalSince1970: 1_792_057_200), timeZone: .utc)
        assertComponentSnapshot(
            name: dark ? "dark" : "light",
            style: dark ? .dark : .light,
            width: 393,
            height: 380,
            clock: clock
        ) {
            ChildPhotoSnapshotContent(photo: testPhoto(), image: testBitmap())
        }
    }
}

private struct ChildPhotoSnapshotContent: View {
    @Environment(\.babyLogTheme) private var theme
    let photo: ChildPhoto
    let image: UIImage

    var body: some View {
        VStack(spacing: theme.spacing.m) {
            HStack(spacing: theme.spacing.m) {
                Avatar(sex: .girl, photo: photo.jpeg)
                PhotoButton(sex: .girl, photo: nil, onClick: {})
                PhotoButton(sex: .girl, photo: photo, onClick: {})
            }
            HStack(spacing: theme.spacing.m) {
                CropArea(image: image, frame: { CropFrame() }, onFrameChange: { _ in })
                    .frame(width: 140, height: 140)
                CropArea(
                    image: image,
                    frame: { CropFrame(zoom: 2, centerX: 0.4, centerY: 0.4) },
                    onFrameChange: { _ in }
                )
                .frame(width: 140, height: 140)
            }
        }
        .padding(theme.spacing.l)
        .background(theme.colors.surfaceContainerHigh)
    }
}

private extension TimeZone {
    static var utc: TimeZone { TimeZone(secondsFromGMT: 0)! }
}
