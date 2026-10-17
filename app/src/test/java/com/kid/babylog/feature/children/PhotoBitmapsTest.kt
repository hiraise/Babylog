package com.kid.babylog.feature.children

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.ExifInterface
import com.kid.babylog.core.model.ChildPhoto
import com.kid.babylog.testing.SKY
import com.kid.babylog.testing.testBitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs

/** Обработка фото на настоящих Bitmap (Robolectric NATIVE): поворот по EXIF, размер, кадр (ADR-0022). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoBitmapsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `camera photo is turned upright by its EXIF tag`() {
        // Левая половина красная, правая синяя; камера сняла «боком» и записала поворот в EXIF
        val bitmap = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.BLUE)
            drawRect(0f, 0f, 100f, 100f, Paint().apply { color = Color.RED })
        }
        val file = save(bitmap)
        ExifInterface(file.path).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }

        val upright = loadForCrop(file.path)

        assertEquals(100 to 200, upright.width to upright.height)
        // Поворот на 90° по часовой: левая половина стала верхней
        assertColor(Color.RED, upright.getPixel(50, 40))
        assertColor(Color.BLUE, upright.getPixel(50, 160))
    }

    @Test
    fun `big photo is decoded reduced to at most 2048 px on the long side`() {
        val file = save(testBitmap(width = 5000, height = 1000))
        val loaded = loadForCrop(file.path)
        assertEquals(1250 to 250, loaded.width to loaded.height)
    }

    @Test
    fun `crop gives a 512 px square JPEG of the chosen square`() {
        val photo = cropPhoto(testBitmap(width = 4000, height = 3000), CropSquare(left = 0f, top = 0f, side = 1500f))
        val decoded = decode(photo)
        assertEquals(ChildPhoto.SIZE to ChildPhoto.SIZE, decoded.width to decoded.height)
        // Левый верхний угол «фото» — небо
        assertColor(SKY, decoded.getPixel(ChildPhoto.SIZE / 2, ChildPhoto.SIZE / 2))
    }

    @Test
    fun `transparent picture becomes white, not black`() {
        val transparent = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val decoded = decode(cropPhoto(transparent, CropSquare(0f, 0f, 100f)))
        assertColor(Color.WHITE, decoded.getPixel(10, 10))
    }

    private fun save(bitmap: Bitmap): File =
        tmp.newFile("photo.jpg").also { file -> file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) } }

    private fun decode(photo: ChildPhoto): Bitmap = BitmapFactory.decodeByteArray(photo.jpeg, 0, photo.jpeg.size)

    /** Цвет с допуском на сжатие JPEG. */
    private fun assertColor(expected: Int, actual: Int) {
        val close = listOf(Color::red, Color::green, Color::blue).all { channel -> abs(channel(expected) - channel(actual)) <= 16 }
        assertTrue("ожидался #%06X, а не #%06X".format(expected and 0xFFFFFF, actual and 0xFFFFFF), close)
    }
}
