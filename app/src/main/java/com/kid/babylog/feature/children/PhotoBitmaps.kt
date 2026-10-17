// Платформенный ExifInterface (ADR-0022): известные уязвимости, из-за которых lint советует
// androidx-версию, исправлены до Android 8 — минимальной версии приложения
@file:SuppressLint("ExifInterface")

package com.kid.babylog.feature.children

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.media.ExifInterface
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import com.kid.babylog.core.model.ChildPhoto
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

/** Длинная сторона фото в окне кадрирования: при [MAX_CROP_ZOOM] в кадре остаётся ~512 px. */
private const val CROP_SOURCE_MAX = 2048

private const val JPEG_QUALITY = 90

/**
 * Фото из файла [path] для кадрирования: уменьшенное при декодировании до [CROP_SOURCE_MAX] и
 * повёрнутое по EXIF — камера записывает поворот в теги, а не в пиксели.
 */
internal fun loadForCrop(path: String): Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Файл — не изображение" }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > CROP_SOURCE_MAX) sample *= 2
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
 * Квадрат [square] из [source] — в JPEG [ChildPhoto.SIZE]×[ChildPhoto.SIZE]. Большой кадр
 * уменьшается вдвое за шаг: одно сильное уменьшение с фильтром даёт зубцы и муар. Прозрачное
 * (PNG) — на белом: в JPEG прозрачности нет, и без фона оно стало бы чёрным.
 */
internal fun cropPhoto(source: Bitmap, square: CropSquare): ChildPhoto {
    val side = square.side.roundToInt().coerceIn(1, minOf(source.width, source.height))
    val left = square.left.roundToInt().coerceIn(0, source.width - side)
    val top = square.top.roundToInt().coerceIn(0, source.height - side)
    var cropped = Bitmap.createBitmap(source, left, top, side, side)
    while (cropped.width >= ChildPhoto.SIZE * 2) {
        cropped = cropped.scale(cropped.width / 2, cropped.height / 2)
    }
    val out = createBitmap(ChildPhoto.SIZE, ChildPhoto.SIZE)
    Canvas(out).apply {
        drawColor(Color.WHITE)
        drawBitmap(cropped, null, Rect(0, 0, ChildPhoto.SIZE, ChildPhoto.SIZE), Paint(Paint.FILTER_BITMAP_FLAG))
    }
    val bytes = ByteArrayOutputStream().also { out.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
    return ChildPhoto(bytes.toByteArray())
}
