package com.kid.babylog.feature.children

import androidx.compose.ui.geometry.Offset

/** Во сколько раз фото можно увеличить в круге относительно «весь короткий край в круге». */
internal const val MAX_CROP_ZOOM = 4f

/**
 * Кадр фото в круге аватара (ADR-0022). [zoom] — от 1 (короткий край фото целиком в круге) до
 * [MAX_CROP_ZOOM]; [centerX], [centerY] — центр кадра в долях ширины и высоты фото. Доли, а не
 * пиксели экрана: кадр переживает поворот экрана, когда окно кадрирования меняет размер.
 */
internal data class CropFrame(val zoom: Float = 1f, val centerX: Float = 0.5f, val centerY: Float = 0.5f)

/** Квадрат кадра в пикселях фото: [left], [top] — угол, [side] — сторона. */
internal data class CropSquare(val left: Float, val top: Float, val side: Float)

/** Квадрат кадра в фото [width]×[height]. Он всегда внутри фото: круг не может оказаться пустым. */
internal fun CropFrame.square(width: Int, height: Int): CropSquare {
    val side = minOf(width, height) / zoom
    return CropSquare(
        left = (centerX * width - side / 2).coerceIn(0f, width - side),
        top = (centerY * height - side / 2).coerceIn(0f, height - side),
        side = side,
    )
}

/**
 * Кадр после жеста в квадратном окне со стороной [viewport] (в любых единицах — пикселях экрана
 * или долях окна): пальцы сдвинулись на [pan] и изменили масштаб в [zoomChange] раз вокруг
 * [centroid]. Точка фото под пальцами остаётся под ними, пока кадр не упрётся в край фото.
 */
internal fun CropFrame.transformed(
    width: Int,
    height: Int,
    viewport: Float,
    centroid: Offset,
    pan: Offset,
    zoomChange: Float,
): CropFrame {
    val old = square(width, height)
    // Точка фото, которая была под пальцами до этого шага жеста
    val anchorX = old.left + (centroid.x - pan.x) * old.side / viewport
    val anchorY = old.top + (centroid.y - pan.y) * old.side / viewport
    val newZoom = (zoom * zoomChange).coerceIn(1f, MAX_CROP_ZOOM)
    val side = minOf(width, height) / newZoom
    val left = (anchorX - centroid.x * side / viewport).coerceIn(0f, width - side)
    val top = (anchorY - centroid.y * side / viewport).coerceIn(0f, height - side)
    return CropFrame(newZoom, centerX = (left + side / 2) / width, centerY = (top + side / 2) / height)
}
