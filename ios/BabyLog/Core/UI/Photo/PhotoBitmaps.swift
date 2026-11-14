import ImageIO
import UIKit

enum PhotoBitmapError: Error {
    case notImage
}

/**
 Фото из файла `path`, уменьшенное уже при декодировании — не больше `maxSide` по длинной стороне
 (вдвое за шаг, поэтому может выйти и меньше) — и повёрнутое по EXIF: камера записывает поворот
 в теги, а не в пиксели. Точный размер даёт `scaledToFit`.
 */
func loadUpright(path: String, maxSide: Int) throws -> UIImage {
    precondition(maxSide > 0)
    let source = CGImageSourceCreateWithURL(URL(fileURLWithPath: path) as CFURL, nil)
    guard let source, let properties = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any],
          let width = (properties[kCGImagePropertyPixelWidth] as? NSNumber)?.intValue,
          let height = (properties[kCGImagePropertyPixelHeight] as? NSNumber)?.intValue,
          width > 0, height > 0
    else { throw PhotoBitmapError.notImage }

    var sample = 1
    while max(width, height) / sample > maxSide { sample *= 2 }
    let longSide = max(width, height)
    let target = max(1, Int((Double(longSide) / Double(sample)).rounded()))
    guard let image = decodeImage(source: source, maxPixelSize: target) else { throw PhotoBitmapError.notImage }
    return image
}

/**
 Не больше `maxSide` по длинной стороне; меньшее не увеличивается. Уменьшает вдвое за шаг, потом
 до точного размера: одно сильное уменьшение с фильтром даёт зубцы и муар.
 */
extension UIImage {
    func scaledToFit(_ maxSide: Int) -> UIImage {
        precondition(maxSide > 0)
        var result = self
        while max(result.pixelWidth, result.pixelHeight) >= maxSide * 2 {
            result = result.resizedPixels(width: max(1, result.pixelWidth / 2), height: max(1, result.pixelHeight / 2))
        }
        let longSide = max(result.pixelWidth, result.pixelHeight)
        if longSide <= maxSide { return result }
        let factor = Double(maxSide) / Double(longSide)
        return result.resizedPixels(
            width: max(1, Int((Double(result.pixelWidth) * factor).rounded())),
            height: max(1, Int((Double(result.pixelHeight) * factor).rounded()))
        )
    }

    /** JPEG качества `quality`. Прозрачное (PNG) — на белом: в JPEG прозрачности нет, без фона оно стало бы чёрным. */
    func toJpeg(quality: Int) -> Data {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = true
        let opaque = UIGraphicsImageRenderer(size: CGSize(width: pixelWidth, height: pixelHeight), format: format).image { _ in
            UIColor(white: 1, alpha: 1).setFill()
            UIRectFill(CGRect(x: 0, y: 0, width: pixelWidth, height: pixelHeight))
            draw(in: CGRect(x: 0, y: 0, width: pixelWidth, height: pixelHeight))
        }
        return opaque.jpegData(compressionQuality: CGFloat(quality) / 100) ?? Data()
    }

    var pixelWidth: Int { cgImage?.width ?? Int((size.width * scale).rounded()) }
    var pixelHeight: Int { cgImage?.height ?? Int((size.height * scale).rounded()) }

    func resizedPixels(width: Int, height: Int) -> UIImage {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = false
        return UIGraphicsImageRenderer(size: CGSize(width: width, height: height), format: format).image { _ in
            draw(in: CGRect(x: 0, y: 0, width: width, height: height))
        }
    }
}

/**
 JPEG `jpeg`, уменьшенный при декодировании — не мельче `minSidePx` по короткой стороне: миниатюре
 и аватару хватает доли пикселей, а уменьшение больше чем вдвое при отрисовке даёт зубцы.
 nil — не декодируется.
 */
func decodeSampled(jpeg: Data, minSidePx: Int) -> UIImage? {
    guard let source = CGImageSourceCreateWithData(jpeg as CFData, nil),
          let properties = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any],
          let width = (properties[kCGImagePropertyPixelWidth] as? NSNumber)?.intValue,
          let height = (properties[kCGImagePropertyPixelHeight] as? NSNumber)?.intValue
    else { return nil }
    let shortSide = min(width, height)
    guard shortSide > 0 else { return nil }
    var sample = 1
    while shortSide / (sample * 2) >= minSidePx { sample *= 2 }
    let target = max(1, Int((Double(max(width, height)) / Double(sample)).rounded()))
    return decodeImage(source: source, maxPixelSize: target)
}

private func decodeImage(source: CGImageSource, maxPixelSize: Int) -> UIImage? {
    let options: [CFString: Any] = [
        kCGImageSourceCreateThumbnailFromImageAlways: true,
        kCGImageSourceCreateThumbnailWithTransform: true,
        kCGImageSourceThumbnailMaxPixelSize: maxPixelSize,
        kCGImageSourceShouldCacheImmediately: true,
    ]
    return CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary).map {
        UIImage(cgImage: $0, scale: 1, orientation: .up)
    }
}
