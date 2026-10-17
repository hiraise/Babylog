package com.kid.babylog.feature.children

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

/** Кадр фото в круге (ADR-0023): квадрат всегда внутри фото, точка под пальцами остаётся под ними. */
class PhotoCropTest {

    @Test
    fun `initial frame is the short side in the middle`() {
        assertEquals(CropSquare(left = 500f, top = 0f, side = 3000f), CropFrame().square(4000, 3000))
        assertEquals(CropSquare(left = 0f, top = 500f, side = 3000f), CropFrame().square(3000, 4000))
    }

    @Test
    fun `pan moves the frame opposite to the fingers and stops at the edge`() {
        // Окно 300 px на кадр 3000 px: 1 px экрана — 10 px фото. Пальцы вправо — фото едет вправо,
        // в круг входит левая часть
        val moved = CropFrame().transformed(4000, 3000, 300f, centroid = Offset(150f, 150f), pan = Offset(20f, 0f), zoomChange = 1f)
        assertEquals(CropSquare(left = 300f, top = 0f, side = 3000f), moved.square(4000, 3000))

        val atEdge = CropFrame().transformed(4000, 3000, 300f, centroid = Offset(150f, 150f), pan = Offset(200f, 50f), zoomChange = 1f)
        assertEquals(CropSquare(left = 0f, top = 0f, side = 3000f), atEdge.square(4000, 3000))
    }

    @Test
    fun `zoom keeps the point under the fingers in place`() {
        val zoomed = CropFrame().transformed(4000, 3000, 300f, centroid = Offset(75f, 75f), pan = Offset.Zero, zoomChange = 2f)
        assertEquals(2f, zoomed.zoom)
        val square = zoomed.square(4000, 3000)
        // До жеста под пальцами точка (500 + 750, 750); после — та же точка в той же четверти окна
        assertEquals(1250f, square.left + 75f * square.side / 300f, 0.01f)
        assertEquals(750f, square.top + 75f * square.side / 300f, 0.01f)
    }

    @Test
    fun `zoom stays between whole short side and the limit`() {
        val frame = CropFrame()
        assertEquals(1f, frame.transformed(400, 300, 1f, Offset(0.5f, 0.5f), Offset.Zero, 0.5f).zoom)
        assertEquals(MAX_CROP_ZOOM, frame.transformed(400, 300, 1f, Offset(0.5f, 0.5f), Offset.Zero, 10f).zoom)
    }
}
