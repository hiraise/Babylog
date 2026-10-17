// Платформенный ExifInterface (ADR-0023): известные уязвимости, из-за которых lint советует
// androidx-версию, исправлены до Android 8 — минимальной версии приложения
@file:SuppressLint("ExifInterface")

package com.kid.babylog.core.ui.photo

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

/**
 * Фото из файла [path], уменьшенное уже при декодировании — не больше [maxSide] по длинной стороне
 * (вдвое за шаг, поэтому может выйти и меньше) — и повёрнутое по EXIF: камера записывает поворот
 * в теги, а не в пиксели. Точный размер даёт [scaledToFit].
 */
fun loadUpright(path: String, maxSide: Int): Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Файл — не изображение" }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
    val bitmap = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        ?: error("Файл — не изображение")
    // EXIF есть не у всех форматов; нет тега — фото уже стоит как надо
    val orientation = runCatching {
        ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    val matrix = orientationMatrix(orientation) ?: return bitmap
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

/** Поворот и отражение, которые ставят фото с тегом [orientation] прямо; null — ничего не нужно. */
private fun orientationMatrix(orientation: Int): Matrix? {
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> matrix.apply { setRotate(90f); postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
        ExifInterface.ORIENTATION_TRANSVERSE -> matrix.apply { setRotate(-90f); postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
        else -> return null
    }
    return matrix
}

/**
 * Не больше [maxSide] по длинной стороне; меньшее не увеличивается. Уменьшает вдвое за шаг, потом
 * до точного размера: одно сильное уменьшение с фильтром даёт зубцы и муар.
 */
fun Bitmap.scaledToFit(maxSide: Int): Bitmap {
    var result = this
    while (maxOf(result.width, result.height) >= maxSide * 2) result = result.scale(result.width / 2, result.height / 2)
    val long = maxOf(result.width, result.height)
    if (long <= maxSide) return result
    val factor = maxSide.toFloat() / long
    return result.scale((result.width * factor).roundToInt(), (result.height * factor).roundToInt())
}

/** JPEG качества [quality]. Прозрачное (PNG) — на белом: в JPEG прозрачности нет, без фона оно стало бы чёрным. */
fun Bitmap.toJpeg(quality: Int): ByteArray {
    val opaque = if (!hasAlpha()) {
        this
    } else {
        createBitmap(width, height).also { out ->
            Canvas(out).apply {
                drawColor(Color.WHITE)
                drawBitmap(this@toJpeg, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
            }
        }
    }
    return ByteArrayOutputStream().also { opaque.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
}

/**
 * JPEG [jpeg], уменьшенный при декодировании — не мельче [minSidePx] по короткой стороне: миниатюре
 * и аватару хватает доли пикселей, а уменьшение больше чем вдвое при отрисовке даёт зубцы.
 * null — не декодируется.
 */
fun decodeSampled(jpeg: ByteArray, minSidePx: Int): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
    val short = minOf(bounds.outWidth, bounds.outHeight)
    if (short <= 0) return null
    var sample = 1
    while (short / (sample * 2) >= minSidePx) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, options)?.asImageBitmap()
}
