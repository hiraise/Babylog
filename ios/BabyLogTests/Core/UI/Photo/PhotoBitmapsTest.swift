import ImageIO
import Testing
import UIKit
import UniformTypeIdentifiers
@testable import BabyLog

/** Обработка фото на настоящих изображениях: поворот по EXIF, размер, JPEG. */
@MainActor
@Suite(.serialized)
struct PhotoBitmapsTest {
    @Test func cameraPhotoIsTurnedUprightByItsExifTag() throws {
        let directory = try makePhotoTestDirectory()
        defer { try? FileManager.default.removeItem(at: directory) }
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        let bitmap = UIGraphicsImageRenderer(size: CGSize(width: 200, height: 100), format: format).image { _ in
            UIColor.blue.setFill()
            UIRectFill(CGRect(x: 0, y: 0, width: 200, height: 100))
            UIColor.red.setFill()
            UIRectFill(CGRect(x: 0, y: 0, width: 100, height: 100))
        }
        let file = try savePhoto(bitmap, to: directory, orientation: 6)

        let upright = try loadUpright(path: file.path, maxSide: 2048)

        #expect(upright.pixelWidth == 100 && upright.pixelHeight == 200)
        assertColor(.red, pixelColor(image: upright, x: 50, y: 40))
        assertColor(.blue, pixelColor(image: upright, x: 50, y: 160))
    }

    @Test func bigPhotoIsDecodedReducedToAtMostTheGivenLongSide() throws {
        let directory = try makePhotoTestDirectory()
        defer { try? FileManager.default.removeItem(at: directory) }
        let file = try savePhoto(testBitmap(width: 5000, height: 1000), to: directory)
        let loaded = try loadUpright(path: file.path, maxSide: 2048)
        #expect(loaded.pixelWidth == 1250 && loaded.pixelHeight == 250)
    }

    @Test func scaledToFitKeepsTheProportionsAndNeverEnlarges() {
        let landscape = testBitmap(width: 4000, height: 3000).scaledToFit(1600)
        let portrait = testBitmap(width: 1800, height: 3200).scaledToFit(1600)
        let small = testBitmap(width: 400, height: 300).scaledToFit(1600)
        #expect(landscape.pixelWidth == 1600 && landscape.pixelHeight == 1200)
        #expect(portrait.pixelWidth == 900 && portrait.pixelHeight == 1600)
        #expect(small.pixelWidth == 400 && small.pixelHeight == 300)
    }

    @Test func transparentPictureBecomesWhiteNotBlack() {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = false
        let transparent = UIGraphicsImageRenderer(size: CGSize(width: 100, height: 100), format: format).image { _ in }
        let jpeg = transparent.toJpeg(quality: 90)
        let decoded = decodeSampled(jpeg: jpeg, minSidePx: Int.max)
        assertColor(.white, pixelColor(image: decoded!, x: 10, y: 10))
    }

    @Test func thumbnailIsDecodedReducedButNotBelowTheAskedSize() throws {
        let jpeg = testBitmap(width: 1600, height: 1200).toJpeg(quality: 85)
        let decoded = try #require(decodeSampled(jpeg: jpeg, minSidePx: 200))
        #expect(decoded.pixelWidth == 400 && decoded.pixelHeight == 300)
        #expect(decodeSampled(jpeg: Data([1, 2, 3]), minSidePx: 200) == nil)
    }

    @Test func eventPhotoIsReducedTo1600IntoANewCacheFileAndKeepsTheSource() throws {
        let directory = try makePhotoTestDirectory()
        defer { try? FileManager.default.removeItem(at: directory) }
        let source = try savePhoto(testBitmap(width: 4000, height: 3000), to: directory)
        let preparedPath = try prepareEventPhoto(source: source.path)
        defer { deletePhotoFile(path: preparedPath) }
        let prepared = URL(fileURLWithPath: preparedPath)
        let decoded = try #require(decodeSampled(jpeg: Data(contentsOf: prepared), minSidePx: Int.max))
        #expect(decoded.pixelWidth == EventPhoto.maxSide && decoded.pixelHeight == 1200)
        #expect(FileManager.default.fileExists(atPath: source.path))
        #expect(prepared.deletingLastPathComponent().lastPathComponent == "photos")
    }

    @Test func tooDetailedPhotoIsCompressedAndThenReducedUntilItFitsADatabaseRow() throws {
        let jpeg = fittingJpeg(bitmap: noisePhoto(width: 400, height: 400), maxBytes: 40_000)
        #expect(jpeg.count <= 40_000)
        let decoded = try #require(decodeSampled(jpeg: jpeg, minSidePx: Int.max))
        #expect(decoded.pixelWidth < 400)
    }
}

private func makePhotoTestDirectory() throws -> URL {
    let directory = FileManager.default.temporaryDirectory.appendingPathComponent("PhotoBitmapsTest-\(UUID().uuidString)", isDirectory: true)
    try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
    return directory
}

private func savePhoto(_ image: UIImage, to directory: URL, orientation: Int? = nil) throws -> URL {
    let file = directory.appendingPathComponent("\(UUID().uuidString).jpg")
    guard let destination = CGImageDestinationCreateWithURL(file as CFURL, UTType.jpeg.identifier as CFString, 1, nil) else {
        throw PhotoBitmapError.notImage
    }
    var options: [CFString: Any] = [kCGImageDestinationLossyCompressionQuality: 0.95]
    if let orientation { options[kCGImagePropertyOrientation] = orientation }
    CGImageDestinationAddImage(destination, image.cgImage!, options as CFDictionary)
    guard CGImageDestinationFinalize(destination) else { throw PhotoBitmapError.notImage }
    return file
}

private func noisePhoto(width: Int, height: Int) -> UIImage {
    let count = width * height * 4
    let bytes = UnsafeMutablePointer<UInt8>.allocate(capacity: count)
    defer { bytes.deallocate() }
    var random: UInt32 = 1
    for index in stride(from: 0, to: count, by: 4) {
        random = random &* 1_664_525 &+ 1_013_904_223
        bytes[index] = UInt8(truncatingIfNeeded: random >> 16)
        bytes[index + 1] = UInt8(truncatingIfNeeded: random >> 8)
        bytes[index + 2] = UInt8(truncatingIfNeeded: random)
        bytes[index + 3] = 255
    }
    let context = CGContext(
        data: bytes,
        width: width,
        height: height,
        bitsPerComponent: 8,
        bytesPerRow: width * 4,
        space: CGColorSpaceCreateDeviceRGB(),
        bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
    )!
    return UIImage(cgImage: context.makeImage()!)
}
