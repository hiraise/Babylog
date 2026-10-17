package com.kid.babylog.core.ui.photo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.ExifInterface
import androidx.test.platform.app.InstrumentationRegistry
import com.kid.babylog.core.model.EventPhoto
import com.kid.babylog.testing.assertColor
import com.kid.babylog.testing.testBitmap
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Обработка фото на настоящих Bitmap (Robolectric NATIVE): поворот по EXIF, размер, JPEG (ADR-0023). */
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

        val upright = loadUpright(file.path, maxSide = 2048)

        assertEquals(100 to 200, upright.width to upright.height)
        // Поворот на 90° по часовой: левая половина стала верхней
        assertColor(Color.RED, upright.getPixel(50, 40))
        assertColor(Color.BLUE, upright.getPixel(50, 160))
    }

    @Test
    fun `big photo is decoded reduced to at most the given long side`() {
        val file = save(testBitmap(width = 5000, height = 1000))
        val loaded = loadUpright(file.path, maxSide = 2048)
        assertEquals(1250 to 250, loaded.width to loaded.height)
    }

    @Test
    fun `scaled to fit keeps the proportions and never enlarges`() {
        assertEquals(1600 to 1200, testBitmap(width = 4000, height = 3000).scaledToFit(1600).let { it.width to it.height })
        assertEquals(900 to 1600, testBitmap(width = 1800, height = 3200).scaledToFit(1600).let { it.width to it.height })
        assertEquals(400 to 300, testBitmap(width = 400, height = 300).scaledToFit(1600).let { it.width to it.height })
    }

    @Test
    fun `transparent picture becomes white, not black`() {
        val jpeg = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).toJpeg(90)
        assertColor(Color.WHITE, BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size).getPixel(10, 10))
    }

    @Test
    fun `thumbnail is decoded reduced but not below the asked size`() {
        val jpeg = testBitmap(width = 1600, height = 1200).toJpeg(85)
        // Короткая сторона 1200: вдвое — 600, вчетверо — 300 ≥ 200, ввосьмеро — 150 < 200
        assertEquals(400 to 300, decodeSampled(jpeg, minSidePx = 200)!!.let { it.width to it.height })
        assertEquals(null, decodeSampled(byteArrayOf(1, 2, 3), minSidePx = 200))
    }

    @Test
    fun `event photo is reduced to 1600 px into a new cache file and keeps the source`() {
        val source = save(testBitmap(width = 4000, height = 3000))
        val prepared = File(prepareEventPhoto(InstrumentationRegistry.getInstrumentation().targetContext, source.path))
        val decoded = BitmapFactory.decodeFile(prepared.path)
        assertEquals(EventPhoto.MAX_SIDE to 1200, decoded.width to decoded.height)
        assertEquals(true, source.exists())
        assertEquals("photos", prepared.parentFile?.name)
    }

    private fun save(bitmap: Bitmap): File =
        tmp.newFile("photo.jpg").also { file -> file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) } }
}
