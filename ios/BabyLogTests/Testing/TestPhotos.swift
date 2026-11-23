import Testing
import UIKit
@testable import BabyLog

/**
 «Фото» для тестов — рисуется кодом, файла в репозитории нет: небо сверху, трава снизу и
 лицо-круг с глазами в центре. На скриншоте сразу видно, повёрнуто ли фото и как обрезано.
 */
func testBitmap(width: Int = 400, height: Int = 300) -> UIImage {
    let format = UIGraphicsImageRendererFormat()
    format.scale = 1
    format.opaque = true
    return UIGraphicsImageRenderer(size: CGSize(width: width, height: height), format: format).image { _ in
        testSky.setFill()
        UIRectFill(CGRect(x: 0, y: 0, width: width, height: height))
        testGrass.setFill()
        UIRectFill(CGRect(x: 0, y: Double(height) * 0.7, width: Double(width), height: Double(height) * 0.3))
        let radius = Double(min(width, height)) * 0.3
        let center = CGPoint(x: Double(width) / 2, y: Double(height) / 2)
        testFace.setFill()
        UIBezierPath(ovalIn: CGRect(
            x: center.x - radius,
            y: center.y - radius,
            width: radius * 2,
            height: radius * 2
        )).fill()
        UIColor.black.setFill()
        for side in [-1.0, 1.0] {
            let eyeRadius = radius * 0.12
            UIBezierPath(ovalIn: CGRect(
                x: center.x + side * radius * 0.4 - eyeRadius,
                y: center.y - radius * 0.2 - eyeRadius,
                width: eyeRadius * 2,
                height: eyeRadius * 2
            )).fill()
        }
    }
}

/** `testBitmap`, обрезанный по центру в JPEG, как после кадрирования. */
func testPhoto() -> ChildPhoto {
    let bitmap = testBitmap(width: 300, height: 300).resizedPixels(width: ChildPhoto.size, height: ChildPhoto.size)
    return ChildPhoto(jpeg: bitmap.toJpeg(quality: 90))
}

let testSky = UIColor(red: 0x8E / 255, green: 0xC5 / 255, blue: 0xFF / 255, alpha: 1)
let testGrass = UIColor(red: 0x5D / 255, green: 0xA8 / 255, blue: 0x5A / 255, alpha: 1)
let testFace = UIColor(red: 0xF6 / 255, green: 0xC9 / 255, blue: 0xA0 / 255, alpha: 1)

func pixelColor(image: UIImage, x: Int, y: Int) -> UIColor {
    let format = UIGraphicsImageRendererFormat()
    format.scale = 1
    format.opaque = true
    let pixel = UIGraphicsImageRenderer(size: CGSize(width: 1, height: 1), format: format).image { _ in
        image.draw(in: CGRect(x: -CGFloat(x), y: -CGFloat(y), width: image.size.width, height: image.size.height))
    }
    let bytes = UnsafeMutablePointer<UInt8>.allocate(capacity: 4)
    defer { bytes.deallocate() }
    guard let context = CGContext(
        data: bytes,
        width: 1,
        height: 1,
        bitsPerComponent: 8,
        bytesPerRow: 4,
        space: CGColorSpaceCreateDeviceRGB(),
        bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
    ) else { return .clear }
    context.draw(pixel.cgImage!, in: CGRect(x: 0, y: 0, width: 1, height: 1))
    return UIColor(red: CGFloat(bytes[0]) / 255, green: CGFloat(bytes[1]) / 255, blue: CGFloat(bytes[2]) / 255, alpha: 1)
}

/** Цвет пикселя с допуском на сжатие JPEG. */
func assertColor(_ expected: UIColor, _ actual: UIColor) {
    var expectedRed: CGFloat = 0
    var expectedGreen: CGFloat = 0
    var expectedBlue: CGFloat = 0
    var expectedAlpha: CGFloat = 0
    var actualRed: CGFloat = 0
    var actualGreen: CGFloat = 0
    var actualBlue: CGFloat = 0
    var actualAlpha: CGFloat = 0
    expected.getRed(&expectedRed, green: &expectedGreen, blue: &expectedBlue, alpha: &expectedAlpha)
    actual.getRed(&actualRed, green: &actualGreen, blue: &actualBlue, alpha: &actualAlpha)
    #expect(abs(expectedRed - actualRed) * 255 <= 16)
    #expect(abs(expectedGreen - actualGreen) * 255 <= 16)
    #expect(abs(expectedBlue - actualBlue) * 255 <= 16)
}
