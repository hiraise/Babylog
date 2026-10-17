package com.kid.babylog.testing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.kid.babylog.core.model.ChildPhoto
import java.io.ByteArrayOutputStream

/**
 * «Фото» для тестов — рисуется кодом, файла в репозитории нет: небо сверху, трава снизу и
 * лицо-круг с глазами в центре. На скриншоте сразу видно, повёрнуто ли фото и как обрезано.
 * Нужен Robolectric с `GraphicsMode.NATIVE`: в остальных режимах Bitmap не рисует.
 */
fun testBitmap(width: Int = 400, height: Int = 300): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    Canvas(bitmap).apply {
        drawColor(SKY)
        paint.color = GRASS
        drawRect(0f, height * 0.7f, width.toFloat(), height.toFloat(), paint)
        val r = minOf(width, height) * 0.3f
        paint.color = FACE
        drawCircle(width / 2f, height / 2f, r, paint)
        paint.color = Color.BLACK
        drawCircle(width / 2f - r * 0.4f, height / 2f - r * 0.2f, r * 0.12f, paint)
        drawCircle(width / 2f + r * 0.4f, height / 2f - r * 0.2f, r * 0.12f, paint)
    }
    return bitmap
}

/** [testBitmap], обрезанный по центру в JPEG, как после кадрирования. */
fun testPhoto(): ChildPhoto {
    val bitmap = Bitmap.createScaledBitmap(testBitmap(width = 300, height = 300), ChildPhoto.SIZE, ChildPhoto.SIZE, true)
    val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    return ChildPhoto(bytes.toByteArray())
}

const val SKY = 0xFF8EC5FF.toInt()
const val GRASS = 0xFF5DA85A.toInt()
const val FACE = 0xFFF6C9A0.toInt()
