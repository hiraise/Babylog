package com.kid.babylog.feature.children

import android.graphics.BitmapFactory
import com.kid.babylog.core.model.ChildPhoto
import com.kid.babylog.testing.SKY
import com.kid.babylog.testing.assertColor
import com.kid.babylog.testing.testBitmap
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** Кадр аватара из фото — JPEG 512×512 выбранного квадрата (ADR-0023). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CropPhotoTest {

    @Test
    fun `crop gives a 512 px square JPEG of the chosen square`() {
        val photo = cropPhoto(testBitmap(width = 4000, height = 3000), CropSquare(left = 0f, top = 0f, side = 1500f))
        val decoded = BitmapFactory.decodeByteArray(photo.jpeg, 0, photo.jpeg.size)
        assertEquals(ChildPhoto.SIZE to ChildPhoto.SIZE, decoded.width to decoded.height)
        // Левый верхний угол «фото» — небо
        assertColor(SKY, decoded.getPixel(ChildPhoto.SIZE / 2, ChildPhoto.SIZE / 2))
    }

    @Test
    fun `small crop is enlarged to 512 px`() {
        val photo = cropPhoto(testBitmap(width = 400, height = 300), CropSquare(left = 0f, top = 0f, side = 100f))
        val decoded = BitmapFactory.decodeByteArray(photo.jpeg, 0, photo.jpeg.size)
        assertEquals(ChildPhoto.SIZE to ChildPhoto.SIZE, decoded.width to decoded.height)
    }
}
