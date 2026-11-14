import CoreGraphics

/** Во сколько раз фото можно увеличить в круге относительно «весь короткий край в круге». */
let maxCropZoom: CGFloat = 4

/**
 Кадр фото в круге аватара. `zoom` — от 1 (короткий край фото целиком в круге) до
 `maxCropZoom`; `centerX`, `centerY` — центр кадра в долях ширины и высоты фото. Доли, а не
 пиксели экрана: кадр переживает поворот экрана, когда окно кадрирования меняет размер.
 */
struct CropFrame: Equatable {
    var zoom: CGFloat = 1
    var centerX: CGFloat = 0.5
    var centerY: CGFloat = 0.5
}

/** Квадрат кадра в пикселях фото: `left`, `top` — угол, `side` — сторона. */
struct CropSquare: Equatable {
    var left: CGFloat
    var top: CGFloat
    var side: CGFloat
}

extension CropFrame {
    /** Квадрат кадра в фото `width`×`height`. Он всегда внутри фото: круг не может оказаться пустым. */
    func square(width: Int, height: Int) -> CropSquare {
        let side = CGFloat(min(width, height)) / zoom
        return CropSquare(
            left: min(max(centerX * CGFloat(width) - side / 2, 0), CGFloat(width) - side),
            top: min(max(centerY * CGFloat(height) - side / 2, 0), CGFloat(height) - side),
            side: side
        )
    }

    /**
     Кадр после жеста в квадратном окне со стороной `viewport`: пальцы сдвинулись на `pan` и изменили
     масштаб в `zoomChange` раз вокруг `centroid`. Точка фото под пальцами остаётся под ними,
     пока кадр не упрётся в край фото.
     */
    func transformed(
        width: Int,
        height: Int,
        viewport: CGFloat,
        centroid: CGPoint,
        pan: CGPoint,
        zoomChange: CGFloat
    ) -> CropFrame {
        let old = square(width: width, height: height)
        let anchorX = old.left + (centroid.x - pan.x) * old.side / viewport
        let anchorY = old.top + (centroid.y - pan.y) * old.side / viewport
        let newZoom = min(max(zoom * zoomChange, 1), maxCropZoom)
        let side = CGFloat(min(width, height)) / newZoom
        let left = min(max(anchorX - centroid.x * side / viewport, 0), CGFloat(width) - side)
        let top = min(max(anchorY - centroid.y * side / viewport, 0), CGFloat(height) - side)
        return CropFrame(zoom: newZoom, centerX: (left + side / 2) / CGFloat(width), centerY: (top + side / 2) / CGFloat(height))
    }
}
