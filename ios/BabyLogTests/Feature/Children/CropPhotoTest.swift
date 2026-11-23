import Foundation
import Testing
import UIKit
@testable import BabyLog

/** Кадр аватара из фото — JPEG 512×512 выбранного квадрата. */
@MainActor
@Suite(.serialized)
struct CropPhotoTest {
    @Test func cropGivesA512SquareJpegOfTheChosenSquare() throws {
        let photo = cropPhoto(
            source: testBitmap(width: 4000, height: 3000),
            square: CropSquare(left: 0, top: 0, side: 1500)
        )
        let decoded = try #require(decodeSampled(jpeg: photo.jpeg, minSidePx: Int.max))
        #expect(decoded.pixelWidth == ChildPhoto.size && decoded.pixelHeight == ChildPhoto.size)
        assertColor(testSky, pixelColor(image: decoded, x: ChildPhoto.size / 2, y: ChildPhoto.size / 2))
    }

    @Test func smallCropIsEnlargedTo512() throws {
        let photo = cropPhoto(
            source: testBitmap(width: 400, height: 300),
            square: CropSquare(left: 0, top: 0, side: 100)
        )
        let decoded = try #require(decodeSampled(jpeg: photo.jpeg, minSidePx: Int.max))
        #expect(decoded.pixelWidth == ChildPhoto.size && decoded.pixelHeight == ChildPhoto.size)
    }
}
